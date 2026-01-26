package dev.jbang.devkitman.util;

import static java.lang.System.getenv;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;

public class JavaUtils {

	private static final Pattern javaVersionPattern = Pattern.compile("\"([^\"]+)\"");

	private static final Logger LOGGER = Logger.getLogger(JavaUtils.class.getName());

	public static boolean isRequestedVersion(String rv) {
		return rv.matches("\\d+[+]?");
	}

	public static int minRequestedVersion(String rv) {
		return Integer.parseInt(isOpenVersion(rv) ? rv.substring(0, rv.length() - 1) : rv);
	}

	public static boolean isOpenVersion(String version) {
		return version.endsWith("+");
	}

	public static int parseJavaVersion(String version) {
		if (version != null) {
			String[] nums = version.split("[-.+]");
			String num = nums.length > 1 && nums[0].equals("1") ? nums[1] : nums[0];
			return parseToInt(num, 0);
		}
		return 0;
	}

	public static int parseToInt(String number, int defaultValue) {
		if (number != null) {
			try {
				return Integer.parseInt(number);
			} catch (NumberFormatException ex) {
				// Ignore
			}
		}
		return defaultValue;
	}

	public static Optional<Integer> resolveJavaVersionFromPath(@NonNull Path home) {
		return resolveJavaVersionStringFromPath(home).map(JavaUtils::parseJavaVersion);
	}

	public static Optional<String> resolveJavaVersionStringFromPath(@NonNull Path home) {
		Optional<String> res = readJavaVersionStringFromReleaseFile(home);
		if (!res.isPresent()) {
			res = readJavaVersionStringFromJavaCommand(home);
		}
		return res;
	}

	public static Optional<String> readJavaVersionStringFromReleaseFile(@NonNull Path home) {
		return readVersionStringFromReleaseFile(home,
				l -> l.startsWith("JAVA_VERSION=") || l.startsWith("JAVA_RUNTIME_VERSION="));
	}

	public static Optional<String> readGraalVMVersionStringFromReleaseFile(@NonNull Path home) {
		return readVersionStringFromReleaseFile(home, l -> l.startsWith("GRAALVM_VERSION="));
	}

	public static Optional<String> readVersionStringFromReleaseFile(@NonNull Path home, Predicate<String> lineFilter) {
		try (Stream<String> lines = Files.lines(home.resolve("release"))) {
			return lines.filter(lineFilter)
				.map(JavaUtils::parseJavaOutput)
				.findAny();
		} catch (IOException e) {
			LOGGER.fine("Unable to read 'release' file in path: " + home);
			return Optional.empty();
		}
	}

	public static Optional<String> readJavaVersionStringFromJavaCommand(@NonNull Path home) {
		Optional<String> res;
		Path javaCmd = OsUtils.searchPath("java", home.resolve("bin").toString());
		if (javaCmd != null) {
			String output = OsUtils.runCommand(javaCmd.toString(), "-version");
			res = Optional.ofNullable(parseJavaOutput(output));
		} else {
			res = Optional.empty();
		}
		if (!res.isPresent()) {
			LOGGER.log(Level.FINE, "Unable to obtain version from: '{0} -version'", javaCmd);
		}
		return res;
	}

	public static String parseJavaOutput(String output) {
		if (output != null) {
			Matcher m = javaVersionPattern.matcher(output);
			if (m.find() && m.groupCount() == 1) {
				return m.group(1);
			}
		}
		return null;
	}

	public static boolean hasJavaCmd(@NonNull Path jdkFolder) {
		return OsUtils.searchPath("java", jdkFolder.resolve("bin").toString()) != null;
	}

	public static boolean hasJavacCmd(@NonNull Path jdkFolder) {
		return OsUtils.searchPath("javac", jdkFolder.resolve("bin").toString()) != null;
	}

	public static boolean hasNativeImageCmd(@NonNull Path jdkFolder) {
		return OsUtils.searchPath("native-image", jdkFolder.resolve("bin").toString()) != null;
	}

	/**
	 * Returns the Path to JAVA_HOME
	 *
	 * @return A Path pointing to JAVA_HOME or null if it isn't defined
	 */
	public static Path getJavaHomeEnv() {
		if (getenv("JAVA_HOME") != null) {
			return Paths.get(getenv("JAVA_HOME"));
		} else {
			return null;
		}
	}

	/**
	 * Method takes the given path which might point to a Java home directory or to
	 * the `jre` directory inside it and makes sure to return the path to the actual
	 * home directory.
	 */
	public static Path jre2jdk(@NonNull Path jdkHome) {
		// Detect if the current JDK is a JRE and try to find the real home
		if (!Files.isRegularFile(jdkHome.resolve("release"))) {
			Path jh = jdkHome.toAbsolutePath();
			try {
				jh = jh.toRealPath();
			} catch (IOException e) {
				// Ignore error
			}
			if (jh.endsWith("jre") && Files.isRegularFile(jh.getParent().resolve("release"))) {
				jdkHome = jh.getParent();
			}
		}
		return jdkHome;
	}

	public static void safeDeleteJdk(@NonNull Path jdkHome) {
		if (OsUtils.isWindows()) {
			// On Windows we have to check nobody is currently using the JDK or we could
			// be causing all kinds of trouble
			try {
				Path jdkTmpDir = jdkHome
					.getParent()
					.resolve("_delete_me_" + jdkHome.getFileName().toString());
				Files.move(jdkHome, jdkTmpDir);
				FileUtils.deletePath(jdkTmpDir);
			} catch (IOException ex) {
				LOGGER.log(Level.WARNING, "Cannot uninstall JDK, it's being used: {0}", jdkHome);
				return;
			}
		} else {
			FileUtils.deletePath(jdkHome);
		}
	}

	public static void installJdk(Path jdkPkg, Path jdkDir) throws IOException {
		Path jdkTmpDir = jdkDir.getParent().resolve(jdkDir.getFileName() + ".tmp");
		Path jdkOldDir = jdkDir.getParent().resolve(jdkDir.getFileName() + ".old");
		FileUtils.deletePath(jdkTmpDir);
		FileUtils.deletePath(jdkOldDir);
		try {
			LOGGER.log(Level.FINE, "Unpacking to {0}", jdkDir);
			// Unpack JDK package to temp dir
			UnpackUtils.unpackJdk(jdkPkg, jdkTmpDir);
			// Check if the package contains a valid JDK
			Optional<String> v = JavaUtils.resolveJavaVersionStringFromPath(jdkTmpDir);
			if (!v.isPresent()) {
				throw new IllegalStateException("The JDK package does not seem to contain a valid JDK");
			}
			if (Files.isDirectory(jdkDir)) {
				// Rename existing JDK dir to have an .old extension
				Files.move(jdkDir, jdkOldDir);
			} else if (Files.isSymbolicLink(jdkDir)) {
				// This means we have a broken/invalid link
				FileUtils.deletePath(jdkDir);
			}
			// Rename temp dir to the final JDK dir name
			Files.move(jdkTmpDir, jdkDir);
			// Delete old JDK dir
			FileUtils.deletePath(jdkOldDir);
		} catch (Exception e) {
			FileUtils.deletePath(jdkTmpDir);
			if (!Files.isDirectory(jdkDir) && Files.isDirectory(jdkOldDir)) {
				try {
					Files.move(jdkOldDir, jdkDir);
				} catch (IOException ex) {
					// Ignore
				}
			}
			throw new IOException("Unable to install JDK", e);
		}
	}
}
