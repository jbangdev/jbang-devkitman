package dev.jbang.devkitman.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.jbang.devkitman.BaseTest;

public class TestFileUtils extends BaseTest {
	@Test
	void testRelativizeLinkTargetWithinLinkParent(@TempDir Path root) {
		Path link = root.resolve("current");
		Path target = root.resolve("jdks").resolve("21");

		assertEquals(root.relativize(target), FileUtils.relativizeLinkTarget(link, target));
	}

	@Test
	void testRelativizeLinkTargetOutsideLinkParent(@TempDir Path root) {
		Path link = root.resolve("links").resolve("current");
		Path target = root.resolve("jdks").resolve("21");

		assertEquals(target.toAbsolutePath(), FileUtils.relativizeLinkTarget(link, target));
	}

	@Test
	void testDeletePath() {
		Path jdk = createMockJdk(11);
		assertTrue(Files.exists(jdk));
		FileUtils.deletePath(jdk);
		assertTrue(Files.notExists(jdk));
	}
}
