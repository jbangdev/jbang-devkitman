package dev.jbang.devkitman.util;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.devkitman.BaseTest;

public class TestRemoteAccessProvider extends BaseTest {

	@Test
	void testDefaultReadJsonFromUrl(@TempDir Path cacheDir) throws IOException {
		RemoteAccessProvider rap = RemoteAccessProvider.createDefaultRemoteAccessProvider(cacheDir);
		String url = "https://raw.githubusercontent.com/jbangdev/jbang-devkitman/refs/heads/main/renovate.json";
		Object json = RemoteAccessProvider.readJsonFromUrl(rap, url, Object.class);
		assertThat(json, instanceOf(Map.class));
	}

	@Test
	void testDefaultCache(@TempDir Path cacheDir) throws IOException {
		RemoteAccessProvider rap = RemoteAccessProvider.createDefaultRemoteAccessProvider(cacheDir);
		String url = "https://raw.githubusercontent.com/jbangdev/jbang-devkitman/refs/heads/main/renovate.json";
		Object json = RemoteAccessProvider.readJsonFromUrl(rap, url, Object.class);
		assertThat(json, instanceOf(Map.class));
		assertThat(Files.exists(cacheDir), is(true));
		assertThat(Files.list(cacheDir).count(), greaterThan(0L));
	}
}
