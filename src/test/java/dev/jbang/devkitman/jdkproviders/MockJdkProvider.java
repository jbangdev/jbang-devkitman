package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.util.JavaUtils;

public class MockJdkProvider extends BaseFoldersJdkProvider {
	protected final Function<String, Path> mockJdk;
	protected final String[] versions;

	@Override
	public @NonNull String description() {
		return "Dummy JDK provider";
	}

	public MockJdkProvider(Path root, Function<String, Path> mockJdk, String... versions) {
		super(root);
		this.mockJdk = mockJdk;
		this.versions = versions;
	}

	@Override
	public @NonNull Stream<Jdk.AvailableJdk> listAvailable() {
		return Arrays.stream(versions)
			.map(v -> new Jdk.AvailableJdk.Default(this, v + "-dummy", v, null));
	}

	@Override
	public Jdk.@NonNull InstalledJdk install(Jdk.@NonNull AvailableJdk jdk) {
		Path jdkPath = mockJdk.apply(jdk.version());
		return Objects.requireNonNull(createJdk(jdk.id(), jdkPath, jdk.version(), null));
	}

	@Override
	public void uninstall(Jdk.@NonNull InstalledJdk jdk) {
		JavaUtils.safeDeleteJdk(jdk.home());
	}

	@Override
	public boolean canUpdate() {
		return true;
	}
}
