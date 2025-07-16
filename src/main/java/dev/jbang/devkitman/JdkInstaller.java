package dev.jbang.devkitman;

import java.nio.file.Path;
import java.util.List;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * This interface must be implemented by installers that are able to install
 * JDKs on the user's system. They should be able to install and uninstall them
 * at the user's request.
 */
public interface JdkInstaller {

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
	default List<Jdk.AvailableJdk> listAvailable() {
		throw new UnsupportedOperationException(
				"Listing available JDKs is not supported by " + getClass().getName());
	}

	/**
	 * Determines if a JDK matching the given version is available for installation
	 * by this installer and if so returns its respective <code>Jdk</code> object,
	 * otherwise it returns <code>null</code>.
	 *
	 * @param version     The (major) version of the JDK to return
	 * @param openVersion Return newer version if available
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	default Jdk.@Nullable AvailableJdk getAvailableByVersion(int version, boolean openVersion) {
		return listAvailable().stream()
			.filter(Jdk.Predicates.forVersion(version, openVersion))
			.findFirst()
			.orElse(null);
	}

	/**
	 * Determines if a JDK matching the given id or token is available for
	 * installation by this installer and if so returns its respective
	 * <code>Jdk</code> object, otherwise it returns <code>null</code>. The
	 * difference between ids and tokens is that ids are matched exactly against the
	 * ids of available JDKs, while tokens can use optional installer-specific
	 * matching logic. NB: In special cases, depending on the installer, this method
	 * might actually return <code>Jdk</code> objects that are not returned by
	 * <code>listAvailable()</code>.
	 *
	 * @param idOrToken The id or token to look for
	 * @return A <code>Jdk</code> object or <code>null</code>
	 */
	default Jdk.@Nullable AvailableJdk getAvailableByIdOrToken(String idOrToken) {
		return listAvailable().stream()
			.filter(Jdk.Predicates.id(idOrToken))
			.findFirst()
			.orElse(null);
	}

	/**
	 * Installs the indicated JDK
	 *
	 * @param jdk        The <code>Jdk</code> object of the JDK to install
	 * @param installDir The path where the JDK should be installed
	 * @return A <code>Jdk</code> object
	 * @throws UnsupportedOperationException if the provider can not update
	 */
	default Jdk.@NonNull InstalledJdk install(Jdk.@NonNull AvailableJdk jdk, Path installDir) {
		throw new UnsupportedOperationException(
				"Installing a JDK is not supported by " + getClass().getName());
	}

	/**
	 * Uninstalls the indicated JDK
	 *
	 * @param jdk The <code>Jdk</code> object of the JDK to uninstall
	 * @throws UnsupportedOperationException if the provider can not update
	 */
	default void uninstall(Jdk.@NonNull InstalledJdk jdk) {
		throw new UnsupportedOperationException(
				"Uninstalling a JDK is not supported by " + getClass().getName());
	}
}
