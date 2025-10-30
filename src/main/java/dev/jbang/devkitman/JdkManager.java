package dev.jbang.devkitman;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import dev.jbang.devkitman.jdkproviders.JBangJdkProvider;
import dev.jbang.devkitman.jdkproviders.LinkedJdkProvider;
import dev.jbang.devkitman.util.JavaUtils;

public class JdkManager {
	public static final int DEFAULT_JAVA_VERSION = 21;
	public final int defaultJavaVersion;

	private static final Logger LOGGER = Logger.getLogger(JdkManager.class.getName());

	private final List<JdkProvider> providers;

	private final JdkProvider defaultProvider;

	/**
	 * Creates a JDK manager that is configured exactly like the one used by JBang.
	 */
	public static JdkManager create() {
		Path installPath = JBangJdkProvider.getJBangJdkDir();
		JdkDiscovery.Config cfg = new JdkDiscovery.Config(installPath);
		cfg.properties().put("link", JBangJdkProvider.getJBangConfigDir().resolve("currentjdk").toString());
		return builder().providers(JdkProviders.instance().all(cfg)).build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		protected final List<JdkProvider> providers = new ArrayList<>();
		protected int defaultJavaVersion = DEFAULT_JAVA_VERSION;

		protected Builder() {
		}

		public Builder providers(JdkProvider... provs) {
			return providers(Arrays.asList(provs));
		}

		public Builder providers(List<JdkProvider> provs) {
			providers.addAll(provs);
			return this;
		}

		public Builder defaultJavaVersion(int defaultJavaVersion) {
			if (defaultJavaVersion < 1) {
				throw new IllegalArgumentException("Default Java version must be at least 1");
			}
			this.defaultJavaVersion = defaultJavaVersion;
			return this;
		}

		public JdkManager build() {
			if (providers.isEmpty()) {
				throw new IllegalStateException("No providers could be initialized. Aborting.");
			}
			return new JdkManager(providers, defaultJavaVersion);
		}
	}

	private JdkManager(List<JdkProvider> providers, int defaultJavaVersion) {
		assert defaultJavaVersion > 0;
		this.providers = Collections.unmodifiableList(providers);
		this.defaultJavaVersion = defaultJavaVersion;
		this.defaultProvider = provider("default");
		for (JdkProvider provider : providers) {
			provider.manager(this);
		}
		if (LOGGER.isLoggable(Level.FINE)) {
			LOGGER.fine(
					"Using JDK provider(s): "
							+ providers.stream()
								.map(p -> p.getClass().getSimpleName())
								.collect(Collectors.joining(", ")));
		}
	}

	public List<JdkProvider> providers() {
		return providers;
	}

	@NonNull
	private Stream<JdkProvider> providers(Predicate<JdkProvider> providerFilter) {
		return providers.stream().filter(providerFilter);
	}

	@Nullable
	private JdkProvider provider(String name) {
		return providers(JdkProvider.Predicates.name(name)).findFirst().orElse(null);
	}

	/**
	 * This method is like <code>getJdk()</code> but will make sure that the JDK
	 * being returned is actually installed. It will perform an installation if
	 * necessary.
	 *
	 * @param versionOrId A version pattern, id or <code>null</code>
	 * @return A <code>Jdk</code> object
	 * @throws IllegalArgumentException If no JDK could be found at all or if one
	 *                                  failed to install
	 */
	public Jdk.@NonNull InstalledJdk getOrInstallJdk(String versionOrId) {
		return getOrInstallJdk(versionOrId, JdkProvider.Predicates.all);
	}

	/**
	 * This method is like <code>getJdk()</code> but will make sure that the JDK
	 * being returned is actually installed. It will perform an installation if
	 * necessary.
	 *
	 * @param versionOrId    A version pattern, id or <code>null</code>
	 * @param providerFilter Only return JDKs from providers that match the filter
	 * @return A <code>Jdk</code> object
	 * @throws IllegalArgumentException If no JDK could be found at all or if one
	 *                                  failed to install
	 */
	public Jdk.@NonNull InstalledJdk getOrInstallJdk(String versionOrId,
			@NonNull Predicate<JdkProvider> providerFilter) {
		if (versionOrId != null) {
			if (JavaUtils.isRequestedVersion(versionOrId)) {
				return getOrInstallJdkByVersion(
						JavaUtils.minRequestedVersion(versionOrId),
						JavaUtils.isOpenVersion(versionOrId),
						providerFilter);
			} else {
				return getOrInstallJdkById(versionOrId, providerFilter);
			}
		} else {
			return getOrInstallJdkByVersion(0, true, providerFilter);
		}
	}

