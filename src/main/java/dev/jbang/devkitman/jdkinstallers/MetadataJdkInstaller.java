package dev.jbang.devkitman.jdkinstallers;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.http.impl.client.HttpClientBuilder;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkInstaller;
import dev.jbang.devkitman.JdkInstallers;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.devkitman.util.*;

/**
 * JDK installer that downloads and installs JDKs using the Java Metadata API
 * from https://joschi.github.io/java-metadata/
 */
public class MetadataJdkInstaller implements JdkInstaller {
	protected final @NonNull JdkProvider jdkProvider;
	protected final Function<MetadataResult, String> jdkId;
	protected @NonNull RemoteAccessProvider remoteAccessProvider = RemoteAccessProvider
		.createDefaultRemoteAccessProvider();
	protected @NonNull String distro = DEFAULT_DISTRO;
	protected String jvmImpl = DEFAULT_JVM_IMPL;

	public static final String METADATA_BASE_URL = "https://joschi.github.io/java-metadata/metadata/";
	public static final String DEFAULT_DISTRO = "temurin,adoptopenjdk";
	public static final String DEFAULT_JVM_IMPL = "hotspot";

	private static final Logger LOGGER = Logger.getLogger(MetadataJdkInstaller.class.getName());

	/**
	 * Represents a single metadata entry from the Java Metadata API
	 */
	public static class MetadataResult {
		public String vendor;
		public String filename;
		public String file_type;
		public String release_type; // "ga" or "ea"
		public String version;
		public String java_version;
		public String jvm_impl; // "hotspot", "openj9", "graalvm"
		public String os; // "linux", "macosx", "windows", "solaris", "aix"
		public String architecture; // "x86_64", "i686", "aarch64", etc.
		public String image_type; // "jdk" or "jre"
		public List<String> features;
		public String url;
		public String md5;
		public String md5_file;
		public String sha1;
		public String sha1_file;
		public String sha256;
		public String sha256_file;
		public String sha512;
		public String sha512_file;
		public Integer size;
	}

	public MetadataJdkInstaller(@NonNull JdkProvider jdkProvider) {
		this.jdkProvider = jdkProvider;
		this.jdkId = jdk -> determineId(jdk) + "-" + jdkProvider.name();
	}

	public MetadataJdkInstaller(@NonNull JdkProvider jdkProvider, @NonNull Function<MetadataResult, String> jdkId) {
		this.jdkProvider = jdkProvider;
		this.jdkId = jdkId;
	}

	public @NonNull MetadataJdkInstaller remoteAccessProvider(@NonNull RemoteAccessProvider remoteAccessProvider) {
		this.remoteAccessProvider = remoteAccessProvider;
		return this;
	}

	public @NonNull MetadataJdkInstaller distro(@Nullable String distro) {
		this.distro = distro != null && !distro.isEmpty() ? distro : DEFAULT_DISTRO;
		return this;
	}

	public @NonNull MetadataJdkInstaller jvmImpl(@Nullable String jvmImpl) {
		this.jvmImpl = jvmImpl;
		return this;
	}

	@NonNull
	@Override
	public Stream<Jdk.AvailableJdk> listAvailable() {
		try {
			List<MetadataResult> results = readMetadataForList();
			return processMetadata(results, majorVersionSort()).distinct();
		} catch (IOException e) {
			LOGGER.log(Level.FINE, "Couldn't list available JDKs", e);
			return Stream.empty();
		}
	}

