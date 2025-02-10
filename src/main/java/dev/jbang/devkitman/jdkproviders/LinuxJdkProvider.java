package dev.jbang.devkitman.jdkproviders;

import java.io.IOException;
import java.nio.file.Files;
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
 * For now just using `/usr/lib/devkitman` as apparently fedora, debian, ubuntu
 * and centos/rhel use it.
 *
 * <p>
 * If need different behavior per linux distro its intended this provider will
 * adjust based on identified distro.
 */
public class LinuxJdkProvider extends BaseFoldersJdkProvider {
	protected static final Path JDKS_ROOT = Paths.get("/usr/lib/devkitman");

	public LinuxJdkProvider() {
		super(JDKS_ROOT);
	}

	@Override
	public @NonNull String description() {
		return "The JDKs installed in the standard location of a Linux distro.";
	}

	@Override
	protected boolean acceptFolder(Path jdkFolder) {
		return super.acceptFolder(jdkFolder) && !isSameFolderLink(jdkFolder);
	}

	// Returns true if a path is a (sym)link to an entry in the same folder
	private boolean isSameFolderLink(Path jdkFolder) {
		Path absFolder = jdkFolder.toAbsolutePath();
		try {
			if (FileUtils.isLink(absFolder)) {
				Path realPath = absFolder.toRealPath();
				return Files.isSameFile(absFolder.getParent(), realPath.getParent());
			}
		} catch (IOException e) {
			/* ignore */
		}
		return false;
	}

	public static class Discovery implements JdkDiscovery {
		public static final String PROVIDER_ID = "linux";

		@Override
		@NonNull
		public String name() {
			return PROVIDER_ID;
		}

		@Override
		public JdkProvider create(Config config) {
			return new LinuxJdkProvider();
		}
	}
}
