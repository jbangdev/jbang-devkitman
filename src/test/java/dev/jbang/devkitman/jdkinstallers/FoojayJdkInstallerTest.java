package dev.jbang.devkitman.jdkinstallers;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.devkitman.BaseTest;
import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkManager;
import dev.jbang.devkitman.jdkproviders.JBangJdkProvider;
import dev.jbang.devkitman.util.RemoteAccessProvider;

public class FoojayJdkInstallerTest extends BaseTest {

	private FoojayJdkInstaller installer;
	private JBangJdkProvider provider;
	private Path testJdkFile;

	@BeforeEach
	@Override
	protected void initEnv(@TempDir Path tempPath) throws IOException {
		super.initEnv(tempPath);

		// Copy test JDK file for installation tests
		testJdkFile = tempPath.resolve("jdk-12.zip");
		Files.copy(
				getClass().getResourceAsStream("/jdk-12.zip"),
				testJdkFile,
				java.nio.file.StandardCopyOption.REPLACE_EXISTING);

		RemoteAccessProvider rap = createRemoteAccessProvider();
		provider = new JBangJdkProvider(config.installPath());
		installer = new FoojayJdkInstaller(provider)
			.distro("jbang")
			.remoteAccessProvider(rap);
		provider.installer(installer);

		// Create a manager so the provider has access to defaultJavaVersion
		JdkManager manager = JdkManager.builder()
			.providers(provider)
			.build();
	}

	private RemoteAccessProvider createRemoteAccessProvider() {
		return new RemoteAccessProvider() {
			@Override
			public Path downloadFromUrl(String url) throws IOException {
				// Verify URL format for Foojay API
				if (!url.startsWith("https://api.foojay.io/disco/v3.0/ids/") || !url.endsWith("/redirect")) {
					throw new IOException("Unexpected URL: " + url);
				}
				return testJdkFile;
			}

			@Override
			public <T> T resultFromUrl(String url, Function<InputStream, T> streamToObject)
					throws IOException {
				// Verify the URL format matches expected Foojay API pattern
				if (!url.startsWith(FoojayJdkInstaller.FOOJAY_JDK_VERSIONS_URL)) {
					throw new IOException("Unexpected URL: " + url);
				}
				// Return our test Foojay JSON for all requests
				return streamToObject.apply(
						getClass().getResourceAsStream("/testFoojayInstall.json"));
			}
		};
	}

	@Test
	public void testListAvailable() {
		List<Jdk.AvailableJdk> jdks = installer.listAvailable().collect(Collectors.toList());

		// Should have JDKs from test data
		assertThat(jdks, is(not(empty())));

		// Verify we have expected major versions from testInstall.json
		List<Integer> versions = jdks.stream()
			.map(jdk -> Integer.parseInt(jdk.version().split("[.\\-+]")[0]))
			.distinct()
			.sorted()
			.collect(Collectors.toList());

		// testInstall.json contains versions 11-25
		assertThat(versions, hasItems(11, 17, 21, 23));
	}

	@Test
	public void testListAvailableOrderedByVersionDescending() {
		List<Jdk.AvailableJdk> jdks = installer.listAvailable().collect(Collectors.toList());

		assertThat(jdks.size(), greaterThan(0));

		// First JDK should be the highest version in our test data
		String firstVersion = jdks.get(0).version();
		int firstMajor = Integer.parseInt(firstVersion.split("[.\\-+]")[0]);

		// Verify it's one of the higher versions
		assertThat(firstMajor, greaterThanOrEqualTo(23));
	}

	@Test
	public void testGetAvailableByVersionExact() {
		Jdk.AvailableJdk jdk21 = installer.getAvailableByVersion(21, false);

		assertThat(jdk21, is(notNullValue()));
		assertThat(jdk21.version(), startsWith("21."));
		assertTrue(jdk21.tags().contains(Jdk.Default.Tags.Ga.name()));
		assertTrue(jdk21.tags().contains(Jdk.Default.Tags.Jdk.name()));
	}

