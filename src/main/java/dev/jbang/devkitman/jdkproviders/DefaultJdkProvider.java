package dev.jbang.devkitman.jdkproviders;

import static dev.jbang.devkitman.Jdk.InstalledJdk.Default.determineTagsFromJdkHome;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
public class DefaultJdkProvider extends BaseJdkProvider {
	@NonNull
	protected final Path defaultJdkLink;

	public DefaultJdkProvider(@NonNull Path defaultJdkLink) {
		this.defaultJdkLink = defaultJdkLink;
	}

	@Override
	@NonNull
	public String name() {
		return Discovery.PROVIDER_ID;
	}

	@Override
	public @NonNull String description() {
		return "The JDK that is set as the default JDK.";
	}

	/**
	 * This is a very special implementation that takes a path to another JDK. No
	 * major validation is done except for the fact that the path exists and
	 * contains a JDK. Any other validations must have been performed beforehand by
	 * the caller.
	 * 
	 * @param idOrToken A string containing a path to an existing JDK
	 * @return A jdk object or <code>null</code>
	 */
	@Override
	public Jdk.@Nullable AvailableJdk getAvailableByIdOrToken(String idOrToken) {
		Path home = Paths.get(idOrToken);
		if (Files.isDirectory(home)) {
			Optional<String> v = JavaUtils.resolveJavaVersionStringFromPath(home);
			if (v.isPresent()) {
				return new Jdk.AvailableJdk.Default(this, idOrToken, v.get(), determineTagsFromJdkHome(home));
			}
		}
		return null;
	}

	@NonNull
	@Override
	public List<Jdk.InstalledJdk> listInstalled() {
		if (Files.isDirectory(defaultJdkLink)) {
			Jdk.InstalledJdk jdk = createJdk(Discovery.PROVIDER_ID, defaultJdkLink, null, false, null);
			if (jdk != null) {
				return Collections.singletonList(jdk);
			}
		}
		return Collections.emptyList();
	}

	@Override
	public Jdk.@NonNull InstalledJdk install(Jdk.@NonNull AvailableJdk jdk) {
		Path home = Paths.get(jdk.id());
		Jdk.InstalledJdk defJdk = getInstalledById(Discovery.PROVIDER_ID);
		if (defJdk != null && defJdk.isInstalled() && !home.equals(defJdk.home())) {
			uninstall(defJdk);
		}
		// Remove anything that might be in the way
		FileUtils.deletePath(defaultJdkLink);
		// Now create the new link
		FileUtils.createLink(defaultJdkLink, home);
		return defJdk;
	}

	@Override
	public void uninstall(Jdk.@NonNull InstalledJdk jdk) {
		FileUtils.deletePath(defaultJdkLink);
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
			return new DefaultJdkProvider(Paths.get(defaultLink));
		}
	}
}
