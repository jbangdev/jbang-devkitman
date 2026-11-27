package dev.jbang.devkitman.jdkproviders;

import java.util.Objects;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkDiscovery;
import dev.jbang.devkitman.JdkFilter;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.devkitman.util.FileUtils;
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
		return listInstalled(JdkFilter.JDK);
	}

	@Override
	public Stream<Jdk.InstalledJdk> listInstalled(JdkFilter filter) {
		// Resolve both commands independently: java and javac may belong to
		// different installations. Preserve the JDK-only lookup's javac precedence.
		return Stream.of(OsUtils.searchPath("javac"), OsUtils.searchPath("java"))
			.filter(Objects::nonNull)
			.map(command -> FileUtils.realPath(command.toAbsolutePath()).getParent().getParent())
			.distinct()
			.map(home -> createJdk(Discovery.PROVIDER_ID, home))
			.filter(Objects::nonNull)
			.filter(filter::matches);
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
		public JdkProvider create(@NonNull Config config) {
			return new PathJdkProvider();
		}
	}
}
