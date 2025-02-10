package dev.jbang.devkitman;

import static dev.jbang.devkitman.util.FileUtils.deleteOnExit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * This interface gives JDK providers the ability to be discovered and
 * instantiated by code that doesn't know about the specific API the provider
 * implements. See {@link JdkProviders} for a possible implementation of the
 * discovery mechanism.
 */
public interface JdkDiscovery {
	@NonNull
	String name();

	@Nullable
	JdkProvider create(Config config);

	class Config {
		@NonNull
		public final Path installPath;
		@NonNull
		public final Path cachePath;
		@NonNull
		public final Map<String, String> properties;

		public Config(@NonNull Path installPaths) {
			this(installPaths, null, null);
		}

		public Config(
				@NonNull Path installPath,
				@Nullable Path cachePath,
				@Nullable Map<String, String> properties) {
			this.installPath = installPath;
			if (cachePath == null) {
				try {
					this.cachePath = deleteOnExit(Files.createTempDirectory("jdk-provider-cache"));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			} else {
				this.cachePath = cachePath;
			}
			this.properties = new HashMap<>();
			if (properties != null) {
				this.properties.putAll(properties);
			}
		}

		public Config copy() {
			return new Config(installPath, cachePath, properties);
		}
	}
}
