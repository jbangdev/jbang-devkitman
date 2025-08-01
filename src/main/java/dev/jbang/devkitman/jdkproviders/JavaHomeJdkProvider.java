package dev.jbang.devkitman.jdkproviders;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import org.jspecify.annotations.NonNull;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.JdkDiscovery;
import dev.jbang.devkitman.JdkProvider;
import dev.jbang.devkitman.util.JavaUtils;

/**
 * This JDK provider detects if a JDK is already available on the system by
 * looking at <code>JAVA_HOME</code> environment variable.
 */
public class JavaHomeJdkProvider extends BaseJdkProvider {

	@Override
	@NonNull
	public String name() {
		return Discovery.PROVIDER_ID;
	}

	@Override
	public @NonNull String description() {
		return "The JDK pointed to by the JAVA_HOME environment variable.";
	}

	@NonNull
	@Override
	public List<Jdk.InstalledJdk> listInstalled() {
		Path jdkHome = JavaUtils.getJavaHomeEnv();
		if (jdkHome != null && Files.isDirectory(jdkHome)) {
			Jdk.InstalledJdk jdk = createJdk(Discovery.PROVIDER_ID, jdkHome, null, false, null);
			if (jdk != null) {
				return Collections.singletonList(jdk);
			}
		}
		return Collections.emptyList();
	}

	public static class Discovery implements JdkDiscovery {
		public static final String PROVIDER_ID = "javahome";

		@Override
		@NonNull
		public String name() {
			return PROVIDER_ID;
		}

		@Override
		public JdkProvider create(Config config) {
			return new JavaHomeJdkProvider();
		}
	}
}
