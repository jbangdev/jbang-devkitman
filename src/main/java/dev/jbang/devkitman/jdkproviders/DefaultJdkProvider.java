package dev.jbang.devkitman.jdkproviders;

import static dev.jbang.devkitman.Jdk.InstalledJdk.Default.determineTagsFromJdkHome;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

	@NonNull
	@Override
	public List<Jdk.InstalledJdk> listInstalled() {
		if (Files.isDirectory(defaultJdkLink)) {
			Jdk.InstalledJdk jdk = createJdk(Discovery.PROVIDER_ID, defaultJdkLink, null, null);
			if (jdk != null) {
				return Collections.singletonList(jdk);
			}
		}
		return Collections.emptyList();
	}

	@Override
	public Jdk.@Nullable InstalledJdk getInstalledByVersion(int version, boolean openVersion) {
		Path jdk = jdksRoot.resolve(Integer.toString(version));
		if (Files.isDirectory(jdk)) {
			return createJdk(jdk);
		} else {
			return super.getInstalledByVersion(version, true);
		}
	}

	@Override
	public Jdk.@NonNull InstalledJdk install(Jdk.@NonNull AvailableJdk jdk) {
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
		Jdk.InstalledJdk newJdk = createJdk(linkPath);
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
	@NonNull
	protected Path getJdkPath(@NonNull String id) {
		if (name().equals(id)) {
			return defaultJdkLink;
		} else {
			String name = id.substring(0, id.length() - name().length() - 1);
			return jdksRoot.resolve(name);
		}
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
	public boolean hasFixedVersions() {
		return false;
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
			String defaultLink = config.properties.computeIfAbsent("link",
					k -> config.installPath.resolve(PROVIDER_ID).toString());
			String defaultDir = config.properties.computeIfAbsent("dir",
					k -> config.installPath.toString());
			return new DefaultJdkProvider(Paths.get(defaultLink), Paths.get(defaultDir));
		}
	}
}
