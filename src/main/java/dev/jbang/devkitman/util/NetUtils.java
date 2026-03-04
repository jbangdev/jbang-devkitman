package dev.jbang.devkitman.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.cache.CacheConfig;
import org.apache.hc.client5.http.impl.cache.CachingHttpClients;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.util.Timeout;
import org.jspecify.annotations.NonNull;

public class NetUtils {

	public static final RequestConfig DEFAULT_REQUEST_CONFIG = RequestConfig.custom()
		.setConnectionRequestTimeout(Timeout.ofMilliseconds(10000))
		.setConnectTimeout(Timeout.ofMilliseconds(10000))
		.setResponseTimeout(Timeout.ofMilliseconds(30000))
		.build();

	public static Path downloadFromUrl(String url) throws IOException {
		HttpClientBuilder builder = createDefaultHttpClientBuilder();
		return downloadFromUrl(builder, url);
	}

	public static Path downloadFromUrl(HttpClientBuilder builder, String url) throws IOException {
		return requestUrl(builder, url, NetUtils::handleDownloadResult);
	}

	public static <T> T resultFromUrl(String url, FunctionWithError<InputStream, T> streamToObject)
			throws IOException {
		HttpClientBuilder builder = createDefaultHttpClientBuilder();
		return resultFromUrl(builder, url, streamToObject);
	}

	public static <T> T resultFromUrl(
			HttpClientBuilder builder, String url, FunctionWithError<InputStream, T> streamToObject)
			throws IOException {
		return requestUrl(
				builder,
				url,
				mimetypeChecker("application/json", "text/plain")
					.andThen(NetUtils::responseStreamer)
					.andThen(is -> streamToObject.apply(is)));
	}

	public static HttpClientBuilder createDefaultHttpClientBuilder() {
		return createCachingHttpClientBuilder(Paths.get("http-cache"));
	}

	public static HttpClientBuilder createCachingHttpClientBuilder(@NonNull Path cacheDir) {
		CacheConfig cacheConfig = CacheConfig.custom()
			.setMaxCacheEntries(1000)
			.setSharedCache(false)
			.build();

		FileHttpCacheStorage cacheStorage = new FileHttpCacheStorage(cacheDir);

		return CachingHttpClients.custom()
			.setCacheConfig(cacheConfig)
			.setHttpCacheStorage(cacheStorage)
			.addResponseInterceptorFirst((response, entity, context) -> {
				// Force cache headers on all 200 OK responses to make them cacheable
				if (response.getCode() == 200) {
					response.setHeader("Cache-Control", "max-age=3600, public");
					if (!response.containsHeader("Date")) {
						response.setHeader("Date", java.time.Instant.now().toString());
					}
				}
			})
			.setDefaultRequestConfig(DEFAULT_REQUEST_CONFIG);
	}

	public static <T> T requestUrl(
			HttpClientBuilder builder, String url, FunctionWithError<ClassicHttpResponse, T> responseHandler)
			throws IOException {
		try (CloseableHttpClient httpClient = builder.build()) {
			HttpGet httpGet = new HttpGet(url);
			return httpClient.execute(httpGet, response -> {
				int responseCode = response.getCode();
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
			});
		} catch (UncheckedIOException e) {
			throw new IOException("Failed to read from URL: " + url + ", " + e.getMessage(), e);
		}
	}

	private static FunctionWithError<ClassicHttpResponse, ClassicHttpResponse> mimetypeChecker(
			String... expectedMimeTypes) {
		return response -> {
			ContentType contentType = ContentType.parse(response.getEntity().getContentType());
			String mimeType = contentType != null ? contentType.getMimeType() : "application/octet-stream";
			if (expectedMimeTypes != null &&
					expectedMimeTypes.length != 0 &&
					!Arrays.asList(expectedMimeTypes).contains(mimeType)) {
				throw new RuntimeException("Unexpected MIME type: " + mimeType);
			}
			return response;
		};
	}

	private static InputStream responseStreamer(ClassicHttpResponse response) {
		try {
			HttpEntity entity = response.getEntity();
			return entity.getContent();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Path handleDownloadResult(ClassicHttpResponse response) {
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