	/**
	 * This method is like <code>getJdkByVersion()</code> but will make sure that
	 * the JDK being returned is actually installed. It will perform an installation
	 * if necessary.
	 *
	 * @param requestedVersion The (minimal) version to return, can be 0
	 * @param openVersion      Return newer version if exact is not available
	 * @param providerFilter   Only return JDKs from providers that match the filter
	 * @return A <code>Jdk</code> object or <code>null</code>
	 * @throws IllegalArgumentException If no JDK could be found at all or if one
	 *                                  failed to install
	 */
	private Jdk.@NonNull InstalledJdk getOrInstallJdkByVersion(
			int requestedVersion,
			boolean openVersion,
			@NonNull Predicate<JdkProvider> providerFilter) {
		LOGGER.log(Level.FINE, "Looking for JDK: {0}", requestedVersion);
		Jdk jdk = getJdkByVersion(requestedVersion, openVersion, providerFilter);
		if (jdk == null) {
			if (requestedVersion > 0) {
				throw new IllegalArgumentException(
						"No suitable JDK was found for requested version: " + requestedVersion);
			} else {
				throw new IllegalArgumentException("No suitable JDK was found");
			}
		}
		Jdk.InstalledJdk ijdk = ensureInstalled(jdk);
		LOGGER.log(Level.FINE, "Using JDK: {0}", ijdk);
		return ijdk;
	}

	/**
	 * This method is like <code>getJdkByVersion()</code> but will make sure that
	 * the JDK being returned is actually installed. It will perform an installation
	 * if necessary.
	 *
	 * @param requestedId    The id of the JDK to return
	 * @param providerFilter Only return JDKs from providers that match the filter
	 * @return A <code>Jdk</code> object or <code>null</code>
	 * @throws IllegalArgumentException If no JDK could be found at all or if one
	 *                                  failed to install
	 */
	private Jdk.@NonNull InstalledJdk getOrInstallJdkById(
			@NonNull String requestedId, @NonNull Predicate<JdkProvider> providerFilter) {
		LOGGER.log(Level.FINE, "Looking for JDK: {0}", requestedId);
		Jdk jdk = getJdkById(requestedId, providerFilter);
		if (jdk == null) {
			throw new IllegalArgumentException(
					"No suitable JDK was found for requested id: " + requestedId);
		}
		Jdk.InstalledJdk ijdk = ensureInstalled(jdk);
		LOGGER.log(Level.FINE, "Using JDK: {0}", ijdk);
		return ijdk;
	}

	private Jdk.@NonNull InstalledJdk ensureInstalled(@NonNull Jdk jdk) {
		Jdk.InstalledJdk ijdk;
		if (!jdk.isInstalled()) {
			Jdk.AvailableJdk ajdk = (Jdk.AvailableJdk) jdk;
			ijdk = ajdk.install();
			if (getDefaultJdk() == null) {
				setDefaultJdk(ijdk);
			}
		} else {
			ijdk = (Jdk.InstalledJdk) jdk;
		}
		return ijdk;
	}

	/**
	 * Returns a <code>Jdk</code> object that matches the requested version from the
	 * list of currently installed JDKs or from the ones available for installation.
	 * The parameter is a string that either contains the actual (strict) major
	 * version of the JDK that should be returned, an open version terminated with a
	 * <code>+</code> sign to indicate that any later version is valid as well, or
	 * it is an id that will be matched against the ids of JDKs that are currently
	 * installed. If the requested version is <code>null</code> the "active" JDK
	 * will be returned, this is normally the JDK currently being used to run the
	 * app itself. The method will return <code>null</code> if no installed or
	 * available JDK matches. NB: This method can return <code>Jdk</code> objects
	 * for JDKs that are currently _not_ installed. It will not cause any installs
	 * to be performed. See <code>getOrInstallJdk()</code> for that.
	 *
	 * @param versionOrId A version pattern, id or <code>null</code>
	 * @return A <code>Jdk</code> object or <code>null</code>
	 * @throws IllegalArgumentException If no JDK could be found at all
	 */
	@Nullable
	public Jdk getJdk(@Nullable String versionOrId) {
		return getJdk(versionOrId, JdkProvider.Predicates.all);
	}