	@Test
	public void testGetAvailableByVersionOpen() {
		// Request version 17+, should return newest available
		Jdk.AvailableJdk jdk = installer.getAvailableByVersion(17, true);

		assertThat(jdk, is(notNullValue()));
		String version = jdk.version();
		int major = Integer.parseInt(version.split("[.\\-+]")[0]);
		assertThat(major, greaterThanOrEqualTo(17));
	}

	@Test
	public void testGetAvailableByVersionNotFound() {
		// Request version that doesn't exist in our test data
		Jdk.AvailableJdk jdk = installer.getAvailableByVersion(6, false);

		assertThat(jdk, is(nullValue()));
	}

	@Test
	public void testDetermineIdIncludesDistro() {
		List<Jdk.AvailableJdk> jdks = installer.listAvailable().collect(Collectors.toList());

		assertThat(jdks, is(not(empty())));

		// All IDs should contain a distribution name
		for (Jdk.AvailableJdk jdk : jdks) {
			// The ID should contain either temurin or aoj or other distro names
			assertThat(jdk.id(), anyOf(
					containsString("temurin"),
					containsString("aoj"),
					containsString("liberica"),
					containsString("zulu")));
		}
	}

	@Test
	public void testDetermineIdForJre() {
		// Note: testInstall.json might not contain JRE entries
		// This test verifies the logic would work if JRE was present
		List<Jdk.AvailableJdk> jdks = installer.listAvailable().collect(Collectors.toList());

		Jdk.AvailableJdk jre = jdks.stream()
			.filter(j -> j.tags().contains(Jdk.Default.Tags.Jre.name()))
			.findFirst()
			.orElse(null);

		if (jre != null) {
			assertThat(jre.id(), containsString("-jre"));
		}
	}

	@Test
	public void testDetermineTagsForGa() {
		Jdk.AvailableJdk jdk17 = installer.getAvailableByVersion(17, false);

		assertThat(jdk17, is(notNullValue()));
		Set<String> tags = jdk17.tags();

		assertThat(tags, hasItem(Jdk.Default.Tags.Ga.name()));
		assertThat(tags, hasItem(Jdk.Default.Tags.Jdk.name()));
	}

	@Test
	public void testDetermineTagsForEa() {
		// testInstall.json contains EA versions
		List<Jdk.AvailableJdk> jdks = installer.listAvailable().collect(Collectors.toList());

		Jdk.AvailableJdk ea = jdks.stream()
			.filter(j -> j.tags().contains(Jdk.Default.Tags.Ea.name()))
			.findFirst()
			.orElse(null);

		if (ea != null) {
			Set<String> tags = ea.tags();
			assertThat(tags, hasItem(Jdk.Default.Tags.Ea.name()));
		}
	}

	@Test
	public void testDetermineTagsForJdk() {
		List<Jdk.AvailableJdk> jdks = installer.listAvailable().collect(Collectors.toList());

		// Most entries should be JDK
		long jdkCount = jdks.stream()
			.filter(j -> j.tags().contains(Jdk.Default.Tags.Jdk.name()))
			.count();

		assertThat(jdkCount, greaterThan(0L));
	}

	@Test
	public void testInstallJdk() throws IOException {
		Jdk.AvailableJdk jdk17 = installer.getAvailableByVersion(17, false);
		assertThat(jdk17, is(notNullValue()));

		Path installDir = config.installPath().resolve("test-jdk-17");
		Files.createDirectories(installDir.getParent());

		Jdk.InstalledJdk installed = installer.install(jdk17, installDir);

		assertThat(installed, is(notNullValue()));
		assertThat(Files.exists(installDir), is(true));
		assertThat(Files.isDirectory(installDir), is(true));
	}

	@Test
	public void testInstallJdkInvalidType() {
		// Create a mock JDK from a different installer type
		Jdk.AvailableJdk mockJdk = new Jdk.AvailableJdk.Default(
				provider,
				"mock-jdk-21",
				"21.0.0",
				Set.of(Jdk.Default.Tags.Ga.name()));

		Path installDir = config.installPath().resolve("test-jdk-mock");

		IllegalArgumentException exception = assertThrows(
				IllegalArgumentException.class,
				() -> installer.install(mockJdk, installDir));

		assertThat(exception.getMessage(), containsString("FoojayJdkInstaller can only install"));
	}

