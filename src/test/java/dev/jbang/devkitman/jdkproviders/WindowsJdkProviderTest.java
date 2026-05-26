package dev.jbang.devkitman.jdkproviders;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import dev.jbang.devkitman.BaseTest;
import dev.jbang.devkitman.Jdk;

public class WindowsJdkProviderTest extends BaseTest {
	@Test
	void testWindowsProviderFindsInstalledJdksFromRegistry() {
		Path jdk17 = createMockJdkExt(17);
		Path jdk21 = createMockJdkExt(21);

		WindowsJdkProvider provider = new WindowsJdkProvider(rootKey -> {
			Map<String, Path> homes = new LinkedHashMap<>();
			homes.put("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Development Kit\\17", jdk17);
			homes.put("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\JDK\\21", jdk21);
			return homes;
		});

		List<Jdk.InstalledJdk> installed = provider.listInstalled().collect(Collectors.toList());

		assertThat(installed, hasSize(2));
		assertThat(
				installed.stream().map(Jdk::id).collect(Collectors.toList()),
				containsInAnyOrder("17-windows", "21-windows"));
		Jdk.InstalledJdk resolved = provider.getInstalledById("21-windows");
		assertThat(resolved, notNullValue());
		assertThat(resolved.home(), is(jdk21));
	}

	@Test
	void testWindowsProviderIgnoresInvalidRegistryEntries() {
		Path validJdk = createMockJdkExt(23);
		Path jreHome = config.cachePath().resolve("jre23");
		initMockJdkDir(jreHome, "23.0.7", "JAVA_RUNTIME_VERSION", false, false, false, false);
		Path missingPath = config.cachePath().resolve("missing-jdk");

		WindowsJdkProvider provider = new WindowsJdkProvider(rootKey -> {
			Map<String, Path> homes = new LinkedHashMap<>();
			homes.put("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Development Kit\\23", validJdk);
			homes.put("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Runtime Environment\\23", jreHome);
			homes.put("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Development Kit\\99", missingPath);
			return homes;
		});

		List<Jdk.InstalledJdk> installed = provider.listInstalled().collect(Collectors.toList());

		assertThat(installed, hasSize(1));
		assertThat(installed.get(0).id(), is("23-windows"));
		assertThat(installed.get(0).home(), is(validJdk));
	}

	@Test
	void testWindowsProviderDeduplicatesSameHomeKeepingLongestKey() {
		Path jdk8 = createMockJdk("1.8.0_333-distro-jbang", "1.8.0_333");

		WindowsJdkProvider provider = new WindowsJdkProvider(rootKey -> {
			Map<String, Path> homes = new LinkedHashMap<>();
			homes.put("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Development Kit\\1.8", jdk8);
			homes.put("HKEY_LOCAL_MACHINE\\SOFTWARE\\JavaSoft\\Java Development Kit\\1.8.0_333", jdk8);
			return homes;
		});

		List<Jdk.InstalledJdk> installed = provider.listInstalled().collect(Collectors.toList());

		assertThat(installed, hasSize(1));
		assertThat(installed.get(0).id(), is("1.8.0_333-windows"));
		assertThat(provider.getInstalledById("1.8-windows"), nullValue());
		assertThat(provider.getInstalledById("1.8.0_333-windows"), notNullValue());
	}
}
