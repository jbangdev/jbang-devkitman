package dev.jbang.devkitman.jdkproviders;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import dev.jbang.devkitman.BaseTest;
import dev.jbang.devkitman.Jdk;

public class MultiHomeJdkProviderTest extends BaseTest {

	@Test
	void testMultiHomeProviderFindsInstalledJdkByVersionPattern() {
		clearJavaHomeVariables();

		Path jdkHome = createMockJdkExt(21);
		environmentVariables.set("JAVA_HOME_21_X64", jdkHome.toString());

		Jdk.InstalledJdk jdk = jdkManager("multihome").getInstalledJdk("21+");

		assertThat(jdk, notNullValue());
		assertThat(jdk.provider(), instanceOf(MultiHomeJdkProvider.class));
		assertThat(jdk.home(), Matchers.is(jdkHome));
		assertThat(jdk.id(), Matchers.is("multihome_21_x64"));
	}

	@Test
	void testMultiHomeProviderIgnoresNonExistingHomes() {
		clearJavaHomeVariables();

		Path validJdkHome = createMockJdkExt(17);
		environmentVariables.set("JAVA_HOME_17_X64", validJdkHome.toString());
		environmentVariables.set("JAVA_HOME_18_X64", config.cachePath().resolve("missing-jdk").toString());
		environmentVariables.set("NOT_A_JAVA_HOME", validJdkHome.toString());

		List<Jdk.InstalledJdk> jdks = jdkManager("multihome").listInstalledJdks();

		assertThat(jdks, hasSize(1));
		assertThat(jdks.stream().map(Jdk::id).collect(Collectors.toList()), contains("multihome_17_x64"));
	}

	@Test
	void testMultiHomeProviderNormalizesEnvSuffixInJdkId() {
		clearJavaHomeVariables();

		Path jdkHome = createMockJdkExt(12);
		environmentVariables.set("JAVA_HOME_12_X64_ARM", jdkHome.toString());

		Jdk.InstalledJdk jdk = new MultiHomeJdkProvider().getInstalledByPath(jdkHome);

		assertThat(jdk, notNullValue());
		assertThat(jdk.id(), Matchers.is("multihome_12_x64_arm"));
	}

	private void clearJavaHomeVariables() {
		environmentVariables.getVariables()
			.keySet()
			.stream()
			.filter(key -> key.startsWith("JAVA_HOME_"))
			.forEach(environmentVariables::remove);
	}
}
