package dev.jbang.devkitman.jdkproviders;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkDiscovery;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.devkitman.util.OsUtils;

/**
 * This JDK provider detects any JDKs that have been installed using the Scoop
 * package manager. Windows only.
 */
public class ScoopJdkProvider extends BaseFoldersJdkProvider {
	private static final Path SCOOP_APPS = Paths.get(System.getProperty("user.home")).resolve("scoop/apps");

	public ScoopJdkProvider() {
		super(SCOOP_APPS);
	}

	@Override
	public @NonNull String description() {
		return "The JDKs installed using the Scoop package manager.";
	}

	@NonNull
	@Override
	protected Stream<Path> listJdkPaths() throws IOException {
		return super.listJdkPaths().map(p -> p.resolve("current"));
	}

	@Override
	protected boolean acceptFolder(@NonNull Path jdkFolder) {
		return jdkFolder.getFileName().startsWith("openjdk") && super.acceptFolder(jdkFolder);
	}

	@Override
	protected Jdk.@Nullable InstalledJdk createJdk(Path home) {
		try {
			// Try to resolve any links
			home = home.toRealPath();
		} catch (IOException e) {
			throw new IllegalStateException("Couldn't resolve 'current' link: " + home, e);
		}
		return super.createJdk(home);
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
		public JdkProvider create(Config config) {
			return new ScoopJdkProvider();
		}
	}
}