	private List<MetadataResult> readMetadataForList() throws IOException {
		List<MetadataResult> allResults = new ArrayList<>();
		IOException lastException = null;

		String[] distros = distro.split(",");
		// Query for GA releases first
		for (String d : distros) {
			try {
				List<MetadataResult> results = readJsonFromUrl(
						getMetadataUrl("ga", OsUtils.getOS(), OsUtils.getArch(), "jdk", jvmImpl, d.trim()));
				allResults.addAll(results);
			} catch (IOException e) {
				lastException = e;
			}
		}
		// And for EA releases second
		for (String d : distros) {
			try {
				List<MetadataResult> results = readJsonFromUrl(
						getMetadataUrl("ea", OsUtils.getOS(), OsUtils.getArch(), "jdk", jvmImpl, d.trim()));
				allResults.addAll(results);
			} catch (IOException e) {
				lastException = e;
			}
		}

		// If we have no results at all and had at least one exception, throw the last
		// one
		if (allResults.isEmpty() && lastException != null) {
			throw lastException;
		}

		return allResults;
	}

	@Override
	public Jdk.@Nullable AvailableJdk getAvailableByVersion(int version, boolean openVersion) {
		int djv = jdkProvider.manager().defaultJavaVersion;
		Comparator<MetadataResult> preferGaSort = (j1, j2) -> {
			int v1 = extractMajorVersion(j1.java_version);
			int v2 = extractMajorVersion(j2.java_version);

			// Prefer versions equal to the default Java version
			if (v1 == djv && v2 != djv) {
				return -1;
			} else if (v2 == djv && v1 != djv) {
				return 1;
			}
			// Prefer GA releases over EA releases
			if (!j1.release_type.equals(j2.release_type)) {
				return j2.release_type.compareTo(j1.release_type);
			}
			// Prefer newer versions
			return majorVersionSort().compare(j1, j2);
		};

		try {
			List<MetadataResult> results = readMetadataForVersion(version, openVersion);
			return processMetadata(results, preferGaSort)
				.filter(Jdk.Predicates.forVersion(version, openVersion))
				.findFirst()
				.orElse(null);
		} catch (IOException e) {
			LOGGER.log(Level.FINE, "Couldn't get available JDK by version", e);
			return null;
		}
	}

	private List<MetadataResult> readMetadataForVersion(int version, boolean openVersion) throws IOException {
		String[] distros = distro.split(",");
		// Try GA first for all selected distros, return the first that has results
		for (String d : distros) {
			List<MetadataResult> results = readMetadataForVersionAndDistro(version, openVersion, "ga", d.trim());
			if (!results.isEmpty()) {
				return results;
			}
		}
		// Try EA if no GA found
		for (String d : distros) {
			List<MetadataResult> results = readMetadataForVersionAndDistro(version, openVersion, "ea", d.trim());
			if (!results.isEmpty()) {
				return results;
			}
		}
		return Collections.emptyList();
	}

	private List<MetadataResult> readMetadataForVersionAndDistro(int version, boolean openVersion, String releaseType,
			String distro) throws IOException {
		List<MetadataResult> gaResults = readJsonFromUrl(
				getMetadataUrl(releaseType, OsUtils.getOS(), OsUtils.getArch(), "jdk", jvmImpl, distro));
		return filterByVersion(gaResults, version, openVersion);
	}

	private List<MetadataResult> filterByVersion(List<MetadataResult> results, int version, boolean openVersion) {
		return results.stream()
			.filter(r -> {
				int majorVersion = extractMajorVersion(r.java_version);
				if (openVersion) {
					return majorVersion >= version;
				} else {
					return majorVersion == version;
				}
			})
			.collect(Collectors.toList());
	}

	private Stream<Jdk.AvailableJdk> processMetadata(List<MetadataResult> jdks, Comparator<MetadataResult> sortFunc) {
		return filterEA(jdks)
			.stream()
			.sorted(sortFunc)
			.map(jdk -> new AvailableMetadataJdk(jdkProvider,
					jdkId.apply(jdk), jdk.java_version,
					jdk.url, determineTags(jdk)));
	}

