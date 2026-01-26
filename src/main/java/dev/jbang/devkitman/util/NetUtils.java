package dev.jbang.devkitman.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Function;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.cache.CacheConfig;
import org.apache.http.impl.client.cache.CachingHttpClientBuilder;
import org.jspecify.annotations.NonNull;

public class NetUtils {

	public static final RequestConfig DEFAULT_REQUEST_CONFIG = RequestConfig.custom()
		.setConnectionRequestTimeout(10000)
		.setConnectTimeout(10000)
		.setSocketTimeout(30000)
		.build();

	public static Path downloadFromUrl(String url) throws IOException {
		HttpClientBuilder builder = createDefaultHttpClientBuilder();
		return downloadFromUrl(builder, url);
	}

	public static Path downloadFromUrl(HttpClientBuilder builder, String url) throws IOException {
		return requestUrl(builder, url, NetUtils::handleDownloadResult);
	}

	public static <T> T resultFromUrl(String url, Function<InputStream, T> streamToObject)
			throws IOException {
		HttpClientBuilder builder = createDefaultHttpClientBuilder();
		return resultFromUrl(builder, url, streamToObject);
	}

	public static <T> T resultFromUrl(
			HttpClientBuilder builder, String url, Function<InputStream, T> streamToObject)
			throws IOException {
		return requestUrl(
				builder,
				url,
				mimetypeChecker("application/json")
					.andThen(NetUtils::responseStreamer)
					.andThen(streamToObject));
	}

	public static HttpClientBuilder createDefaultHttpClientBuilder() {
		return createCachingHttpClientBuilder(Paths.get("http-cache"));
	}

	public static HttpClientBuilder createCachingHttpClientBuilder(@NonNull Path cacheDir) {
		CacheConfig cacheConfig = CacheConfig.custom().setMaxCacheEntries(1000).build();

		FileHttpCacheStorage cacheStorage = new FileHttpCacheStorage(cacheDir);

		return CachingHttpClientBuilder.create()
			.setCacheConfig(cacheConfig)
			.setHttpCacheStorage(cacheStorage)
			.setDefaultRequestConfig(DEFAULT_REQUEST_CONFIG);
	}

	public static <T> T requestUrl(
			HttpClientBuilder builder, String url, Function<HttpResponse, T> responseHandler)
			throws IOException {
		try (CloseableHttpClient httpClient = builder.build()) {
			HttpGet httpGet = new HttpGet(url);
			try (CloseableHttpResponse response = httpClient.execute(httpGet)) {
				int responseCode = response.getStatusLine().getStatusCode();
				if (responseCode != 200) {
					throw new IOException(
							"Failed to read from URL: "
									+ url
									+ ", response code: #"
									+ responseCode);
				}
				HttpEntity entity = response.getEntity();
				if (entity == null) {
					throw new IOException("Failed to read from URL: " + url + ", no content");
				}
				return responseHandler.apply(response);
			}
		} catch (UncheckedIOException e) {
			throw new IOException("Failed to read from URL: " + url + ", " + e.getMessage(), e);
		}
	}

	private static Function<HttpResponse, HttpResponse> mimetypeChecker(String expectedMimeType) {
		return response -> {
			String mimeType = ContentType.getOrDefault(response.getEntity()).getMimeType();
			if (expectedMimeType != null && !mimeType.equals(expectedMimeType)) {
				throw new RuntimeException("Unexpected MIME type: " + mimeType);
			}
			return response;
		};
	}

	private static InputStream responseStreamer(HttpResponse response) {
		try {
			HttpEntity entity = response.getEntity();
			return entity.getContent();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Path handleDownloadResult(HttpResponse response) {
		try {
			HttpEntity entity = response.getEntity();
			try (InputStream is = entity.getContent()) {
				// TODO implement
				return null;
			}
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
