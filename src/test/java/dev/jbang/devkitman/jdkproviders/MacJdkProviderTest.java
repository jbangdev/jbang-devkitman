package dev.jbang.devkitman.jdkproviders;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import dev.jbang.devkitman.BaseTest;
import dev.jbang.devkitman.Jdk;

public class MacJdkProviderTest extends BaseTest {

	private Path createMockMacJdkBundle(Path jvmRoot, String bundleName, int version) throws IOException {
		Path bundlePath = jvmRoot.resolve(bundleName + ".jdk");
		Path home = bundlePath.resolve("Contents/Home");
		Files.createDirectories(home);
		initMockJdkDir(home, version + ".0.7");
		return home;
	}

	@Test
	void testMacProviderFindsInstalledJdks() throws IOException {
		Path jvmRoot = config.cachePath().resolve("JavaVirtualMachines");
		Files.createDirectories(jvmRoot);

		createMockMacJdkBundle(jvmRoot, "temurin-17", 17);
		createMockMacJdkBundle(jvmRoot, "temurin-21", 21);

		MacJdkProvider provider = new MacJdkProvider(jvmRoot);

		List<Jdk.InstalledJdk> installed = provider.listInstalled().collect(Collectors.toList());

		assertThat(installed, hasSize(2));
		List<String> ids = installed.stream().map(Jdk::id).toList();
		assertThat(ids.contains("temurin-17"), is(true));
		assertThat(ids.contains("temurin-21"), is(true));
	}

	@Test
	void testMacProviderIgnoresJreOnlyBundles() throws IOException {
		Path jvmRoot = config.cachePath().resolve("JavaVirtualMachines");
		Files.createDirectories(jvmRoot);

		Path home21 = createMockMacJdkBundle(jvmRoot, "temurin-21", 21);

		// Create a JRE bundle (no javac)
		Path jreBundle = jvmRoot.resolve("corretto-jre-11.jre");
		Path jreHome = jreBundle.resolve("Contents/Home");
		Files.createDirectories(jreHome);
		initMockJdkDir(jreHome, "11.0.7", "JAVA_RUNTIME_VERSION", false, false, false, false);

		MacJdkProvider provider = new MacJdkProvider(jvmRoot);

		List<Jdk.InstalledJdk> installed = provider.listInstalled().collect(Collectors.toList());

		assertThat(installed, hasSize(1));
		assertThat(installed.get(0).id(), is("temurin-21"));
		assertThat(installed.get(0).home(), is(home21));
	}

	@Test
	void testMacProviderJdkId() throws IOException {
		Path jvmRoot = config.cachePath().resolve("JavaVirtualMachines");
		Files.createDirectories(jvmRoot);

		createMockMacJdkBundle(jvmRoot, "zulu-17.0.10", 17);

		MacJdkProvider provider = new MacJdkProvider(jvmRoot);

		List<Jdk.InstalledJdk> installed = provider.listInstalled().collect(Collectors.toList());

		assertThat(installed, hasSize(1));
		assertThat(installed.get(0).id(), is("zulu-17.0.10"));
	}
}
