package dev.jbang.devkitman;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import dev.jbang.devkitman.jdkproviders.DefaultJdkProvider;
import dev.jbang.devkitman.jdkproviders.JavaHomeJdkProvider;
import dev.jbang.devkitman.jdkproviders.LinkedJdkProvider;
import dev.jbang.devkitman.jdkproviders.MultiHomeJdkProvider;
import dev.jbang.devkitman.jdkproviders.PathJdkProvider;

public class TestJdkManager extends BaseTest {
	@Test
	void testNoJdksInstalled() {
		assertThat(jdkManager().listInstalledJdks(), is(empty()));
	}

	@Test
	void testHasJdksInstalled() {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);
		List<Jdk> jdks = jdkManager().listInstalledJdks();
		assertThat(jdks, hasSize(4));
		assertThat(
				jdks.stream().map(Jdk::majorVersion).collect(Collectors.toList()),
				containsInAnyOrder(11, 11, 12, 13));
		assertThat(
				jdks.stream().map(Jdk::version).collect(Collectors.toList()),
				containsInAnyOrder("11.0.7", "11.0.7", "12.0.7", "13.0.7"));
	}

	@Test
	void testHasJdksInstalledWithJavaHome() {
		Arrays.asList(11, 12).forEach(this::createMockJdk);

		Path jdkPath = createMockJdkExt(13);
		environmentVariables.set("JAVA_HOME", jdkPath.toString());

		List<Jdk> jdks = jdkManager("default", "javahome", "jbang").listInstalledJdks();
		assertThat(jdks, hasSize(4));
		assertThat(
				jdks.stream().map(Jdk::majorVersion).collect(Collectors.toList()),
				containsInAnyOrder(11, 11, 12, 13));
		assertThat(
				jdks.stream().map(Jdk::version).collect(Collectors.toList()),
				containsInAnyOrder("11.0.7", "11.0.7", "12.0.7", "13.0.7"));
		assertThat(
				jdks.stream().map(Jdk::isFixedVersion).collect(Collectors.toList()),
				containsInAnyOrder(false, true, true, false));
	}

	@Test
	void testHasJdksInstalledAllProvider() {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);
		List<Jdk> jdks = jdkManager(JdkProviders.instance().allNames().toArray(new String[] {}))
			.listInstalledJdks();
		assertThat(jdks, hasSize(greaterThanOrEqualTo(5)));
	}

	@Test
	void testDefault() {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);
		JdkManager jm = jdkManager();
		assertThat(jm.getDefaultJdk(), not(nullValue()));
		assertThat(jm.getDefaultJdk().majorVersion(), is(11));
		jm.setDefaultJdk(jm.getInstalledJdk("12"));
		assertThat(jm.getDefaultJdk().majorVersion(), is(12));
	}

	@Test
	void testDefaultPlus() {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);
		JdkManager jm = jdkManager();
		assertThat(jm.getDefaultJdk(), not(nullValue()));
		assertThat(jm.getDefaultJdk().majorVersion(), is(11));
		jm.setDefaultJdk(jm.getInstalledJdk("16+"));
		assertThat(jm.getDefaultJdk().majorVersion(), is(17));
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
		jm.setDefaultJdk(jm.getInstalledJdk("12"));
		assertThat(jm.getInstalledJdk(null).majorVersion(), is(12));
	}

	@Test
	void testHasTagJdk() {
		createMockJdk(11);
		JdkManager jm = jdkManager();
		assertThat(jm.getInstalledJdk(null), not(nullValue()));
		Set<String> tags = jm.getInstalledJdk(null).tags();
		assertThat(tags, contains("Jdk"));
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
		assertThat(tags, containsInAnyOrder("Jdk", "Graalvm"));
	}

	@Test
	void testHasTagsJdkGraalVMNative() {
		createMockJdk(11, (p, v) -> initMockJdkDir(p, v, "JAVA_VERSION", true, true, true, false));
		JdkManager jm = jdkManager();
		assertThat(jm.getInstalledJdk(null), not(nullValue()));
		Set<String> tags = jm.getInstalledJdk(null).tags();
		assertThat(tags, containsInAnyOrder("Jdk", "Graalvm", "Native"));
	}

	@Test
	void testHasTagsJdkJavaFX() {
		createMockJdk(11, (p, v) -> initMockJdkDir(p, v, "JAVA_VERSION", true, false, false, true));
		JdkManager jm = jdkManager();
		assertThat(jm.getInstalledJdk(null), not(nullValue()));
		Set<String> tags = jm.getInstalledJdk(null).tags();
		assertThat(tags, containsInAnyOrder("Jdk", "Javafx"));
	}

	@Test
	void testDefaultUninstallNext() {
		Arrays.asList(14, 11, 17).forEach(this::createMockJdk);
		JdkManager jm = jdkManager();
		assertThat(jm.getDefaultJdk(), not(nullValue()));
		assertThat(jm.getDefaultJdk().majorVersion(), is(14));
		jm.getInstalledJdk("14", JdkProvider.Predicates.canUpdate).uninstall();
		assertThat(jm.getDefaultJdk(), not(nullValue()));
		assertThat(jm.getDefaultJdk().majorVersion(), is(17));
	}

	@Test
	void testDefaultUninstallPrev() {
		Arrays.asList(17, 11, 14).forEach(this::createMockJdk);
		JdkManager jm = jdkManager();
		assertThat(jm.getDefaultJdk(), not(nullValue()));
		assertThat(jm.getDefaultJdk().majorVersion(), is(17));
		jm.getInstalledJdk("17", JdkProvider.Predicates.canUpdate).uninstall();
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
		Jdk jdk = jm.getInstalledJdk("12");
		assertThat(jdk.provider(), instanceOf(JavaHomeJdkProvider.class));
		assertThat(jdk.home().toString(), endsWith(File.separator + "jdk12"));
	}

	@Test
	void testDefaultWithJavaHome() throws IOException {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);

		Path jdkPath = createMockJdkExt(12);
		environmentVariables.set("JAVA_HOME", jdkPath.toString());

		JdkManager jm = jdkManager("default", "javahome", "jbang");
		jm.setDefaultJdk(jm.getInstalledJdk("12"));
		Jdk jdk = jm.getInstalledJdk("12");
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
		Jdk jdk = jm.getInstalledJdk("12");
		assertThat(jdk.provider(), instanceOf(PathJdkProvider.class));
		assertThat(jdk.home().toString(), endsWith(File.separator + "jdk12"));
	}

	@Test
	void testLinkToExistingJdkPath() {
		Path jdkPath = createMockJdkExt(12);
		jdkManager().linkToExistingJdk(jdkPath, "12");
		List<Jdk> jdks = jdkManager().listInstalledJdks();
		assertThat(jdks, hasSize(1));
		assertThat(jdks.get(0).provider(), instanceOf(LinkedJdkProvider.class));
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
	void testLinkToInvalidVersion() {
		try {
			Path jdkPath = createMockJdkExt(12);
			jdkManager().linkToExistingJdk(jdkPath, "11");
			assertThat("Should have thrown an exception", false);
		} catch (IllegalArgumentException ex) {
			assertThat(
					ex.getMessage(),
					startsWith("Linked JDK is not of the correct version: 12 instead of: 11"));
		}
	}

	@Test
	void testLinkToInvalidId() {
		try {
			Path jdkPath = createMockJdkExt(12);
			jdkManager().linkToExistingJdk(jdkPath, "11foo");
			assertThat("Should have thrown an exception", false);
		} catch (IllegalArgumentException ex) {
			assertThat(
					ex.getMessage(),
					startsWith("Invalid JDK id: 11foo, must be a valid major version number"));
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
	void testInstallVersion() {
		Path home = mockJdkManager(11, 14, 17).getOrInstallJdk("17").home();
		assertThat(home.toString(), endsWith(File.separator + "17"));
		assertThat(home.resolve("release").toFile().exists(), is(true));
	}

	@Test
	void testInstallVersionFail() {
		try {
			Path home = mockJdkManager(11, 14, 17).getOrInstallJdk("15").home();
		} catch (Exception e) {
			assertThat(
					e.getMessage(),
					containsString("No suitable JDK was found for requested version: 15"));
		}
	}

	@Test
	void testInstallVersionPlus() {
		Path home = mockJdkManager(11, 14, 17).getOrInstallJdk("15+").home();
		assertThat(home.toString(), endsWith(File.separator + "17"));
		assertThat(home.resolve("release").toFile().exists(), is(true));
	}

	@Test
	void testInstallDefaultVersion() {
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
		Jdk jdk = jm.getInstalledJdk("12");
		assertThat(jdk.provider(), instanceOf(MultiHomeJdkProvider.class));
		assertThat(jdk.home().toString(), endsWith(File.separator + "jdk12"));
		jdk = jm.getInstalledJdk("17");
		assertThat(jdk.provider(), instanceOf(MultiHomeJdkProvider.class));
		assertThat(jdk.home().toString(), endsWith(File.separator + "jdk17"));
		List<Jdk> jdks = jm.listInstalledJdks();
		assertThat(jdks, hasSize(2));
		assertThat(jdks.stream().map(Jdk::majorVersion).collect(Collectors.toList()), containsInAnyOrder(12, 17));
	}
}
