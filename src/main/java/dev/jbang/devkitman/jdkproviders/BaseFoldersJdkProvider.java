package dev.jbang.devkitman.jdkproviders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import dev.jbang.devkitman.Jdk;
import dev.jbang.devkitman.util.JavaUtils;

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
	public Jdk.@Nullable AvailableJdk getAvailableByVersion(int version, boolean openVersion) {
		if (canUpdate()) {
			return super.getAvailableByVersion(version, openVersion);
		} else {
			return null;
		}
	}

	@Override
	public Jdk.@Nullable AvailableJdk getAvailableByIdOrToken(String idOrToken) {
		if (isValidId(idOrToken) && canUpdate()) {
			return super.getAvailableByIdOrToken(idOrToken);
		} else {
			return null;
		}
	}

	@NonNull
	@Override
	public Stream<Jdk.InstalledJdk> listInstalled() {
		try {
			return listJdkPaths()
				.map(this::createJdk)
				.filter(Objects::nonNull);
		} catch (IOException e) {
			LOGGER.log(Level.FINE, "Couldn't list installed JDKs", e);
			return Stream.empty();
		}
	}

	@Override
	public Jdk.@Nullable InstalledJdk getInstalledById(@NonNull String id) {
		if (isValidId(id)) {
			return getInstalledByPath(getJdkPath(id));
		}
		return null;
	}

	@Override
	public Jdk.@Nullable InstalledJdk getInstalledByPath(@NonNull Path jdkPath) {
		if (acceptFolder(jdkPath)) {
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
	 * @param id The identifier of the JDK to install
	 * @return A path to the requested JDK
	 */
	@NonNull
	protected Path getJdkPath(@NonNull String id) {
		return jdksRoot.resolve(id);
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

	@NonNull
	protected Stream<Path> listJdkPaths() throws IOException {
		if (Files.isDirectory(jdksRoot)) {
			return Files.list(jdksRoot).filter(this::acceptFolder);
		}
		return Stream.empty();
	}

	protected Jdk.@Nullable InstalledJdk createJdk(@NonNull Path home) {
		if (acceptFolder(home)) {
			return createJdk(jdkId(home), home);
		}
		return null;
	}

	protected boolean acceptFolder(@NonNull Path jdkFolder) {
		return jdkFolder.startsWith(jdksRoot) && isValidId(jdkFolder.getFileName().toString())
				&& JavaUtils.hasJavacCmd(jdkFolder);
	}

	private final Pattern validId = Pattern.compile("^[a-zA-Z0-9._+-]+$");

	@Override
	public boolean isValidId(@NonNull String id) {
		return id.endsWith("-" + name()) && validId.matcher(id).matches();
	}

	/**
	 * Returns a JDK id for the given JDK folder. By default, the id is a
	 * combination of the folder name and the provider name. This should only be
	 * called with paths that have passed the {@link #acceptFolder(Path)} check.
	 *
	 * @param jdkFolder The folder of the JDK
	 * @return A valid JDK id
	 */
	public String jdkId(@NonNull Path jdkFolder) {
		return jdkFolder.getFileName().toString();
	}
}