	/**
	 * Returns a <code>Jdk</code> object that matches the requested version from the
	 * list of currently installed JDKs or from the ones available for installation.
	 * The parameter is a string that either contains the actual (strict) major
	 * version of the JDK that should be returned, an open version terminated with a
	 * <code>+</code> sign to indicate that any later version is valid as well, or
	 * it is an id that will be matched against the ids of JDKs that are currently
	 * installed. If the requested version is <code>null</code> the "active" JDK
	 * will be returned, this is normally the JDK currently being used to run the
	 * app itself. The method will return <code>null</code> if no installed or
	 * available JDK matches. NB: This method can return <code>Jdk</code> objects
	 * for JDKs that are currently _not_ installed. It will not cause any installs
	 * to be performed. See <code>getOrInstallJdk()</code> for that.
	 *
	 * @param versionOrId    A version pattern, id or <code>null</code>
	 * @param providerFilter Only return JDKs from providers that match the filter
	 * @return A <code>Jdk</code> object or <code>null</code>
	 * @throws IllegalArgumentException If no JDK could be found at all
	 */
	@Nullable
	public Jdk getJdk(
			@Nullable String versionOrId, @NonNull Predicate<JdkProvider> providerFilter) {
		if (versionOrId != null) {
			if (JavaUtils.isRequestedVersion(versionOrId)) {
				return getJdkByVersion(
						JavaUtils.minRequestedVersion(versionOrId),
						JavaUtils.isOpenVersion(versionOrId),
						providerFilter);
			} else {
				return getJdkById(versionOrId, providerFilter);
			}
		} else {
			return getJdkByVersion(0, true, providerFilter);
		}
	}

	/**
	 * Returns an <code>Jdk</code> object that matches the requested version from
	 * the list of currently installed JDKs or from the ones available for
	 * installation. The method will return <code>null</code> if no installed or
	 * available JDK matches. NB: This method can return <code>
	 * Jdk</code> objects for JDKs that are currently _not_ installed. It will not
	 * cause any installs to be performed. See
	 * <code>getOrInstallJdkByVersion()</code> for that.
	 *
	 * @param requestedVersion The (minimal) version to return, can be 0
	 * @param openVersion      Return newer version if exact is not available
	 * @param providerFilter   Only return JDKs from providers that match the filter
	 * @return A <code>Jdk</code> object or <code>null</code>
	 * @throws IllegalArgumentException If no JDK could be found at all
	 */
	@Nullable
	private Jdk getJdkByVersion(
			int requestedVersion,
			boolean openVersion,
			@NonNull Predicate<JdkProvider> providerFilter) {
		Jdk jdk = getInstalledJdkByVersion(requestedVersion, openVersion, providerFilter);
		if (jdk == null) {
			if (requestedVersion > 0 && (requestedVersion >= defaultJavaVersion || !openVersion)) {
				jdk = getAvailableJdkByVersion(requestedVersion, openVersion);
			} else {
				jdk = getJdkByVersion(defaultJavaVersion, openVersion, providerFilter);
				if (jdk == null) {
					// If we can't find the default version or higher,
					// we'll just find the highest version available
					jdk = prevAvailableJdk(defaultJavaVersion).orElse(null);
				}
			}
		}
		return jdk;
	}

	/**
	 * Returns an <code>Jdk</code> object that matches the requested version from
	 * the list of currently installed JDKs or from the ones available for
	 * installation. The method will return <code>null</code> if no installed or
	 * available JDK matches. NB: This method can return <code>
	 * Jdk</code> objects for JDKs that are currently _not_ installed. It will not
	 * cause any installs to be performed. See
	 * <code>getOrInstallJdkByVersion()</code> for that.
	 *
	 * @param requestedId    The id of the JDK to return
	 * @param providerFilter Only return JDKs from providers that match the filter
	 * @return A <code>Jdk</code> object or <code>null</code>
	 * @throws IllegalArgumentException If no JDK could be found at all
	 */
	private @Nullable Jdk getJdkById(
			@NonNull String requestedId, @NonNull Predicate<JdkProvider> providerFilter) {
		Jdk jdk = getInstalledJdkById(requestedId, providerFilter);
		if (jdk == null) {
			jdk = getAvailableJdkById(requestedId);
		}
		return jdk;
	}

