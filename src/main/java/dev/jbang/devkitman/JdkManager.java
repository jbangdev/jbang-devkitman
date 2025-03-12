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

import dev.jbang.devkitman.jdkproviders.DefaultJdkProvider;
import dev.jbang.devkitman.jdkproviders.JBangJdkProvider;
import dev.jbang.devkitman.jdkproviders.LinkedJdkProvider;
import dev.jbang.devkitman.util.FileUtils;
import dev.jbang.devkitman.util.JavaUtils;
import dev.jbang.devkitman.util.OsUtils;

public class JdkManager {
	public static final int DEFAULT_JAVA_VERSION = 21;
	private static final Logger LOGGER = Logger.getLogger(JdkManager.class.getName());

	private final List<JdkProvider> providers;
	private final int defaultJavaVersion;

	private final JdkProvider defaultProvider;

	public static JdkManager create() {
		Path installPath = JBangJdkProvider.getJBangJdkDir();
		JdkDiscovery.Config cfg = new JdkDiscovery.Config(installPath);
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
	@NonNull
	public Jdk getOrInstallJdk(String versionOrId) {
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
	@NonNull
	public Jdk getOrInstallJdk(String versionOrId, @NonNull Predicate<JdkProvider> providerFilter) {
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
	@NonNull
	private Jdk getOrInstallJdkByVersion(
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
		jdk = ensureInstalled(jdk);
		LOGGER.log(Level.FINE, "Using JDK: {0}", jdk);

		return jdk;
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
	@NonNull
	private Jdk getOrInstallJdkById(
			@NonNull String requestedId, @NonNull Predicate<JdkProvider> providerFilter) {
		LOGGER.log(Level.FINE, "Looking for JDK: {0}", requestedId);
		Jdk jdk = getJdkById(requestedId, providerFilter);
		if (jdk == null) {
			throw new IllegalArgumentException(
					"No suitable JDK was found for requested id: " + requestedId);
		}
		jdk = ensureInstalled(jdk);
		LOGGER.log(Level.FINE, "Using JDK: {0}", jdk);

		return jdk;
	}

	private Jdk ensureInstalled(Jdk jdk) {
		if (!jdk.isInstalled()) {
			jdk = jdk.install();
			if (getDefaultJdk() == null) {
				setDefaultJdk(jdk);
			}
		}
		return jdk;
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
	 * @param versionOrId    A version pattern, id or <code>null</code>
	 * @param providerFilter Only return JDKs from providers that match the filter
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	@Nullable
	public Jdk getInstalledJdk(String versionOrId, @NonNull Predicate<JdkProvider> providerFilter) {
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
	@Nullable
	private Jdk getInstalledJdkByVersion(
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
	@Nullable
	private Jdk getInstalledJdkById(
			String requestedId, @NonNull Predicate<JdkProvider> providerFilter) {
		return providers(providerFilter)
			.map(p -> p.getInstalledById(requestedId))
			.filter(Objects::nonNull)
			.findFirst()
			.orElse(null);
	}

	@Nullable
	private Jdk getAvailableJdkByVersion(int version, boolean openVersion) {
		Optional<Jdk> jdk = getJdkBy(
				listAvailableJdks().stream(),
				Jdk.Predicates.forVersion(version, openVersion));
		return jdk.orElse(null);
	}

	@Nullable
	private Jdk getAvailableJdkById(String id) {
		Optional<Jdk> jdk = getJdkBy(listAvailableJdks().stream(), Jdk.Predicates.id(id));
		return jdk.orElse(null);
	}

	public void uninstallJdk(Jdk jdk) {
		Jdk defaultJdk = getDefaultJdk();
		if (OsUtils.isWindows()) {
			// On Windows we have to check nobody is currently using the JDK or we could
			// be causing all kinds of trouble
			try {
				Path jdkTmpDir = jdk.home()
					.getParent()
					.resolve("_delete_me_" + jdk.home().getFileName().toString());
				Files.move(jdk.home(), jdkTmpDir);
				Files.move(jdkTmpDir, jdk.home());
			} catch (IOException ex) {
				LOGGER.log(Level.WARNING, "Cannot uninstall JDK, it's being used: {0}", jdk);
				return;
			}
		}

		boolean resetDefault = false;
		if (defaultJdk != null) {
			Path defHome = defaultJdk.home();
			try {
				resetDefault = Files.isSameFile(defHome, jdk.home());
			} catch (IOException ex) {
				LOGGER.log(Level.WARNING, "Error while trying to reset default JDK", ex);
				resetDefault = defHome.equals(jdk.home());
			}
		}

		if (jdk.isInstalled()) {
			FileUtils.deletePath(jdk.home());
			LOGGER.log(Level.INFO, "JDK {0} has been uninstalled", new Object[] { jdk.id() });
		}

		if (resetDefault) {
			Optional<Jdk> newjdk = nextInstalledJdk(jdk.majorVersion(), JdkProvider.Predicates.canUpdate);
			if (!newjdk.isPresent()) {
				newjdk = prevInstalledJdk(jdk.majorVersion(), JdkProvider.Predicates.canUpdate);
			}
			if (newjdk.isPresent()) {
				setDefaultJdk(newjdk.get());
			} else {
				removeDefaultJdk();
				LOGGER.log(Level.INFO, "Default JDK unset");
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
			return;
		}
		if (!Files.isDirectory(jdkPath)) {
			throw new IllegalArgumentException("Unable to resolve path as directory: " + jdkPath);
		}
		Jdk linkedJdk = linked.getAvailableByIdOrToken(id + "@" + jdkPath);
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
	 * @param minVersion     the minimal version to return
	 * @param providerFilter Only return JDKs from providers that match the filter
	 * @return an optional JDK
	 */
	private Optional<Jdk> nextInstalledJdk(
			int minVersion, @NonNull Predicate<JdkProvider> providerFilter) {
		return listInstalledJdks(providerFilter)
			.filter(jdk -> jdk.majorVersion() >= minVersion)
			.min(Jdk::compareTo);
	}

	/**
	 * Returns an installed JDK that matches the requested version or the previous
	 * available version. Returns <code>Optional.empty()</code> if no matching JDK
	 * was found;
	 *
	 * @param maxVersion     the maximum version to return
	 * @param providerFilter Only return JDKs from providers that match the filter
	 * @return an optional JDK
	 */
	private Optional<Jdk> prevInstalledJdk(
			int maxVersion, @NonNull Predicate<JdkProvider> providerFilter) {
		return listInstalledJdks(providerFilter)
			.filter(jdk -> jdk.majorVersion() <= maxVersion)
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
	private Optional<Jdk> prevAvailableJdk(int maxVersion) {
		return listAvailableJdks().stream()
			.filter(jdk -> jdk.majorVersion() <= maxVersion)
			.max(Jdk::compareTo);
	}

	public Set<Distro> listAvailableDistros() {
		return providers(JdkProvider.Predicates.canUpdate)
			.flatMap(p -> p.listDistros().stream())
			.collect(Collectors.toSet());
	}

	public List<Jdk> listAvailableJdks() {
		return listAvailableJdks(null, null);
	}

	public List<Jdk> listAvailableJdks(String distros, Set<String> tags) {
		return providers(JdkProvider.Predicates.canUpdate)
			.flatMap(p -> p.listAvailable(distros, tags).stream())
			.collect(Collectors.toList());
	}

	public List<Jdk> listInstalledJdks() {
		return listInstalledJdks(JdkProvider.Predicates.all).sorted().collect(Collectors.toList());
	}

	private Stream<Jdk> listInstalledJdks(Predicate<JdkProvider> providerFilter) {
		return providers(providerFilter).flatMap(p -> p.listInstalled().stream());
	}

	public boolean hasDefaultProvider() {
		return defaultProvider != null;
	}

	@Nullable
	public Jdk getDefaultJdk() {
		return hasDefaultProvider()
				? defaultProvider.getInstalledById(
						DefaultJdkProvider.Discovery.PROVIDER_ID)
				: null;
	}

	public void setDefaultJdk(Jdk jdk) {
		if (hasDefaultProvider()) {
			Jdk defJdk = getDefaultJdk();
			// Check if the new jdk exists and isn't the same as the current default
			if (jdk.isInstalled() && !jdk.equals(defJdk)) {
				// Special syntax for "installing" the default JDK
				Jdk newDefJdk = defaultProvider.createJdk(
						DefaultJdkProvider.Discovery.PROVIDER_ID,
						jdk.home(),
						jdk.version(),
						false,
						jdk.distro(),
						jdk.tags());
				defaultProvider.install(newDefJdk);
				LOGGER.log(Level.INFO, "Default JDK set to {0}", jdk);
			}
		}
	}

	public void removeDefaultJdk() {
		Jdk defJdk = getDefaultJdk();
		if (defJdk != null) {
			defJdk.uninstall();
		}
	}

	public boolean isCurrentJdkManaged() {
		Path currentJdk = Paths.get(System.getProperty("java.home"));
		return providers(JdkProvider.Predicates.canUpdate)
			.anyMatch(p -> p.getInstalledByPath(currentJdk) != null);
	}

	@NonNull
	static Optional<Jdk> getJdkBy(@NonNull Stream<Jdk> jdks, @NonNull Predicate<Jdk> jdkFilter) {
		return jdks.filter(jdkFilter).findFirst();
	}
}
