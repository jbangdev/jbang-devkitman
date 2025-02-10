package dev.jbang.devkitman;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import dev.jbang.devkitman.jdkproviders.DefaultJdkProvider;
import dev.jbang.devkitman.jdkproviders.JavaHomeJdkProvider;
import dev.jbang.devkitman.jdkproviders.LinkedJdkProvider;
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

		Path jdkPath = config.cachePath.resolve("jdk13");
		FileUtils.mkdirs(jdkPath);
		initMockJdkDir(jdkPath, "13.0.7");
		environmentVariables.set("JAVA_HOME", jdkPath.toString());

		List<Jdk> jdks = jdkManager("default", "javahome", "jbang").listInstalledJdks();
		assertThat(jdks, hasSize(4));
		assertThat(
				jdks.stream().map(Jdk::majorVersion).collect(Collectors.toList()),
				containsInAnyOrder(11, 11, 12, 13));
		assertThat(
				jdks.stream().map(Jdk::version).collect(Collectors.toList()),
				containsInAnyOrder("11.0.7", "11.0.7", "12.0.7", "13.0.7"));
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
		jm.setDefaultJdk(jm.getJdk("12"));
		assertThat(jm.getDefaultJdk().majorVersion(), is(12));
	}

	@Test
	void testDefaultPlus() {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);
		JdkManager jm = jdkManager();
		assertThat(jm.getDefaultJdk(), not(nullValue()));
		assertThat(jm.getDefaultJdk().majorVersion(), is(11));
		jm.setDefaultJdk(jm.getJdk("16+"));
		assertThat(jm.getDefaultJdk().majorVersion(), is(17));
	}

	@Test
	void testHomeDir() {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);
		Path home = jdkManager().getOrInstallJdk(null).home();
		assertThat(home.toString(), endsWith(File.separator + "default"));
	}

	@Test
	void testDefaultHomeDir() {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);
		Path home = jdkManager().getOrInstallJdk("default").home();
		assertThat(home.toString(), endsWith(File.separator + "default"));
	}

	@Test
	void testDefaultUninstallNext() {
		Arrays.asList(14, 11, 17).forEach(this::createMockJdk);
		JdkManager jm = jdkManager();
		assertThat(jm.getDefaultJdk(), not(nullValue()));
		assertThat(jm.getDefaultJdk().majorVersion(), is(14));
		jm.getJdk("14", JdkProvider.Predicates.canUpdate).uninstall();
		assertThat(jm.getDefaultJdk(), not(nullValue()));
		assertThat(jm.getDefaultJdk().majorVersion(), is(17));
	}

	@Test
	void testDefaultUninstallPrev() {
		Arrays.asList(17, 11, 14).forEach(this::createMockJdk);
		JdkManager jm = jdkManager();
		assertThat(jm.getDefaultJdk(), not(nullValue()));
		assertThat(jm.getDefaultJdk().majorVersion(), is(17));
		jm.getJdk("17", JdkProvider.Predicates.canUpdate).uninstall();
		assertThat(jm.getDefaultJdk(), not(nullValue()));
		assertThat(jm.getDefaultJdk().majorVersion(), is(14));
	}

	@Test
	void testVersionHomeDir() {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);
		Path home = jdkManager().getOrInstallJdk("17").home();
		assertThat(home.toString(), endsWith(File.separator + "17"));
	}

	@Test
	void testVersionPlusHomeDir() {
		Arrays.asList(11, 14, 17).forEach(this::createMockJdk);
		Path home = jdkManager().getOrInstallJdk("16+").home();
		assertThat(home.toString(), endsWith(File.separator + "17"));
	}

	@Test
	void testJavaHome() throws IOException {
		Arrays.asList(11, 13).forEach(this::createMockJdk);

		Path jdkPath = config.cachePath.resolve("jdk12");
		FileUtils.mkdirs(jdkPath);
		initMockJdkDir(jdkPath, "12.0.7");
		environmentVariables.set("JAVA_HOME", jdkPath.toString());

		JdkManager jm = jdkManager("javahome", "jbang");
		Jdk jdk = jm.getOrInstallJdk("12");
		assertThat(jdk.provider(), instanceOf(JavaHomeJdkProvider.class));
		assertThat(jdk.home().toString(), endsWith(File.separator + "jdk12"));
	}

	@Test
	void testDefaultWithJavaHome() throws IOException {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);

		Path jdkPath = config.cachePath.resolve("jdk12");
		FileUtils.mkdirs(jdkPath);
		initMockJdkDir(jdkPath, "12.0.7");
		environmentVariables.set("JAVA_HOME", jdkPath.toString());

		JdkManager jm = jdkManager("default", "javahome", "jbang");
		jm.setDefaultJdk(jm.getJdk("12"));
		Jdk jdk = jm.getOrInstallJdk("12");
		assertThat(jdk.provider(), instanceOf(DefaultJdkProvider.class));
		assertThat(jdk.home().toString(), endsWith(File.separator + "default"));
		assertThat(jdk.home().toRealPath().toString(), endsWith(File.separator + "jdk12"));
	}

	@Test
	void testPath() throws IOException {
		Arrays.asList(11, 13).forEach(this::createMockJdk);

		Path jdkPath = config.cachePath.resolve("jdk12");
		FileUtils.mkdirs(jdkPath);
		initMockJdkDir(jdkPath, "12.0.7");
		environmentVariables.set(
				"PATH", jdkPath.resolve("bin") + File.pathSeparator + System.getenv("PATH"));

		JdkManager jm = jdkManager("path", "jbang");
		Jdk jdk = jm.getOrInstallJdk("12");
		assertThat(jdk.provider(), instanceOf(PathJdkProvider.class));
		assertThat(jdk.home().toString(), endsWith(File.separator + "jdk12"));
	}

	@Test
	void testLinkToExistingJdkPath() {
		Path jdkPath = config.cachePath.resolve("jdk12");
		FileUtils.mkdirs(jdkPath);
		initMockJdkDir(jdkPath, "12.0.7");

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
	void testProviderOrder() {
		Arrays.asList(11, 12, 13).forEach(this::createMockJdk);

		Path jdkPath = config.cachePath.resolve("jdk12");
		FileUtils.mkdirs(jdkPath);
		initMockJdkDir(jdkPath, "12.0.7");
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
}
