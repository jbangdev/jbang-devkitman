package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkDiscovery;
import dev.jbang.devkitman.JdkInstaller;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.devkitman.jdkinstallers.FoojayJdkInstaller;
import dev.jbang.devkitman.util.FileUtils;
import dev.jbang.devkitman.util.JavaUtils;

/**
 * JBang's main JDK provider that (by default) can download and install the JDKs
 * provided by the Foojay Disco API. They get installed in the user's JBang
 * folder.
 */
public class JBangJdkProvider extends BaseFoldersJdkProvider {
	protected JdkInstaller jdkInstaller;

	public JBangJdkProvider() {
		this(getJBangJdkDir());
	}

	public JBangJdkProvider(Path jdksRoot) {
		super(jdksRoot);
		jdkInstaller = new FoojayJdkInstaller(this);
	}

	@Override
	public @NonNull String description() {
		return "The JDKs managed by JBang.";
	}

	public JBangJdkProvider installer(@NonNull JdkInstaller jdkInstaller) {
		this.jdkInstaller = jdkInstaller;
		return this;
	}

	@NonNull
	@Override
	public Stream<Jdk.AvailableJdk> listAvailable() {
		return jdkInstaller.listAvailable();
	}

	@Override
	public Jdk.@Nullable AvailableJdk getAvailableByVersion(int version, boolean openVersion) {
		return jdkInstaller.getAvailableByVersion(version, openVersion);
	}

	@Override
	public Jdk.@Nullable AvailableJdk getAvailableByIdOrToken(String idOrToken) {
		return jdkInstaller.getAvailableByIdOrToken(idOrToken);
	}

	@Override
	public Jdk.@NonNull InstalledJdk install(Jdk.@NonNull AvailableJdk jdk) {
		return jdkInstaller.install(jdk, getJdkPath(jdk.id()));
	}

	@Override
	public void uninstall(Jdk.@NonNull InstalledJdk jdk) {
		jdkInstaller.uninstall(jdk);
	}

	@Override
	public boolean canUpdate() {
		return true;
	}

	@Override
	protected boolean acceptFolder(@NonNull Path jdkFolder) {
		// We additionally allow folders that are named with a number
		// (e.g. "11", "17", etc.) for backwards compatibility with older
		// JBang versions
		return (super.acceptFolder(jdkFolder) || JavaUtils.parseToInt(jdkFolder.getFileName().toString(), 0) > 0)
				&& !FileUtils.isLink(jdkFolder);
	}

	@Override
	public String jdkId(@NonNull Path jdkFolder) {
		String name = jdkFolder.getFileName().toString();
		if (JavaUtils.parseToInt(name, 0) > 0) {
			// If the folder is named with a number, it means it's probably a
			// JDK installed by an older JBang version so we append the
			// provider name to the id to avoid naming conflicts
			return name + "-" + name();
		}
		return super.jdkId(jdkFolder);
	}

	public static Path getJBangJdkDir() {
		Path dir;
		String v = System.getenv("JBANG_CACHE_DIR_JDKS");
		if (v != null) {
			dir = Paths.get(v);
		} else {
			dir = getJBangCacheDir().resolve("jdks");
		}
		return dir;
	}

	private static Path getJBangCacheDir() {
		Path dir;
		String v = System.getenv("JBANG_CACHE_DIR");
		if (v != null) {
			dir = Paths.get(v);
		} else {
			dir = getJBangConfigDir().resolve("cache");
		}
		return dir;
	}

	public static Path getJBangConfigDir() {
		Path dir;
		String jd = System.getenv("JBANG_DIR");
		if (jd != null) {
			dir = Paths.get(jd);
		} else {
			dir = Paths.get(System.getProperty("user.home")).resolve(".jbang");
		}
		return dir;
	}

	public static class Discovery implements JdkDiscovery {
		public static final String PROVIDER_ID = "jbang";

		@Override
		@NonNull
		public String name() {
			return PROVIDER_ID;
		}

		@Override
		public JdkProvider create(@NonNull Config config) {
			JBangJdkProvider prov = new JBangJdkProvider(config.installPath());
			return prov
				.installer(new FoojayJdkInstaller(prov)
					.distro(config.properties().getOrDefault("distro", null)));
			// TODO make RAP configurable
			// .remoteAccessProvider(RemoteAccessProvider.createDefaultRemoteAccessProvider(config.cachePath));
		}
	}
}
