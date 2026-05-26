package dev.jbang.devkitman.jdkproviders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.JdkDiscovery;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.devkitman.util.FileUtils;
import dev.jbang.devkitman.util.OsUtils;

/**
 * This JDK provider detects JDKs that have been installed in the standard
 * location on macOS: {@code /Library/Java/JavaVirtualMachines/}.
 *
 * <p>
 * On macOS, JDKs are stored as bundles with the structure:
 * {@code /Library/Java/JavaVirtualMachines/<name>.jdk/Contents/Home}
 */
public class MacJdkProvider extends BaseFoldersJdkProvider {
	private static final Path JDKS_ROOT = Paths.get("/Library/Java/JavaVirtualMachines");
	private static final String CONTENTS_HOME = "Contents/Home";

	public MacJdkProvider() {
		super(jdksRoot());
	}

	MacJdkProvider(@NonNull Path jdksRoot) {
		super(jdksRoot);
	}

	public static Path jdksRoot() {
		return JDKS_ROOT;
	}

	@Override
	public @NonNull String description() {
		return "The JDKs installed in /Library/Java/JavaVirtualMachines on macOS.";
	}

	@Override
	public boolean canUse() {
		return OsUtils.isMac() && super.canUse();
	}

	@Override
	@NonNull
	protected Stream<Path> listJdkPaths() throws IOException {
		if (Files.isDirectory(jdksRoot)) {
			return Files.list(jdksRoot)
				.map(bundle -> bundle.resolve(CONTENTS_HOME))
				.filter(this::acceptFolder);
		}
		return Stream.empty();
	}

	@Override
	protected boolean acceptFolder(@NonNull Path jdkFolder) {
		return super.acceptFolder(jdkFolder) && !FileUtils.isLink(jdkFolder.getParent().getParent());
	}

	@Override
	public String jdkId(@NonNull Path jdkFolder) {
		// jdkFolder is <jdksRoot>/<bundle>.jdk/Contents/Home
		// Use the bundle name (without .jdk extension) as the ID
		String bundleName = jdkFolder.getParent().getParent().getFileName().toString();
		if (bundleName.endsWith(".jdk")) {
			bundleName = bundleName.substring(0, bundleName.length() - 4);
		}
		return bundleName;
	}

	@Override
	@NonNull
	protected Path getJdkPath(@NonNull String id) {
		// Strip the provider suffix to get the bundle base name
		String bundleBase = id.endsWith("-" + name()) ? id.substring(0, id.length() - name().length() - 1) : id;
		return jdksRoot.resolve(bundleBase + ".jdk").resolve(CONTENTS_HOME);
	}

	public static class Discovery implements JdkDiscovery {
		public static final String PROVIDER_ID = "mac";

		@Override
		@NonNull
		public String name() {
			return PROVIDER_ID;
		}

		@Override
		public JdkProvider create(@NonNull Config config) {
			return new MacJdkProvider();
		}
	}
}
