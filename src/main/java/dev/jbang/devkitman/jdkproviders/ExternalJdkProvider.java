package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Path;
import java.util.stream.Stream;
import java.util.zip.CRC32;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import dev.jbang.devkitman.Jdk;

/**
 * This JDK provider is only used to be able to provide Jdk objects for
 * unmanaged external Java installations. It should only be used internally.
 */
public class ExternalJdkProvider extends BaseJdkProvider {

	@Override
	public @NonNull String name() {
		return "external";
	}

	@Override
	public @NonNull String description() {
		return "The JDK that is being used to run the current application.";
	}

	@Override
	public @NonNull Stream<Jdk.InstalledJdk> listInstalled() {
		return Stream.empty();
	}

	@Override
	public Jdk.@Nullable InstalledJdk getInstalledByPath(@NonNull Path jdkPath) {
		// Let's try to create a somewhat unique id based on the path
		CRC32 crc = new CRC32();
		String path = jdkPath.toAbsolutePath().toString();
		crc.update(path.getBytes(), 0, path.length());
		String id = "external-" + Long.toHexString(crc.getValue());
		return createJdk(id, jdkPath);
	}
}
