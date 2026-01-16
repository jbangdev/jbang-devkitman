package dev.jbang.devkitman.jdkproviders;

import static dev.jbang.devkitman.Jdk.InstalledJdk.Default.determineTagsFromJdkHome;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkDiscovery;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.devkitman.util.FileUtils;
import dev.jbang.devkitman.util.JavaUtils;

/**
 * This JDK provider returns the "default" JDK if it was set. This is not a JDK
 * in itself but a link to a JDK that was set as the default. The path that is
 * configured for this default JDK should be stable and unchanging so it can be
 * added to the user's PATH.
 */
public class DefaultJdkProvider extends BaseFoldersJdkProvider {

	@NonNull
	protected final Path defaultJdkLink;

	public DefaultJdkProvider(@NonNull Path defaultJdkLink, @NonNull Path defaultJdkDir) {
		super(defaultJdkDir);
		this.defaultJdkLink = defaultJdkLink;
	}

	@Override
	public Jdk.@Nullable LinkedJdk createJdk(@NonNull String id, @Nullable Path home, @NonNull String version,
			@Nullable Set<String> tags) {
		return new Jdk.LinkedJdk.Default(this, id, home, version, tags);
	}

	@Override
	@NonNull
	public String name() {
		return Discovery.PROVIDER_ID;
	}

	@Override
	public @NonNull String description() {
		return "The JDKs that have been set as the default (globally or per major version).";
	}

	/**
	 * This is a very special implementation that takes token of the form "id@path",
	 * where id is either the word "default" or an integer followed by "-default"
	 * and path is a path to another JDK. No major validation is done except for the
	 * fact that the path exists and contains a JDK. Any other validations must have
	 * been performed beforehand by the caller.
	 * 
	 * @param idOrToken A string containing a path to an existing JDK
	 * @return A jdk object or <code>null</code>
	 */
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
				return new AvailableDefaultJdk(this, parts[0], version.get(), jdkPath,
						determineTagsFromJdkHome(jdkPath));
			}
			return null;
		} else {
			return super.getAvailableByIdOrToken(idOrToken);
		}
	}

	@Override
	public Jdk.@Nullable LinkedJdk getInstalledByVersion(int version, boolean openVersion) {
		// First we check if the "default" link exists and has the correct version
		if (acceptFolder(defaultJdkLink)) {
			Optional<String> v = JavaUtils.resolveJavaVersionStringFromPath(defaultJdkLink);
			if (v.isPresent() && JavaUtils.parseJavaVersion(v.get()) == version) {
				return (Jdk.LinkedJdk) createJdk(defaultJdkLink);
			}
		}
		// Then we check if there's a link with the exact number matching the version
		Path jdk = jdksRoot.resolve(Integer.toString(version));
		if (Files.isDirectory(jdk)) {
			return (Jdk.LinkedJdk) createJdk(jdk);
		} else {
			// Finally we fall back to the default implementation
			return (Jdk.LinkedJdk) super.getInstalledByVersion(version, openVersion);
		}
	}

	@Override
	public Jdk.@NonNull LinkedJdk install(Jdk.@NonNull AvailableJdk jdk) {
		if (!(jdk instanceof AvailableDefaultJdk)) {
			throw new IllegalArgumentException(
					"DefaultJdkInstaller can only install JDKs listed as available by itself");
		}
		AvailableDefaultJdk availJdk = (AvailableDefaultJdk) jdk;
		Jdk.InstalledJdk existingJdk = getInstalledById(availJdk.id());
		if (existingJdk != null && existingJdk.isInstalled() && !availJdk.home.equals(existingJdk.home())) {
			uninstall(existingJdk);
		}
		Path linkPath = getJdkPath(availJdk.id());
		// Remove anything that might be in the way
		FileUtils.deletePath(linkPath);
		// Now create the new link
		FileUtils.createLink(linkPath, availJdk.home);
		Jdk.LinkedJdk newJdk = (Jdk.LinkedJdk) createJdk(linkPath);
		if (newJdk == null) {
			throw new IllegalStateException("Failed to find JDK in: " + linkPath);
		}
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
	@NonNull
	protected Stream<Path> listJdkPaths() throws IOException {
		if (acceptFolder(defaultJdkLink)) {
			return Stream.concat(Stream.of(defaultJdkLink),
					super.listJdkPaths().filter(p -> !p.equals(defaultJdkLink)));
		} else {
			return super.listJdkPaths();
		}
	}

	@Override
	@NonNull
	protected Path getJdkPath(@NonNull String id) {
		if (name().equals(id)) {
			return defaultJdkLink;
		} else {
			String name = id.substring(0, id.length() - name().length() - 1);
			return jdksRoot.resolve(name);
		}
	}

	protected boolean acceptFolder(@NonNull Path jdkFolder) {
		if (!jdkFolder.equals(defaultJdkLink) && !jdkFolder.startsWith(jdksRoot)) {
			return false;
		}
		String nm = jdkFolder.getFileName().toString();
		// Check the folder is either the default link or it's name is a number
		if (!jdkFolder.equals(defaultJdkLink) && JavaUtils.parseToInt(nm, 0) == 0) {
			return false;
		}
		return FileUtils.isLink(jdkFolder) && JavaUtils.hasJavacCmd(jdkFolder);
	}

	@Override
	public boolean isValidId(@NonNull String id) {
		if (id.equals(name())) {
			return true;
		} else if (id.endsWith("-" + name())) {
			String version = id.substring(0, id.length() - name().length() - 1);
			return JavaUtils.parseToInt(version, 0) > 0;
		}
		return false;
	}

	@Override
	public String jdkId(@NonNull Path jdkFolder) {
		if (jdkFolder.equals(defaultJdkLink)) {
			return "default";
		} else {
			// For backward compatibility, the default folders are named with a number
			// (e.g. "11", "17", etc.) but for the id we need to add the provider name
			String name = jdkFolder.getFileName().toString();
			return name + "-" + name();
		}
	}

	@Override
	public boolean hasFixedVersions() {
		return false;
	}

	@Override
	public boolean hasLinkedVersions() {
		return true;
	}

	static class AvailableDefaultJdk extends Jdk.AvailableJdk.Default {
		public final Path home;

		AvailableDefaultJdk(@NonNull JdkProvider provider, @NonNull String id, @NonNull String version,
				@NonNull Path home, @NonNull Set<String> tags) {
			super(provider, id, version, tags);
			this.home = home;
		}
	}

	public static class Discovery implements JdkDiscovery {
		public static final String PROVIDER_ID = "default";

		@Override
		@NonNull
		public String name() {
			return PROVIDER_ID;
		}

		@Override
		public JdkProvider create(Config config) {
			String defaultLink = config.properties()
				.computeIfAbsent("link",
						k -> config.installPath().resolve(PROVIDER_ID).toString());
			String defaultDir = config.properties()
				.computeIfAbsent("dir",
						k -> config.installPath().toString());
			return new DefaultJdkProvider(Paths.get(defaultLink), Paths.get(defaultDir));
		}
	}
}
