package dev.jbang.devkitman.jdkproviders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.util.JavaUtils;
import dev.jbang.devkitman.util.OsUtils;

public abstract class BaseFoldersJdkProvider extends BaseJdkProvider {
	protected final Path jdksRoot;

	private static final Logger LOGGER = Logger.getLogger(BaseFoldersJdkProvider.class.getName());

	protected BaseFoldersJdkProvider(Path jdksRoot) {
		this.jdksRoot = jdksRoot;
	}

	@Override
	@NonNull
	public String name() {
		String nm = getClass().getSimpleName();
		if (nm.endsWith("JdkProvider")) {
			return nm.substring(0, nm.length() - 11).toLowerCase();
		} else {
			return nm.toLowerCase();
		}
	}

	@Override
	public boolean canUse() {
		return Files.isDirectory(jdksRoot) || canUpdate();
	}

	@Override
	public @Nullable Jdk getAvailableByIdOrToken(String idOrToken) {
		if (isValidId(idOrToken) && super.canUpdate()) {
			return super.getAvailableByIdOrToken(idOrToken);
		} else {
			return null;
		}
	}

	@NonNull
	@Override
	public List<Jdk> listInstalled() {
		if (Files.isDirectory(jdksRoot)) {
			try (Stream<Path> jdkPaths = listJdkPaths()) {
				return jdkPaths	.map(this::createJdk)
								.filter(Objects::nonNull)
								.collect(Collectors.toList());
			} catch (IOException e) {
				LOGGER.log(Level.FINE, "Couldn't list installed JDKs", e);
			}
		}
		return Collections.emptyList();
	}

	@Nullable
	@Override
	public Jdk getInstalledById(@NonNull String id) {
		return getInstalledByPath(getJdkPath(id));
	}

	@Nullable
	@Override
	public Jdk getInstalledByPath(@NonNull Path jdkPath) {
		if (jdkPath.startsWith(jdksRoot) && Files.isDirectory(jdkPath) && acceptFolder(jdkPath)) {
			return createJdk(jdkPath);
		}
		return null;
	}

	/**
	 * Returns a path to the requested JDK. This method should never return
	 * <code>null</code> and should return the path where the requested JDK is
	 * either currently installed or where it would be installed if it were
	 * available. This only needs to be implemented for providers that are
	 * updatable.
	 *
	 * @param jdk The identifier of the JDK to install
	 * @return A path to the requested JDK
	 */
	@NonNull
	protected Path getJdkPath(@NonNull String jdk) {
		return jdksRoot.resolve(jdk);
	}

	protected Predicate<Path> sameJdk(Path jdkRoot) {
		Path release = jdkRoot.resolve("release");
		return (Path p) -> {
			try {
				return Files.isSameFile(p.resolve("release"), release);
			} catch (IOException e) {
				return false;
			}
		};
	}

	protected Stream<Path> listJdkPaths() throws IOException {
		if (Files.isDirectory(jdksRoot)) {
			return Files.list(jdksRoot).filter(this::acceptFolder);
		}
		return Stream.empty();
	}

	@Nullable
	protected Jdk createJdk(Path home) {
		String name = home.getFileName().toString();
		Optional<String> version = JavaUtils.resolveJavaVersionStringFromPath(home);
		if (version.isPresent() && acceptFolder(home)) {
			return createJdk(jdkId(name), home, version.get());
		}
		return null;
	}

	protected String jdkId(String name) {
		return name + "-" + name();
	}

	protected boolean acceptFolder(Path jdkFolder) {
		return OsUtils.searchPath("javac", jdkFolder.resolve("bin").toString()) != null;
	}

	@Override
	public boolean isValidId(@NonNull String id) {
		return id.endsWith("-" + name());
	}
}
