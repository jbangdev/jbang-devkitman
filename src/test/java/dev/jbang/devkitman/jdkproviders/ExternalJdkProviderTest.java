package dev.jbang.devkitman.jdkproviders;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;

import java.nio.file.Path;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import dev.jbang.devkitman.BaseTest;
import dev.jbang.devkitman.Jdk;

public class ExternalJdkProviderTest extends BaseTest {

	@Test
	void testExternalProviderCreatesStableIdForSamePath() {
		Path jdkHome = createMockJdkExt(24);
		ExternalJdkProvider provider = new ExternalJdkProvider();

		Jdk.InstalledJdk jdk1 = provider.getInstalledByPath(jdkHome);
		Jdk.InstalledJdk jdk2 = provider.getInstalledByPath(jdkHome.toAbsolutePath());

		assertThat(jdk1, notNullValue());
		assertThat(jdk2, notNullValue());
		assertThat(jdk1.provider(), instanceOf(ExternalJdkProvider.class));
		assertThat(jdk1.home(), Matchers.is(jdkHome));
		assertThat(jdk1.id(), Matchers.startsWith("external-"));
		assertThat(jdk1.id(), Matchers.is(jdk2.id()));
	}

	@Test
	void testExternalProviderCreatesDifferentIdsForDifferentPaths() {
		Path jdkHome1 = createMockJdkExt(25);
		Path jdkHome2 = createMockJdkExt(26);
		ExternalJdkProvider provider = new ExternalJdkProvider();

		Jdk.InstalledJdk jdk1 = provider.getInstalledByPath(jdkHome1);
		Jdk.InstalledJdk jdk2 = provider.getInstalledByPath(jdkHome2);

		assertThat(jdk1, notNullValue());
		assertThat(jdk2, notNullValue());
		assertThat(jdk1.id(), not(jdk2.id()));
	}
}
