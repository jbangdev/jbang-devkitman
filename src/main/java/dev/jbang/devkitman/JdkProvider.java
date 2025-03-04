package dev.jbang.devkitman;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * This interface must be implemented by providers that are able to give access
 * to JDKs installed on the user's system. Some providers will also be able to
 * manage those JDKs by installing and uninstalling them at the user's request.
 * In those cases the <code>canUpdate()</code> should return <code>true</code>.
 *
 * <p>
 * The providers deal in JDK identifiers, not in versions. Those identifiers are
 * specific to the implementation but should follow an important rule: they must
 * be unique across implementations.
 */
public interface JdkProvider {

	default Jdk createJdk(@NonNull String id, @Nullable Path home, @NonNull String version) {
		return new Jdk.Default(this, id, home, version);
	}

	@NonNull
	JdkManager manager();

	void manager(@NonNull JdkManager manager);

	/**
	 * Returns the name of the provider. This name should be unique across all
	 * providers and consist only of lowercase letters and numbers.
	 *
	 * @return The name of the provider
	 */
	@NonNull
	String name();

	/**
	 * Returns a description of the provider.
	 *
	 * @return The description of the provider
	 */
	@NonNull
	String description();

	/**
	 * Returns a set of JDKs that are currently installed on the user's system.
	 *
	 * @return List of <code>Jdk</code> objects, possibly empty
	 */
	@NonNull
	List<Jdk> listInstalled();

	/**
	 * Determines if a JDK of the requested version is currently installed by this
	 * provider and if so returns its respective <code>Jdk</code> object, otherwise
	 * it returns <code>null</code>. If <code>openVersion</code> is set to true the
	 * method will also return the next installed version if the exact version was
	 * not found.
	 *
	 * @param version     The specific JDK version to return
	 * @param openVersion Return newer version if exact is not available
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	@Nullable
	default Jdk getInstalledByVersion(int version, boolean openVersion) {
		return JdkManager.getJdkBy(
				listInstalled().stream(), Jdk.Predicates.forVersion(version, openVersion))
			.orElse(null);
	}

	/**
	 * Determines if the given id refers to a JDK managed by this provider and if so
	 * returns its respective <code>Jdk</code> object, otherwise it returns
	 * <code>null</code>.
	 *
	 * @param id The id to look for
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	@Nullable
	default Jdk getInstalledById(@NonNull String id) {
		if (isValidId(id)) {
			return JdkManager.getJdkBy(listInstalled().stream(), Jdk.Predicates.id(id))
				.orElse(null);
		}
		return null;
	}

	/**
	 * Determines if the given path belongs to a JDK managed by this provider and if
	 * so returns its respective <code>Jdk</code> object, otherwise it returns
	 * <code>null</code>.
	 *
	 * @param jdkPath The path to look for
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	@Nullable
	default Jdk getInstalledByPath(@NonNull Path jdkPath) {
		return JdkManager.getJdkBy(listInstalled().stream(), Jdk.Predicates.path(jdkPath))
			.orElse(null);
	}

	/**
	 * Determines if the given id is a valid JDK id for this provider.
	 *
	 * @param id The id to validate
	 * @return True if the id is valid, false otherwise
	 */
	default boolean isValidId(@NonNull String id) {
		return name().equals(id);
	}

	/**
	 * Indicates if the provider can be used or not. This can perform sanity checks
	 * like the availability of certain package being installed on the system or
	 * even if the system is running a supported operating system.
	 *
	 * @return True if the provider can be used, false otherwise
	 */
	default boolean canUse() {
		return true;
	}

	/**
	 * Indicates if the provider is able to (un)install JDKs or not. If true the
	 * provider must also implement the <code>JdkInstaller</code> interface.
	 *
	 * @return True if JDKs can be (un)installed, false otherwise
	 */
	default boolean canUpdate() {
		return false;
	}

	/**
	 * This method returns a set of JDKs that are available for installation.
	 * Implementations might set the <code>home</code> field of the JDK objects if
	 * the respective JDK is currently installed on the user's system, but only if
	 * they can ensure that it's the exact same version, otherwise they should just
	 * leave the field <code>null</code>.
	 *
	 * @return List of <code>Jdk</code> objects
	 */
	@NonNull
	default List<Jdk> listAvailable() {
		throw new UnsupportedOperationException(
				"Listing available JDKs is not supported by " + getClass().getName());
	}

	/**
	 * Determines if a JDK matching the given id or token is available for
	 * installation by this provider and if so returns its respective
	 * <code>Jdk</code> object, otherwise it returns <code>null</code>. The
	 * difference between ids and tokens is that ids are matched exactly against the
	 * ids of available JDKs, while tokens can use optional provider-specific
	 * matching logic. NB: In special cases, depending on the provider, this method
	 * might actually return <code>Jdk</code> objects that are not returned by
	 * <code>listAvailable()</code>.
	 *
	 * @param idOrToken The id or token to look for
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	@Nullable
	default Jdk getAvailableByIdOrToken(String idOrToken) {
		return JdkManager.getJdkBy(listAvailable().stream(), Jdk.Predicates.id(idOrToken))
			.orElse(null);
	}

	/**
	 * Installs the indicated JDK
	 *
	 * @param jdk The <code>Jdk</code> object of the JDK to install
	 * @return A <code>Jdk</code> object
	 * @throws UnsupportedOperationException if the provider can not update
	 */
	@NonNull
	default Jdk install(@NonNull Jdk jdk) {
		throw new UnsupportedOperationException(
				"Installing a JDK is not supported by " + getClass().getName());
	}

	/**
	 * Uninstalls the indicated JDK
	 *
	 * @param jdk The <code>Jdk</code> object of the JDK to uninstall
	 * @throws UnsupportedOperationException if the provider can not update
	 */
	default void uninstall(@NonNull Jdk jdk) {
		if (!canUpdate()) {
			throw new UnsupportedOperationException(
					"Uninstalling a JDK is not supported by " + getClass().getName());
		}
		manager().uninstallJdk(jdk);
	}

	class Predicates {
		public static final Predicate<JdkProvider> all = provider -> true;
		public static final Predicate<JdkProvider> canUpdate = JdkProvider::canUpdate;

		public static Predicate<JdkProvider> name(String name) {
			return provider -> provider.name().equalsIgnoreCase(name);
		}
	}
}
