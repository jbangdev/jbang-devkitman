package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkDiscovery;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.devkitman.util.JavaUtils;
import dev.jbang.devkitman.util.OsUtils;

/**
 * This JDK provider detects JDKs registered under HKLM\SOFTWARE\JavaSoft.
 */
public class WindowsJdkProvider extends BaseJdkProvider {
	public static final String JAVA_SOFT_KEY = "HKLM\\SOFTWARE\\JavaSoft";

	private static final Logger LOGGER = Logger.getLogger(WindowsJdkProvider.class.getName());
	private static final Pattern KEY_LINE = Pattern.compile("^\\s*HKEY_.*$");
	private static final Pattern JAVA_HOME_LINE = Pattern.compile("^\\s*JavaHome\\s+REG_\\w+\\s+(.+?)\\s*$");
	private static final Pattern VALID_ID = Pattern.compile("^[a-zA-Z0-9._+-]+-windows$");

	private final RegistryReader registryReader;

	public WindowsJdkProvider() {
		this(new RegCommandRegistryReader());
	}

	public WindowsJdkProvider(@NonNull RegistryReader registryReader) {
		this.registryReader = registryReader;
	}

	@Override
	@NonNull
	public String name() {
		return Discovery.PROVIDER_ID;
	}

	@Override
	public @NonNull String description() {
		return "The JDKs registered in the HKLM\\SOFTWARE\\JavaSoft registry key.";
	}

	@Override
	public boolean canUse() {
		return OsUtils.isWindows();
	}

	@Override
	public boolean isValidId(@NonNull String id) {
		return VALID_ID.matcher(id).matches();
	}

	@NonNull
	@Override
	public Stream<Jdk.InstalledJdk> listInstalled() {
		return dedupeByJavaHome(registryReader.listJavaHomes(JAVA_SOFT_KEY))
			.entrySet()
			.stream()
			.map(this::createJdk)
			.filter(Objects::nonNull);
	}

	private Map<String, Path> dedupeByJavaHome(Map<String, Path> javaHomes) {
		Map<Path, Entry<String, Path>> selectedByHome = new LinkedHashMap<>();
		for (Entry<String, Path> entry : javaHomes.entrySet()) {
			selectedByHome.merge(entry.getValue(), entry, this::preferLongestKey);
		}
		Map<String, Path> deduped = new LinkedHashMap<>();
		for (Entry<String, Path> entry : selectedByHome.values()) {
			deduped.put(entry.getKey(), entry.getValue());
		}
		return deduped;
	}

	private Entry<String, Path> preferLongestKey(Entry<String, Path> existing, Entry<String, Path> replacement) {
		return replacement.getKey().length() > existing.getKey().length() ? replacement : existing;
	}

	private Jdk.InstalledJdk createJdk(Map.Entry<String, Path> javaHomeEntry) {
		Path javaHome = javaHomeEntry.getValue();
		if (!Files.isDirectory(javaHome) || !JavaUtils.hasJavacCmd(javaHome)) {
			return null;
		}
		String id = registryVersionSegment(javaHomeEntry.getKey()) + "-" + name();
		return createJdk(id, javaHome);
	}

	private String registryVersionSegment(String registryKey) {
		int idx = registryKey.lastIndexOf('\\');
		String segment = idx >= 0 && idx + 1 < registryKey.length() ? registryKey.substring(idx + 1) : registryKey;
		return segment.replaceAll("[^a-zA-Z0-9._+-]+", "_");
	}

	public interface RegistryReader {
		@NonNull
		Map<String, Path> listJavaHomes(@NonNull String rootKey);
	}

	static class RegCommandRegistryReader implements RegistryReader {
		@Override
		public @NonNull Map<String, Path> listJavaHomes(@NonNull String rootKey) {
			Map<String, Path> javaHomes = new LinkedHashMap<>();
			String output = OsUtils.runCommand("reg", "query", rootKey, "/s", "/v", "JavaHome");
			if (output == null || output.trim().isEmpty()) {
				return javaHomes;
			}
			String currentKey = null;
			for (String line : output.split("\\r?\\n")) {
				Matcher keyMatcher = KEY_LINE.matcher(line);
				if (keyMatcher.matches()) {
					currentKey = line.trim();
					continue;
				}
				if (currentKey == null) {
					continue;
				}
				Matcher javaHomeMatcher = JAVA_HOME_LINE.matcher(line);
				if (javaHomeMatcher.matches()) {
					String home = javaHomeMatcher.group(1).trim();
					try {
						javaHomes.put(currentKey, Paths.get(home));
					} catch (InvalidPathException ex) {
						LOGGER.log(Level.FINE, "Ignoring invalid registry JavaHome: " + home, ex);
					}
				}
			}
			return javaHomes;
		}
	}

	public static class Discovery implements JdkDiscovery {
		public static final String PROVIDER_ID = "windows";

		@Override
		@NonNull
		public String name() {
			return PROVIDER_ID;
		}

		@Override
		public JdkProvider create(@NonNull Config config) {
			return new WindowsJdkProvider();
		}
	}
}
