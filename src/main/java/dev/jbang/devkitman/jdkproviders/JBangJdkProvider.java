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

/**
 * JBang's main JDK provider that (by default) can download and install the JDKs
 * provided by the Foojay Disco API. They get installed in the user's JBang
 * folder.
 */
public class JBangJdkProvider extends BaseFoldersJdkProvider
		implements FoojayJdkInstaller.JdkFactory {
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

	public JBangJdkProvider installer(JdkInstaller jdkInstaller) {
		this.jdkInstaller = jdkInstaller;
		return this;
	}

	@NonNull
	@Override
	public List<Jdk> listAvailable() {
		return jdkInstaller.listAvailable();
	}

	@Override
	public @Nullable Jdk getAvailableByIdOrToken(String idOrToken) {
		return jdkInstaller.getAvailableByIdOrToken(idOrToken);
	}

	@NonNull
	@Override
	public Jdk install(@NonNull Jdk jdk) {
		Jdk installedJdk = jdkInstaller.install(jdk, getJdkPath(jdk.id()));

		// TODO Move this to JdkManager somehow!
		// Now create or update symlink from major version to jdk
		Path linkPath = getJdksPath().resolve(Integer.toString(installedJdk.majorVersion()));
		if (!Files.exists(linkPath) || !FileUtils.isLink(linkPath)) {
			// If the path exists but is not a link it's most likely a
			// full JDK installation from a previous devkitman version.
			// In any case we'll remove it to make place for a link
			FileUtils.deletePath(linkPath);
			// Now create the link
			FileUtils.createLink(linkPath, installedJdk.home());
		}

		return installedJdk;
	}

	@Override
	public void uninstall(@NonNull Jdk jdk) {
		super.uninstall(jdk);
		jdkInstaller.uninstall(jdk);
	}

	@Override
	public Jdk createJdk(@NonNull String id, @Nullable Path home, @Nullable String version) {
		return super.createJdk(id, home, version, true);
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
		return super.jdkId(name);
	}

	@Override
	protected boolean acceptFolder(@NonNull Path jdkFolder) {
		return super.acceptFolder(jdkFolder) && !FileUtils.isLink(jdkFolder);
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
				.installer(new FoojayJdkInstaller(prov)
					.distro(config.properties.getOrDefault("distro", null)));
			// TODO make RAP configurable
			// .remoteAccessProvider(RemoteAccessProvider.createDefaultRemoteAccessProvider(config.cachePath));
		}
	}
}
