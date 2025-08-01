package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

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
		jdkInstaller = new FoojayJdkInstaller(this, this::jdkId);
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
	public List<Jdk.AvailableJdk> listAvailable() {
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
		super.uninstall(jdk);
		jdkInstaller.uninstall(jdk);
	}

//	@Override
//	public Jdk.InstalledJdk supplyJdk(@NonNull String id, @Nullable Path home, @Nullable String version) {
//		return super.createJdk(id, home, version, true, null);
//	}

	@Override
	public Jdk.@Nullable InstalledJdk getInstalledByVersion(int version, boolean openVersion) {
		Path jdk = jdksRoot.resolve(Integer.toString(version));
		if (Files.isDirectory(jdk)) {
			return createJdk(jdk);
		} else if (openVersion) {
			return super.getInstalledByVersion(version, openVersion);
		}
		return null;
	}

	@Override
	public boolean canUpdate() {
		return true;
	}

	@NonNull
	public Path getJdksPath() {
		return jdksRoot;
	}

	@NonNull
	@Override
	public String jdkId(String name) {
		int majorVersion = JavaUtils.parseJavaVersion(name);
		return super.jdkId(Integer.toString(majorVersion));
	}

	@Override
	public boolean isValidId(@NonNull String id) {
		return JavaUtils.parseToInt(id, 0) > 0;
	}

	@NonNull
	@Override
	protected Path getJdkPath(@NonNull String jdk) {
		return getJdksPath().resolve(Integer.toString(jdkVersion(jdk)));
	}

	private static int jdkVersion(String jdk) {
		return JavaUtils.parseJavaVersion(jdk);
	}

	@Override
	protected boolean acceptFolder(@NonNull Path jdkFolder) {
		return isValidId(jdkFolder.getFileName().toString())
				&& super.acceptFolder(jdkFolder)
				&& !FileUtils.isLink(jdkFolder);
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
		public JdkProvider create(Config config) {
			JBangJdkProvider prov = new JBangJdkProvider(config.installPath);
			return prov
				.installer(new FoojayJdkInstaller(prov, prov::jdkId)
					.distro(config.properties.getOrDefault("distro", null)));
			// TODO make RAP configurable
			// .remoteAccessProvider(RemoteAccessProvider.createDefaultRemoteAccessProvider(config.cachePath));
		}
	}
}
