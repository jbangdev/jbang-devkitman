package dev.jbang.devkitman;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Test;

import dev.jbang.devkitman.jdkproviders.*;

public class TestJdkProviders extends BaseTest {
	@Test
	void testMinimalNames() {
		assertThat(
				JdkProviders.instance().minimalNames(),
				contains("current", "javahome", "path"));
	}

	@Test
	void testBasicNames() {
		assertThat(
				JdkProviders.instance().basicNames(),
				contains("current", "default", "javahome", "path", "linked", "jbang"));
	}

	@Test
	void testAllNames() {
		assertThat(
				JdkProviders.instance().allNames(),
				contains(
						"current",
						"default",
						"javahome",
						"path",
						"linked",
						"jbang",
						"linux",
						"scoop",
						"sdkman"));
	}

	@Test
	void testMinimal() {
		assertThat(
				JdkProviders.instance().minimal(),
				contains(
						instanceOf(CurrentJdkProvider.class),
						instanceOf(JavaHomeJdkProvider.class),
						instanceOf(PathJdkProvider.class)));
	}

	@Test
	void testBasic() {
		assertThat(
				JdkProviders.instance().basic(config),
				contains(
						instanceOf(CurrentJdkProvider.class),
						instanceOf(DefaultJdkProvider.class),
						instanceOf(JavaHomeJdkProvider.class),
						instanceOf(PathJdkProvider.class),
						instanceOf(LinkedJdkProvider.class),
						instanceOf(JBangJdkProvider.class)));
	}

	@Test
	void testAll() {
		assertThat(
				JdkProviders.instance().all(config),
				contains(
						instanceOf(CurrentJdkProvider.class),
						instanceOf(DefaultJdkProvider.class),
						instanceOf(JavaHomeJdkProvider.class),
						instanceOf(PathJdkProvider.class),
						instanceOf(LinkedJdkProvider.class),
						instanceOf(JBangJdkProvider.class),
						instanceOf(LinuxJdkProvider.class),
						instanceOf(ScoopJdkProvider.class),
						instanceOf(SdkmanJdkProvider.class)));
	}

	@Test
	void testParseNames() {
		String names = "current,default,javahome,path,linked,jbang,linux,scoop,sdkman";
		assertThat(
				JdkProviders.instance().parseNames(config, names),
				contains(
						instanceOf(CurrentJdkProvider.class),
						instanceOf(DefaultJdkProvider.class),
						instanceOf(JavaHomeJdkProvider.class),
						instanceOf(PathJdkProvider.class),
						instanceOf(LinkedJdkProvider.class),
						instanceOf(JBangJdkProvider.class),
						instanceOf(LinuxJdkProvider.class),
						instanceOf(ScoopJdkProvider.class),
						instanceOf(SdkmanJdkProvider.class)));
	}

	@Test
	void testParseNameWithConfig() {
		String name = "jbang;aap=noot;mies=wim";
		assertThat(
				JdkProviders.instance()
							.parseName(
									config,
									name,
									(prov, config) -> {
										assertThat(prov, equalTo("jbang"));
										assertThat(config.properties, hasEntry("aap", "noot"));
										assertThat(config.properties, hasEntry("mies", "wim"));
										return new JBangJdkProvider(
												config.installPath);
									}),
				is(instanceOf(JBangJdkProvider.class)));
	}

	@Test
	void testByName() {
		assertThat(
				JdkProviders.instance().byName("jbang", config),
				is(instanceOf(JBangJdkProvider.class)));
	}
}
