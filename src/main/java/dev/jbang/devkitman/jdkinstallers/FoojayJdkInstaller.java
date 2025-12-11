package dev.jbang.devkitman.jdkinstallers;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkInstaller;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.devkitman.util.*;

/**
 * JVM's main JDK installer that can download and install the JDKs provided by
 * the Foojay Disco API.
 */
public class FoojayJdkInstaller implements JdkInstaller {
	protected final JdkProvider jdkProvider;
	protected final Function<String, String> versionToId;
	protected RemoteAccessProvider remoteAccessProvider = RemoteAccessProvider.createDefaultRemoteAccessProvider();
	protected String distro = DEFAULT_DISTRO;

	public static final String FOOJAY_JDK_VERSIONS_URL = "https://api.foojay.io/disco/v3.0/packages?";

	public static final String DEFAULT_DISTRO = "temurin,aoj";

	private static final Comparator<JdkResult> majorVersionSort = Comparator
		.comparingInt((JdkResult jdk) -> jdk.major_version)
		.reversed();

	private static final Logger LOGGER = Logger.getLogger(FoojayJdkInstaller.class.getName());

	public static class JdkResultLinks {
		public String pkg_download_redirect;
	}

	public static class JdkResult {
		public String java_version;
		public int major_version;
		public String release_status; // ga, ea
		public String package_type; // jdk, jre
		public boolean javafx_bundled;
		public JdkResultLinks links;
	}

	public static class VersionsResponse {
		public List<JdkResult> result;
	}

	public FoojayJdkInstaller(@NonNull JdkProvider jdkProvider, @NonNull Function<String, String> versionToId) {
		this.jdkProvider = jdkProvider;
		this.versionToId = versionToId;
	}

	public @NonNull FoojayJdkInstaller remoteAccessProvider(@NonNull RemoteAccessProvider remoteAccessProvider) {
		this.remoteAccessProvider = remoteAccessProvider;
		return this;
	}

	public @NonNull FoojayJdkInstaller distro(String distro) {
		this.distro = distro != null && !distro.isEmpty() ? distro : DEFAULT_DISTRO;
		return this;
	}

	@NonNull
	@Override
	public List<Jdk.AvailableJdk> listAvailable() {
		try {
			VersionsResponse res = readPackagesForList();
			return processPackages(res.result, majorVersionSort).distinct().collect(Collectors.toList());
		} catch (IOException e) {
			LOGGER.log(Level.FINE, "Couldn't list available JDKs", e);
			return Collections.emptyList();
		}
	}

	private VersionsResponse readPackagesForList() throws IOException {
		return readJsonFromUrl(
				getVersionsUrl(0, true, OsUtils.getOS(), OsUtils.getArch(), distro, "ga,ea"));
	}

	@Override
	public Jdk.@Nullable AvailableJdk getAvailableByVersion(int version, boolean openVersion) {
		int djv = jdkProvider.manager().defaultJavaVersion;
		Comparator<JdkResult> preferGaSort = (j1, j2) -> {
			// Prefer versions equal to the default Java version
			if (j1.major_version == djv && j2.major_version != djv) {
				return -1;
			} else if (j2.major_version == djv && j1.major_version != djv) {
				return 1;
			}
			// Prefer GA releases over EA releases
			if (!j1.release_status.equals(j2.release_status)) {
				return j2.release_status.compareTo(j1.release_status);
			}
			// Prefer newer versions
			return majorVersionSort.compare(j1, j2);
		};
		try {
			VersionsResponse res = readPackagesForVersion(version, openVersion);
			return processPackages(res.result, preferGaSort)
				.filter(Jdk.Predicates.forVersion(version, openVersion))
				.findFirst()
				.orElse(null);
		} catch (IOException e) {
			LOGGER.log(Level.FINE, "Couldn't get available JDK by version", e);
			return null;
		}
	}

