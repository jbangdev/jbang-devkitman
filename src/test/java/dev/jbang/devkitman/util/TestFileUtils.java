package dev.jbang.devkitman.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import dev.jbang.devkitman.BaseTest;

public class TestFileUtils extends BaseTest {

	@Test
	void testDeletePath() {
		Path jdk = createMockJdk(11);
		assertTrue(Files.exists(jdk));
		FileUtils.deletePath(jdk);
		assertTrue(Files.notExists(jdk));
	}
}
