package dev.jbang.devkitman;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import dev.jbang.devkitman.util.JavaUtils;

public interface Jdk extends Comparable<Jdk> {
	@NonNull
	JdkProvider provider();

	@NonNull
	String id();

	@NonNull
	String version();

	@NonNull
	Path home();

	int majorVersion();

	@NonNull
	Jdk install();

	void uninstall();

	boolean isInstalled();

	class Default implements Jdk {
		@NonNull
		private final transient JdkProvider provider;
		@NonNull
		private final String id;
		@NonNull
		private final String version;
		@Nullable
		private final Path home;
		@NonNull
		private final Set<String> tags = new HashSet<>();

		Default(
				@NonNull JdkProvider provider,
				@NonNull String id,
				@Nullable Path home,
				@NonNull String version,
				@NonNull String... tags) {
			this.provider = provider;
			this.id = id;
			this.version = version;
			this.home = home;
		}

		@Override
		@NonNull
		public JdkProvider provider() {
			return provider;
		}

		/**
		 * Returns the id that is used to uniquely identify this JDK across all
		 * providers
		 */
		@Override
		@NonNull
		public String id() {
			return id;
		}

		/** Returns the JDK's version */
		@Override
		@NonNull
		public String version() {
			return version;
		}

		/**
		 * The path to where the JDK is installed. Can be <code>null</code> which means
		 * the JDK isn't currently installed by that provider
		 */
		@Override
		@NonNull
		public Path home() {
			if (home == null) {
				throw new IllegalStateException(
						"Trying to retrieve home folder for uninstalled JDK");
			}
			return home;
		}

		@Override
		public int majorVersion() {
			return JavaUtils.parseJavaVersion(version());
		}

		@Override
		@NonNull
		public Jdk install() {
			if (!provider.canUpdate()) {
				throw new UnsupportedOperationException(
						"Installing a JDK is not supported by " + provider);
			}
			return provider.install(this);
		}

		@Override
		public void uninstall() {
			provider.uninstall(this);
		}

		@Override
		public boolean isInstalled() {
			return home != null;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			Default jdk = (Default) o;
			return id.equals(jdk.id) && Objects.equals(home, jdk.home);
		}

		@Override
		public int hashCode() {
			return Objects.hash(home, id);
		}

		@Override
		public int compareTo(Jdk o) {
			return Integer.compare(majorVersion(), o.majorVersion());
		}

		@Override
		public String toString() {
			return majorVersion() + " (" + version + ", " + id + ", " + home + ")";
		}
	}

	class Predicates {
		public static final Predicate<Jdk> all = provider -> true;

		public static Predicate<Jdk> exactVersion(int version) {
			return jdk -> jdk.majorVersion() == version;
		}

		public static Predicate<Jdk> openVersion(int version) {
			return jdk -> jdk.majorVersion() >= version;
		}

		public static Predicate<Jdk> forVersion(String version) {
			int v = JavaUtils.parseJavaVersion(version);
			return forVersion(v, JavaUtils.isOpenVersion(version));
		}

		public static Predicate<Jdk> forVersion(int version, boolean openVersion) {
			return openVersion ? openVersion(version) : exactVersion(version);
		}

		public static Predicate<Jdk> id(String id) {
			return jdk -> jdk.id().equals(id);
		}

		public static Predicate<Jdk> path(Path jdkPath) {
			return jdk -> jdk.isInstalled() && jdkPath.startsWith(jdk.home());
		}
	}
}
