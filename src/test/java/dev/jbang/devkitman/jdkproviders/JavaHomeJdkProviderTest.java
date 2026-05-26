package dev.jbang.devkitman.jdkproviders;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import dev.jbang.devkitman.BaseTest;
import dev.jbang.devkitman.Jdk;

public class JavaHomeJdkProviderTest extends BaseTest {

	@Test
	void testJavaHomeProviderFindsInstalledJdkByVersionPattern() {
		Path jdkHome = createMockJdkExt(23);
		environmentVariables.set("JAVA_HOME", jdkHome.toString());

		Jdk.InstalledJdk jdk = jdkManager("javahome").getInstalledJdk("23+");

		assertThat(jdk, notNullValue());
		assertThat(jdk.provider(), instanceOf(JavaHomeJdkProvider.class));
		assertThat(jdk.home(), Matchers.is(jdkHome));
		assertThat(jdk.id(), Matchers.is("javahome"));
	}

	@Test
	void testJavaHomeProviderIgnoresInvalidJavaHomePath() throws IOException {
		Path invalidHome = config.cachePath().resolve("invalid-java-home");
		Files.createDirectories(invalidHome);
		environmentVariables.set("JAVA_HOME", invalidHome.toString());

		assertThat(jdkManager("javahome").listInstalledJdks(), empty());
	}
}
