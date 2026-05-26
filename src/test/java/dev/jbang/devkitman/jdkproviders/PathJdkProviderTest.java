package dev.jbang.devkitman.jdkproviders;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.file.Path;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import dev.jbang.devkitman.BaseTest;
import dev.jbang.devkitman.Jdk;

public class PathJdkProviderTest extends BaseTest {

	@Test
	void testPathProviderFindsInstalledJdkByVersionPattern() {
		Path jdkHome = createMockJdkExt(22);
		environmentVariables.set("PATH", jdkHome.resolve("bin").toString());

		Jdk.InstalledJdk jdk = jdkManager("path").getInstalledJdk("22+");

		assertThat(jdk, notNullValue());
		assertThat(jdk.provider(), instanceOf(PathJdkProvider.class));
		assertThat(jdk.home(), Matchers.is(jdkHome));
		assertThat(jdk.id(), Matchers.is("path"));
	}

	@Test
	void testPathProviderIgnoresPathWithoutJavac() {
		Path jreHome = config.cachePath().resolve("jre17");
		initMockJdkDir(jreHome, "17.0.7", "JAVA_RUNTIME_VERSION", false, false, false, false);
		environmentVariables.set("PATH", jreHome.resolve("bin").toString());

		assertThat(jdkManager("path").listInstalledJdks(), empty());
	}
}
