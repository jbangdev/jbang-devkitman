package dev.jbang.devkitman;

import static dev.jbang.devkitman.util.FileUtils.deleteOnExit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.BiFunction;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class JdkInstallers {
	private List<JdkInstallers.Discovery> discoveries;

	private static final JdkInstallers INSTANCE = new JdkInstallers();

	private JdkInstallers() {
	}

	public static JdkInstallers instance() {
		return INSTANCE;
	}

	public static Discovery.Config config(JdkProvider jdkProvider, Map<String, String> properties, Path cachePath) {
		return new Discovery.Config(jdkProvider, properties, cachePath);
	}

	/**
	 * Returns a list of names of all available installers.
	 *
	 * @return a list of installer names
	 */
	public List<String> allNames() {
		LinkedHashSet<String> names = new LinkedHashSet<>();
		ArrayList<String> sorted = new ArrayList<>();
		for (Discovery discovery : discoveries()) {
			sorted.add(discovery.name());
		}
		names.addAll(sorted);
		return new ArrayList<>(names);
	}

	public List<JdkInstaller> all(Discovery.Config config) {
		return parseNames(config, allNames().toArray(new String[0]));
	}

	public List<JdkInstaller> parseNames(Discovery.Config config, String names) {
		return parseNames(config, names.split(","));
	}

	public List<JdkInstaller> parseNames(Discovery.Config config, String... names) {
		ArrayList<JdkInstaller> installers = new ArrayList<>();
		if (names != null) {
			for (String nameAndConfig : names) {
				JdkInstaller installer = parseName(config, nameAndConfig);
				if (installer != null) {
					installers.add(installer);
				}
			}
		}
		return installers;
	}

	public JdkInstaller parseName(Discovery.Config config, String nameAndConfig) {
		return parseName(config, nameAndConfig, this::byName);
	}

	JdkInstaller parseName(
			Discovery.Config config,
			String nameAndConfig,
			BiFunction<String, Discovery.Config, JdkInstaller> action) {
		String[] parts = nameAndConfig.split(";");
		String name = parts[0];
		Discovery.Config cfg = config.copy();
		for (int i = 1; i < parts.length; i++) {
			String[] keyValue = parts[i].split("=");
			if (keyValue.length == 2) {
				cfg.properties().put(keyValue[0], keyValue[1]);
			}
		}
		return action.apply(name, cfg);
	}

	public JdkInstaller byName(String name, Discovery.Config config) {
		for (Discovery discovery : discoveries()) {
			if (discovery.name().equals(name)) {
				JdkInstaller installer = discovery.create(config);
				if (installer != null) {
					return installer;
				}
			}
		}
		return null;
	}

	private synchronized List<Discovery> discoveries() {
		if (discoveries == null) {
			ServiceLoader<Discovery> loader = ServiceLoader.load(Discovery.class);
			discoveries = new ArrayList<>();
			for (Discovery discovery : loader) {
				discoveries.add(discovery);
			}
			discoveries.sort(Comparator.comparing(Discovery::name));
		}
		return discoveries;
	}

	public interface Discovery {
		@NonNull
		String name();

		@Nullable
		JdkInstaller create(Config config);

		class Config {
			@NonNull
			private final JdkProvider jdkProvider;
			@NonNull
			private final Map<String, String> properties;
			private Path cachePath;

			public Config(@NonNull JdkProvider jdkProvider, @Nullable Map<String, String> properties, Path cachePath) {
				this.jdkProvider = jdkProvider;
				this.properties = new HashMap<>();
				if (properties != null) {
					this.properties.putAll(properties);
				}
				this.cachePath = cachePath;
			}

			public @NonNull JdkProvider jdkProvider() {
				return jdkProvider;
			}

			public @NonNull Map<String, String> properties() {
				return properties;
			}

			public @NonNull Path cachePath() {
				if (cachePath == null) {
					// If no cache path is set, we create a temp dir as a curtesy that will be
					// deleted on exit
					try {
						cachePath = deleteOnExit(java.nio.file.Files.createTempDirectory("jdk-installer-cache"));
					} catch (java.io.IOException e) {
						throw new RuntimeException(e);
					}
				}
				return cachePath;
			}

			public Config copy() {
				return new Config(jdkProvider, properties, cachePath);
			}
		}
	}
}
