package dev.jbang.devkitman;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import dev.jbang.devkitman.jdkproviders.DefaultJdkProvider;
import dev.jbang.devkitman.jdkproviders.JavaHomeJdkProvider;
import dev.jbang.devkitman.jdkproviders.LinkedJdkProvider;
import dev.jbang.devkitman.jdkproviders.MultiHomeJdkProvider;
import dev.jbang.devkitman.jdkproviders.PathJdkProvider;
import dev.jbang.devkitman.util.FileUtils;

public class TestJdkManager extends BaseTest {
	@Test
	void testNoJdksInstalled() {
		assertThat(jdkManager().listInstalledJdks(), is(empty()));
	}

	@Test
	void testHasJdksInstalled() {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);
		List<Jdk.InstalledJdk> jdks = jdkManager().listInstalledJdks();
		assertThat(jdks, hasSize(7));
		assertThat(
				jdks.stream().map(Jdk::majorVersion).collect(Collectors.toList()),
				containsInAnyOrder(11, 11, 11, 12, 12, 13, 13));
		assertThat(
				jdks.stream().map(Jdk::version).collect(Collectors.toList()),
				containsInAnyOrder("11.0.7", "11.0.7", "11.0.7", "12.0.7", "12.0.7", "13.0.7", "13.0.7"));
		assertThat(
				jdks.stream().map(Jdk::id).collect(Collectors.toList()),
				containsInAnyOrder("default", "11-default", "11.0.7-distro-jbang", "12-default", "12.0.7-distro-jbang",
						"13-default",
						"13.0.7-distro-jbang"));
	}

	@Test
	void testHasJdksInstalledWithJavaHome() {
		Arrays.asList(11, 12).forEach(this::createMockJdk);

		Path jdkPath = createMockJdkExt(13);
		environmentVariables.set("JAVA_HOME", jdkPath.toString());

		List<Jdk.InstalledJdk> jdks = jdkManager("default", "javahome", "jbang").listInstalledJdks();
		assertThat(jdks, hasSize(6));
		assertThat(
				jdks.stream().map(Jdk::majorVersion).collect(Collectors.toList()),
				containsInAnyOrder(11, 11, 11, 12, 12, 13));
		assertThat(
				jdks.stream().map(Jdk::version).collect(Collectors.toList()),
				containsInAnyOrder("11.0.7", "11.0.7", "11.0.7", "12.0.7", "12.0.7", "13.0.7"));
		assertThat(
				jdks.stream().map(jdk -> jdk.provider().hasFixedVersions()).collect(Collectors.toList()),
				containsInAnyOrder(false, false, true, false, true, false));
	}

	@Test
	void testHasJdksInstalledAllProvider() {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);
		List<Jdk.InstalledJdk> jdks = jdkManager(JdkProviders.instance().allNames().toArray(new String[] {}))
			.listInstalledJdks();
		assertThat(jdks, hasSize(greaterThanOrEqualTo(5)));
	}

	@Test
	void testDefault() {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);
		JdkManager jm = jdkManager();
		assertThat(jm.getDefaultJdk(), not(nullValue()));
		assertThat(jm.getDefaultJdk().majorVersion(), is(11));
		jm.setDefaultJdk(Objects.requireNonNull(jm.getInstalledJdk("12")));
		assertThat(jm.getDefaultJdk().majorVersion(), is(12));
		assertThat(jm.getDefaultJdk().linked().id(), startsWith("12.0.7-distro-jbang"));
	}

	@Test
	void testDefaultPlus() {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);
		JdkManager jm = jdkManager();
		assertThat(jm.getDefaultJdk(), not(nullValue()));
		assertThat(jm.getDefaultJdk().majorVersion(), is(11));
		jm.setDefaultJdk(Objects.requireNonNull(jm.getInstalledJdk("16+")));
		assertThat(jm.getDefaultJdk().majorVersion(), is(17));
	}

	@Test
	void testDefaultCustomLinkPath() {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);
		Path tempPath = Paths.get(System.getProperty("user.home")).getParent();
		String link = tempPath.resolve("deflink").toAbsolutePath().toString();
		JdkManager jm = jdkManager("default;link=" + link, "linked", "jbang");
		// The following is null because the mocking doesn't create the correct link!
		assertThat(jm.getDefaultJdk(), nullValue());
		jm.setDefaultJdk(Objects.requireNonNull(jm.getInstalledJdk("16+")));
		assertThat(jm.getDefaultJdk().majorVersion(), is(17));
		assertThat(jm.getDefaultJdk().id(), is("default"));
	}

	@Test
	void testHomeDir() {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);
		Path home = jdkManager().getInstalledJdk(null).home();
		assertThat(home.toString(), endsWith(File.separator + "default"));
	}

	@Test
	void testDefaultHomeDir() {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);
		Path home = jdkManager().getInstalledJdk("default").home();
		assertThat(home.toString(), endsWith(File.separator + "default"));
	}

	@Test
	void testGetJdkNull() {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);
		JdkManager jm = jdkManager();
		assertThat(jm.getInstalledJdk(null), not(nullValue()));
		assertThat(jm.getInstalledJdk(null).majorVersion(), is(11));
		jm.setDefaultJdk(Objects.requireNonNull(jm.getInstalledJdk("12")));
		assertThat(jm.getInstalledJdk(null).majorVersion(), is(12));
	}

	@Test
	void testHasTagJdk() {
		createMockJdk(11);
		JdkManager jm = jdkManager();
		assertThat(jm.getInstalledJdk(null), not(nullValue()));
		Set<String> tags = jm.getInstalledJdk(null).tags();
		assertThat(tags, containsInAnyOrder("Jdk", "Ga"));
	}

	@Test
	@Disabled("Enable when we have support for JREs")
	void testHasTagJre() {
		createMockJdk(11, (p, v) -> initMockJdkDir(p, v, "JAVA_VERSION", false, false, false, false));
		JdkManager jm = jdkManager("jbang");
		assertThat(jm.getInstalledJdk(null), not(nullValue()));
		Set<String> tags = jm.getInstalledJdk(null).tags();
		assertThat(tags, contains("Jre"));
	}

	@Test
	void testHasTagsJdkGraalVM() {
		createMockJdk(11, (p, v) -> initMockJdkDir(p, v, "JAVA_VERSION", true, true, false, false));
		JdkManager jm = jdkManager();
		assertThat(jm.getInstalledJdk(null), not(nullValue()));
		Set<String> tags = jm.getInstalledJdk(null).tags();
		assertThat(tags, containsInAnyOrder("Jdk", "Ga", "Graalvm"));
	}

	@Test
	void testHasTagsJdkGraalVMNative() {
		createMockJdk(11, (p, v) -> initMockJdkDir(p, v, "JAVA_VERSION", true, true, true, false));
		JdkManager jm = jdkManager();
		assertThat(jm.getInstalledJdk(null), not(nullValue()));
		Set<String> tags = jm.getInstalledJdk(null).tags();
		assertThat(tags, containsInAnyOrder("Jdk", "Ga", "Graalvm", "Native"));
	}

	@Test
	void testHasTagsJdkJavaFX() {
		createMockJdk(11, (p, v) -> initMockJdkDir(p, v, "JAVA_VERSION", true, false, false, true));
		JdkManager jm = jdkManager();
		assertThat(jm.getInstalledJdk(null), not(nullValue()));
		Set<String> tags = jm.getInstalledJdk(null).tags();
		assertThat(tags, containsInAnyOrder("Jdk", "Ga", "Javafx"));
	}

	@Test
	void testDefaultUninstallNext() {
		Arrays.asList(14, 11, 17).forEach(this::createMockJdk);
		JdkManager jm = jdkManager();
		assertThat(jm.getDefaultJdk(), not(nullValue()));
		assertThat(jm.getDefaultJdk().majorVersion(), is(14));
		jm.getInstalledJdk("14", JdkProvider.Predicates.canInstall).uninstall();
		assertThat(jm.getDefaultJdk(), not(nullValue()));
		assertThat(jm.getDefaultJdk().majorVersion(), is(17));
	}

	@Test
	void testDefaultUninstallPrev() {
		Arrays.asList(17, 11, 14).forEach(this::createMockJdk);
		JdkManager jm = jdkManager();
		assertThat(jm.getDefaultJdk(), not(nullValue()));
		assertThat(jm.getDefaultJdk().majorVersion(), is(17));
		jm.getInstalledJdk("17", JdkProvider.Predicates.canInstall).uninstall();
		assertThat(jm.getDefaultJdk(), not(nullValue()));
		assertThat(jm.getDefaultJdk().majorVersion(), is(14));
	}

	@Test
	void testVersionHomeDir() {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);
		Path home = jdkManager().getInstalledJdk("17").home();
		assertThat(home.toString(), endsWith(File.separator + "17"));
	}

	@Test
	void testVersionPlusHomeDir() {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);
		Path home = jdkManager().getInstalledJdk("16+").home();
		assertThat(home.toString(), endsWith(File.separator + "17"));
	}

	@Test
	void testJavaHome() throws IOException {
		Arrays.asList(11, 13).forEach(this::createMockJdk);

		Path jdkPath = createMockJdkExt(12);
		environmentVariables.set("JAVA_HOME", jdkPath.toString());

		JdkManager jm = jdkManager("javahome", "jbang");
		Jdk.InstalledJdk jdk = jm.getInstalledJdk("12");
		assertThat(jdk.provider(), instanceOf(JavaHomeJdkProvider.class));
		assertThat(jdk.home().toString(), endsWith(File.separator + "jdk12"));
	}

	@Test
	@EnabledOnOs(OS.WINDOWS)
	void testJavaHomeLegacyDosPath() throws IOException {
		Path jdkPath = config.cachePath().resolve("dir123456789/jdk12");
		FileUtils.mkdirs(jdkPath);
		initMockJdkDir(jdkPath, "12.5.1");
		environmentVariables.set("JAVA_HOME", config.cachePath().resolve("dir123~1/jdk12"));

		JdkProvider javahome = JdkProviders.instance().byName("javahome", null);
		Jdk jdk = javahome.getInstalledByPath(jdkPath);
		assertThat(jdk, notNullValue());
	}

	@Test
	void testDefaultWithJavaHome() throws IOException {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);

		Path jdkPath = createMockJdkExt(12);
		environmentVariables.set("JAVA_HOME", jdkPath.toString());

		JdkManager jm = jdkManager("default", "javahome", "jbang");
		jm.setDefaultJdk(Objects.requireNonNull(jm.getInstalledJdk("javahome")));
		Jdk.InstalledJdk jdk = jm.getInstalledJdk("12");
		assertThat(jdk.provider(), instanceOf(DefaultJdkProvider.class));
		assertThat(jdk.home().toString(), endsWith(File.separator + "default"));
		assertThat(jdk.home().toRealPath().toString(), endsWith(File.separator + "jdk12"));
	}

	@Test
	void testPath() throws IOException {
		Arrays.asList(11, 13).forEach(this::createMockJdk);

		Path jdkPath = createMockJdkExt(12);
		environmentVariables.set(
				"PATH", jdkPath.resolve("bin") + File.pathSeparator + System.getenv("PATH"));

		JdkManager jm = jdkManager("path", "jbang");
		Jdk.InstalledJdk jdk = jm.getInstalledJdk("12");
		assertThat(jdk.provider(), instanceOf(PathJdkProvider.class));
		assertThat(jdk.home().toString(), endsWith(File.separator + "jdk12"));
	}

	@Test
	void testLinkToJdk() {
		Path jdkPath = createMockJdkExt(12);
		jdkManager().linkToExistingJdk(jdkPath, "mylink");
		List<Jdk.InstalledJdk> jdks = jdkManager().listInstalledJdks();
		assertThat(jdks, hasSize(1));
		assertThat(jdks.get(0).provider(), instanceOf(LinkedJdkProvider.class));
		assertThat(jdks.get(0).majorVersion(), is(12));
	}

	@Test
	void testLinkToExistingLink() {
		Path jdk12Path = createMockJdkExt(12);
		Path jdk14Path = createMockJdkExt(14);
		jdkManager().linkToExistingJdk(jdk12Path, "mylink");
		jdkManager().linkToExistingJdk(jdk14Path, "mylink");
		List<Jdk.InstalledJdk> jdks = jdkManager().listInstalledJdks();
		assertThat(jdks, hasSize(1));
		assertThat(jdks.get(0).provider(), instanceOf(LinkedJdkProvider.class));
		assertThat(jdks.get(0).majorVersion(), is(14));
	}

	@Test
	void testLinkToInvalidJdkPath() {
		try {
			jdkManager().linkToExistingJdk(Paths.get("/invalid-path"), "11");
			assertThat("Should have thrown an exception", false);
		} catch (IllegalArgumentException ex) {
			assertThat(
					ex.getMessage(),
					startsWith(
							"Unable to resolve path as directory: "
									+ File.separator
									+ "invalid-path"));
		}
	}

	@Test
	void testLinkToInvalidId() {
		try {
			Path jdkPath = createMockJdkExt(12);
			jdkManager().linkToExistingJdk(jdkPath, "11%foo");
			assertThat("Should have thrown an exception", false);
		} catch (IllegalArgumentException ex) {
			assertThat(
					ex.getMessage(),
					startsWith("Unable to create link to JDK in path:"));
		}
	}

	@Test
	void testProviderOrder() {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);

		Path jdkPath = createMockJdkExt(12);
		environmentVariables.set("JAVA_HOME", jdkPath.toString());

		JdkManager jm = jdkManager("javahome", "jbang");
		Jdk jdk = jm.getOrInstallJdk(null);
		assertThat(jdk.provider(), instanceOf(JavaHomeJdkProvider.class));
	}

	@Test
	void testGetOrInstallVersion() {
		Path home = mockJdkManager(11, 14, 17).getOrInstallJdk("17").home();
		assertThat(home.toString(), endsWith(File.separator + "17.0.7-distro-jbang"));
		assertThat(home.resolve("release").toFile().exists(), is(true));
	}

	@Test
	void testGetOrInstallVersionFail() {
		try {
			Path home = mockJdkManager(11, 14, 17).getOrInstallJdk("15").home();
		} catch (Exception e) {
			assertThat(
					e.getMessage(),
					containsString("No suitable JDK was found for requested version: 15"));
		}
	}

	@Test
	void testGetOrInstallVersionPlus() {
		Path home = mockJdkManager(11, 14, 17).getOrInstallJdk("15+").home();
		assertThat(home.toString(), endsWith(File.separator + "17.0.7-distro-jbang"));
		assertThat(home.resolve("release").toFile().exists(), is(true));
	}

	@Test
	void testGetOrInstallDefaultVersion() {
		Jdk jdk = mockJdkManager(8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24)
			.getOrInstallJdk(
					null);
		assertThat(jdk.majorVersion(), is(JdkManager.DEFAULT_JAVA_VERSION));
	}

	@Test
	void testMultiHome() {
		// Make sure we don't use any existing JAVA_HOME_ variables
		environmentVariables.getVariables()
			.keySet()
			.stream()
			.filter(k -> k.startsWith("JAVA_HOME_"))
			.forEach(environmentVariables::remove);

		Path jdkPath1 = createMockJdkExt(12);
		environmentVariables.set("JAVA_HOME_12_X64", jdkPath1.toString());
		Path jdkPath2 = createMockJdkExt(17);
		environmentVariables.set("JAVA_HOME_17_X64", jdkPath2.toString());

		JdkManager jm = jdkManager("multihome");
		Jdk.InstalledJdk jdk = jm.getInstalledJdk("12");
		assertThat(jdk.provider(), instanceOf(MultiHomeJdkProvider.class));
		assertThat(jdk.home().toString(), endsWith(File.separator + "jdk12"));
		jdk = jm.getInstalledJdk("17");
		assertThat(jdk.provider(), instanceOf(MultiHomeJdkProvider.class));
		assertThat(jdk.home().toString(), endsWith(File.separator + "jdk17"));
		List<Jdk.InstalledJdk> jdks = jm.listInstalledJdks();
		assertThat(jdks, hasSize(2));
		assertThat(jdks.stream().map(Jdk::majorVersion).collect(Collectors.toList()), containsInAnyOrder(12, 17));
	}

	@Test
	void testBackwardCompatList() {
		Arrays.asList("8", "11", "17").forEach(v -> createMockJdk(v, v + ".0.7"));
		List<Jdk.InstalledJdk> jdks = jdkManager().listInstalledJdks();
		assertThat(jdks, hasSize(4));
		assertThat(jdks.stream().map(Jdk::majorVersion).collect(Collectors.toList()), containsInAnyOrder(8, 8, 11, 17));
		assertThat(jdks.stream().map(Jdk::id).collect(Collectors.toList()),
				containsInAnyOrder("default", "8-jbang", "11-jbang", "17-jbang"));
	}

	@Test
	void testInstallVersion() {
		Jdk.InstalledJdk jdk = jdkManager().getOrInstallJdk("21");
		assertThat(jdk, notNullValue());
		List<Jdk.InstalledJdk> jdks = jdkManager().listInstalledJdks();
		assertThat(jdks, hasSize(3));
		// Mocked installations are always version 12!
		assertThat(jdks.stream().map(Jdk::majorVersion).collect(Collectors.toList()), containsInAnyOrder(12, 12, 12));
		assertThat(jdks.stream().map(Jdk::id).collect(Collectors.toList()),
				containsInAnyOrder("default", "12-default", "21.0.6+7-temurin-jbang"));
	}

	@Test
	void testUninstallNumberLink() {
		Path home = createMockJdk("11", "11.0.7");
		FileUtils.createLink(home.getParent().resolve("12"), home);
		List<Jdk.InstalledJdk> jdks = jdkManager().listInstalledJdks();
		assertThat(jdks, hasSize(3));
		assertThat(jdks.stream().map(Jdk::majorVersion).collect(Collectors.toList()), containsInAnyOrder(11, 11, 11));
		assertThat(jdks.stream().map(Jdk::id).collect(Collectors.toList()),
				containsInAnyOrder("default", "12-default", "11-jbang"));
		jdkManager().getOrInstallJdk("12", JdkProvider.Predicates.canUpdate).uninstall();
		jdks = jdkManager().listInstalledJdks();
		assertThat(jdks, hasSize(2));
		assertThat(jdks.stream().map(Jdk::majorVersion).collect(Collectors.toList()), containsInAnyOrder(11, 11));
		assertThat(jdks.stream().map(Jdk::id).collect(Collectors.toList()),
				containsInAnyOrder("default", "11-jbang"));
	}

	@Test
	void testUninstallAll() {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);
		JdkManager jm = jdkManager();
		List<Jdk.InstalledJdk> jdks = jm.listInstalledJdks();
		assertThat(jdks, hasSize(7));
		assertThat(jdks.stream().map(Jdk::id).collect(Collectors.toList()),
				containsInAnyOrder("default", "11-default", "12-default", "13-default", "11.0.7-distro-jbang",
						"12.0.7-distro-jbang", "13.0.7-distro-jbang"));
		jm.getOrInstallJdk("11", JdkProvider.Predicates.canInstall).uninstall();
		jdks = jm.listInstalledJdks();
		assertThat(jdks, hasSize(5));
		assertThat(jdks.stream().map(Jdk::id).collect(Collectors.toList()),
				containsInAnyOrder("default", "12-default", "13-default", "12.0.7-distro-jbang",
						"13.0.7-distro-jbang"));
		jm.getOrInstallJdk("12", JdkProvider.Predicates.canInstall).uninstall();
		jdks = jm.listInstalledJdks();
		assertThat(jdks, hasSize(3));
		assertThat(jdks.stream().map(Jdk::id).collect(Collectors.toList()),
				containsInAnyOrder("default", "13-default", "13.0.7-distro-jbang"));
		jm.getOrInstallJdk("13", JdkProvider.Predicates.canInstall).uninstall();
		jdks = jm.listInstalledJdks();
		assertThat(jdks, empty());
	}
}
