package dev.jbang.devkitman.jdkproviders;

import static dev.jbang.devkitman.jdkproviders.MiseJdkProvider.jdksRoot;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import dev.jbang.devkitman.BaseTest;
import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.util.FileUtils;

public class MiseJdkProviderTest extends BaseTest {

	@Test
	void testMiseProviderFindsInstalledJdkByVersionPattern() {
		Path jdkHome = installMiseJdk("25.0.1-tem", "25.0.1");
		Jdk.InstalledJdk jdk = jdkManager("mise").getInstalledJdk("25+");

		assertThat(jdk, notNullValue());
		assertThat(jdk.provider(), instanceOf(MiseJdkProvider.class));
		assertThat(jdk.home(), Matchers.is(jdkHome));
		assertThat(jdk.id(), Matchers.is(jdkHome.getFileName().toString()));
	}

	@Test
	void testMiseProviderIgnoresCurrentSymlink() {
		Path jdkHome = installMiseJdk("26.0.1-tem", "26.0.1");
		Path current = jdksRoot().resolve("current");
		FileUtils.createLink(current, jdkHome);

		List<String> ids = jdkManager("mise").listInstalledJdks()
			.stream()
			.map(Jdk::id)
			.collect(Collectors.toList());
		assertThat(ids, hasSize(1));
		assertThat(ids, Matchers.contains("26.0.1-tem"));
		assertThat(new MiseJdkProvider().getInstalledByPath(current), nullValue());
	}

	private Path installMiseJdk(String folderName, String releaseVersion) {
		Path jdkHome = jdksRoot().resolve(folderName);
		initMockJdkDir(jdkHome, releaseVersion);
		return jdkHome;
	}
}
