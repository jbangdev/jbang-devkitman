package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NonNull;

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

	@NonNull
	@Override
	public List<Jdk> listInstalled() {
		if (Files.isDirectory(defaultJdkLink)) {
			Optional<String> version = JavaUtils.resolveJavaVersionStringFromPath(defaultJdkLink);
			if (version.isPresent()) {
				return Collections.singletonList(
						createJdk(Discovery.PROVIDER_ID, defaultJdkLink, version.get(), false));
			}
		}
		return Collections.emptyList();
	}

	@Override
	public @NonNull Jdk install(@NonNull Jdk jdk) {
		Jdk defJdk = getInstalledById(Discovery.PROVIDER_ID);
		if (defJdk != null && defJdk.isInstalled() && !jdk.equals(defJdk)) {
			uninstall(defJdk);
		}
		// Remove anything that might be in the way
		FileUtils.deletePath(defaultJdkLink);
		// Now create the new link
		FileUtils.createLink(defaultJdkLink, jdk.home());
		return defJdk;
	}

	@Override
	public void uninstall(@NonNull Jdk jdk) {
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
			return new DefaultJdkProvider(config.installPath.resolve(PROVIDER_ID));
		}
	}
}
