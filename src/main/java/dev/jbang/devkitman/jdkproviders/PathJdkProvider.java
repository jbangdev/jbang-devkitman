package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkDiscovery;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.devkitman.util.JavaUtils;
import dev.jbang.devkitman.util.OsUtils;

/**
 * This JDK provider detects if a JDK is already available on the system by
 * first looking at the user's <code>PATH</code>.
 */
public class PathJdkProvider extends BaseJdkProvider {

	@Override
	@NonNull
	public String name() {
		return Discovery.PROVIDER_ID;
	}

	@Override
	public @NonNull String description() {
		return "The JDK pointed to by the PATH environment variable.";
	}

	@NonNull
	@Override
	public List<Jdk> listInstalled() {
		Path jdkHome = null;
		Path javac = OsUtils.searchPath("javac");
		if (javac != null) {
			javac = javac.toAbsolutePath();
			jdkHome = javac.getParent().getParent();
		}
		if (jdkHome != null) {
			Optional<String> version = JavaUtils.resolveJavaVersionStringFromPath(jdkHome);
			if (version.isPresent()) {
				return Collections.singletonList(
						createJdk(Discovery.PROVIDER_ID, jdkHome, version.get(), false, null, Collections.emptySet()));
			}
		}
		return Collections.emptyList();
	}

	public static class Discovery implements JdkDiscovery {
		public static final String PROVIDER_ID = "path";

		@Override
		@NonNull
		public String name() {
			return PROVIDER_ID;
		}

		@Override
		public JdkProvider create(Config config) {
			return new PathJdkProvider();
		}
	}
}