	private @NonNull String determineId(@NonNull MetadataResult jdk) {
		String id = jdk.java_version + "-" + jdk.vendor;
		if ("jre".equals(jdk.image_type)) {
			id += "-jre";
		}
		if (jdk.features != null && jdk.features.contains("javafx")) {
			id += "-jfx";
		}
		if (!"hotspot".equals(jdk.jvm_impl)) {
			id += "-" + jdk.jvm_impl;
		}
		return id;
	}

	private @NonNull Set<String> determineTags(MetadataResult jdk) {
		Set<String> tags = new HashSet<>();
		if ("ga".equalsIgnoreCase(jdk.release_type)) {
			tags.add(Jdk.Default.Tags.Ga.name());
		} else if ("ea".equalsIgnoreCase(jdk.release_type)) {
			tags.add(Jdk.Default.Tags.Ea.name());
		}
		if ("jdk".equalsIgnoreCase(jdk.image_type)) {
			tags.add(Jdk.Default.Tags.Jdk.name());
		} else if ("jre".equalsIgnoreCase(jdk.image_type)) {
			tags.add(Jdk.Default.Tags.Jre.name());
		}
		if (jdk.features != null && jdk.features.contains("javafx")) {
			tags.add(Jdk.Default.Tags.Javafx.name());
		}
		return tags;
	}

