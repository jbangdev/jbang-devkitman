package dev.jbang.devkitman;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;

import dev.jbang.devkitman.jdkinstallers.FoojayJdkInstaller;
import dev.jbang.devkitman.jdkinstallers.MetadataJdkInstaller;
import dev.jbang.devkitman.jdkproviders.BaseJdkProvider;
import dev.jbang.devkitman.jdkproviders.CurrentJdkProvider;
import dev.jbang.devkitman.jdkproviders.DefaultJdkProvider;
import dev.jbang.devkitman.jdkproviders.ExternalJdkProvider;
import dev.jbang.devkitman.jdkproviders.JBangJdkProvider;
import dev.jbang.devkitman.jdkproviders.JavaHomeJdkProvider;
import dev.jbang.devkitman.jdkproviders.LinkedJdkProvider;
import dev.jbang.devkitman.jdkproviders.MultiHomeJdkProvider;
import dev.jbang.devkitman.jdkproviders.PathJdkProvider;
import dev.jbang.devkitman.util.FileUtils;
import dev.jbang.devkitman.util.FunctionWithError;
import dev.jbang.devkitman.util.OsUtils;
import dev.jbang.devkitman.util.RemoteAccessProvider;

class TestJreSupport extends BaseTest {
	private static final JdkFilter JRE = JdkFilter.tag("Jre");
	private static final JdkFilter RUNTIME = JdkFilter.or(JdkFilter.JDK, JRE);

	private Path runtime(String id, String version, boolean jdk) {
		Path home = config.installPath().resolve(id);
		initMockJdkDir(home, version, "JAVA_VERSION", jdk, false, false, false);
		return home;
	}

	@Test
	void mixedInstalledVersionsAndIds() {
		Path jre = runtime("17-jre-jbang", "17.0.2", false);
		Path jdk = runtime("21-jbang", "21.0.2", true);
		JdkManager manager = JdkManager.builder().providers(new JBangJdkProvider(config.installPath())).build();
		assertEquals(1, manager.listInstalledJdks().size());
		assertEquals(2, manager.listInstalled(RUNTIME).size());
		assertEquals(jre, manager.getInstalledJdk("17", RUNTIME).home());
		assertEquals(jre, manager.getInstalledJdk("17+", JRE).home());
		assertEquals(jre, manager.getOrInstallJdk("17-jre-jbang", RUNTIME).home());
		assertNull(manager.getInstalledJdk("17-jre-jbang"));
		assertNull(manager.getInstalledJdk("18", RUNTIME));
		assertEquals(jdk, manager.getInstalledJdk(null).home());
		assertThrows(IllegalArgumentException.class, () -> manager.setDefaultJdk(manager.getInstalledJdk("17", JRE)));
		assertThrows(IllegalArgumentException.class,
				() -> manager.setDefaultJdkForVersion(manager.getInstalledJdk("17", JRE)));
	}

	@Test
	void providerPrecedenceAndProviderPredicates() {
		Path jre = runtime("17-jre-jbang", "17.0.2", false);
		Path jdk = runtime("17-jbang", "17.0.3", true);
		environmentVariables.set("JAVA_HOME", jre.toString());
		JavaHomeJdkProvider homeProvider = new JavaHomeJdkProvider();
		JBangJdkProvider installed = new JBangJdkProvider(config.installPath());
		JdkManager manager = JdkManager.builder().providers(homeProvider, installed).build();
		assertSame(homeProvider, manager.getJdk("17", RUNTIME).provider());
		assertSame(installed, manager.getJdk("17").provider());
		assertEquals(jdk, manager.getInstalledJdk("17", JdkFilter.JDK).home());
		assertNull(manager.getInstalledJdk("javahome"));
		assertEquals(jre, manager.getOrInstallJdk("javahome", RUNTIME, p -> p == homeProvider).home());
		assertNull(manager.getInstalledJdk("javahome", RUNTIME, p -> p == installed));
	}

