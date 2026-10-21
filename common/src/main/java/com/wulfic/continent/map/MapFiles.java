package com.wulfic.continent.map;

import com.wulfic.continent.Continent;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The one place this mod writes files. It only ever creates a new file directly inside
 * {@code <world>/continent-maps/}: it never overwrites, never deletes, and refuses any name or folder
 * that would resolve anywhere else. Every refusal and every write is logged with the path.
 */
public final class MapFiles {
	public static final String FOLDER = "continent-maps";

	private static final Logger LOGGER = LoggerFactory.getLogger(Continent.MOD_ID);
	private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9._-]+\\.png");

	private MapFiles() {
	}

	/**
	 * Writes {@code bytes} to {@code <worldDir>/continent-maps/<name>}, creating the folder if needed.
	 *
	 * @return the file written
	 * @throws IllegalArgumentException if {@code name} is not a plain {@code .png} file name, or the folder
	 *         resolves outside {@code worldDir} (for example through a symlink); nothing is written
	 * @throws FileAlreadyExistsException if the file exists; it is left untouched
	 */
	public static Path writeNew(Path worldDir, String name, byte[] bytes) throws IOException {
		if (name == null || !SAFE_NAME.matcher(name).matches() || name.contains("..")) {
			throw refuse("map file name \"" + name + "\" is not a plain .png name");
		}
		Path world = worldDir.toRealPath();
		Path folder = world.resolve(FOLDER);
		Files.createDirectories(folder);
		// Real paths, so a symlinked folder can't point the write somewhere else. Fail closed.
		Path realFolder = folder.toRealPath();
		if (!realFolder.startsWith(world)) {
			throw refuse(folder + " resolves to " + realFolder + ", outside the world folder " + world);
		}
		Path target = realFolder.resolve(name).normalize();
		if (!target.getParent().equals(realFolder)) {
			throw refuse("map file " + target + " is not directly inside " + realFolder);
		}
		LOGGER.info("[{}] writing {} bytes to new map file {}", Continent.MOD_ID, bytes.length, target);
		try {
			Files.write(target, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
		} catch (FileAlreadyExistsException e) {
			LOGGER.error("[{}] refusing to overwrite existing map file {}", Continent.MOD_ID, target);
			throw e;
		}
		LOGGER.info("[{}] wrote map file {}", Continent.MOD_ID, target);
		return target;
	}

	private static IllegalArgumentException refuse(String message) {
		LOGGER.error("[{}] refusing to write a map: {}", Continent.MOD_ID, message);
		return new IllegalArgumentException(message);
	}
}
