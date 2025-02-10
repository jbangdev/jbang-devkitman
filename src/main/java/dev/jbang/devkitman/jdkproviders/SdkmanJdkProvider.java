package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.JdkDiscovery;
import dev.jbang.devkitman.JdkProvider;

/**
 * This JDK provider detects any JDKs that have been installed using the SDKMAN
 * package manager.
 */
public class SdkmanJdkProvider extends BaseFoldersJdkProvider {
	private static final Path JDKS_ROOT = Paths.get(System.getProperty("user.home")).resolve(".sdkman/candidates/java");

	public SdkmanJdkProvider() {
		super(JDKS_ROOT);
	}

	@Override
	public @NonNull String description() {
		return "The JDKs installed using the SDKMAN package manager.";
	}

	public static class Discovery implements JdkDiscovery {
		public static final String PROVIDER_ID = "sdkman";

		@Override
		@NonNull
		public String name() {
			return PROVIDER_ID;
		}

		@Override
		public JdkProvider create(Config config) {
			return new SdkmanJdkProvider();
		}
	}
}
