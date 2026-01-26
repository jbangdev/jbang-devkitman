package dev.jbang.devkitman.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Function;

import org.apache.http.impl.client.HttpClientBuilder;

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

	static RemoteAccessProvider createDefaultRemoteAccessProvider(HttpClientBuilder clientBuilder) {
		if (clientBuilder != null) {
			return new DefaultRemoteAccessProvider(clientBuilder);
		} else {
			return new DefaultRemoteAccessProvider();
		}
	}

	class DefaultRemoteAccessProvider implements RemoteAccessProvider {
		private final HttpClientBuilder clientBuilder;

		public DefaultRemoteAccessProvider() {
			this.clientBuilder = NetUtils.createDefaultHttpClientBuilder();
		}

		public DefaultRemoteAccessProvider(HttpClientBuilder clientBuilder) {
			this.clientBuilder = clientBuilder;
		}

		@Override
		public Path downloadFromUrl(String url) throws IOException {
			return NetUtils.downloadFromUrl(clientBuilder, url);
		}

		@Override
		public <T> T resultFromUrl(String url, Function<InputStream, T> streamToObject)
				throws IOException {
			return NetUtils.resultFromUrl(clientBuilder, url, streamToObject);
		}
	}
}
