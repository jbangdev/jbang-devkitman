package dev.jbang.devkitman.jdkinstallers;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.devkitman.Distro;
import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkInstaller;
import dev.jbang.devkitman.util.*;

/**
 * JVM's main JDK installer that can download and install the JDKs provided by
 * the Foojay Disco API.
 */
public class FoojayJdkInstaller implements JdkInstaller {
	protected final JdkFactory jdkFactory;
	protected RemoteAccessProvider remoteAccessProvider = RemoteAccessProvider.createDefaultRemoteAccessProvider();

	public static final String FOOJAY_JDK_DOWNLOAD_URL = "https://api.foojay.io/disco/v3.0/directuris?";
	public static final String FOOJAY_JDK_VERSIONS_URL = "https://api.foojay.io/disco/v3.0/packages?";
	public static final String FOOJAY_JDK_DISTROS_URL = "https://api.foojay.io/disco/v3.0/distributions?include_versions=false&include_synonyms=false";

	private static final Logger LOGGER = Logger.getLogger(FoojayJdkInstaller.class.getName());

	public static class DistroResult {
		public String api_parameter;
		public Boolean build_of_openjdk;
		public Boolean build_of_graalvm;
		public Boolean available;
		public Boolean maintained;
	}

	public static class DistrosResponse {
		public List<DistroResult> result;
	}

	public static class JdkResult {
		public String java_version;
		public int major_version;
		public String release_status;
		public String distribution;
		public String package_type;
		public String operating_system;
		public String architecture;
		public Boolean javafx_bundled;
	}

	public static class VersionsResponse {
		public List<JdkResult> result;
	}

	public FoojayJdkInstaller(JdkFactory jdkFactory) {
		this.jdkFactory = jdkFactory;
	}

	public FoojayJdkInstaller remoteAccessProvider(RemoteAccessProvider remoteAccessProvider) {
		this.remoteAccessProvider = remoteAccessProvider;
		return this;
	}

	@NonNull
	@Override
	public List<Distro> listDistros() {
		try {
			DistrosResponse res = readJsonFromUrl(FOOJAY_JDK_DISTROS_URL, DistrosResponse.class);
			List<Distro> distros = res.result.stream()
				.filter(d -> d.api_parameter != null && d.available == Boolean.TRUE)
				.map(d -> new Distro(d.api_parameter, d.build_of_graalvm == Boolean.TRUE))
				.collect(Collectors.toList());
			return Collections.unmodifiableList(distros);
		} catch (IOException e) {
			LOGGER.log(Level.FINE, "Couldn't list available distributions", e);
		}
		return Collections.emptyList();
	}

	@NonNull
	@Override
	public List<Jdk> listAvailable(String distros, Set<String> tags) {
		try {
			if (distros == null) {
				distros = "temurin,aoj";
			}
			Set<String> ltags = tags != null ? tags : Collections.emptySet();
			VersionsResponse res = readJsonFromUrl(getVersionsUrl(OsUtils.getOS(), OsUtils.getArch(), distros, ltags),
					VersionsResponse.class);
			Set<Jdk> result = filterEA(res.result)
				.map(this::toJdk)
				.filter(jdk -> jdk.tags().containsAll(ltags))
				.collect(Collectors.toSet());
			return Collections.unmodifiableList(new ArrayList<>(result));
		} catch (IOException e) {
			LOGGER.log(Level.FINE, "Couldn't list available JDKs", e);
		}
		return Collections.emptyList();
	}

	private Jdk toJdk(JdkResult res) {
		HashSet<String> jdkTags = new HashSet<>();
		if (res.release_status != null && !res.release_status.isEmpty()) {
			jdkTags.add(res.release_status);
		}
		if (res.package_type != null && !res.package_type.isEmpty()) {
			jdkTags.add(res.package_type);
		}
		if (res.operating_system != null && !res.operating_system.isEmpty()) {
			jdkTags.add(res.operating_system);
		}
		if (res.architecture != null && !res.architecture.isEmpty()) {
			jdkTags.add(res.architecture);
		}
		if (res.javafx_bundled == Boolean.TRUE) {
			jdkTags.add("javafx");
		}
		return jdkFactory.createJdk(jdkFactory.jdkId(res.java_version), null, res.java_version, res.distribution,
				jdkTags);
	}