	/**
	 * Returns an <code>Jdk</code> object for an installed JDK of the given version
	 * or id. Will return <code>null</code> if no JDK of that version or id is
	 * currently installed.
	 *
	 * @param versionOrId A version pattern, id or <code>null</code>
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	public Jdk.@Nullable InstalledJdk getInstalledJdk(String versionOrId) {
		return getInstalledJdk(versionOrId, JdkProvider.Predicates.all);
	}

	/**
	 * Returns an <code>Jdk</code> object for an installed JDK of the given version
	 * or id. Will return <code>null</code> if no JDK of that version or id is
	 * currently installed.
	 *
	 * @param versionOrId    A version pattern, id or <code>null</code>
	 * @param providerFilter Only return JDKs from providers that match the filter
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	public Jdk.@Nullable InstalledJdk getInstalledJdk(String versionOrId,
			@NonNull Predicate<JdkProvider> providerFilter) {
		if (versionOrId != null) {
			if (JavaUtils.isRequestedVersion(versionOrId)) {
				return getInstalledJdkByVersion(
						JavaUtils.minRequestedVersion(versionOrId),
						JavaUtils.isOpenVersion(versionOrId),
						providerFilter);
			} else {
				return getInstalledJdkById(versionOrId, providerFilter);
			}
		} else {
			return getInstalledJdkByVersion(0, true, providerFilter);
		}
	}

	/**
	 * Returns an <code>Jdk</code> object for an installed JDK of the given version.
	 * Will return <code>null</code> if no JDK of that version is currently
	 * installed.
	 *
	 * @param version        The (major) version of the JDK to return
	 * @param openVersion    Return newer version if exact is not available
	 * @param providerFilter Only return JDKs from providers that match the filter
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	private Jdk.@Nullable InstalledJdk getInstalledJdkByVersion(
			int version, boolean openVersion, @NonNull Predicate<JdkProvider> providerFilter) {
		return providers(providerFilter)
			.map(p -> p.getInstalledByVersion(version, openVersion))
			.filter(Objects::nonNull)
			.findFirst()
			.orElse(null);
	}

	/**
	 * Returns an <code>Jdk</code> object for an installed JDK with the given id.
	 * Will return <code>
	 * null</code> if no JDK with that id is currently installed.
	 *
	 * @param requestedId    The id of the JDK to return
	 * @param providerFilter Only return JDKs from providers that match the filter
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	private Jdk.@Nullable InstalledJdk getInstalledJdkById(
			String requestedId, @NonNull Predicate<JdkProvider> providerFilter) {
		return providers(providerFilter)
			.map(p -> p.getInstalledById(requestedId))
			.filter(Objects::nonNull)
			.findFirst()
			.orElse(null);
	}

	private Jdk.@Nullable AvailableJdk getAvailableJdkByVersion(int version, boolean openVersion) {
		return providers(JdkProvider.Predicates.canInstall)
			.map(p -> p.getAvailableByVersion(version, openVersion))
			.filter(Objects::nonNull)
			.findFirst()
			.orElse(null);
	}

	private Jdk.@Nullable AvailableJdk getAvailableJdkById(String id) {
		return providers(JdkProvider.Predicates.canInstall)
			.map(p -> p.getAvailableByIdOrToken(id))
			.filter(Objects::nonNull)
			.findFirst()
			.orElse(null);
	}

	Jdk.@NonNull InstalledJdk installJdk(Jdk.@NonNull AvailableJdk jdk) {
		Jdk.InstalledJdk newJdk = jdk.provider().install(jdk);

		if (hasDefaultProvider() && !newJdk.provider().equals(defaultProvider)) {
			// Check if we have a global default Jdk set, if not set the new JDK as default
			Jdk.InstalledJdk defJdk = getDefaultJdk();
			if (defJdk == null) {
				Jdk.AvailableJdk newDefJdk = defaultProvider.getAvailableByIdOrToken("default@" + newJdk.home());
				assert newDefJdk != null : "Internal error, global default JDK should always be available";
				newDefJdk.install();
			}
			// Check if we have a versioned default Jdk set, if not set the new JDK as
			// the default for the installed JDK's major version
			int v = newJdk.majorVersion();
			Jdk.InstalledJdk defJdkVer = getDefaultJdkForVersion(v);
			if (defJdkVer == null) {
				Jdk.AvailableJdk newDefJdk = defaultProvider.getAvailableByIdOrToken(v + "-default@" + newJdk.home());
				assert newDefJdk != null : "Internal error, versioned default JDK should always be available";
				newDefJdk.install();
			}
		}

		return newJdk;
	}

	void uninstallJdk(Jdk.@NonNull InstalledJdk jdk) {
		boolean resetDefault = false;
		boolean resetDefaultVer = false;
		if (hasDefaultProvider() && !jdk.provider().equals(defaultProvider)) {
			// Check if the JDK is the global default JDK, if so we need to reset it
			Jdk.InstalledJdk defaultJdk = getDefaultJdk();
			if (defaultJdk != null) {
				Path defHome = defaultJdk.home();
				try {
					resetDefault = Files.isSameFile(defHome, jdk.home());
				} catch (IOException ex) {
					LOGGER.log(Level.WARNING, "Error while trying to reset global default JDK", ex);
					resetDefault = defHome.equals(jdk.home());
				}
			}
			// Check if the JDK is the global default JDK, if so we need to reset it
			Jdk.InstalledJdk defaultJdkVer = getDefaultJdk();
			if (defaultJdkVer != null) {
				Path defHome = defaultJdkVer.home();
				try {
					resetDefaultVer = Files.isSameFile(defHome, jdk.home());
				} catch (IOException ex) {
					LOGGER.log(Level.WARNING, "Error while trying to reset versioned default JDK", ex);
					resetDefaultVer = defHome.equals(jdk.home());
				}
			}
		}

		jdk.provider().uninstall(jdk);

		if (resetDefault) {
			Optional<Jdk.InstalledJdk> newjdk = nextInstalledJdk(Jdk.Predicates.minVersion(jdk.majorVersion()),
					JdkProvider.Predicates.canInstall);
			if (!newjdk.isPresent()) {
				newjdk = prevInstalledJdk(Jdk.Predicates.maxVersion(jdk.majorVersion()),
						JdkProvider.Predicates.canInstall);
			}
			if (newjdk.isPresent()) {
				setDefaultJdk(newjdk.get());
			} else {
				removeDefaultJdk();
				LOGGER.log(Level.INFO, "Global default JDK unset");
			}
		}
		if (resetDefaultVer) {
			int v = jdk.majorVersion();
			Optional<Jdk.InstalledJdk> newjdk = nextInstalledJdk(Jdk.Predicates.exactVersion(v),
					JdkProvider.Predicates.canInstall);
			if (!newjdk.isPresent()) {
				newjdk = prevInstalledJdk(Jdk.Predicates.exactVersion(v), JdkProvider.Predicates.canInstall);
			}
			if (newjdk.isPresent()) {
				setDefaultJdkForVersion(newjdk.get());
			} else {
				removeDefaultJdkForVersion(v);
				LOGGER.log(Level.INFO, "Versioned default JDK unset");
			}
		}
	}

	/**
	 * Links a JDK folder to an already existing JDK path with a link. It checks if
	 * the incoming version number is the same that the linked JDK has, if not an
	 * exception will be raised.
	 *
	 * @param jdkPath path to the pre-installed JDK.
	 * @param id      id for the new JDK.
	 */
	public void linkToExistingJdk(Path jdkPath, String id) {
		JdkProvider linked = provider(LinkedJdkProvider.Discovery.PROVIDER_ID);
		if (linked == null) {
			throw new IllegalStateException("No provider available to link JDK");
		}
		if (!Files.isDirectory(jdkPath)) {
			throw new IllegalArgumentException("Unable to resolve path as directory: " + jdkPath);
		}
		Jdk.AvailableJdk linkedJdk = linked.getAvailableByIdOrToken(id + "-linked@" + jdkPath);
		if (linkedJdk == null) {
			throw new IllegalArgumentException("Unable to create link to JDK in path: " + jdkPath);
		}
		LOGGER.log(Level.FINE, "Linking JDK: {0} to {1}", new Object[] { id, jdkPath });
		linked.install(linkedJdk);
	}