	@Test
	void javaHomeAndMultiHomeAreOptIn() {
		Path jre = runtime("runtime", "11.0.2", false);
		environmentVariables.set("JAVA_HOME", jre.toString());
		environmentVariables.set("JAVA_HOME_JRE_TEST", jre.toString());
		JavaHomeJdkProvider provider = new JavaHomeJdkProvider();
		assertEquals(0, provider.listInstalled().count());
		assertEquals(jre, provider.getInstalledById("javahome", JRE).home());
		MultiHomeJdkProvider multi = new MultiHomeJdkProvider();
		assertTrue(multi.listInstalled(JRE).anyMatch(j -> j.home().equals(jre)));
		assertFalse(multi.listInstalled().anyMatch(j -> j.home().equals(jre)));
	}

	@Test
	void currentJavaHomeAndJava8NestedJre() throws IOException {
		String original = System.getProperty("java.home");
		try {
			Path jre = runtime("runtime", "17.0.2", false);
			System.setProperty("java.home", jre.toString());
			CurrentJdkProvider provider = new CurrentJdkProvider();
			assertEquals(0, provider.listInstalled().count());
			assertEquals(jre, provider.getInstalledById("current", RUNTIME).home());
			Path jdk8 = runtime("jdk8", "1.8.0_442", true);
			Files.createDirectories(jdk8.resolve("jre"));
			System.setProperty("java.home", jdk8.resolve("jre").toString());
			assertEquals(jdk8, provider.getInstalledById("current").home());
			assertTrue(JdkFilter.JDK.matches(provider.getInstalledById("current")));
		} finally {
			System.setProperty("java.home", original);
		}
	}

	@Test
	void pathWithoutJavacFindsJreOnlyWithFilter() {
		Path jre = runtime("runtime", "17.0.2", false);
		environmentVariables.set("PATH", jre.resolve("bin").toString());
		PathJdkProvider provider = new PathJdkProvider();
		assertEquals(0, provider.listInstalled().count());
		assertEquals(jre, provider.getInstalledById("path", RUNTIME).home());
		assertNull(provider.getInstalledByVersion(21, false, RUNTIME));
	}

	@Test
	void linkedAndExistingDefaultJreRemainInspectableButCannotBecomeJdkDefault() {
		Path jre = runtime("17-jre-jbang", "17.0.2", false);
		DefaultJdkProvider defaults = new DefaultJdkProvider(config.installPath().resolve("default"),
				config.installPath());
		LinkedJdkProvider linked = new LinkedJdkProvider(config.installPath());
		JBangJdkProvider installed = new JBangJdkProvider(config.installPath());
		JdkManager manager = JdkManager.builder().providers(defaults, linked, installed).build();
		manager.linkToExistingJdk(jre, "runtime", RUNTIME);
		Jdk.LinkedJdk link = (Jdk.LinkedJdk) manager.getJdk("runtime-linked", RUNTIME);
		assertNotNull(link);
		assertEquals(jre, link.linked().home());
		assertNull(manager.getInstalledJdk("runtime-linked"));
		assertThrows(IllegalArgumentException.class, () -> manager.linkToExistingJdk(jre, "jdkonly"));
		assertNull(defaults.getAvailableByIdOrToken("default@" + jre, RUNTIME));
		FileUtils.createLink(config.installPath().resolve("default"), jre);
		assertNull(manager.getDefaultJdk());
		assertEquals(17, manager.getJdk("default", RUNTIME).majorVersion());
		assertEquals(17, manager.getJdk("17", JRE).majorVersion());
	}

	@Test
	void externalRuntimeTypeIsAccurate() {
		Path jre = runtime("runtime", "17.0.2", false);
		ExternalJdkProvider provider = new ExternalJdkProvider();
		assertNull(provider.getInstalledByPath(jre));
		assertTrue(JRE.matches(provider.getInstalledByPath(jre, RUNTIME)));
	}

