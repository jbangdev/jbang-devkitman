package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
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
	public @NonNull List<Jdk.AvailableJdk> listAvailable() {
		return Arrays.stream(versions)
			.mapToObj(v -> new Jdk.AvailableJdk.Default(this, v + "-dummy", v + ".0.7", null))
			.collect(Collectors.toList());
	}

	@Override
	public Jdk.@NonNull InstalledJdk install(Jdk.@NonNull AvailableJdk jdk) {
		Path jdkPath = mockJdk.apply(jdk.majorVersion());
		return Objects.requireNonNull(createJdk(jdk.id(), jdkPath, jdk.version(), true, null));
	}

	@Override
	public void uninstall(Jdk.@NonNull InstalledJdk jdk) {
		if (jdk.isInstalled()) {
			FileUtils.deletePath(jdk.home());
		}
	}

	@Override
	public boolean canUpdate() {
		return true;
	}
}