	private <T> T readJsonFromUrl(String url, Class<T> klass) throws IOException {
		return remoteAccessProvider.resultFromUrl(url, is -> {
			try (InputStream ignored = is) {
				Gson parser = new GsonBuilder().create();
				return parser.fromJson(new InputStreamReader(is), klass);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	// Filter out any EA releases for which a GA with
	// the same major version exists
	private Stream<JdkResult> filterEA(List<JdkResult> jdks) {
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
					});
	}

	@NonNull
	@Override
	public Jdk install(@NonNull Jdk jdk, Path jdkDir) {
		int version = jdkVersion(jdk.id());
		LOGGER.log(
				Level.INFO,
				"Downloading JDK {0}. Be patient, this can take several minutes...",
				version);
		String url = getDownloadUrl(version, OsUtils.getOS(), OsUtils.getArch(), jdk.distro(), jdk.tags());
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
			Optional<String> fullVersion = JavaUtils.resolveJavaVersionStringFromPath(jdkDir);
			if (!fullVersion.isPresent()) {
				throw new IllegalStateException("Cannot obtain version of recently installed JDK");
			}
			Set<String> tags = JavaUtils.readTagsFromReleaseFile(jdkDir);
			return jdkFactory.createJdk(jdk.id(), jdkDir, fullVersion.get(), jdk.distro(), tags);
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
			/*
			 * Jdk defjdk = JdkManager.getJdk(null, false); if (defjdk != null) { msg +=
			 * " You can run with '--java " + defjdk.getMajorVersion() +
			 * "' to force using the default installed Java."; }
			 */
			LOGGER.log(Level.FINE, msg);
			throw new IllegalStateException(
					"Unable to download or install JDK version " + version, e);
		}
	}

	@Override
	public void uninstall(@NonNull Jdk jdk) {
		if (jdk.isInstalled()) {
			FileUtils.deletePath(jdk.home());
		}
	}

	private static String getDownloadUrl(
			int version, OsUtils.OS os, OsUtils.Arch arch, String distro, @NonNull Set<String> tags) {
		return FOOJAY_JDK_DOWNLOAD_URL + getUrlParams(version, os, arch, distro, tags);
	}

	private static String getVersionsUrl(OsUtils.OS os, OsUtils.Arch arch, String distro, @NonNull Set<String> tags) {
		return FOOJAY_JDK_VERSIONS_URL + getUrlParams(null, os, arch, distro, tags);
	}

	private static String getUrlParams(
			Integer version, OsUtils.OS os, OsUtils.Arch arch, String distro, @NonNull Set<String> tags) {
		Map<String, String> params = new HashMap<>();
		if (version != null) {
			params.put("version", String.valueOf(version));
		}

		if (distro == null) {
			if (version == null || version == 8 || version == 11 || version >= 17) {
				distro = "temurin";
			} else {
				distro = "aoj";
			}
		}
		params.put("distro", distro);

		if (tags.contains("javafx")) {
			params.put("javafx_bundled", "true");
		} else {
			params.put("javafx_bundled", "false");
		}

		if (tags.contains("ea")) {
			params.put("release_status", "ea");
		} else if (tags.contains("ga")) {
			params.put("release_status", "ga");
		} else {
			params.put("release_status", "ga,ea");
		}

		if (tags.contains("jre")) {
			params.put("package_type", "jre");
		} else {
			params.put("package_type", "jdk");
		}

		String archiveType;
		if (os == OsUtils.OS.windows) {
			archiveType = "zip";
		} else {
			archiveType = "tar.gz";
		}
		params.put("archive_type", archiveType);

		params.put("architecture", arch.name());
		params.put("operating_system", os.name());

		if (os == OsUtils.OS.windows) {
			params.put("libc_type", "c_std_lib");
		} else if (os == OsUtils.OS.mac) {
			params.put("libc_type", "libc");
		} else {
			params.put("libc_type", "glibc");
		}

		params.put("latest", "available");
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

	public interface JdkFactory {
		String jdkId(String name);

		Jdk createJdk(@NonNull String id, @Nullable Path home, @NonNull String version, String distro,
				@NonNull Set<String> tags);
	}
}
