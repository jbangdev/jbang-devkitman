package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkDiscovery;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.devkitman.util.JavaUtils;

/**
 * This JDK provider returns the "current" JDK, which is the JDK that is being
 * used to run the current application.
 */
public class CurrentJdkProvider extends BaseJdkProvider {

	@Override
	public @NonNull String name() {
		return Discovery.PROVIDER_ID;
	}

	@Override
	public @NonNull String description() {
		return "The JDK that is being used to run the current application.";
	}

	@Override
	public @NonNull List<Jdk> listInstalled() {
		String jh = System.getProperty("java.home");
		if (jh != null) {
			Path jdkHome = Paths.get(jh);
			jdkHome = JavaUtils.jre2jdk(jdkHome);
			Optional<String> version = JavaUtils.resolveJavaVersionStringFromPath(jdkHome);
			if (version.isPresent()) {
				return Collections.singletonList(
						createJdk(Discovery.PROVIDER_ID, jdkHome, version.get(), false, null, Collections.emptySet()));
			}
		}
		return Collections.emptyList();
	}

	public static class Discovery implements JdkDiscovery {
		public static final String PROVIDER_ID = "current";

		@Override
		public @NonNull String name() {
			return PROVIDER_ID;
		}

		@Override
		public @NonNull JdkProvider create(Config config) {
			return new CurrentJdkProvider();
		}
	}
}