	/**
	 * Returns an installed JDK that matches the requested version or the next
	 * available version. Returns <code>Optional.empty()</code> if no matching JDK
	 * was found;
	 *
	 * @param jdkFilter      Only return JDKs that match the filter
	 * @param providerFilter Only return JDKs from providers that match the filter
	 * @return an optional JDK
	 */
	private Optional<Jdk.InstalledJdk> nextInstalledJdk(
			@NonNull Predicate<Jdk.InstalledJdk> jdkFilter,
			@NonNull Predicate<JdkProvider> providerFilter) {
		return listInstalledJdks(providerFilter)
			.filter(jdkFilter)
			.min(Jdk::compareTo);
	}

	/**
	 * Returns an installed JDK that matches the requested version or the previous
	 * available version. Returns <code>Optional.empty()</code> if no matching JDK
	 * was found;
	 *
	 * @param jdkFilter      Only return JDKs that match the filter
	 * @param providerFilter Only return JDKs from providers that match the filter
	 * @return an optional JDK
	 */
	private Optional<Jdk.InstalledJdk> prevInstalledJdk(
			@NonNull Predicate<Jdk.InstalledJdk> jdkFilter,
			@NonNull Predicate<JdkProvider> providerFilter) {
		return listInstalledJdks(providerFilter)
			.filter(jdkFilter)
			.max(Jdk::compareTo);
	}

