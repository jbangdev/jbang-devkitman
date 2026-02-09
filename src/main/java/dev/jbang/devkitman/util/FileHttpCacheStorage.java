package dev.jbang.devkitman.util;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.apache.hc.client5.http.cache.HttpCacheCASOperation;
import org.apache.hc.client5.http.cache.HttpCacheEntry;
import org.apache.hc.client5.http.cache.HttpCacheStorage;
import org.apache.hc.client5.http.cache.ResourceIOException;

public class FileHttpCacheStorage implements HttpCacheStorage {

	private final Path cacheDir;

	public FileHttpCacheStorage(Path cacheDir) {
		this.cacheDir = cacheDir;
	}

	@Override
	public synchronized void putEntry(String key, HttpCacheEntry entry) throws ResourceIOException {
		try {
			Files.createDirectories(cacheDir);
		} catch (IOException e) {
			throw new RuntimeException("Failed to create cache directory", e);
		}
		Path filePath = cacheDir.resolve(encodeKey(key));
		try (ObjectOutputStream oos = new ObjectOutputStream(
				new BufferedOutputStream(Files.newOutputStream(filePath)))) {
			oos.writeObject(entry);
		} catch (IOException e) {
			throw new ResourceIOException("Failed to write cache entry", e);
		}
	}

	@Override
	public synchronized HttpCacheEntry getEntry(String key) throws ResourceIOException {
		Path filePath = cacheDir.resolve(encodeKey(key));
		if (!Files.exists(filePath)) {
			return null;
		}
		try (ObjectInputStream ois = new ObjectInputStream(
				new BufferedInputStream(Files.newInputStream(filePath)))) {
			return (HttpCacheEntry) ois.readObject();
		} catch (IOException | ClassNotFoundException e) {
			throw new ResourceIOException("Failed to read cache entry", e);
		}
	}

	@Override
	public synchronized void removeEntry(String key) {
		Path filePath = cacheDir.resolve(encodeKey(key));
		try {
			Files.deleteIfExists(filePath);
		} catch (IOException e) {
			// Ignore errors on removal
		}
	}

	@Override
	public synchronized void updateEntry(String key, HttpCacheCASOperation operation) throws ResourceIOException {
		HttpCacheEntry existingEntry = getEntry(key);
		HttpCacheEntry updatedEntry = operation.execute(existingEntry);
		if (updatedEntry != null) {
			putEntry(key, updatedEntry);
		}
	}

	@Override
	public synchronized Map<String, HttpCacheEntry> getEntries(Collection<String> keys) throws ResourceIOException {
		Map<String, HttpCacheEntry> result = new HashMap<>();
		for (String key : keys) {
			HttpCacheEntry entry = getEntry(key);
			if (entry != null) {
				result.put(key, entry);
			}
		}
		return result;
	}

	private String encodeKey(String key) {
		int p = key.indexOf("https://");
		if (p == -1) {
			p = key.indexOf("http://");
		}
		if (p != -1) {
			String hap = key.substring(p);
			p = hap.indexOf("?");
			if (p != -1) {
				hap = hap.substring(0, p);
			}
			String encoded = hap.replaceAll("[^a-zA-Z0-9-_]", "_");
			if (encoded.length() > 100) {
				encoded = encoded.substring(0, 100);
			}
			encoded = encoded + "_" + Integer.toHexString(key.hashCode()) + ".cache";
			return encoded;
		} else {
			return Integer.toHexString(key.hashCode()) + ".cache";
		}
	}
}
