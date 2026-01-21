package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.JdkDiscovery;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.devkitman.util.FileUtils;

/**
 * This JDK provider detects any JDKs that have been installed using Mise
 * (https://mise.jdx.dev/)
 */
public class MiseJdkProvider extends BaseFoldersJdkProvider {
	private static final Path JDKS_ROOT = Paths.get(System.getProperty("user.home"))
		.resolve(".local/share/mise/installs/java");

	public MiseJdkProvider() {
		super(JDKS_ROOT);
	}

	@Override
	public @NonNull String description() {
		return "The JDKs installed using the Mise package manager.";
	}

	@Override
	protected boolean acceptFolder(@NonNull Path jdkFolder) {
		return super.acceptFolder(jdkFolder) && !FileUtils.isSameFolderLink(jdkFolder);
	}

	public static class Discovery implements JdkDiscovery {
		public static final String PROVIDER_ID = "mise";

		@Override
		@NonNull
		public String name() {
			return PROVIDER_ID;
		}

		@Override
		public JdkProvider create(@NonNull Config config) {
			return new MiseJdkProvider();
		}
	}
}
