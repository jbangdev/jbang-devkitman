package dev.jbang.devkitman.jdkproviders;

import static dev.jbang.devkitman.Jdk.InstalledJdk.Default.determineTagsFromJdkHome;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
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
	public Jdk.@Nullable LinkedJdk createJdk(@NonNull String id, @Nullable Path home, @NonNull String version,
			@Nullable Set<String> tags) {
		return new Jdk.LinkedJdk.Default(this, id, home, version, tags);
	}

	@Override
	public @NonNull String description() {
		return "Any unmanaged JDKs that have been linked to.";
	}

	@Override
	public Jdk.@Nullable AvailableJdk getAvailableByIdOrToken(String idOrToken) {
		// Check if the token follows our special format
		String[] parts = idOrToken.split("@", 2);
		if (parts.length == 2 && isValidId(parts[0]) && FileUtils.isValidPath(parts[1])) {
			Path jdkPath = Paths.get(parts[1]);
			if (JavaUtils.hasJavacCmd(jdkPath)) {
				Optional<String> version = JavaUtils.resolveJavaVersionStringFromPath(jdkPath);
				if (!version.isPresent()) {
					throw new IllegalArgumentException(
							"Unable to determine Java version in given path: " + jdkPath);
				}
				return new AvailableLinkedJdk(this, parts[0], version.get(), jdkPath,
						determineTagsFromJdkHome(jdkPath));
			}
			return null;
		} else {
			return super.getAvailableByIdOrToken(idOrToken);
		}
	}

	@Override
	protected boolean acceptFolder(@NonNull Path jdkFolder) {
		return super.acceptFolder(jdkFolder) && FileUtils.isLink(jdkFolder);
	}

	@Override
	public Jdk.@NonNull LinkedJdk install(Jdk.@NonNull AvailableJdk jdk) {
		if (!(jdk instanceof AvailableLinkedJdk)) {
			throw new IllegalArgumentException(
					"LinkedJdkInstaller can only install JDKs listed as available by itself");
		}
		AvailableLinkedJdk availJdk = (AvailableLinkedJdk) jdk;
		// If there's an existing installed Jdk with the same id, uninstall it
		Jdk.InstalledJdk existingJdk = getInstalledById(availJdk.id());
		if (existingJdk != null && !FileUtils.isSameFile(availJdk.home, existingJdk.home())) {
			LOGGER.log(
					Level.FINE,
					"A managed JDK already exists, it must be deleted to make sure linking works");
			uninstall(existingJdk);
		}
		Path linkPath = getJdkPath(availJdk.id());
		// Remove anything that might be in the way
		FileUtils.deletePath(linkPath);
		// Now create the new link
		FileUtils.createLink(linkPath, availJdk.home);
		Jdk.LinkedJdk newJdk = (Jdk.LinkedJdk) createJdk(linkPath);
		if (newJdk == null) {
			throw new IllegalStateException("Failed to find JDK in: " + availJdk.home);
		}
		LOGGER.log(Level.INFO, "JDK {0} has been linked to: {1}", new Object[] { availJdk.id(), availJdk.home });
		return newJdk;
	}

	@Override
	public void uninstall(Jdk.@NonNull InstalledJdk jdk) {
		JavaUtils.safeDeleteJdk(jdk.home());
	}

	@Override
	public boolean canUpdate() {
		return true;
	}

	@Override
	public boolean hasFixedVersions() {
		return false;
	}

	@Override
	public boolean hasLinkedVersions() {
		return true;
	}

	static class AvailableLinkedJdk extends Jdk.AvailableJdk.Default {
		public final Path home;

		AvailableLinkedJdk(@NonNull JdkProvider provider, @NonNull String id, @NonNull String version,
				@NonNull Path home, @NonNull Set<String> tags) {
			super(provider, id, version, tags);
			this.home = home;
		}
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
			return new LinkedJdkProvider(config.installPath());
		}
	}
}