	/**
	 * Returns an available JDK that matches the requested version or the previous
	 * available version. Returns <code>Optional.empty()</code> if no matching JDK
	 * was found;
	 *
	 * @param maxVersion the maximum version to return
	 * @return an optional JDK
	 */
	private Optional<Jdk.AvailableJdk> prevAvailableJdk(int maxVersion) {
		return listAvailableJdks().stream()
			.filter(jdk -> jdk.majorVersion() <= maxVersion)
			.max(Jdk::compareTo);
	}

	/**
	 * Returns a list of all JDKs that are available for installation. This includes
	 * JDKs from all active JDK providers.
	 *
	 * @return A list of <code>Jdk.AvailableJdk</code> objects, possibly empty
	 */
	@NonNull
	public List<Jdk.AvailableJdk> listAvailableJdks() {
		return providers(JdkProvider.Predicates.canInstall)
			.flatMap(JdkProvider::listAvailable)
			.sorted(Comparator.comparingInt(Jdk::majorVersion).reversed())
			.collect(Collectors.toList());
	}

	/**
	 * Returns a list of all JDKs that are currently installed. This includes JDKs
	 * from all active JDK providers.
	 *
	 * @return A list of <code>Jdk.InstalledJdk</code> objects, possibly empty
	 */
	@NonNull
	public List<Jdk.InstalledJdk> listInstalledJdks() {
		return listInstalledJdks(JdkProvider.Predicates.all).collect(Collectors.toList());
	}

	private Stream<Jdk.InstalledJdk> listInstalledJdks(Predicate<JdkProvider> providerFilter) {
		return providers(providerFilter).flatMap(JdkProvider::listInstalled);
	}

	/**
	 * Returns the default provider that is used to manage the default JDK and
	 * versioned defaults.
	 *
	 * @return boolean indicating if a default provider is available
	 */
	public boolean hasDefaultProvider() {
		return defaultProvider != null;
	}

	/**
	 * Returns a list of all JDKs that are managed by the default provider.
	 *
	 * @return A list of <code>Jdk.InstalledJdk</code> objects, possibly empty
	 */
	@NonNull
	@SuppressWarnings("unchecked")
	public List<Jdk.LinkedJdk> listDefaultJdks() {
		if (hasDefaultProvider()) {
			return (List<Jdk.LinkedJdk>) (List<?>) defaultProvider.listInstalled().collect(Collectors.toList());
		} else {
			return Collections.emptyList();
		}
	}

	/**
	 * Returns the default JDK, if one is set. This is the JDK that will be used by
	 * default when no specific JDK is requested.
	 *
	 * @return The default JDK or <code>null</code> if no default is set
	 */
	public Jdk.@Nullable LinkedJdk getDefaultJdk() {
		return hasDefaultProvider()
				? (Jdk.LinkedJdk) defaultProvider.getInstalledById("default")
				: null;
	}

