package dev.jbang.devkitman;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * This interface must be implemented by installers that are able to install
 * JDKs on the user's system. They should be able to install and uninstall them
 * at the user's request.
 */
public interface JdkInstaller {

	/**
	 * This method returns a list of distributions that are supported by this
	 * installer.
	 *
	 * @return List of distribution names
	 */
	@NonNull
	default List<Distro> listDistros() {
		throw new UnsupportedOperationException(
				"Listing available distributions is not supported by " + getClass().getName());
	}

	/**
	 * This method returns a set of JDKs that are available for installation.
	 * Implementations might set the <code>home</code> field of the JDK objects if
	 * the respective JDK is currently installed on the user's system, but only if
	 * they can ensure that it's the exact same version, otherwise they should just
	 * leave the field <code>null</code>.
	 *
	 * @param distros Comma separated list of distribution names to look for. Can be
	 *                null to use a default selection defined by the installer. Use
	 *                an empty string to list all.
	 * @param tags    The tags to filter the JDKs by. Can be null to list all.
	 * @return List of <code>Jdk</code> objects
	 */
	@NonNull
	default List<Jdk> listAvailable(String distros, Set<String> tags) {
		throw new UnsupportedOperationException(
				"Listing available JDKs is not supported by " + getClass().getName());
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
	@Nullable
	default Jdk getAvailableByIdOrToken(String idOrToken) {
		return JdkManager.getJdkBy(listAvailable(null, null).stream(), Jdk.Predicates.id(idOrToken))
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
	@NonNull
	default Jdk install(@NonNull Jdk jdk, Path installDir) {
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
		throw new UnsupportedOperationException(
				"Uninstalling a JDK is not supported by " + getClass().getName());
	}
}
