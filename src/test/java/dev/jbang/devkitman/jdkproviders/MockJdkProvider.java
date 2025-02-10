package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.util.FileUtils;

public class MockJdkProvider extends BaseFoldersJdkProvider {
	protected final Function<Integer, Path> mockJdk;
	protected final int[] versions;

	@Override
	public @NonNull String description() {
		return "Dummy JDK provider";
	}

	public MockJdkProvider(Path root, Function<Integer, Path> mockJdk, int... versions) {
		super(root);
		this.mockJdk = mockJdk;
		this.versions = versions;
	}

	@Override
	public @NonNull List<Jdk> listAvailable() {
		return Arrays	.stream(versions)
						.mapToObj(v -> createJdk(v + "-dummy", null, v + ".0.7"))
						.collect(Collectors.toList());
	}

	@Override
	public @NonNull Jdk install(@NonNull Jdk jdk) {
		Path jdkPath = mockJdk.apply(jdk.majorVersion());
		return createJdk(jdk.id(), jdkPath, jdk.version());
	}

	@Override
	public void uninstall(@NonNull Jdk jdk) {
		if (jdk.isInstalled()) {
			FileUtils.deletePath(jdk.home());
		}
	}

	@Override
	public boolean canUpdate() {
		return true;
	}
}