	/**
	 * Returns the default JDK for a specific major version, if one is set. This is
	 * the JDK that will be used by default when a specific major version is
	 * requested, e.g. "21" for JDK 21.
	 *
	 * @param majorVersion The major version of the JDK to return
	 * @return The default JDK for the given version or <code>null</code> if no
	 *         default is set
	 */
	public Jdk.@Nullable LinkedJdk getDefaultJdkForVersion(int majorVersion) {
		return hasDefaultProvider()
				? (Jdk.LinkedJdk) defaultProvider.getInstalledById(majorVersion + "-default")
				: null;
	}

	/**
	 * Sets the default JDK to the given JDK. This is the JDK that will be used by
	 * default when no specific JDK is requested.
	 *
	 * @param jdk The JDK to set as the default
	 * @throws IllegalArgumentException If the JDK is not installed or if it cannot
	 *                                  be determined
	 */
	public void setDefaultJdk(Jdk.@NonNull InstalledJdk jdk) {
		if (hasDefaultProvider()) {
			Jdk.InstalledJdk defJdk = getDefaultJdk();
			// Check if the new jdk exists and isn't the same as the current default
			if (jdk.isInstalled() && !jdk.equals(defJdk)) {
				// Special syntax for "installing" the default JDK
				Jdk.AvailableJdk newDefJdk = defaultProvider.getAvailableByIdOrToken("default@" + jdk.home());
				if (newDefJdk == null) {
					throw new IllegalArgumentException(
							"Unable to determine Java version in given path: " + jdk.home());
				}
				defaultProvider.install(newDefJdk);
				LOGGER.log(Level.INFO, "Default JDK set to {0}", jdk);
			}
		}
	}

	/**
	 * Sets the default JDK for a specific major version. This is the JDK that will
	 * be used by default when a specific major version is requested, e.g. "21" for
	 * JDK 21.
	 *
	 * @param jdk The JDK to set as the default for the given major version
	 * @throws IllegalArgumentException If the JDK is not installed or if it cannot
	 *                                  be determined
	 */
	public void setDefaultJdkForVersion(Jdk.@NonNull InstalledJdk jdk) {
		if (hasDefaultProvider()) {
			Jdk.InstalledJdk defJdk = getDefaultJdkForVersion(jdk.majorVersion());
			// Check if the new jdk exists and isn't the same as the current default
			if (jdk.isInstalled() && !jdk.equals(defJdk)) {
				// Special syntax for "installing" the default JDK
				Jdk.AvailableJdk newDefJdk = defaultProvider
					.getAvailableByIdOrToken(jdk.majorVersion() + "-default@" + jdk.home());
				if (newDefJdk == null) {
					throw new IllegalArgumentException(
							"Unable to determine Java version in given path: " + jdk.home());
				}
				defaultProvider.install(newDefJdk);
				LOGGER.log(Level.INFO, "Default JDK for version {0} set to {1}",
						new Object[] { jdk.majorVersion(), jdk });
			}
		}
	}

	/**
	 * Unsets the default JDK, if one is set. This will not uninstall the JDK, but
	 * it will make the selection of a JDK more ambiguous, as JBang will no longer
	 * know which JDK to use when the user does not specify a version or id.
	 */
	public void removeDefaultJdk() {
		Jdk.InstalledJdk defJdk = getDefaultJdk();
		if (defJdk != null) {
			defJdk.uninstall();
		}
	}

	/**
	 * Unsets the default JDK for a specific major version, if one is set. This will
	 * not uninstall the JDK, but it will make the selection of a versioned JDK more
	 * ambiguous, as JBang will no longer know which JDK to use when the user does
	 * not specify an id.
	 *
	 * @param majorVersion The major version of the JDK to remove as default
	 */
	public void removeDefaultJdkForVersion(int majorVersion) {
		Jdk.InstalledJdk defJdk = getDefaultJdkForVersion(majorVersion);
		if (defJdk != null) {
			defJdk.uninstall();
		}
	}

	public boolean isCurrentJdkManaged() {
		String jh = System.getProperty("java.home");
		if (jh == null) {
			return false;
		}
		Path currentJdk = Paths.get(jh);
		return providers(JdkProvider.Predicates.canUpdate)
			.anyMatch(p -> p.getInstalledByPath(currentJdk) != null);
	}
}
