package dev.jbang.devkitman;

import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiFunction;

public class JdkProviders {
	private List<JdkDiscovery> discoveries;

	private static final JdkProviders INSTANCE = new JdkProviders();

	private JdkProviders() {
	}

	public static JdkProviders instance() {
		return INSTANCE;
	}

	/**
	 * Returns an ordered list of names of the minimal set of providers that can be
	 * used to find JDKs already available in the user's environment. This does
	 * specifically not include providers that can install JDKs, nor does it include
	 * 3rd party or platform providers.
	 *
	 * @return a list of provider names
	 */
	public List<String> minimalNames() {
		return Arrays.asList("current", "javahome", "path");
	}

	/**
	 * Returns an ordered list of names of a basic set of providers that are needed
	 * to deliver proper functionality for the JBang tool. TODO: Remove this once
	 * JBang defaults to all()
	 *
	 * @return a list of provider names
	 */
	public List<String> basicNames() {
		return Arrays.asList("current", "default", "javahome", "path", "linked", "jbang");
	}

	/**
	 * Returns an ordered list of names of all available providers. The list will
	 * always start with the names returned by {@link #basicNames()} and then
	 * followed by the names of any remaining providers.
	 *
	 * @return a list of provider names
	 */
	public List<String> allNames() {
		LinkedHashSet<String> names = new LinkedHashSet<>(basicNames());
		ArrayList<String> sorted = new ArrayList<>();
		for (JdkDiscovery discovery : discoveries()) {
			sorted.add(discovery.name());
		}
		names.addAll(sorted);
		return new ArrayList<>(names);
	}

	public List<JdkProvider> minimal() {
		JdkDiscovery.Config cfg = new JdkDiscovery.Config(Paths.get(""));
		return parseNames(cfg, minimalNames().toArray(new String[0]));
	}

	public List<JdkProvider> basic(JdkDiscovery.Config config) {
		return parseNames(config, basicNames().toArray(new String[0]));
	}

	public List<JdkProvider> all(JdkDiscovery.Config config) {
		return parseNames(config, allNames().toArray(new String[0]));
	}

	public List<JdkProvider> parseNames(JdkDiscovery.Config config, String names) {
		return parseNames(config, names.split(","));
	}

	public List<JdkProvider> parseNames(JdkDiscovery.Config config, String... names) {
		ArrayList<JdkProvider> providers = new ArrayList<>();
		if (names != null) {
			for (String nameAndConfig : names) {
				JdkProvider provider = parseName(config, nameAndConfig);
				if (provider != null) {
					providers.add(provider);
				}
			}
		}
		return providers;
	}

	public JdkProvider parseName(JdkDiscovery.Config config, String nameAndConfig) {
		return parseName(config, nameAndConfig, this::byName);
	}

	JdkProvider parseName(
			JdkDiscovery.Config config,
			String nameAndConfig,
			BiFunction<String, JdkDiscovery.Config, JdkProvider> action) {
		String[] parts = nameAndConfig.split(";");
		String name = parts[0];
		JdkDiscovery.Config providerConfig = config.copy();
		for (int i = 1; i < parts.length; i++) {
			String[] keyValue = parts[i].split("=");
			if (keyValue.length == 2) {
				providerConfig.properties.put(keyValue[0], keyValue[1]);
			}
		}
		return action.apply(name, providerConfig);
	}

	public JdkProvider byName(String name, JdkDiscovery.Config config) {
		for (JdkDiscovery discovery : discoveries()) {
			if (discovery.name().equals(name)) {
				JdkProvider provider = discovery.create(config);
				if (provider != null) {
					return provider;
				}
			}
		}
		return null;
	}

	private synchronized List<JdkDiscovery> discoveries() {
		if (discoveries == null) {
			ServiceLoader<JdkDiscovery> loader = ServiceLoader.load(JdkDiscovery.class);
			discoveries = new ArrayList<>();
			for (JdkDiscovery discovery : loader) {
				discoveries.add(discovery);
			}
			discoveries.sort(Comparator.comparing(JdkDiscovery::name));
		}
		return discoveries;
	}
}
