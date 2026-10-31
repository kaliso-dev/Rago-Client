package gg.rago.profile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;

/**
 * Profile codes as files, for codes too long for one chat message: an exported profile is also
 * written to {@code config/rago-profiles/<name>.txt}, and a friend's file put in
 * {@code config/rago-profiles/import} is read when there is no code on the clipboard.
 */
public final class ProfileFiles {
	/** The longest message Discord sends without making it a file. */
	public static final int CHAT_LIMIT = 2000;
	/** Files bigger than this are not profile codes. */
	private static final long MAX_FILE = 300_000;

	private ProfileFiles() {
	}

	public static Path folder(Path configDir) {
		return configDir.resolve("rago-profiles");
	}

	public static Path importFolder(Path configDir) {
		return folder(configDir).resolve("import");
	}

	/** Writes a profile's code; returns the file's path from the config folder, or null if it could not be written. */
	public static String write(Path configDir, String name, String code) {
		if (configDir == null) {
			return null;
		}
		try {
			Path dir = folder(configDir);
			Files.createDirectories(importFolder(configDir));
			String file = fileName(name) + ".txt";
			String text = "Rago profile \"" + name + "\". To import it in Rago: copy the code below (or put this file in "
				+ "config/rago-profiles/import), then click the import button under the profiles in the Rago menu.\n\n"
				+ code + "\n";
			Files.write(dir.resolve(file), text.getBytes(StandardCharsets.UTF_8));
			return "config/rago-profiles/" + file;
		} catch (IOException | RuntimeException e) {
			return null;
		}
	}

	/** The newest file in the import folder, or null when there is none. */
	public static Path newestImport(Path configDir) {
		if (configDir == null) {
			return null;
		}
		Path dir = importFolder(configDir);
		if (!Files.isDirectory(dir)) {
			try {
				Files.createDirectories(dir);
			} catch (IOException ignored) {
				// shown as "no code found" all the same
			}
			return null;
		}
		Path newest = null;
		FileTime newestTime = null;
		try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
			for (Path f : files) {
				String n = f.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
				if (!Files.isRegularFile(f) || n.endsWith(".imported") || Files.size(f) > MAX_FILE) {
					continue;
				}
				FileTime time = Files.getLastModifiedTime(f);
				if (newestTime == null || time.compareTo(newestTime) > 0) {
					newest = f;
					newestTime = time;
				}
			}
		} catch (IOException e) {
			return null;
		}
		return newest;
	}

	public static String read(Path file) {
		try {
			return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
		} catch (IOException e) {
			return null;
		}
	}

	/** Marks an imported file so the next import does not take it again. */
	public static void markImported(Path file) {
		try {
			Files.move(file, file.resolveSibling(file.getFileName() + ".imported"), StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException ignored) {
			// imported again next time at worst
		}
	}

	/** A file name for a profile: letters, digits, spaces, - and _ kept, the rest made _. */
	static String fileName(String name) {
		String s = name == null ? "" : name.replaceAll("[^A-Za-z0-9 _-]", "_").trim();
		return s.isEmpty() ? "profile" : s;
	}
}
