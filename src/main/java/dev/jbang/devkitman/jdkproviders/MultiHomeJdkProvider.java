package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
	public List<Jdk> listInstalled() {
		return System.getenv()
			.entrySet()
			.stream()
			.filter(entry -> entry.getKey().startsWith("JAVA_HOME_"))
			.map(entry -> Paths.get(entry.getValue()))
			.filter(Files::isDirectory)
			.map(jdkHome -> createJdk(Discovery.PROVIDER_ID, jdkHome, null, false))
			.filter(Objects::nonNull)
			.collect(Collectors.toList());
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
