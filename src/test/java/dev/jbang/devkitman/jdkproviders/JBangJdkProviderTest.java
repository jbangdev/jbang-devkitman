package dev.jbang.devkitman.jdkproviders;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import dev.jbang.devkitman.BaseTest;
import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.util.FileUtils;

public class JBangJdkProviderTest extends BaseTest {

	@Test
	void testJBangProviderIgnoresNumericLinkWithoutJavac() throws IOException {
		// Create a directory without javac
		Path jreHome = config.cachePath().resolve("jre17");
		initMockJdkDir(jreHome, "17.0.7", "JAVA_RUNTIME_VERSION", false, false, false, false);

		// Create a symbolic link with numeric name pointing to the directory without
		// javac
		Path numericLink = config.installPath().resolve("17");
		FileUtils.createLink(numericLink, jreHome);

		// Verify the link exists and points to a directory without javac
		assertThat("Link should exist", Files.exists(numericLink), Matchers.is(true));
		assertThat("Target should not have javac", FileUtils.isLink(numericLink), Matchers.is(true));

		// The JBang provider should not accept this folder since it has no javac
		JBangJdkProvider provider = createJbangProvider();
		Jdk.InstalledJdk jdk = provider.getInstalledByPath(numericLink);

		// This should be null because the link points to a directory without javac
		assertThat("Numeric link without javac should not be accepted", jdk, Matchers.nullValue());
	}

	@Test
	void testJBangProviderAcceptsNumericFolderWithJavac() throws IOException {
		// Create a directory with numeric name and javac (backwards compatibility
		// scenario)
		Path jreHome = config.installPath().resolve("17");
		initMockJdkDir(jreHome, "17.0.7", "JAVA_VERSION", true, false, false, false);

		// Verify it's not a link
		assertThat("Should be a regular folder", FileUtils.isLink(jreHome), Matchers.is(false));

		// The JBang provider should accept this folder since it has javac
		JBangJdkProvider provider = createJbangProvider();
		Jdk.InstalledJdk jdk = provider.getInstalledByPath(jreHome);

		assertThat("Numeric folder with javac should be accepted", jdk, notNullValue());
		assertThat(jdk.version(), Matchers.is("17.0.7"));
	}

	@Test
	void testJBangProviderIgnoresNumericFolderWithoutJavac() throws IOException {
		// Create a directory with numeric name but without javac
		Path jreHome = config.installPath().resolve("17");
		initMockJdkDir(jreHome, "17.0.7", "JAVA_RUNTIME_VERSION", false, false, false, false);

		// Verify it's not a link and doesn't have javac
		assertThat("Should be a regular folder", FileUtils.isLink(jreHome), Matchers.is(false));

		// The JBang provider should not accept this folder since it has no javac
		JBangJdkProvider provider = createJbangProvider();
		Jdk.InstalledJdk jdk = provider.getInstalledByPath(jreHome);

		// This should be null because the folder has no javac, despite having a numeric
		// name
		assertThat("Numeric folder without javac should not be accepted", jdk, Matchers.nullValue());
	}
}
