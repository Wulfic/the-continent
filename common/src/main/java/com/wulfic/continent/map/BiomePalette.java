package com.wulfic.continent.map;

import static com.wulfic.continent.layout.VanillaBiomes.*;

import java.util.Map;

/** Map colours (0xRRGGBB) for every biome the layout can emit, close to their in-game look. */
public final class BiomePalette {
	/** Anything not listed, so an unexpected biome stands out on the map. */
	public static final int UNKNOWN = 0xFF00FF;

	private static final Map<String, Integer> COLORS = Map.ofEntries(
			Map.entry(PLAINS, 0x8DB360), Map.entry(SUNFLOWER_PLAINS, 0xB5DB88), Map.entry(FOREST, 0x056621),
			Map.entry(RIVER, 0x3F76E4), Map.entry(TAIGA, 0x0B6659), Map.entry(OLD_GROWTH_PINE_TAIGA, 0x596651),
			Map.entry(GROVE, 0x7FA87F), Map.entry(SNOWY_SLOPES, 0xD8DEEE), Map.entry(JAGGED_PEAKS, 0xFFFFFF),
			Map.entry(BIRCH_FOREST, 0x307444), Map.entry(MEADOW, 0x83BB6D), Map.entry(CHERRY_GROVE, 0xF2A9C8),
			Map.entry(SAVANNA, 0xBDB25F), Map.entry(DESERT, 0xFA9418), Map.entry(BADLANDS, 0xD94515),
			Map.entry(DARK_FOREST, 0x40511A), Map.entry(PALE_GARDEN, 0x9EA39A), Map.entry(SWAMP, 0x07F9B2),
			Map.entry(MANGROVE_SWAMP, 0x2CCC8E), Map.entry(JUNGLE, 0x537B09), Map.entry(BAMBOO_JUNGLE, 0x768E14),
			Map.entry(MUSHROOM_FIELDS, 0xB25FB2), Map.entry(BEACH, 0xFADE55), Map.entry(OCEAN, 0x000070),
			Map.entry(DEEP_OCEAN, 0x000030), Map.entry(WARM_OCEAN, 0x0099C8), Map.entry(FROZEN_OCEAN, 0x7070D6),
			Map.entry(LUSH_CAVES, 0x7BA331), Map.entry(DRIPSTONE_CAVES, 0x866043), Map.entry(DEEP_DARK, 0x0F2B2B),
			Map.entry(SULFUR_CAVES, 0xD9C23A), Map.entry(DAPPLED_FOREST, 0xC8702A));

	private BiomePalette() {
	}

	public static int color(String biome) {
		return COLORS.getOrDefault(biome, UNKNOWN);
	}
}
