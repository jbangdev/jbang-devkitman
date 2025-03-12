package dev.jbang.devkitman;

import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import dev.jbang.devkitman.util.JavaUtils;

public interface Jdk extends Comparable<Jdk> {
	/**
	 * Returns the provider that is responsible for this JDK
	 */
	@NonNull
	JdkProvider provider();

	/**
	 * Returns the id that is used to uniquely identify this JDK across all
	 * providers
	 */
	@NonNull
	String id();

	/**
	 * Returns the JDK's version
	 */
	@NonNull
	String version();

	/**
	 * Determines if the JDK version is fixed or that it can change
	 */
	boolean isFixedVersion();

	/**
	 * The path to where the JDK is installed. Can be <code>null</code> which means
	 * the JDK isn't currently installed by that provider
	 */
	@NonNull
	Path home();

	/**
	 * The JDK distribution name (e.g. "openjdk", "zulu", "temurin", etc.). Can be
	 * <code>null</code> if the provider doesn't have or support a distribution
	 * names
	 */
	String distro();

	/**
	 * Returns a set of tags that can be used to give additional information about
	 * the JDK
	 */
	@NonNull
	Set<String> tags();

	/**
	 * Returns the major version of the JDK
	 */
	int majorVersion();

	/**
	 * Installs the JDK if it isn't already installed. If already installed it will
	 * simply return the current object, if not it will return a new copy with
	 * updated information (e.g. the home path will be set)
	 * 
	 * @return A <code>Jdk</code> object
	 */
	@NonNull
	Jdk install();

	/**
	 * Uninstalls the JDK. If the JDK isn't installed it will do nothing
	 */
	void uninstall();

	/**
	 * Determines if this JDK is currently installed
	 */
	boolean isInstalled();

	class Default implements Jdk {
		@NonNull
		private final transient JdkProvider provider;
		@NonNull
		private final String id;
		@NonNull
		private final String version;
		private final boolean fixedVersion;
		@Nullable
		private final Path home;
		private final String distro;
		@NonNull
		private final Set<String> tags;

		Default(
				@NonNull JdkProvider provider,
				@NonNull String id,
				@Nullable Path home,
				@NonNull String version,
				boolean fixedVersion,
				String distro,
				@NonNull Set<String> tags) {
			this.provider = provider;
			this.id = id;
			this.version = version;
			this.fixedVersion = fixedVersion;
			this.home = home;
			this.distro = distro;
			this.tags = Collections.unmodifiableSet(new HashSet<>(tags));
		}

		@Override
		@NonNull
		public JdkProvider provider() {
			return provider;
		}

		@Override
		@NonNull
		public String id() {
			return id;
		}

		@Override
		@NonNull
		public String version() {
			return version;
		}

		@Override
		public boolean isFixedVersion() {
			return fixedVersion;
		}

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
		public String distro() {
			return distro;
		}

		@Override
		@NonNull
		public Set<String> tags() {
			return tags;
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
			return majorVersion() + " (" + version + (isFixedVersion() ? " (fixed)" : " (dynamic)") + ", " + id + ", "
					+ home + ", " + distro + ", " + tags + ")";
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

		public static Predicate<Jdk> fixedVersion() {
			return Jdk::isFixedVersion;
		}

		public static Predicate<Jdk> id(String id) {
			return jdk -> jdk.id().equals(id);
		}

		public static Predicate<Jdk> path(Path jdkPath) {
			return jdk -> jdk.isInstalled() && jdkPath.startsWith(jdk.home());
		}
	}
}
