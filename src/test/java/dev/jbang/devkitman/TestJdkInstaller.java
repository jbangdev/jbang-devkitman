package dev.jbang.devkitman;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

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
					if (!url.startsWith(FoojayJdkInstaller.FOOJAY_JDK_VERSIONS_URL)) {
						throw new IOException("Unexpected URL: " + url);
					}
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

	protected JdkManager jbangJdkManager(RemoteAccessProvider rap) {
		JBangJdkProvider jbang = new JBangJdkProvider(config.installPath);
		FoojayJdkInstaller installer = new FoojayJdkInstaller(jbang);
		installer.remoteAccessProvider(rap);
		jbang.installer(installer);
		return JdkManager.builder().providers(jbang).build();
	}
}
