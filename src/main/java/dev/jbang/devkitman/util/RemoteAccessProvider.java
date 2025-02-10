package dev.jbang.devkitman.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

public interface RemoteAccessProvider {

	Path downloadFromUrl(String url) throws IOException;

	default <T> T resultFromUrl(String url, Function<InputStream, T> streamToObject)
			throws IOException {
		Path file = downloadFromUrl(url);
		try (InputStream is = Files.newInputStream(file)) {
			return streamToObject.apply(is);
		}
	}

	static RemoteAccessProvider createDefaultRemoteAccessProvider() {
		return new DefaultRemoteAccessProvider();
	}

	class DefaultRemoteAccessProvider implements RemoteAccessProvider {
		@Override
		public Path downloadFromUrl(String url) throws IOException {
			return NetUtils.downloadFromUrl(url);
		}

		@Override
		public <T> T resultFromUrl(String url, Function<InputStream, T> streamToObject)
				throws IOException {
			return NetUtils.resultFromUrl(url, streamToObject);
		}
	}
}
