package dev.jbang.devkitman.jdkproviders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.JdkDiscovery;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.devkitman.util.OsUtils;

/**
 * This JDK provider detects any JDKs that have been installed using the Scoop
 * package manager. Windows only.
 */
public class ScoopJdkProvider extends BaseFoldersJdkProvider {
	private static final Path JDKS_ROOT = Paths.get("scoop", "apps");

	public ScoopJdkProvider() {
		this(jdksRoot());
	}

	ScoopJdkProvider(@NonNull Path jdksRoot) {
		super(jdksRoot);
	}

	public static Path jdksRoot() {
		return Paths.get(System.getProperty("user.home")).resolve(JDKS_ROOT);
	}

	@Override
	public @NonNull String description() {
		return "The JDKs installed using the Scoop package manager.";
	}

	@NonNull
	@Override
	protected Stream<Path> listJdkPaths() throws IOException {
		if (Files.isDirectory(jdksRoot)) {
			return Files.list(jdksRoot)
				.filter(p -> p.getFileName().toString().startsWith("openjdk"))
				.map(p -> p.resolve("current"))
				.filter(this::acceptFolder);
		}
		return Stream.empty();
	}

	@Override
	protected boolean acceptFolder(@NonNull Path jdkFolder) {
		return jdkFolder.getParent().getFileName().toString().startsWith("openjdk") && super.acceptFolder(jdkFolder);
	}

	@Override
	public boolean canUse() {
		return OsUtils.isWindows() && super.canUse();
	}

	public static class Discovery implements JdkDiscovery {
		public static final String PROVIDER_ID = "scoop";

		@Override
		@NonNull
		public String name() {
			return PROVIDER_ID;
		}

		@Override
		public JdkProvider create(@NonNull Config config) {
			return new ScoopJdkProvider();
		}
	}
}
