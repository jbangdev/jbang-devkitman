package dev.jbang.devkitman.jdkproviders;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import dev.jbang.devkitman.BaseTest;
import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.util.FileUtils;

public class SdkmanJdkProviderTest extends BaseTest {
	@ParameterizedTest
	@CsvSource({
			"25.0.1-tem,25.0.1,25+",
			"21-tem,21.0.7,21+",
			"25.0.2-zulu,25.0.2,25+",
			"25.0.2.fx-zulu,25.0.2,25+",
			"21.0.10-librca,21.0.10,21+",
			"25-graal,25,25+",
			"26.ea.13-graal,26.ea.13,26+",
			"22.1.0.1.r17-gln,22.1.0.1.r17,22+"
	})
	void testSdkmanProviderFindsInstalledJdkByVersionPattern(
			String folderName, String releaseVersion, String requestedVersion) {
		Path jdkHome = installSdkmanJdk(folderName, releaseVersion);
		Jdk.InstalledJdk jdk = jdkManager("sdkman").getInstalledJdk(requestedVersion);

		assertThat(jdk, notNullValue());
		assertThat(jdk.provider(), instanceOf(SdkmanJdkProvider.class));
		assertThat(jdk.home(), Matchers.is(jdkHome));
		assertThat(jdk.id(), Matchers.is(folderName));
	}

	@Test
	void testSdkmanProviderIgnoresCurrentSymlink() {
		installSdkmanJdk("25.0.1-tem", "25.0.1");
		Path jdkHome = sdkmanJdksRoot().resolve("25.0.1-tem");
		FileUtils.createLink(sdkmanJdksRoot().resolve("current"), jdkHome);

		List<String> ids = jdkManager("sdkman").listInstalledJdks()
			.stream()
			.map(Jdk::id)
			.collect(Collectors.toList());
		assertThat(ids, hasSize(1));
		assertThat(ids, Matchers.contains("25.0.1-tem"));
	}

	private Path sdkmanJdksRoot() {
		return Paths.get(System.getProperty("user.home")).resolve(".sdkman/candidates/java");
	}

	private Path installSdkmanJdk(String folderName, String releaseVersion) {
		Path jdkHome = sdkmanJdksRoot().resolve(folderName);
		initMockJdkDir(jdkHome, releaseVersion);
		return jdkHome;
	}
}
