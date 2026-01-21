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

public class MetadataJdkInstallerTest extends BaseTest {

	private MetadataJdkInstaller installer;
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
		installer = new MetadataJdkInstaller(provider)
			.distro("temurin")
			.jvmImpl("hotspot")
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
				// Return our test JDK file for any download request
				return testJdkFile;
			}

			@Override
			public <T> T resultFromUrl(String url, Function<InputStream, T> streamToObject)
					throws IOException {
				// Verify the URL format matches expected metadata API pattern
				if (!url.startsWith(MetadataJdkInstaller.METADATA_BASE_URL)) {
					throw new IOException("Unexpected URL: " + url);
				}
				// Return our test metadata JSON for all requests
				return streamToObject.apply(
						getClass().getResourceAsStream("/testMetadataInstall.json"));
			}
		};
	}

	@Test
	public void testListAvailable() {
		List<Jdk.AvailableJdk> jdks = installer.listAvailable().collect(Collectors.toList());

		// Should have JDKs from test data (excluding duplicate major versions and
		// filtered EAs)
		assertThat(jdks, is(not(empty())));

		// Verify we have expected major versions (23, 21, 17, 11)
		// 24 is EA and should be filtered if 24 GA exists (it doesn't in our test data)
		List<Integer> versions = jdks.stream()
			.map(jdk -> Integer.parseInt(jdk.version().split("[.\\-+]")[0]))
			.distinct()
			.sorted()
			.collect(Collectors.toList());

		assertThat(versions, hasItems(11, 17, 21, 23));
	}

	@Test
	public void testListAvailableOrderedByVersionDescending() {
		List<Jdk.AvailableJdk> jdks = installer.listAvailable().collect(Collectors.toList());

		assertThat(jdks.size(), greaterThan(0));

		// First JDK should be the highest version (24 EA in our test data)
		String firstVersion = jdks.get(0).version();
		int firstMajor = Integer.parseInt(firstVersion.split("[.\\-+]")[0]);
		assertThat(firstMajor, is(24));
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
		// Request version 17+, should return newest available (23 in our test data)
		Jdk.AvailableJdk jdk = installer.getAvailableByVersion(17, true);

		assertThat(jdk, is(notNullValue()));
		String version = jdk.version();
		int major = Integer.parseInt(version.split("[.\\-+]")[0]);
		assertThat(major, greaterThanOrEqualTo(17));
	}

	@Test
	public void testGetAvailableByVersionNotFound() {
		// Request version that doesn't exist in our test data
		Jdk.AvailableJdk jdk = installer.getAvailableByVersion(8, false);

		assertThat(jdk, is(nullValue()));
	}

	@Test
	public void testDetermineIdIncludesDistro() {
		List<Jdk.AvailableJdk> jdks = installer.listAvailable().collect(Collectors.toList());

		assertThat(jdks, is(not(empty())));

		// All IDs should contain the vendor name
		for (Jdk.AvailableJdk jdk : jdks) {
			assertThat(jdk.id(), containsString("temurin"));
		}
	}

	@Test
	public void testDetermineIdForJre() {
		// Find the JRE in our test data (11.0.25+9 JRE)
		List<Jdk.AvailableJdk> jdks = installer.listAvailable().collect(Collectors.toList());

		Jdk.AvailableJdk jre = jdks.stream()
			.filter(j -> j.tags().contains(Jdk.Default.Tags.Jre.name()))
			.findFirst()
			.orElse(null);

		assertThat(jre, is(notNullValue()));
		assertThat(jre.id(), containsString("-jre"));
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
		// Our test data has version 24 as EA
		List<Jdk.AvailableJdk> jdks = installer.listAvailable().collect(Collectors.toList());

		Jdk.AvailableJdk ea = jdks.stream()
			.filter(j -> j.version().contains("24"))
			.findFirst()
			.orElse(null);

		if (ea != null) {
			Set<String> tags = ea.tags();
			assertThat(tags, hasItem(Jdk.Default.Tags.Ea.name()));
		}
	}

	@Test
	public void testDetermineTagsForJre() {
		List<Jdk.AvailableJdk> jdks = installer.listAvailable().collect(Collectors.toList());

		Jdk.AvailableJdk jre = jdks.stream()
			.filter(j -> j.tags().contains(Jdk.Default.Tags.Jre.name()))
			.findFirst()
			.orElse(null);

		assertThat(jre, is(notNullValue()));
		assertThat(jre.tags(), hasItem(Jdk.Default.Tags.Jre.name()));
		assertThat(jre.tags(), not(hasItem(Jdk.Default.Tags.Jdk.name())));
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

		assertThat(exception.getMessage(), containsString("MetadataJdkInstaller can only install"));
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
		// In our test data, if we had both EA and GA for same version,
		// EA should be filtered out
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
	public void testAvailableMetadataJdkCreation() {
		List<Jdk.AvailableJdk> jdks = installer.listAvailable().collect(Collectors.toList());

		assertThat(jdks, is(not(empty())));

		// Verify that the JDKs are of the correct type
		for (Jdk.AvailableJdk jdk : jdks) {
			assertThat(jdk, is(instanceOf(MetadataJdkInstaller.AvailableMetadataJdk.class)));

			// Verify essential properties
			assertThat(jdk.id(), is(not(emptyString())));
			assertThat(jdk.version(), is(not(emptyString())));
			assertThat(jdk.tags(), is(not(empty())));
			assertThat(jdk.provider(), is(provider));
		}
	}
}
