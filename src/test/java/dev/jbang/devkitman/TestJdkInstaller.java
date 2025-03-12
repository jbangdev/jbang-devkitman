package dev.jbang.devkitman;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import dev.jbang.devkitman.jdkinstallers.FoojayJdkInstaller;
import dev.jbang.devkitman.jdkproviders.JBangJdkProvider;
import dev.jbang.devkitman.util.FileUtils;
import dev.jbang.devkitman.util.RemoteAccessProvider;

public class TestJdkInstaller extends BaseTest {

	@Test
	void testInstall() throws IOException {
		Path tmpJdk = Files.createTempFile("junit-test-jdk", ".zip");
		try {
			Files.copy(
					getClass().getResourceAsStream("/jdk-12.zip"),
					tmpJdk,
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);

			RemoteAccessProvider rap = new RemoteAccessProvider() {
				@Override
				public Path downloadFromUrl(String url) throws IOException {
					if (!url.startsWith("https://api.foojay.io/disco/v3.0/directuris?")) {
						throw new IOException("Unexpected URL: " + url);
					}
					return tmpJdk;
				}

				@Override
				public <T> T resultFromUrl(
						String url, Function<InputStream, T> streamToObject)
						throws IOException {
					assertThat(url, startsWith(FoojayJdkInstaller.FOOJAY_JDK_VERSIONS_URL));
					return streamToObject.apply(
							getClass().getResourceAsStream("/testInstall.json"));
				}
			};

			JdkManager jm = jbangJdkManager(rap);
			Jdk jdk = jm.getOrInstallJdk("12");
			assertThat(jdk.provider(), instanceOf(JBangJdkProvider.class));
			assertThat(jdk.home().toString(), endsWith(File.separator + "12"));
		} finally {
			FileUtils.deletePath(tmpJdk);
		}
	}

	@Test
	void testDistros() throws IOException {
		RemoteAccessProvider rap = new RemoteAccessProvider() {
			@Override
			public Path downloadFromUrl(String url) throws IOException {
				throw new IOException("We shouldn't get here");
			}

			@Override
			public <T> T resultFromUrl(
					String url, Function<InputStream, T> streamToObject) throws UnsupportedEncodingException {
				assertThat(url, is(FoojayJdkInstaller.FOOJAY_JDK_DISTROS_URL));
				return streamToObject.apply(
						getClass().getResourceAsStream("/testDistros.json"));
			}
		};

		JdkManager jm = jbangJdkManager(rap);
		Set<Distro> distros = jm.listAvailableDistros();
		assertThat(distros, hasSize(33));
		assertThat(distros.stream().filter(d -> d.isGraalVM).collect(Collectors.toList()), hasSize(11));
	}

	@Test
	void testAvailable() throws IOException {
		RemoteAccessProvider rap = new RemoteAccessProvider() {
			@Override
			public Path downloadFromUrl(String url) throws IOException {
				throw new IOException("We shouldn't get here");
			}

			@Override
			public <T> T resultFromUrl(
					String url, Function<InputStream, T> streamToObject) throws UnsupportedEncodingException {
				String u = URLDecoder.decode(url, "UTF8");
				assertThat(url, startsWith(FoojayJdkInstaller.FOOJAY_JDK_VERSIONS_URL));
				assertThat(u, containsString("distro=temurin,aoj"));
				assertThat(u, containsString("javafx_bundled=false"));
				assertThat(u, containsString("package_type=jdk"));
				assertThat(u, containsString("release_status=ga,ea"));
				return streamToObject.apply(
						getClass().getResourceAsStream("/testInstall.json"));
			}
		};

		JdkManager jm = jbangJdkManager(rap);
		List<Jdk> jdks = jm.listAvailableJdks();
		assertThat(jdks, hasSize(18));
	}

	@Test
	void testAvailableFiltered() throws IOException {
		RemoteAccessProvider rap = new RemoteAccessProvider() {
			@Override
			public Path downloadFromUrl(String url) throws IOException {
				throw new IOException("We shouldn't get here");
			}

			@Override
			public <T> T resultFromUrl(
					String url, Function<InputStream, T> streamToObject) throws UnsupportedEncodingException {
				String u = URLDecoder.decode(url, "UTF8");
				assertThat(url, startsWith(FoojayJdkInstaller.FOOJAY_JDK_VERSIONS_URL));
				assertThat(u, containsString("distro=temurin"));
				assertThat(u, containsString("javafx_bundled=true"));
				assertThat(u, containsString("package_type=jre"));
				assertThat(u, containsString("release_status=ea"));
				return streamToObject.apply(
						getClass().getResourceAsStream("/testInstall.json"));
			}
		};

		JdkManager jm = jbangJdkManager(rap);
		HashSet<String> tags = new HashSet<>();
		tags.add("jre");
		tags.add("javafx");
		tags.add("ea");
		List<Jdk> jdks = jm.listAvailableJdks("temurin", tags);
		assertThat(jdks, hasSize(0));
	}

	@Test
	void testAvailableFiltered2() throws IOException {
		RemoteAccessProvider rap = new RemoteAccessProvider() {
			@Override
			public Path downloadFromUrl(String url) throws IOException {
				throw new IOException("We shouldn't get here");
			}

			@Override
			public <T> T resultFromUrl(
					String url, Function<InputStream, T> streamToObject) throws UnsupportedEncodingException {
				String u = URLDecoder.decode(url, "UTF8");
				assertThat(url, startsWith(FoojayJdkInstaller.FOOJAY_JDK_VERSIONS_URL));
				assertThat(u, containsString("distro=temurin"));
				assertThat(u, containsString("javafx_bundled=false"));
				assertThat(u, containsString("package_type=jdk"));
				assertThat(u, containsString("release_status=ea"));
				return streamToObject.apply(
						getClass().getResourceAsStream("/testInstall.json"));
			}
		};

		JdkManager jm = jbangJdkManager(rap);
		HashSet<String> tags = new HashSet<>();
		tags.add("ea");
		List<Jdk> jdks = jm.listAvailableJdks("temurin", tags);
		assertThat(jdks, hasSize(2));
	}

	protected JdkManager jbangJdkManager(RemoteAccessProvider rap) {
		JBangJdkProvider jbang = new JBangJdkProvider(config.installPath);
		FoojayJdkInstaller installer = new FoojayJdkInstaller(jbang);
		installer.remoteAccessProvider(rap);
		jbang.installer(installer);
		return JdkManager.builder().providers(jbang).build();
	}
}