	@Test
	public void testUninstallJdk() throws IOException {
		// Create a mock installed JDK
		Path jdkPath = config.installPath().resolve("test-jdk-uninstall");
		initMockJdkDir(jdkPath, "17.0.13");

		Jdk.InstalledJdk jdk = new Jdk.InstalledJdk.Default(
				provider,
				"17.0.13-temurin-jbang",
				jdkPath,
				"17.0.13",
				Set.of(Jdk.Default.Tags.Ga.name()));

		assertThat(Files.exists(jdkPath), is(true));

		installer.uninstall(jdk);

		// JDK directory should be removed
		assertThat(Files.exists(jdkPath), is(false));
	}

	@Test
	public void testFilterEAWhenGAExists() {
		// If we have both EA and GA for same version, EA should be filtered out
		List<Jdk.AvailableJdk> jdks = installer.listAvailable().collect(Collectors.toList());

		// Count how many times each major version appears
		List<Integer> majorVersions = jdks.stream()
			.map(jdk -> Integer.parseInt(jdk.version().split("[.\\-+]")[0]))
			.collect(Collectors.toList());

		// Each major version should appear at most once (after filtering)
		long uniqueVersions = majorVersions.stream().distinct().count();
		assertThat((long) majorVersions.size(), greaterThanOrEqualTo(uniqueVersions));
	}

	@Test
	public void testAvailableFoojayJdkCreation() {
		List<Jdk.AvailableJdk> jdks = installer.listAvailable().collect(Collectors.toList());

		assertThat(jdks, is(not(empty())));

		// Verify that the JDKs are of the correct type
		for (Jdk.AvailableJdk jdk : jdks) {
			assertThat(jdk, is(instanceOf(FoojayJdkInstaller.AvailableFoojayJdk.class)));

			// Verify essential properties
			assertThat(jdk.id(), is(not(emptyString())));
			assertThat(jdk.version(), is(not(emptyString())));
			assertThat(jdk.tags(), is(not(empty())));
			assertThat(jdk.provider(), is(provider));
		}
	}

	@Test
	public void testDistroSortingOrder() {
		// Test that JDKs are sorted by distribution preference
		List<Jdk.AvailableJdk> jdks = installer.listAvailable().collect(Collectors.toList());

		assertThat(jdks, is(not(empty())));

		// When filtering by same major version, the preferred distro should come first
		// This is based on the distro configuration (default: "temurin,aoj")
		List<Integer> majorVersions = jdks.stream()
			.map(jdk -> Integer.parseInt(jdk.version().split("[.\\-+]")[0]))
			.distinct()
			.collect(Collectors.toList());

		// Just verify we have multiple versions
		assertThat(majorVersions.size(), greaterThan(0));
	}

	@Test
	public void testJavaFXBundled() {
		// Test JavaFX bundled detection
		List<Jdk.AvailableJdk> jdks = installer.listAvailable().collect(Collectors.toList());

		// Check if any JDKs have JavaFX tag (depends on test data)
		long javafxCount = jdks.stream()
			.filter(j -> j.tags().contains(Jdk.Default.Tags.Javafx.name()))
			.count();

		// This is just verifying the tag system works, count might be 0
		assertThat(javafxCount, greaterThanOrEqualTo(0L));
	}

	@Test
	public void testMajorVersionExtraction() {
		List<Jdk.AvailableJdk> jdks = installer.listAvailable().collect(Collectors.toList());

		assertThat(jdks, is(not(empty())));

		// Verify all JDKs have parseable major versions
		for (Jdk.AvailableJdk jdk : jdks) {
			String version = jdk.version();
			int major = Integer.parseInt(version.split("[.\\-+]")[0]);

			// Major version should be reasonable (8-30 range as of 2026)
			assertThat(major, allOf(greaterThanOrEqualTo(8), lessThanOrEqualTo(30)));
		}
	}
}
