package dev.jbang.devkitman.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

public class UnpackUtils {
	private static final Logger LOGGER = Logger.getLogger(UnpackUtils.class.getName());

	public static void unpackJdk(Path archive, Path outputDir) throws IOException {
		String name = archive.toString().toLowerCase(Locale.ENGLISH);
		Path selectFolder = OsUtils.isMac() ? Paths.get("Contents/Home") : null;
		if (name.endsWith(".zip")) {
			unzip(archive, outputDir, true, selectFolder, UnpackUtils::defaultZipEntryCopy);
		} else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
			untargz(archive, outputDir, true, selectFolder);
		}
	}

	public static void unpack(Path archive, Path outputDir) throws IOException {
		unpack(archive, outputDir, false);
	}

	public static void unpack(Path archive, Path outputDir, boolean stripRootFolder)
			throws IOException {
		unpack(archive, outputDir, stripRootFolder, null);
	}

	public static void unpack(
			Path archive, Path outputDir, boolean stripRootFolder, Path selectFolder)
			throws IOException {
		String name = archive.toString().toLowerCase(Locale.ENGLISH);
		if (name.endsWith(".zip") || name.endsWith(".jar")) {
			unzip(
					archive,
					outputDir,
					stripRootFolder,
					selectFolder,
					UnpackUtils::defaultZipEntryCopy);
		} else if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
			untargz(archive, outputDir, stripRootFolder, selectFolder);
		} else {
			throw new IllegalArgumentException(
					"Unsupported archive format: " + FileUtils.extension(archive.toString()));
		}
	}

	public static void unzip(
			Path zip,
			Path outputDir,
			boolean stripRootFolder,
			Path selectFolder,
			ExistingZipFileHandler onExisting)
			throws IOException {
		try (ZipFile zipFile = new ZipFile(zip.toFile())) {
			Enumeration<ZipArchiveEntry> entries = zipFile.getEntries();
			while (entries.hasMoreElements()) {
				ZipArchiveEntry zipEntry = entries.nextElement();
				Path entry = Paths.get(zipEntry.getName());
				if (stripRootFolder) {
					if (entry.getNameCount() == 1) {
						continue;
					}
					entry = entry.subpath(1, entry.getNameCount());
				}
				if (selectFolder != null) {
					if (!entry.startsWith(selectFolder) || entry.equals(selectFolder)) {
						continue;
					}
					entry = entry.subpath(selectFolder.getNameCount(), entry.getNameCount());
				}
				entry = outputDir.resolve(entry).normalize();
				if (!entry.startsWith(outputDir)) {
					throw new IOException(
							"Entry is outside of the target dir: " + zipEntry.getName());
				}
				if (zipEntry.isDirectory()) {
					Files.createDirectories(entry);
				} else if (zipEntry.isUnixSymlink()) {
					Scanner s = new Scanner(zipFile.getInputStream(zipEntry)).useDelimiter("\\A");
					String result = s.hasNext() ? s.next() : "";
					Files.createSymbolicLink(entry, Paths.get(result));
				} else {
					if (!Files.isDirectory(entry.getParent())) {
						Files.createDirectories(entry.getParent());
					}
					if (Files.isRegularFile(entry)) {
						onExisting.handle(zipFile, zipEntry, entry);
					} else {
						defaultZipEntryCopy(zipFile, zipEntry, entry);
					}
				}
			}
		}
	}

	public interface ExistingZipFileHandler {
		void handle(ZipFile zipFile, ZipArchiveEntry zipEntry, Path outFile) throws IOException;
	}

	public static void defaultZipEntryCopy(ZipFile zipFile, ZipArchiveEntry zipEntry, Path outFile)
			throws IOException {
		try (InputStream zis = zipFile.getInputStream(zipEntry)) {
			Files.copy(zis, outFile, StandardCopyOption.REPLACE_EXISTING);
		}
		int mode = zipEntry.getUnixMode();
		if (mode != 0 && !OsUtils.isWindows()) {
			Set<PosixFilePermission> permissions = PosixFilePermissionSupport.toPosixFilePermissions(mode);
			Files.setPosixFilePermissions(outFile, permissions);
		}
	}

	public static void untargz(
			Path targz, Path outputDir, boolean stripRootFolder, Path selectFolder)
			throws IOException {
		try (TarArchiveInputStream tarArchiveInputStream = new TarArchiveInputStream(
				new GzipCompressorInputStream(Files.newInputStream(targz.toFile().toPath())))) {
			TarArchiveEntry targzEntry;
			while ((targzEntry = tarArchiveInputStream.getNextEntry()) != null) {
				Path entry = Paths.get(targzEntry.getName()).normalize();
				if (stripRootFolder) {
					if (entry.getNameCount() == 1) {
						continue;
					}
					entry = entry.subpath(1, entry.getNameCount());
				}
				if (selectFolder != null) {
					if (!entry.startsWith(selectFolder) || entry.equals(selectFolder)) {
						continue;
					}
					entry = entry.subpath(selectFolder.getNameCount(), entry.getNameCount());
				}
				entry = outputDir.resolve(entry).normalize();
				if (!entry.startsWith(outputDir)) {
					throw new IOException(
							"Entry is outside of the target dir: " + targzEntry.getName());
				}
				if (targzEntry.isDirectory()) {
					Files.createDirectories(entry);
				} else if (targzEntry.isSymbolicLink()) {
					Path linkTarget = Paths.get(targzEntry.getLinkName());
					Files.createDirectories(entry.getParent());
					if (!Files.exists(entry)) {
						try {
							Files.createSymbolicLink(entry, linkTarget);
						} catch (IOException e) {
							LOGGER.log(Level.WARNING, "Could not create symbolic link " + entry + " -> "
									+ linkTarget + " due to " + e.getMessage(), e);
						}
					}
				} else {
					Files.createDirectories(entry.getParent());
					Files.copy(tarArchiveInputStream, entry, StandardCopyOption.REPLACE_EXISTING);
					int mode = targzEntry.getMode();
					if (mode != 0 && !OsUtils.isWindows()) {
						Set<PosixFilePermission> permissions = PosixFilePermissionSupport.toPosixFilePermissions(mode);
						Files.setPosixFilePermissions(entry, permissions);
					}
				}
			}
		}
	}
}

