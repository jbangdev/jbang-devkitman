package dev.jbang.devkitman;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.jbang.devkitman.jdkproviders.JBangJdkProvider;

public class TestJdkInstaller extends BaseTest {

	@Test
	void testListAvailable() throws IOException {
		JdkManager jm = jdkManager("jbang");
		List<Jdk.AvailableJdk> jdks = jm.listAvailableJdks();
		assertThat(jdks, hasSize(21));
		assertThat(jdks.get(0).majorVersion(), is(25));
		assertThat(jdks.get(jdks.size() - 1).majorVersion(), is(8));
	}

	@Test
	void testInstallExact() throws IOException {
		JdkManager jm = jdkManager("jbang");
		Jdk.InstalledJdk jdk = jm.getOrInstallJdk("12");
		assertThat(jdk.provider(), instanceOf(JBangJdkProvider.class));
		assertThat(jdk.home().toString(), endsWith(File.separator + "12.0.2-aoj-jbang"));
		assertThat(jdk.home().resolve("release").toFile().exists(), is(true));
	}

	@Test
	void testInstallOpen() throws IOException {
		JdkManager jm = jdkManager("jbang");
		Jdk.InstalledJdk jdk = jm.getOrInstallJdk("12+");
		assertThat(jdk.provider(), instanceOf(JBangJdkProvider.class));
		assertThat(jdk.home().toString(), endsWith(File.separator + "21.0.6+7-temurin-jbang"));
	}
}
