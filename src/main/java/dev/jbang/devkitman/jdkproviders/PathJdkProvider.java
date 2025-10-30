package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Path;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkDiscovery;
import dev.jbang.devkitman.JdkProvider;
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
	public Stream<Jdk.InstalledJdk> listInstalled() {
		Path jdkHome = null;
		Path javac = OsUtils.searchPath("javac");
		if (javac != null) {
			javac = javac.toAbsolutePath();
			jdkHome = javac.getParent().getParent();
		}
		if (jdkHome != null) {
			Jdk.InstalledJdk jdk = createJdk(Discovery.PROVIDER_ID, jdkHome);
			if (jdk != null) {
				return Stream.of(jdk);
			}
		}
		return Stream.empty();
	}

	@Override
	public boolean hasFixedVersions() {
		return false;
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
