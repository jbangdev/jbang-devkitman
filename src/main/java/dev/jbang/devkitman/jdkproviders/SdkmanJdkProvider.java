package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.JdkDiscovery;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.devkitman.util.FileUtils;
import dev.jbang.devkitman.util.JavaUtils;

/**
 * This JDK provider detects any JDKs that have been installed using the SDKMAN
 * package manager.
 */
public class SdkmanJdkProvider extends BaseFoldersJdkProvider {
	private static final Path JDKS_ROOT = Paths.get(".sdkman", "candidates", "java");

	public SdkmanJdkProvider() {
		super(Paths.get(System.getProperty("user.home")).resolve(JDKS_ROOT));
	}

	@Override
	public @NonNull String description() {
		return "The JDKs installed using the SDKMAN package manager.";
	}

	@Override
	protected boolean acceptFolder(@NonNull Path jdkFolder) {
		return jdkFolder.startsWith(jdksRoot)
				&& !FileUtils.isSameFolderLink(jdkFolder)
				&& JavaUtils.hasJavacCmd(jdkFolder);
	}

	public static class Discovery implements JdkDiscovery {
		public static final String PROVIDER_ID = "sdkman";

		@Override
		@NonNull
		public String name() {
			return PROVIDER_ID;
		}

		@Override
		public JdkProvider create(@NonNull Config config) {
			return new SdkmanJdkProvider();
		}
	}
}