	private VersionsResponse readPackagesForVersion(Integer minVersion, boolean openVersion) throws IOException {
		VersionsResponse res = readJsonFromUrl(
				getVersionsUrl(minVersion, openVersion, OsUtils.getOS(), OsUtils.getArch(), distro, "ga"));
		if (res.result.isEmpty()) {
			res = readJsonFromUrl(
					getVersionsUrl(minVersion, openVersion, OsUtils.getOS(), OsUtils.getArch(), distro, "ea"));
		}
		return res;
	}

	private Stream<Jdk.AvailableJdk> processPackages(List<JdkResult> jdks, Comparator<JdkResult> sortFunc) {
		return filterEA(jdks)
			.stream()
			.sorted(sortFunc)
			.map(jdk -> new AvailableFoojayJdk(jdkProvider, versionToId.apply(jdk.java_version),
					jdk.java_version, jdk.links.pkg_download_redirect, determineTags(jdk)));
	}

	private @NonNull Set<String> determineTags(JdkResult jdk) {
		Set<String> tags = new HashSet<>();
		if (Jdk.Default.Tags.Ga.name().equals(jdk.release_status)) {
			tags.add(Jdk.Default.Tags.Ga.name());
		} else if (Jdk.Default.Tags.Ea.name().equals(jdk.release_status)) {
			tags.add(Jdk.Default.Tags.Ea.name());
		}
		if (Jdk.Default.Tags.Jdk.name().equals(jdk.package_type)) {
			tags.add(Jdk.Default.Tags.Jdk.name());
		} else if (Jdk.Default.Tags.Jre.name().equals(jdk.package_type)) {
			tags.add(Jdk.Default.Tags.Jre.name());
		}
		if (jdk.javafx_bundled) {
			tags.add(Jdk.Default.Tags.Javafx.name());
		}
		return tags;
	}

