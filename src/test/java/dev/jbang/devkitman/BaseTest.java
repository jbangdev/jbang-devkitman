package dev.jbang.devkitman;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.logging.*;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.devkitman.jdkinstallers.FoojayJdkInstaller;
import dev.jbang.devkitman.jdkproviders.JBangJdkProvider;
import dev.jbang.devkitman.jdkproviders.MockJdkProvider;
import dev.jbang.devkitman.util.FileUtils;
import dev.jbang.devkitman.util.RemoteAccessProvider;

import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
public class BaseTest {
	protected JdkDiscovery.Config config;

	@SystemStub
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	private static Path testJdkFile;

	@BeforeAll
	protected static void initAll(@TempDir Path tempPath) throws IOException {
		// Force the logging level to FINE
		Logger root = LogManager.getLogManager().getLogger("");
		root.setLevel(Level.FINE);
		Handler consoleHandler = null;
		for (Handler handler : root.getHandlers()) {
			if (handler instanceof ConsoleHandler) {
				consoleHandler = handler;
				break;
			}
		}
		if (consoleHandler == null) {
			consoleHandler = new ConsoleHandler();
			root.addHandler(new ConsoleHandler());
		}
		consoleHandler.setLevel(Level.FINE);

		testJdkFile = tempPath.resolve("jdk-12.zip");
		Files.copy(
				BaseTest.class.getResourceAsStream("/jdk-12.zip"),
				testJdkFile,
				java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	@BeforeEach
	protected void initEnv(@TempDir Path tempPath) throws IOException {
		System.setProperty("user.home", tempPath.resolve("home").toString());
		config = new JdkDiscovery.Config(tempPath.resolve("jdks"), null, null);
	}

	protected JdkManager jdkManager() {
		return jdkManager("default", "linked", "jbang");
	}

	protected JdkManager jdkManager(String... providerNames) {
		List<JdkProvider> providers = JdkProviders.instance()
			.parseNames(config, providerNames)
			.stream()
			.map(p -> {
				if (p instanceof JBangJdkProvider) {
					return createJbangProvider();
				} else {
					return p;
				}
			})
			.collect(Collectors.toList());

		return JdkManager.builder()
			.providers(providers)
			.build();
	}

	protected JdkManager mockJdkManager(int... versions) {
		return mockJdkManager(this::createMockJdk, versions);
	}

	protected JdkManager mockJdkManager(Function<Integer, Path> mockJdk, int... versions) {
		return JdkManager.builder()
			.providers(new MockJdkProvider(config.installPath(), mockJdk, versions))
			.build();
	}

	protected Path createMockJdk(int jdkVersion) {
		return createMockJdk(jdkVersion, this::initMockJdkDir);
	}

	protected Path createMockJdkRuntime(int jdkVersion) {
		return createMockJdk(jdkVersion, this::initMockJdkDirRuntime);
	}

	protected Path createMockJdk(int jdkVersion, BiConsumer<Path, String> init) {
		Path jdkPath = config.installPath().resolve(String.valueOf(jdkVersion));
		init.accept(jdkPath, jdkVersion + ".0.7");
		Path link = config.installPath().resolve("default");
		if (!Files.exists(link)) {
			FileUtils.createLink(link, jdkPath);
		}
		return jdkPath;
	}

	protected Path createMockJdkExt(int jdkVersion) {
		Path jdkPath = config.cachePath().resolve("jdk" + jdkVersion);
		FileUtils.mkdirs(jdkPath);
		initMockJdkDir(jdkPath, jdkVersion + ".0.7");
		return jdkPath;
	}

	protected void initMockJdkDirRuntime(Path jdkPath, String version) {
		initMockJdkDir(jdkPath, version, "JAVA_RUNTIME_VERSION", true, false, false, false);
	}

	protected void initMockJdkDir(Path jdkPath, String version) {
		initMockJdkDir(jdkPath, version, "JAVA_VERSION", true, false, false, false);
	}

	protected void initMockJdkDir(Path jdkPath, String version, String key, boolean isJdk, boolean isGraalVM,
			boolean hasNativeCmd, boolean hasJavaFX) {
		try {
			Path jdkBinPath = jdkPath.resolve("bin");
			Files.createDirectories(jdkBinPath);
			String releaseText = "";
			String rawJavaVersion = key + "=\"" + version + "\"\n";
			releaseText += rawJavaVersion;
			Path release = jdkPath.resolve("release");
			Path javaPath = jdkBinPath.resolve("java");
			writeString(javaPath, "dummy");
			javaPath.toFile().setExecutable(true, true);
			writeString(jdkBinPath.resolve("java.exe"), "dummy");
			if (isJdk) {
				Path javacPath = jdkBinPath.resolve("javac");
				writeString(javacPath, "dummy");
				javacPath.toFile().setExecutable(true, true);
				writeString(jdkBinPath.resolve("javac.exe"), "dummy");
				if (isGraalVM) {
					String rawGraalVMVersion = "GRAALVM_VERSION=\"" + version + "\"\n";
					releaseText += rawGraalVMVersion;
					if (hasNativeCmd) {
						Path nativePath = jdkBinPath.resolve("native-image");
						writeString(nativePath, "dummy");
						nativePath.toFile().setExecutable(true, true);
						writeString(jdkBinPath.resolve("native-image.exe"), "dummy");
					}
				}
			}
			if (hasJavaFX) {
				Path jdkLibPath = jdkPath.resolve("lib");
				Files.createDirectories(jdkLibPath);
				String rawJavaFXVersion = "javafx.version=" + version;
				Path jfxprops = jdkLibPath.resolve("javafx.properties");
				writeString(jfxprops, rawJavaFXVersion);
			}
			writeString(release, releaseText);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	protected JBangJdkProvider createJbangProvider() {
		RemoteAccessProvider rap = new RemoteAccessProvider() {
			@Override
			public Path downloadFromUrl(String url) throws IOException {
				if (!url.startsWith("https://api.foojay.io/disco/v3.0/ids/") || !url.endsWith("/redirect")) {
					throw new IOException("Unexpected URL: " + url);
				}
				return testJdkFile;
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

		JBangJdkProvider jbang = new JBangJdkProvider(config.installPath());
		FoojayJdkInstaller installer = new FoojayJdkInstaller(jbang, jbang::jdkId);
		installer.remoteAccessProvider(rap);
		jbang.installer(installer);
		return jbang;
	}

	protected void writeString(Path toPath, String scriptText) throws IOException {
		Files.write(toPath, scriptText.getBytes());
	}
}
