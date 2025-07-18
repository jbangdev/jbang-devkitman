package dev.jbang.devkitman;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
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
	 * Returns the tags that are associated with this JDK
	 */
	@NonNull
	Set<String> tags();

	/**
	 * Returns the major version of the JDK
	 */
	default int majorVersion() {
		return JavaUtils.parseJavaVersion(version());
	}

	/**
	 * Determines if this JDK is currently installed
	 */
	boolean isInstalled();

	interface AvailableJdk extends Jdk {

		@Override
		default boolean isInstalled() {
			return false;
		}

		/**
		 * Installs the JDK if it isn't already installed. If already installed it will
		 * simply return the current object, if not it will return a new copy with
		 * updated information (e.g. the home path will be set)
		 *
		 * @return A <code>Jdk</code> object
		 */
		@NonNull
		InstalledJdk install();

		class Default extends Jdk.Default implements AvailableJdk {

			public Default(
					@NonNull JdkProvider provider,
					@NonNull String id,
					@NonNull String version,
					@Nullable Set<String> tags) {
				super(provider, id, version, tags);
			}

			@Override
			@NonNull
			public InstalledJdk install() {
				if (!provider.canUpdate()) {
					throw new UnsupportedOperationException(
							"Installing a JDK is not supported by " + provider);
				}
				return provider.install(this);
			}

			@Override
			public String toString() {
				return majorVersion() + " (" + version + ", " + id + ", " + ", " + tags + "))";
			}
		}
	}

	interface InstalledJdk extends Jdk {

		/**
		 * The path to where the JDK is installed. Can be <code>null</code> which means
		 * the JDK isn't currently installed by that provider
		 */
		@NonNull
		Path home();

		/**
		 * Determines if the JDK version is fixed or that it can change
		 */
		boolean isFixedVersion();

		@Override
		default boolean isInstalled() {
			return true;
		}

		/**
		 * Uninstalls the JDK. If the JDK isn't installed it will do nothing
		 */
		void uninstall();

		class Default extends Jdk.Default implements InstalledJdk {
			private final boolean fixedVersion;
			@Nullable
			private final Path home;

			enum Tags {
				Jre, Jdk, Graalvm, Native, Javafx
			}

			public Default(
					@NonNull JdkProvider provider,
					@NonNull String id,
					@Nullable Path home,
					@NonNull String version,
					boolean fixedVersion,
					@Nullable Set<String> tags) {
				super(provider, id, version, tags != null ? tags : determineTagsFromJdkHome(home));
				this.fixedVersion = fixedVersion;
				this.home = home;
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
				InstalledJdk.Default jdk = (InstalledJdk.Default) o;
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
				return majorVersion() + " (" + version + (isFixedVersion() ? " [fixed]" : " [dynamic]") + ", " + id
						+ ", "
						+ home + ", " + tags + "))";
			}

			@NonNull
			public static Set<String> determineTagsFromJdkHome(@Nullable Path home) {
				if (home == null) {
					return Collections.emptySet();
				}
				Set<String> tags = new HashSet<>();
				if (JavaUtils.hasJavacCmd(home)) {
					tags.add(Jdk.Default.Tags.Jdk.name());
				} else if (JavaUtils.hasJavaCmd(home)) {
					tags.add(Jdk.Default.Tags.Jre.name());
				}
				Optional<String> graalVersion = JavaUtils.readGraalVMVersionStringFromReleaseFile(home);
				if (graalVersion.isPresent()) {
					tags.add(Jdk.Default.Tags.Graalvm.name());
					if (JavaUtils.hasNativeImageCmd(home)) {
						tags.add(Jdk.Default.Tags.Native.name());
					}
				}
				Path javafxProps = home.resolve("lib").resolve("javafx.properties");
				if (Files.exists(javafxProps)) {
					tags.add(Jdk.Default.Tags.Javafx.name());
				}
				return tags;
			}
		}

	}

	abstract class Default implements Jdk {
		@NonNull
		protected final transient JdkProvider provider;
		@NonNull
		protected final String id;
		@NonNull
		protected final String version;
		@NonNull
		protected final Set<String> tags;

		enum Tags {
			Jre, Jdk, Graalvm, Native, Javafx
		}

		Default(
				@NonNull JdkProvider provider,
				@NonNull String id,
				@NonNull String version,
				@Nullable Set<String> tags) {
			this.provider = provider;
			this.id = id;
			this.version = version;
			TreeSet<String> ts = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
			if (tags != null) {
				ts.addAll(tags);
			}
			this.tags = Collections.unmodifiableSet(ts);
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
		@NonNull
		public Set<String> tags() {
			return tags;
		}

		@Override
		public boolean equals(Object o) {
			if (this == o)
				return true;
			if (o == null || getClass() != o.getClass())
				return false;
			Default jdk = (Default) o;
			return id.equals(jdk.id);
		}

		@Override
		public int hashCode() {
			return Objects.hash(id);
		}

		@Override
		public int compareTo(Jdk o) {
			return Integer.compare(majorVersion(), o.majorVersion());
		}
	}

	class Predicates {
		public static final Predicate<? extends Jdk> all = provider -> true;

		public static <T extends Jdk> Predicate<T> exactVersion(int version) {
			return jdk -> jdk.majorVersion() == version;
		}

		public static <T extends Jdk> Predicate<T> openVersion(int version) {
			return jdk -> jdk.majorVersion() >= version;
		}

		public static <T extends Jdk> Predicate<T> forVersion(String version) {
			int v = JavaUtils.parseJavaVersion(version);
			return forVersion(v, JavaUtils.isOpenVersion(version));
		}

		public static <T extends Jdk> Predicate<T> forVersion(int version, boolean openVersion) {
			return openVersion ? openVersion(version) : exactVersion(version);
		}

		public static <T extends Jdk> Predicate<T> id(String id) {
			return jdk -> jdk.id().equals(id);
		}

		public static <T extends Jdk> Predicate<T> allTags(Set<String> tags) {
			return jdk -> jdk.tags().containsAll(tags);
		}

		public static <T extends InstalledJdk> Predicate<T> fixedVersion() {
			return InstalledJdk::isFixedVersion;
		}

		public static <T extends InstalledJdk> Predicate<T> path(Path jdkPath) {
			return jdk -> jdkPath.startsWith(jdk.home());
		}
	}
}