class PosixFilePermissionSupport {

	private static final int OWNER_READ_FILEMODE = 0b100_000_000;
	private static final int OWNER_WRITE_FILEMODE = 0b010_000_000;
	private static final int OWNER_EXEC_FILEMODE = 0b001_000_000;

	private static final int GROUP_READ_FILEMODE = 0b000_100_000;
	private static final int GROUP_WRITE_FILEMODE = 0b000_010_000;
	private static final int GROUP_EXEC_FILEMODE = 0b000_001_000;

	private static final int OTHERS_READ_FILEMODE = 0b000_000_100;
	private static final int OTHERS_WRITE_FILEMODE = 0b000_000_010;
	private static final int OTHERS_EXEC_FILEMODE = 0b000_000_001;

	private PosixFilePermissionSupport() {
	}

	static Set<PosixFilePermission> toPosixFilePermissions(int octalFileMode) {
		Set<PosixFilePermission> permissions = new LinkedHashSet<>();
		// Owner
		if ((octalFileMode & OWNER_READ_FILEMODE) == OWNER_READ_FILEMODE) {
			permissions.add(PosixFilePermission.OWNER_READ);
		}
		if ((octalFileMode & OWNER_WRITE_FILEMODE) == OWNER_WRITE_FILEMODE) {
			permissions.add(PosixFilePermission.OWNER_WRITE);
		}
		if ((octalFileMode & OWNER_EXEC_FILEMODE) == OWNER_EXEC_FILEMODE) {
			permissions.add(PosixFilePermission.OWNER_EXECUTE);
		}
		// Group
		if ((octalFileMode & GROUP_READ_FILEMODE) == GROUP_READ_FILEMODE) {
			permissions.add(PosixFilePermission.GROUP_READ);
		}
		if ((octalFileMode & GROUP_WRITE_FILEMODE) == GROUP_WRITE_FILEMODE) {
			permissions.add(PosixFilePermission.GROUP_WRITE);
		}
		if ((octalFileMode & GROUP_EXEC_FILEMODE) == GROUP_EXEC_FILEMODE) {
			permissions.add(PosixFilePermission.GROUP_EXECUTE);
		}
		// Others
		if ((octalFileMode & OTHERS_READ_FILEMODE) == OTHERS_READ_FILEMODE) {
			permissions.add(PosixFilePermission.OTHERS_READ);
		}
		if ((octalFileMode & OTHERS_WRITE_FILEMODE) == OTHERS_WRITE_FILEMODE) {
			permissions.add(PosixFilePermission.OTHERS_WRITE);
		}
		if ((octalFileMode & OTHERS_EXEC_FILEMODE) == OTHERS_EXEC_FILEMODE) {
			permissions.add(PosixFilePermission.OTHERS_EXECUTE);
		}
		return permissions;
	}
}
