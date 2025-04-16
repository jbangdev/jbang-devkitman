package dev.jbang.devkitman;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;

import dev.jbang.devkitman.jdkproviders.JBangJdkProvider;

public class TestJdkInstaller extends BaseTest {

	@Test
	void testInstall() throws IOException {
		JdkManager jm = jdkManager("jbang");
		Jdk jdk = jm.getOrInstallJdk("12");
		assertThat(jdk.provider(), instanceOf(JBangJdkProvider.class));
		assertThat(jdk.home().toString(), endsWith(File.separator + "12"));
	}
}