	private VersionsResponse readJsonFromUrl(String url) throws IOException {
		return remoteAccessProvider.resultFromUrl(url, is -> {
			try (InputStream ignored = is) {
				Gson parser = new GsonBuilder().create();
				return parser.fromJson(new InputStreamReader(is), VersionsResponse.class);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	// Filter out any EA releases for which a GA with
	// the same major version exists
	private List<JdkResult> filterEA(List<JdkResult> jdks) {
		Set<Integer> GAs = jdks.stream()
			.filter(jdk -> jdk.release_status.equals("ga"))
			.map(jdk -> jdk.major_version)
			.collect(Collectors.toSet());

		JdkResult[] lastJdk = new JdkResult[] { null };
		return jdks.stream()
			.filter(
					jdk -> {
						if (lastJdk[0] == null
								|| lastJdk[0].major_version != jdk.major_version
										&& (jdk.release_status.equals("ga")
												|| !GAs.contains(jdk.major_version))) {
							lastJdk[0] = jdk;
							return true;
						} else {
							return false;
						}
					})
			.collect(Collectors.toList());
	}

	@Override
	public Jdk.@NonNull InstalledJdk install(Jdk.@NonNull AvailableJdk jdk, Path jdkDir) {
		if (!(jdk instanceof AvailableFoojayJdk)) {
			throw new IllegalArgumentException(
					"FoojayJdkInstaller can only install JDKs listed as available by itself");
		}
		AvailableFoojayJdk foojayJdk = (AvailableFoojayJdk) jdk;
		int version = jdkVersion(foojayJdk.id());
		LOGGER.log(
				Level.INFO,
				"Downloading JDK {0}. Be patient, this can take several minutes...",
				version);
		String url = foojayJdk.downloadUrl;
		LOGGER.log(Level.FINE, "Downloading {0}", url);
		Path jdkTmpDir = jdkDir.getParent().resolve(jdkDir.getFileName() + ".tmp");
		Path jdkOldDir = jdkDir.getParent().resolve(jdkDir.getFileName() + ".old");
		FileUtils.deletePath(jdkTmpDir);
		FileUtils.deletePath(jdkOldDir);
		try {
			Path jdkPkg = remoteAccessProvider.downloadFromUrl(url);
			LOGGER.log(Level.INFO, "Installing JDK {0}...", version);
			LOGGER.log(Level.FINE, "Unpacking to {0}", jdkDir);
			UnpackUtils.unpackJdk(jdkPkg, jdkTmpDir);
			if (Files.isDirectory(jdkDir)) {
				Files.move(jdkDir, jdkOldDir);
			} else if (Files.isSymbolicLink(jdkDir)) {
				// This means we have a broken/invalid link
				FileUtils.deletePath(jdkDir);
			}
			Files.move(jdkTmpDir, jdkDir);
			FileUtils.deletePath(jdkOldDir);
			Jdk.InstalledJdk newJdk = jdkProvider.createJdk(foojayJdk.id(), jdkDir, null, true, null);
			if (newJdk == null) {
				throw new IllegalStateException("Cannot obtain version of recently installed JDK");
			}
			return newJdk;
		} catch (Exception e) {
			FileUtils.deletePath(jdkTmpDir);
			if (!Files.isDirectory(jdkDir) && Files.isDirectory(jdkOldDir)) {
				try {
					Files.move(jdkOldDir, jdkDir);
				} catch (IOException ex) {
					// Ignore
				}
			}
			String msg = "Required Java version not possible to download or install.";
			LOGGER.log(Level.FINE, msg);
			throw new IllegalStateException(
					"Unable to download or install JDK version " + version, e);
		}
	}

	@Override
	public void uninstall(Jdk.@NonNull InstalledJdk jdk) {
		if (jdk.isInstalled()) {
			FileUtils.deletePath(jdk.home());
		}
	}

	private static String getVersionsUrl(int minVersion, boolean openVersion, OsUtils.OS os, OsUtils.Arch arch,
			String distro, String status) {
		return FOOJAY_JDK_VERSIONS_URL + getUrlParams(minVersion, openVersion, os, arch, distro, status);
	}

	private static String getUrlParams(int version, boolean openVersion, OsUtils.OS os, OsUtils.Arch arch,
			String distro, String status) {
		Map<String, String> params = new HashMap<>();
		if (version > 0) {
			String v = String.valueOf(version);
			if (openVersion) {
				v += "..<999";
			}
			params.put("version", v);
		}

		if (distro == null) {
			if (version == 0 || version == 8 || version == 11 || version >= 17) {
				distro = "temurin";
			} else {
				distro = "aoj";
			}
		}
		params.put("distro", distro);

		String archiveType;
		if (os == OsUtils.OS.windows) {
			archiveType = "zip";
		} else {
			archiveType = "tar.gz";
		}
		params.put("archive_type", archiveType);

		params.put("architecture", arch.name());
		params.put("package_type", "jdk");
		params.put("operating_system", os.name());

		if (os == OsUtils.OS.windows) {
			params.put("libc_type", "c_std_lib");
		} else if (os == OsUtils.OS.mac) {
			params.put("libc_type", "libc");
		} else if (os == OsUtils.OS.alpine_linux) {
			params.put("libc_type", "musl");
		} else {
			params.put("libc_type", "glibc");
		}

		params.put("javafx_bundled", "false");
		params.put("latest", "available");
		params.put("release_status", status);
		params.put("directly_downloadable", "true");

		return urlEncodeUTF8(params);
	}

	static String urlEncodeUTF8(Map<?, ?> map) {
		StringBuilder sb = new StringBuilder();
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (sb.length() > 0) {
				sb.append("&");
			}
			sb.append(
					String.format(
							"%s=%s",
							urlEncodeUTF8(entry.getKey().toString()),
							urlEncodeUTF8(entry.getValue().toString())));
		}
		return sb.toString();
	}

	static String urlEncodeUTF8(String s) {
		try {
			return URLEncoder.encode(s, "UTF-8");
		} catch (UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	private static int jdkVersion(String jdk) {
		return JavaUtils.parseJavaVersion(jdk);
	}

	static class AvailableFoojayJdk extends Jdk.AvailableJdk.Default {
		public final String downloadUrl;

		AvailableFoojayJdk(@NonNull JdkProvider provider, @NonNull String id, @NonNull String version,
				@NonNull String downloadUrl, @NonNull Set<String> tags) {
			super(provider, id, version, tags);
			this.downloadUrl = downloadUrl;
		}
	}
}
