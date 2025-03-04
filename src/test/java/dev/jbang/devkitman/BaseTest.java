package dev.jbang.devkitman;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.BiConsumer;
import java.util.logging.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.devkitman.jdkproviders.MockJdkProvider;
import dev.jbang.devkitman.util.FileUtils;

import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

@ExtendWith(SystemStubsExtension.class)
public class BaseTest {
	protected JdkDiscovery.Config config;

	@SystemStub
	public final EnvironmentVariables environmentVariables = new EnvironmentVariables();

	@BeforeAll
	protected static void initAll() {
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
	}

	@BeforeEach
	protected void initEnv(@TempDir Path tempPath) throws IOException {
		System.setProperty("user.home", tempPath.resolve("home").toString());
		config = new JdkDiscovery.Config(tempPath.resolve("jdks"), null, null);
	}

	// TODO Remove once https://github.com/junit-team/junit5/issues/4299 is released
	@AfterEach
	protected void cleanupEnv() throws IOException {
		FileUtils.deletePath(config.installPath);
	}

	protected JdkManager jdkManager() {
		return jdkManager("default", "linked", "jbang");
	}

	protected JdkManager jdkManager(String... providerNames) {
		return JdkManager.builder()
			.providers(JdkProviders.instance().parseNames(config, providerNames))
			.build();
	}

	protected JdkManager mockJdkManager(int... providerNames) {
		return JdkManager.builder()
			.providers(
					new MockJdkProvider(config.installPath, this::createMockJdk, providerNames))
			.build();
	}

	protected Path createMockJdk(int jdkVersion) {
		return createMockJdk(jdkVersion, this::initMockJdkDir);
	}

	protected Path createMockJdkRuntime(int jdkVersion) {
		return createMockJdk(jdkVersion, this::initMockJdkDirRuntime);
	}

	protected Path createMockJdk(int jdkVersion, BiConsumer<Path, String> init) {
		Path jdkPath = config.installPath.resolve(String.valueOf(jdkVersion));
		init.accept(jdkPath, jdkVersion + ".0.7");
		Path link = config.installPath.resolve("default");
		if (!Files.exists(link)) {
			FileUtils.createLink(link, jdkPath);
		}
		return jdkPath;
	}

	protected void initMockJdkDirRuntime(Path jdkPath, String version) {
		initMockJdkDir(jdkPath, version, "JAVA_RUNTIME_VERSION");
	}

	protected void initMockJdkDir(Path jdkPath, String version) {
		initMockJdkDir(jdkPath, version, "JAVA_VERSION");
	}

	protected void initMockJdkDir(Path jdkPath, String version, String key) {
		try {
			Path jdkBinPath = jdkPath.resolve("bin");
			Files.createDirectories(jdkBinPath);
			String rawJavaVersion = key + "=\"" + version + "\"";
			Path release = jdkPath.resolve("release");
			Path javacPath = jdkBinPath.resolve("javac");
			writeString(javacPath, "dummy");
			javacPath.toFile().setExecutable(true, true);
			writeString(jdkBinPath.resolve("javac.exe"), "dummy");
			writeString(release, rawJavaVersion);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	protected void writeString(Path toPath, String scriptText) throws IOException {
		Files.write(toPath, scriptText.getBytes());
	}
}
