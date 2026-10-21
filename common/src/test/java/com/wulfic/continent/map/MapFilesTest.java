package com.wulfic.continent.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The only file-writing code in the mod: it must create, never overwrite, and never leave its folder. */
class MapFilesTest {
	private static final byte[] PNG = {1, 2, 3, 4};

	@TempDir
	Path root;

	private Path world() throws IOException {
		return Files.createDirectories(root.resolve("world"));
	}

	private List<Path> everyFileUnder(Path dir) throws IOException {
		try (Stream<Path> files = Files.walk(dir)) {
			return files.filter(Files::isRegularFile).toList();
		}
	}

	@Test
	void writesANewFileInsideTheMapFolder() throws IOException {
		Path world = world();
		Path written = MapFiles.writeNew(world, "map-1.png", PNG);
		assertEquals(world.toRealPath().resolve(MapFiles.FOLDER).resolve("map-1.png"), written);
		assertArrayEquals(PNG, Files.readAllBytes(written));
	}

	@Test
	void refusesToOverwriteAndLeavesTheOriginalUntouched() throws IOException {
		Path world = world();
		Path first = MapFiles.writeNew(world, "map-1.png", PNG);
		assertThrows(FileAlreadyExistsException.class, () -> MapFiles.writeNew(world, "map-1.png", new byte[] {9, 9}));
		assertArrayEquals(PNG, Files.readAllBytes(first), "existing map was modified");
	}

	@ParameterizedTest
	@ValueSource(strings = {"../escape.png", "..png", "sub/map.png", "sub\\map.png", "/tmp/abs.png", "map.txt", "map.png.exe",
			".png", "", " map.png", "map\t.png"})
	void refusesAnythingButAPlainPngNameAndWritesNothing(String name) throws IOException {
		Path world = world();
		assertThrows(IllegalArgumentException.class, () -> MapFiles.writeNew(world, name, PNG));
		assertTrue(everyFileUnder(root).isEmpty(), "something was written: " + everyFileUnder(root));
	}

	@Test
	void refusesANullName() throws IOException {
		Path world = world();
		assertThrows(IllegalArgumentException.class, () -> MapFiles.writeNew(world, null, PNG));
		assertTrue(everyFileUnder(root).isEmpty());
	}

	/** A map folder that is a symlink to somewhere else must not become a way out of the world folder. */
	@Test
	void refusesAMapFolderThatLinksOutsideTheWorld() throws IOException, InterruptedException {
		Path world = world();
		Path outside = Files.createDirectories(root.resolve("outside"));
		linkDirectory(world.resolve(MapFiles.FOLDER), outside);
		assertThrows(IllegalArgumentException.class, () -> MapFiles.writeNew(world, "map-1.png", PNG));
		assertFalse(Files.exists(outside.resolve("map-1.png")), "wrote through the link");
		assertTrue(everyFileUnder(outside).isEmpty());
	}

	/**
	 * Links {@code link} to the directory {@code target} with a symlink. Windows refuses symlinks without Developer
	 * Mode or admin rights, so there it falls back to a directory junction, which needs neither and leads out of the
	 * folder just the same.
	 */
	private static void linkDirectory(Path link, Path target) throws IOException, InterruptedException {
		try {
			Files.createSymbolicLink(link, target);
		} catch (FileSystemException refused) {
			if (!System.getProperty("os.name").startsWith("Windows")) {
				throw refused;
			}
			Process mklink = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
					.redirectErrorStream(true).start();
			String output = new String(mklink.getInputStream().readAllBytes());
			assertEquals(0, mklink.waitFor(), "the symlink was refused (" + refused.getMessage()
					+ ") and so was the junction: " + output);
		}
	}
}
