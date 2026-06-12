package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.JdkDiscovery;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.devkitman.util.FileUtils;

/**
 * This JDK provider is intended to detects JDKs that have been installed in
 * standard location of the users linux distro.
 *
 * <p>
 * For now just using `/usr/lib/jvm` as apparently fedora, debian, ubuntu and
 * centos/rhel use it.
 *
 * <p>
 * If need different behavior per linux distro its intended this provider will
 * adjust based on identified distro.
 */
public class LinuxJdkProvider extends BaseFoldersJdkProvider {
	private static final Path JDKS_ROOT = Paths.get("/usr/lib/jvm");

	public LinuxJdkProvider() {
		this(jdksRoot());
	}

	LinuxJdkProvider(@NonNull Path jdksRoot) {
		super(jdksRoot);
	}

	public static Path jdksRoot() {
		return JDKS_ROOT;
	}

	@Override
	public @NonNull String description() {
		return "The JDKs installed in the standard location of a Linux distro.";
	}

	@Override
	protected boolean acceptFolder(@NonNull Path jdkFolder) {
		return super.acceptFolder(jdkFolder) && !FileUtils.isLink(jdkFolder);
	}

	public static class Discovery implements JdkDiscovery {
		public static final String PROVIDER_ID = "linux";

		@Override
		@NonNull
		public String name() {
			return PROVIDER_ID;
		}

		@Override
		public JdkProvider create(@NonNull Config config) {
			return new LinuxJdkProvider();
		}
	}
}
