package com.wulfic.continent.layout;

import java.util.LinkedHashSet;
import java.util.Set;

/** Vanilla biome ids used by the layout, as strings so this package stays free of Minecraft types. */
public final class VanillaBiomes {
	public static final String PLAINS = "minecraft:plains";
	public static final String SUNFLOWER_PLAINS = "minecraft:sunflower_plains";
	public static final String FOREST = "minecraft:forest";
	public static final String RIVER = "minecraft:river";
	public static final String TAIGA = "minecraft:taiga";
	public static final String OLD_GROWTH_PINE_TAIGA = "minecraft:old_growth_pine_taiga";
	public static final String GROVE = "minecraft:grove";
	public static final String SNOWY_SLOPES = "minecraft:snowy_slopes";
	public static final String JAGGED_PEAKS = "minecraft:jagged_peaks";
	public static final String BIRCH_FOREST = "minecraft:birch_forest";
	public static final String MEADOW = "minecraft:meadow";
	public static final String CHERRY_GROVE = "minecraft:cherry_grove";
	public static final String SAVANNA = "minecraft:savanna";
	public static final String DESERT = "minecraft:desert";
	public static final String BADLANDS = "minecraft:badlands";
	public static final String DARK_FOREST = "minecraft:dark_forest";
	public static final String PALE_GARDEN = "minecraft:pale_garden";
	public static final String SWAMP = "minecraft:swamp";
	public static final String MANGROVE_SWAMP = "minecraft:mangrove_swamp";
	public static final String JUNGLE = "minecraft:jungle";
	public static final String BAMBOO_JUNGLE = "minecraft:bamboo_jungle";
	public static final String MUSHROOM_FIELDS = "minecraft:mushroom_fields";
	public static final String BEACH = "minecraft:beach";
	public static final String OCEAN = "minecraft:ocean";
	public static final String DEEP_OCEAN = "minecraft:deep_ocean";
	public static final String WARM_OCEAN = "minecraft:warm_ocean";
	public static final String FROZEN_OCEAN = "minecraft:frozen_ocean";
	public static final String LUSH_CAVES = "minecraft:lush_caves";
	public static final String DRIPSTONE_CAVES = "minecraft:dripstone_caves";
	public static final String DEEP_DARK = "minecraft:deep_dark";
	/** New in 26.2: the only natural source of cinnabar, sulfur, potent sulfur and sulfur spikes. */
	public static final String SULFUR_CAVES = "minecraft:sulfur_caves";
	/** New in 26.3: the only natural source of poplar, its three leaf colours, red shrubs and shelf mushrooms. */
	public static final String DAPPLED_FOREST = "minecraft:dappled_forest";

	/** Every biome newer than the oldest band (26.1). The map has each one only in the bands whose game has it. */
	public static final Set<String> NEWER = Set.of(SULFUR_CAVES, DAPPLED_FOREST);

	private VanillaBiomes() {
	}

	/** The biomes of {@link #NEWER} that this band's game has. {@code BandBiomesTest} holds it to vanilla's own list. */
	public static Set<String> inThisBand() {
		Set<String> ids = new LinkedHashSet<>();
		//? if >=26.2 {
		/*ids.add(SULFUR_CAVES);
		*///?}
		//? if >=26.3 {
		/*ids.add(DAPPLED_FOREST);
		*///?}
		return Set.copyOf(ids);
	}
}