	@Test
	void compilerOnlyHomesRetainHistoricalDefaultAndLinkBehavior() throws IOException {
		Path compilerHome = runtime("17-jbang", "17.0.2", true);
		Files.delete(compilerHome.resolve("bin").resolve("java"));
		Files.delete(compilerHome.resolve("bin").resolve("java.exe"));
		DefaultJdkProvider defaults = new DefaultJdkProvider(config.installPath().resolve("default"),
				config.installPath());
		LinkedJdkProvider links = new LinkedJdkProvider(config.installPath());
		JdkManager manager = JdkManager.builder()
			.providers(defaults, links, new JBangJdkProvider(config.installPath()))
			.build();
		Jdk.InstalledJdk compiler = manager.getInstalledJdk("17-jbang");
		assertNotNull(compiler);
		manager.setDefaultJdk(compiler);
		assertNotNull(manager.getDefaultJdk());
		manager.linkToExistingJdk(compilerHome, "compiler");
		assertNotNull(manager.getInstalledJdk("compiler-linked"));
		assertNull(manager.getInstalledJdk("compiler-linked", JRE));
	}

	@Test
	void legacyProvidersAndInstallersStillWorkAndFilteredOverrideReceivesAst() {
		JdkProvider legacy = new BaseJdkProvider() {
			public String name() {
				return "legacy";
			}

			public String description() {
				return "legacy";
			}

			public Stream<Jdk.InstalledJdk> listInstalled() {
				return Stream.of(new Jdk.InstalledJdk.Default(this, "legacy", config.installPath(), "17",
						Collections.singleton("Jdk")));
			}
		};
		JdkManager manager = JdkManager.builder().providers(legacy).build();
		assertEquals(17, manager.getOrInstallJdk("17", p -> p == legacy).majorVersion());
		assertNull(manager.getInstalledJdk("17", JRE));
		List<JdkFilter> filters = new ArrayList<>();
		JdkProvider filtered = new BaseJdkProvider() {
			public String name() {
				return "filtered";
			}

			public String description() {
				return "filtered";
			}

			public Stream<Jdk.InstalledJdk> listInstalled() {
				return Stream.empty();
			}

			public Stream<Jdk.InstalledJdk> listInstalled(JdkFilter filter) {
				filters.add(filter);
				return Stream.<Jdk.InstalledJdk>of(new Jdk.InstalledJdk.Default(this, "filtered", config.installPath(),
						"17", Collections.singleton("Jre")))
					.filter(filter::matches);
			}
		};
		manager = JdkManager.builder().providers(filtered).build();
		assertEquals(17, manager.getOrInstallJdk("17", RUNTIME).majorVersion());
		assertSame(RUNTIME, filters.get(filters.size() - 1));
		JdkInstaller queryOnly = new JdkInstaller() {
			@Override
			public Jdk.AvailableJdk getAvailableByVersion(int version, boolean openVersion) {
				return version == 17
						? new Jdk.AvailableJdk.Default(legacy, "legacy", "17", Collections.singleton("Jdk"))
						: null;
			}
		};
		assertNotNull(queryOnly.getAvailableByVersion(17, false, JdkFilter.JDK));
		assertNull(queryOnly.getAvailableByVersion(18, false, JdkFilter.JDK));
		assertNull(queryOnly.getAvailableByVersion(17, false, JRE));
	}

