package dev.jbang.devkitman.jdkproviders;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkDiscovery;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.devkitman.util.FileUtils;
import dev.jbang.devkitman.util.JavaUtils;

/**
 * This JDK provider returns JDKs that are not managed or found by any of the
 * providers but that are available on the user's system and that the user has
 * explicitly told we can use. Each of those JDKs is represented by a symbolic
 * link to the actual JDK folder.
 */
public class LinkedJdkProvider extends BaseFoldersJdkProvider {
	private static final Logger LOGGER = Logger.getLogger(LinkedJdkProvider.class.getName());

	public LinkedJdkProvider(Path jdksRoot) {
		super(jdksRoot);
	}

	@Override
	public @NonNull String description() {
		return "Any unmanaged JDKs that the user has linked to.";
	}

	@Override
	public boolean canUse() {
		return true;
	}

	@Override
	public @Nullable Jdk getAvailableByIdOrToken(String idOrToken) {
		String[] parts = idOrToken.split("@", 2);
		if (parts.length == 2 && isValidId(parts[0]) && isValidPath(parts[1])) {
			Path jdkPath = Paths.get(parts[1]);
			if (super.acceptFolder(jdkPath)) {
				Optional<String> version = JavaUtils.resolveJavaVersionStringFromPath(jdkPath);
				if (!version.isPresent()) {
					throw new IllegalArgumentException(
							"Unable to determine Java version in given path: " + jdkPath);
				}
				return createJdk(idOrToken, null, version.get());
			}
			return null;
		} else {
			return super.getAvailableByIdOrToken(idOrToken);
		}
	}

	private static boolean isValidPath(String path) {
		try {
			Paths.get(path);
			return true;
		} catch (InvalidPathException e) {
			return false;
		}
	}

	@Override
	protected boolean acceptFolder(Path jdkFolder) {
		return isValidId(jdkFolder.getFileName().toString())
				&& super.acceptFolder(jdkFolder)
				&& FileUtils.isLink(jdkFolder);
	}

	@Override
	public @NonNull Jdk install(@NonNull Jdk jdk) {
		if (jdk.isInstalled()) {
			return jdk;
		}
		// Check this Jdk's id follows our special format
		String[] parts = jdk.id().split("@", 2);
		if (parts.length != 2 || !isValidPath(parts[1])) {
			throw new IllegalStateException("Invalid linked Jdk id: " + jdk.id());
		}
		String id = parts[0];
		Path jdkPath = Paths.get(parts[1]);
		// If there's an existing installed Jdk with the same id, uninstall it
		Jdk existingJdk = getInstalledById(jdkId(id));
		if (existingJdk != null && existingJdk.isInstalled() && !jdk.equals(existingJdk)) {
			LOGGER.log(
					Level.FINE,
					"A managed JDK already exists, it must be deleted to make sure linking works");
			uninstall(existingJdk);
		}
		Path linkPath = getJdkPath(id);
		// Remove anything that might be in the way
		FileUtils.deletePath(linkPath);
		// Now create the new link
		FileUtils.createLink(linkPath, jdkPath);
		Jdk newJdk = Objects.requireNonNull(createJdk(linkPath));
		LOGGER.log(Level.INFO, "JDK {0} has been linked to: {1}", new Object[] { id, jdkPath });
		return newJdk;
	}

	@Override
	public void uninstall(@NonNull Jdk jdk) {
		if (jdk.isInstalled()) {
			FileUtils.deletePath(jdk.home());
			LOGGER.log(Level.INFO, "JDK {0} has been uninstalled", new Object[] { jdk.id() });
		}
	}

	// TODO remove these 3 methods when switching to the new folder structure
	@NonNull
	@Override
	public String jdkId(String name) {
		int majorVersion = JavaUtils.parseJavaVersion(name);
		return Integer.toString(majorVersion);
	}

	@Override
	public boolean isValidId(@NonNull String id) {
		return JavaUtils.parseToInt(id, 0) > 0;
	}

	@NonNull
	@Override
	protected Path getJdkPath(@NonNull String jdk) {
		return jdksRoot.resolve(Integer.toString(JavaUtils.parseToInt(jdk, 0)));
	}

	public static class Discovery implements JdkDiscovery {
		public static final String PROVIDER_ID = "linked";

		@Override
		@NonNull
		public String name() {
			return PROVIDER_ID;
		}

		@Override
		public JdkProvider create(Config config) {
			return new LinkedJdkProvider(config.installPath);
		}
	}
}
