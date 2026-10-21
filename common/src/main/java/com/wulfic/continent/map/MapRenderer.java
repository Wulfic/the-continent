package com.wulfic.continent.map;

import com.wulfic.continent.Continent;
import com.wulfic.continent.layout.Point;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Renders a top-down biome map centred on the origin: one pixel per {@code blocksPerPixel} blocks, north
 * up. Pure Java, so the limits and colours are unit-tested without a game.
 */
public final class MapRenderer {
	public static final int MIN_RADIUS = 16;
	public static final int MAX_RADIUS = 8192;
	public static final int MAX_BLOCKS_PER_PIXEL = 64;
	public static final int MAX_SIDE = 4096;
	/** Half-width, in pixels, of the square drawn on a marker. */
	static final int MARKER_HALF = 2;
	static final int MARKER_RING = 0x000000;
	static final int MARKER_CORE = 0xFF2020;

	private static final Logger LOGGER = LoggerFactory.getLogger(Continent.MOD_ID);

	/** The surface biome id at a block column. Called from the rendering thread, so it must be thread-safe. */
	@FunctionalInterface
	public interface BiomeLookup {
		String biomeAt(int x, int z);
	}

	private MapRenderer() {
	}

	/** Image side in pixels for these settings, after checking them. */
	public static int side(int radius, int blocksPerPixel) {
		require(blocksPerPixel >= 1 && blocksPerPixel <= MAX_BLOCKS_PER_PIXEL,
				"blocksPerPixel " + blocksPerPixel + " is outside 1-" + MAX_BLOCKS_PER_PIXEL);
		require(radius >= MIN_RADIUS && radius <= MAX_RADIUS, "radius " + radius + " is outside " + MIN_RADIUS + "-" + MAX_RADIUS);
		int side = 2 * radius / blocksPerPixel;
		require(side <= MAX_SIDE, "a " + side + "-pixel map is over the " + MAX_SIDE + "-pixel limit; raise blocksPerPixel");
		return side;
	}

	/**
	 * @param marker a block position to mark, or null for none
	 */
	public static BufferedImage render(int radius, int blocksPerPixel, BiomeLookup lookup, Point marker) {
		int side = side(radius, blocksPerPixel);
		BufferedImage image = new BufferedImage(side, side, BufferedImage.TYPE_INT_RGB);
		for (int pz = 0; pz < side; pz++) {
			for (int px = 0; px < side; px++) {
				image.setRGB(px, pz, BiomePalette.color(lookup.biomeAt(blockOf(px, radius, blocksPerPixel),
						blockOf(pz, radius, blocksPerPixel))));
			}
		}
		if (marker != null) {
			drawMarker(image, pixelOf(marker.x(), radius, blocksPerPixel), pixelOf(marker.z(), radius, blocksPerPixel));
		}
		return image;
	}

	public static byte[] png(BufferedImage image) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			if (!ImageIO.write(image, "png", out)) {
				throw new IllegalStateException("no PNG writer available");
			}
		} catch (IOException e) {
			LOGGER.error("[{}] encoding a map as PNG failed", Continent.MOD_ID, e);
			throw new UncheckedIOException(e);
		}
		return out.toByteArray();
	}

	/** The block at the centre of pixel {@code p}. */
	static int blockOf(int p, int radius, int blocksPerPixel) {
		return -radius + p * blocksPerPixel + blocksPerPixel / 2;
	}

	static int pixelOf(double block, int radius, int blocksPerPixel) {
		return (int) Math.floor((block + radius) / blocksPerPixel);
	}

	private static void drawMarker(BufferedImage image, int cx, int cz) {
		for (int dz = -MARKER_HALF - 1; dz <= MARKER_HALF + 1; dz++) {
			for (int dx = -MARKER_HALF - 1; dx <= MARKER_HALF + 1; dx++) {
				int x = cx + dx;
				int z = cz + dz;
				if (x >= 0 && z >= 0 && x < image.getWidth() && z < image.getHeight()) {
					boolean ring = Math.abs(dx) > MARKER_HALF || Math.abs(dz) > MARKER_HALF;
					image.setRGB(x, z, ring ? MARKER_RING : MARKER_CORE);
				}
			}
		}
	}

	private static void require(boolean ok, String message) {
		if (!ok) {
			LOGGER.warn("[{}] map refused: {}", Continent.MOD_ID, message);
			throw new IllegalArgumentException(message);
		}
	}
}
