package dev.jbang.devkitman.jdkproviders;

import static dev.jbang.devkitman.jdkproviders.ScoopJdkProvider.jdksRoot;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import dev.jbang.devkitman.BaseTest;
import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.util.FileUtils;
import dev.jbang.devkitman.util.JavaUtils;

public class ScoopJdkProviderTest extends BaseTest {
	@Test
	void testScoopProviderFindsInstalledJdkByVersionPattern() {
		Path jdkHome = installScoopJdk("25.0.1");
		Jdk.InstalledJdk jdk = jdkManager("scoop").getInstalledJdk("25+");

		assertThat(jdk, notNullValue());
		assertThat(jdk.provider(), instanceOf(ScoopJdkProvider.class));
		assertThat(jdk.home(), Matchers.is(jdkHome));
		assertThat(jdk.id(), Matchers.is(jdkHome.getFileName().toString()));
	}

	@Test
	void testScoopProviderIgnoresOtherScoopApps() {
		Path jdkHome = installScoopJdk("25.0.1");
		Path gitHome = installOtherScoopApp("git", true);
		installOtherScoopApp("7zip", false);
		Path nodejsHome = installOtherScoopApp("nodejs", true);

		ScoopJdkProvider provider = new ScoopJdkProvider();
		Jdk.InstalledJdk jdk = provider.getInstalledByPath(jdkHome);
		assertThat(jdk, notNullValue());
		assertThat(jdk.home(), Matchers.is(jdkHome));
		assertThat(provider.getInstalledByPath(gitHome), nullValue());
		assertThat(provider.getInstalledByPath(nodejsHome), nullValue());
	}

	private Path installScoopJdk(String releaseVersion) {
		int majorVersion = JavaUtils.parseJavaVersion(releaseVersion);
		Path scoopPackagePath = jdksRoot().resolve("openjdk" + majorVersion);

		Path jdkHome = scoopPackagePath.resolve(releaseVersion);
		initMockJdkDir(jdkHome, releaseVersion);
		Path current = scoopPackagePath.resolve("current");
		FileUtils.createLink(current, jdkHome);
		return current;
	}

	private Path installOtherScoopApp(String appName, boolean looksLikeJdk) {
		Path appPath = jdksRoot().resolve(appName).resolve("99.0.1");
		if (looksLikeJdk) {
			initMockJdkDir(appPath, "99.0.1");
		} else {
			try {
				Files.createDirectories(appPath);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}
		}
		return appPath;
	}
}
