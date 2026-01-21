package dev.jbang.devkitman;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.devkitman.jdkinstallers.FoojayJdkInstaller;
import dev.jbang.devkitman.jdkinstallers.MetadataJdkInstaller;

public class TestJdkInstallers extends BaseTest {
	private JdkInstallers.Discovery.Config iconfig;

	@BeforeEach
	protected void initInstallerEnv(@TempDir Path tempPath) throws IOException {
		iconfig = JdkInstallers.config(createJbangProvider(), java.util.Collections.emptyMap());
	}

	@Test
	void testAllNames() {
		assertThat(
				JdkInstallers.instance().allNames(),
				contains(
						"foojay",
						"metadata"));
	}

	@Test
	void testAll() {
		assertThat(
				JdkInstallers.instance().all(iconfig),
				contains(
						instanceOf(FoojayJdkInstaller.class),
						instanceOf(MetadataJdkInstaller.class)));
	}

	@Test
	void testParseNames() {
		String names = "foojay,metadata";
		assertThat(
				JdkInstallers.instance().parseNames(iconfig, names),
				contains(
						instanceOf(FoojayJdkInstaller.class),
						instanceOf(MetadataJdkInstaller.class)));
	}

	@Test
	void testParseNameWithConfig() {
		String name = "foojay;aap=noot;mies=wim";
		assertThat(
				JdkInstallers.instance()
					.parseName(
							iconfig,
							name,
							(prov, config) -> {
								assertThat(prov, equalTo("foojay"));
								assertThat(config.properties(), hasEntry("aap", "noot"));
								assertThat(config.properties(), hasEntry("mies", "wim"));
								return new FoojayJdkInstaller(createJbangProvider());
							}),
				is(instanceOf(FoojayJdkInstaller.class)));
	}

	@Test
	void testByName() {
		assertThat(
				JdkInstallers.instance().byName("foojay", iconfig),
				is(instanceOf(FoojayJdkInstaller.class)));
	}
}