	@Test
	void foojayQueriesTypesFiltersBeforeSelectionAndInstallsJreWithoutDefaults() throws IOException {
		List<String> urls = new ArrayList<>();
		Path archive = jreArchive();
		JBangJdkProvider provider = new JBangJdkProvider(config.installPath());
		provider.installer(new FoojayJdkInstaller(provider).remoteAccessProvider(foojayAccess(urls, archive)));
		DefaultJdkProvider defaults = new DefaultJdkProvider(config.installPath().resolve("default"),
				config.installPath());
		JdkManager manager = JdkManager.builder().providers(defaults, provider).build();
		assertTrue(manager.listAvailableJdks().stream().allMatch(JdkFilter.JDK::matches));
		assertTrue(urls.stream().allMatch(url -> url.contains("package_type=jdk")));
		urls.clear();
		assertEquals(2, manager.listAvailable(RUNTIME).size());
		assertTrue(urls.stream().anyMatch(url -> url.contains("package_type=jre")));
		urls.clear();
		assertTrue(JdkFilter.JDK.matches(manager.getAvailableJdk("17+", RUNTIME)));
		assertTrue(urls.stream().allMatch(url -> url.contains("package_type=jdk")));
		assertTrue(JRE.matches(manager.getAvailableJdk("17", JdkFilter.not(JdkFilter.JDK))));
		assertNull(manager.getAvailableJdk("17.0.2-temurin-jre-jbang"));
		assertNotNull(manager.getAvailableJdk("17.0.2-temurin-jre-jbang", JRE));
		assertNull(manager.getJdk("17", JRE, p -> p == defaults));
		Jdk.InstalledJdk jre = manager.getOrInstallJdk("17", JRE);
		assertTrue(JRE.matches(jre));
		assertFalse(JdkFilter.JDK.matches(jre));
		assertNull(manager.getDefaultJdk());
		assertNull(manager.getDefaultJdkForVersion(17));
		assertEquals(0, manager.listInstalledJdks().size());
		assertEquals(1, manager.listInstalled(JRE).size());
		urls.clear();
		assertEquals(jre.home(), manager.getOrInstallJdk("17+", RUNTIME).home());
		assertTrue(urls.isEmpty());
	}

	@Test
	void metadataExplicitJreQueriesAndVersionSelection() {
		List<String> urls = new ArrayList<>();
		JBangJdkProvider provider = new JBangJdkProvider(config.installPath());
		MetadataJdkInstaller installer = new MetadataJdkInstaller(provider)
			.remoteAccessProvider(new RemoteAccessProvider() {
				public Path downloadFromUrl(String url) throws IOException {
					throw new IOException("not expected");
				}

				public <T> T resultFromUrl(String url, FunctionWithError<InputStream, T> reader) throws IOException {
					urls.add(url);
					return reader.apply(getClass().getResourceAsStream("/testMetadataInstall.json"));
				}
			});
		provider.installer(installer);
		JdkManager manager = JdkManager.builder().providers(provider).build();
		assertTrue(JRE.matches(manager.getAvailableJdk("11", JRE)));
		assertTrue(urls.stream().allMatch(url -> url.contains("jre")));
		assertNull(manager.getAvailableJdk("11"));
		assertTrue(JdkFilter.JDK.matches(manager.getAvailableJdk("11+", RUNTIME)));
	}

	private RemoteAccessProvider foojayAccess(List<String> urls, Path archive) {
		return new RemoteAccessProvider() {
			public Path downloadFromUrl(String url) {
				return archive;
			}

			public <T> T resultFromUrl(String url, FunctionWithError<InputStream, T> reader) throws IOException {
				urls.add(url);
				String result = "{\"result\":["
						+ "{\"java_version\":\"21.0.2\",\"major_version\":21,\"distribution\":\"temurin\",\"release_status\":\"ga\",\"package_type\":\"jdk\",\"links\":{\"pkg_download_redirect\":\"https://example.invalid/jdk\"}},"
						+ "{\"java_version\":\"17.0.2\",\"major_version\":17,\"distribution\":\"temurin\",\"release_status\":\"ga\",\"package_type\":\"jre\",\"links\":{\"pkg_download_redirect\":\"https://example.invalid/jre\"}}]}";
				return reader.apply(new ByteArrayInputStream(result.getBytes(StandardCharsets.UTF_8)));
			}
		};
	}

	private Path jreArchive() throws IOException {
		Path archive = config.installPath().resolve("runtime.zip");
		Files.createDirectories(archive.getParent());
		String prefix = OsUtils.isMac() ? "runtime/Contents/Home/" : "runtime/";
		try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
			for (String name : new String[] { "release", "bin/java", "bin/java.exe" }) {
				zip.putNextEntry(new ZipEntry(prefix + name));
				zip.write((name.equals("release") ? "JAVA_VERSION=\"17.0.2\"\n" : "dummy")
					.getBytes(StandardCharsets.UTF_8));
				zip.closeEntry();
			}
		}
		return archive;
	}
}
