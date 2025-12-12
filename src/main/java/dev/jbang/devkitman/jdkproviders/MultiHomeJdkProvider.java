package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkDiscovery;
import dev.jbang.devkitman.JdkProvider;

/**
 * This JDK provider detects if JDKs are already available on the system by
 * looking at any environment variables that start with <code>JAVA_HOME_</code>.
 * This is used for example in GitHub actions where multiple JDKs can be
 * installed using the setup-java action.
 */
public class MultiHomeJdkProvider extends BaseJdkProvider {

	@Override
	@NonNull
	public String name() {
		return Discovery.PROVIDER_ID;
	}

	@Override
	public @NonNull String description() {
		return "JDKs pointed to by any environment variable starting with JAVA_HOME_";
	}

	@NonNull
	@Override
	public Stream<Jdk.InstalledJdk> listInstalled() {
		return System.getenv()
			.entrySet()
			.stream()
			.filter(entry -> entry.getKey().startsWith("JAVA_HOME_"))
			.map(entry -> {
				String versionArch = entry.getKey().substring("JAVA_HOME_".length()).toLowerCase();
				Path jdkHome = Paths.get(entry.getValue());
				if (Files.isDirectory(jdkHome)) {
					return createJdk(Discovery.PROVIDER_ID + "_" + versionArch, jdkHome);
				} else {
					return null;
				}
			})
			.filter(Objects::nonNull);
	}

	@Override
	public boolean hasFixedVersions() {
		return false;
	}

	public static class Discovery implements JdkDiscovery {
		public static final String PROVIDER_ID = "multihome";

		@Override
		@NonNull
		public String name() {
			return PROVIDER_ID;
		}

		@Override
		public JdkProvider create(Config config) {
			return new MultiHomeJdkProvider();
		}
	}
}