	private List<MetadataResult> readJsonFromUrl(String url) throws IOException {
		return remoteAccessProvider.resultFromUrl(url, is -> {
			try (InputStream ignored = is) {
				Gson parser = new GsonBuilder().create();
				MetadataResult[] results = parser.fromJson(new InputStreamReader(is), MetadataResult[].class);
				return Arrays.asList(results);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	// Filter out any EA releases for which a GA with the same major version exists
	private List<MetadataResult> filterEA(List<MetadataResult> jdks) {
		Set<Integer> GAs = jdks.stream()
			.filter(jdk -> "ga".equals(jdk.release_type))
			.map(jdk -> extractMajorVersion(jdk.java_version))
			.collect(Collectors.toSet());

		MetadataResult[] lastJdk = new MetadataResult[] { null };
		return jdks.stream()
			.filter(jdk -> {
				int majorVersion = extractMajorVersion(jdk.java_version);
				if (lastJdk[0] == null
						|| extractMajorVersion(lastJdk[0].java_version) != majorVersion
								&& ("ga".equals(jdk.release_type)
										|| !GAs.contains(majorVersion))) {
					lastJdk[0] = jdk;
					return true;
				} else {
					return false;
				}
			})
			.collect(Collectors.toList());
	}

	private static final Comparator<MetadataResult> metadataResultVersionComparator = (o1,
			o2) -> VersionComparator.INSTANCE.compare(o1.java_version, o2.java_version);

	private Comparator<MetadataResult> majorVersionSort() {
		return Comparator
			.comparingInt((MetadataResult jdk) -> -extractMajorVersion(jdk.java_version))
			.thenComparing(metadataResultVersionComparator.reversed());
	}

	@Override
	public Jdk.@NonNull InstalledJdk install(Jdk.@NonNull AvailableJdk jdk, @NonNull Path jdkDir) {
		if (!(jdk instanceof AvailableMetadataJdk)) {
			throw new IllegalArgumentException(
					"MetadataJdkInstaller can only install JDKs listed as available by itself");
		}
		AvailableMetadataJdk metadataJdk = (AvailableMetadataJdk) jdk;
		int version = jdkVersion(metadataJdk.id());
		LOGGER.log(
				Level.INFO,
				"Downloading JDK {0}. Be patient, this can take several minutes...",
				version);
		String url = metadataJdk.downloadUrl;

		try {
			LOGGER.log(Level.FINE, "Downloading {0}", url);
			Path jdkPkg = remoteAccessProvider.downloadFromUrl(url);

			LOGGER.log(Level.INFO, "Installing JDK {0}...", version);
			JavaUtils.installJdk(jdkPkg, jdkDir);

			Jdk.InstalledJdk newJdk = jdkProvider.createJdk(metadataJdk.id(), jdkDir);
			if (newJdk == null) {
				throw new IllegalStateException("Cannot obtain version of recently installed JDK");
			}
			return newJdk;
		} catch (Exception e) {
			String msg = "Required Java version not possible to download or install: " + version;
			LOGGER.log(Level.FINE, msg);
			throw new IllegalStateException(
					"Unable to download or install JDK version " + version, e);
		}
	}

	@Override
	public void uninstall(Jdk.@NonNull InstalledJdk jdk) {
		JavaUtils.safeDeleteJdk(jdk.home());
	}

	/**
	 * Constructs the metadata API URL for the given parameters Format:
	 * /metadata/{release_type}/{os}/{arch}/{image_type}/{jvm_impl}/{vendor}.json
	 */
	private static String getMetadataUrl(String releaseType, OsUtils.OS os, OsUtils.Arch arch,
			String imageType, String jvmImpl, String vendor) {
		String osName = mapOsToMetadataName(os);
		String archName = mapArchToMetadataName(arch);
		if (jvmImpl == null || jvmImpl.isEmpty()) {
			if (vendor.contains("graalvm") || vendor.equals("mandrel")) {
				jvmImpl = "graalvm";
			} else {
				jvmImpl = DEFAULT_JVM_IMPL;
			}
		}
		URI uri = URI.create(METADATA_BASE_URL + releaseType + "/" + osName + "/" + archName + "/"
				+ imageType + "/" + jvmImpl + "/" + vendor + ".json");
		return uri.toString();
	}

	/**
	 * Maps OsUtils.OS enum to the metadata API os name
	 */
	private static String mapOsToMetadataName(OsUtils.OS os) {
		switch (os) {
		case linux:
		case alpine_linux:
			return "linux";
		case mac:
			return "macosx";
		case windows:
			return "windows";
		case aix:
			return "aix";
		default:
			return "linux";
		}
	}

	/**
	 * Maps OsUtils.Arch enum to the metadata API architecture name
	 */
	private static String mapArchToMetadataName(OsUtils.Arch arch) {
		switch (arch) {
		case x64:
			return "x86_64";
		case x32:
			return "i686";
		case aarch64:
		case arm64:
			return "aarch64";
		case arm:
			return "arm32";
		case ppc64:
			return "ppc64";
		case ppc64le:
			return "ppc64le";
		case s390x:
			return "s390x";
		case riscv64:
			return "riscv64";
		default:
			return "x86_64";
		}
	}

	/**
	 * Extracts the major version from a Java version string
	 */
	private static int extractMajorVersion(String javaVersion) {
		return JavaUtils.parseJavaVersion(javaVersion);
	}

	private static int jdkVersion(String jdk) {
		return JavaUtils.parseJavaVersion(jdk);
	}

	static class AvailableMetadataJdk extends Jdk.AvailableJdk.Default {
		public final String downloadUrl;

		AvailableMetadataJdk(@NonNull JdkProvider provider, @NonNull String id, @NonNull String version,
				@NonNull String downloadUrl, @NonNull Set<String> tags) {
			super(provider, id, version, tags);
			this.downloadUrl = downloadUrl;
		}
	}

	public static class Discovery implements JdkInstallers.Discovery {
		@Override
		public @NonNull String name() {
			return "metadata";
		}

		@Override
		public @NonNull JdkInstaller create(Config config) {
			MetadataJdkInstaller installer = new MetadataJdkInstaller(config.jdkProvider());
			installer
				.distro(config.properties().getOrDefault("distro", null))
				.jvmImpl(config.properties().getOrDefault("impl", null));
			HttpClientBuilder httpClientBuilder = NetUtils.createCachingHttpClientBuilder(config.cachePath());
			RemoteAccessProvider rap = RemoteAccessProvider.createDefaultRemoteAccessProvider(httpClientBuilder);
			installer.remoteAccessProvider(rap);
			return installer;
		}
	}
}
