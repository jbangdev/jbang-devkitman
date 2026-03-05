package dev.jbang.devkitman.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.Strictness;

public interface RemoteAccessProvider {

	Path downloadFromUrl(String url) throws IOException;

	default <T> T resultFromUrl(String url, FunctionWithError<InputStream, T> streamToObject)
			throws IOException {
		Path file = downloadFromUrl(url);
		try (InputStream is = Files.newInputStream(file)) {
			return streamToObject.apply(is);
		}
	}

	static <T> T readJsonFromUrl(RemoteAccessProvider rap, String url, Class<T> klass) throws IOException {
		return rap.resultFromUrl(url, is -> {
			try (InputStream ignored = is) {
				Gson parser = new GsonBuilder().setStrictness(Strictness.LENIENT).create();
				return parser.fromJson(new InputStreamReader(is), klass);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		});
	}

	static RemoteAccessProvider createDefaultRemoteAccessProvider() {
		return new DefaultRemoteAccessProvider();
	}

	static RemoteAccessProvider createDefaultRemoteAccessProvider(Path cacheDir) {
		return new DefaultRemoteAccessProvider(cacheDir);
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

		public DefaultRemoteAccessProvider(Path cacheDir) {
			this.clientBuilder = NetUtils.createCachingHttpClientBuilder(cacheDir);
		}

		public DefaultRemoteAccessProvider(HttpClientBuilder clientBuilder) {
			this.clientBuilder = clientBuilder;
		}

		@Override
		public Path downloadFromUrl(String url) throws IOException {
			return NetUtils.downloadFromUrl(clientBuilder, url);
		}

		@Override
		public <T> T resultFromUrl(String url, FunctionWithError<InputStream, T> streamToObject)
				throws IOException {
			return NetUtils.resultFromUrl(clientBuilder, url, streamToObject);
		}
	}
}
