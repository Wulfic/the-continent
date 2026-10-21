package com.wulfic.continent.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfic.continent.layout.DefaultLayout;
import com.wulfic.continent.layout.Point;
import com.wulfic.continent.layout.VanillaBiomes;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class MapRendererTest {
	/** East half desert, west half plains: lets a test check orientation and colour together. */
	private static final MapRenderer.BiomeLookup HALVES = (x, z) -> x >= 0 ? VanillaBiomes.DESERT : VanillaBiomes.PLAINS;

	@Test
	void sizeAndOrientationFollowTheArguments() {
		BufferedImage image = MapRenderer.render(64, 8, HALVES, null);
		assertEquals(16, image.getWidth());
		assertEquals(16, image.getHeight());
		assertEquals(BiomePalette.color(VanillaBiomes.PLAINS), image.getRGB(0, 0) & 0xFFFFFF, "west edge");
		assertEquals(BiomePalette.color(VanillaBiomes.DESERT), image.getRGB(15, 15) & 0xFFFFFF, "east edge");
	}

	@Test
	void pixelCentresMapToTheBlocksTheyCover() {
		assertEquals(-60, MapRenderer.blockOf(0, 64, 8));
		assertEquals(60, MapRenderer.blockOf(15, 64, 8));
		assertEquals(0, MapRenderer.pixelOf(-64, 64, 8));
		assertEquals(8, MapRenderer.pixelOf(0, 64, 8));
	}

	@Test
	void markerIsDrawnAtItsBlock() {
		Point anchor = DefaultLayout.create().villageAnchor();
		BufferedImage image = MapRenderer.render(256, 4, HALVES, anchor);
		int px = MapRenderer.pixelOf(anchor.x(), 256, 4);
		int pz = MapRenderer.pixelOf(anchor.z(), 256, 4);
		assertEquals(MapRenderer.MARKER_CORE, image.getRGB(px, pz) & 0xFFFFFF);
		assertEquals(MapRenderer.MARKER_RING, image.getRGB(px + MapRenderer.MARKER_HALF + 1, pz) & 0xFFFFFF);
	}

	@Test
	void everyLayoutBiomeHasItsOwnColour() {
		long distinct = DefaultLayout.create().biomeIds().stream().map(BiomePalette::color).distinct().count();
		assertEquals(DefaultLayout.create().biomeIds().size(), distinct, "two biomes share a colour");
		assertTrue(DefaultLayout.create().biomeIds().stream().noneMatch(b -> BiomePalette.color(b) == BiomePalette.UNKNOWN));
	}

	@Test
	void pngRoundTrips() throws IOException {
		BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(MapRenderer.png(MapRenderer.render(32, 4, HALVES, null))));
		assertEquals(16, decoded.getWidth());
	}

	@Test
	void refusesOutOfRangeSettings() {
		assertThrows(IllegalArgumentException.class, () -> MapRenderer.side(1600, 0));
		assertThrows(IllegalArgumentException.class, () -> MapRenderer.side(1600, MapRenderer.MAX_BLOCKS_PER_PIXEL + 1));
		assertThrows(IllegalArgumentException.class, () -> MapRenderer.side(MapRenderer.MIN_RADIUS - 1, 1));
		assertThrows(IllegalArgumentException.class, () -> MapRenderer.side(MapRenderer.MAX_RADIUS + 1, 64));
		IllegalArgumentException tooBig = assertThrows(IllegalArgumentException.class, () -> MapRenderer.side(4096, 1));
		assertTrue(tooBig.getMessage().contains("raise blocksPerPixel"), tooBig.getMessage());
		assertEquals(800, MapRenderer.side(1600, 4));
	}
}
