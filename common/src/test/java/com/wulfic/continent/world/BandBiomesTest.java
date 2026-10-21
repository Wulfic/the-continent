package com.wulfic.continent.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.wulfic.continent.layout.DefaultLayout;
import com.wulfic.continent.layout.VanillaBiomes;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.core.registries.Registries;
import org.junit.jupiter.api.Test;

/** The band's biome list against the biomes this band's vanilla data actually registers. */
class BandBiomesTest {
	/**
	 * Every biome of the oldest band, 26.1, read from the 26.1.2 jar's {@code data/minecraft/worldgen/biome} on
	 * 2026-10-20. Whatever a band registers beyond these is new to it, and has to be in {@link VanillaBiomes#NEWER}.
	 */
	private static final Set<String> FIRST_BAND = Stream.of("badlands", "bamboo_jungle", "basalt_deltas", "beach",
			"birch_forest", "cherry_grove", "cold_ocean", "crimson_forest", "dark_forest", "deep_cold_ocean",
			"deep_dark", "deep_frozen_ocean", "deep_lukewarm_ocean", "deep_ocean", "desert", "dripstone_caves",
			"end_barrens", "end_highlands", "end_midlands", "eroded_badlands", "flower_forest", "forest",
			"frozen_ocean", "frozen_peaks", "frozen_river", "grove", "ice_spikes", "jagged_peaks", "jungle",
			"lukewarm_ocean", "lush_caves", "mangrove_swamp", "meadow", "mushroom_fields", "nether_wastes", "ocean",
			"old_growth_birch_forest", "old_growth_pine_taiga", "old_growth_spruce_taiga", "pale_garden", "plains",
			"river", "savanna", "savanna_plateau", "small_end_islands", "snowy_beach", "snowy_plains", "snowy_slopes",
			"snowy_taiga", "soul_sand_valley", "sparse_jungle", "stony_peaks", "stony_shore", "sunflower_plains",
			"swamp", "taiga", "the_end", "the_void", "warm_ocean", "warped_forest", "windswept_forest",
			"windswept_gravelly_hills", "windswept_hills", "windswept_savanna", "wooded_badlands")
			.map(id -> "minecraft:" + id).collect(Collectors.toUnmodifiableSet());

	private static Set<String> vanillaBiomes() {
		return TestWorlds.LOOKUP.lookupOrThrow(Registries.BIOME).listElementIds()
				.map(key -> key.identifier().toString()).collect(Collectors.toSet());
	}

	/**
	 * Both ways, against everything vanilla added since 26.1: a band can't claim a biome its game lacks, or leave out
	 * one its game has, even one nobody has put in {@link VanillaBiomes#NEWER} yet.
	 */
	@Test
	void theBandListsExactlyTheBiomesItsGameAddedSinceTheFirstBand() {
		Set<String> added = new HashSet<>(vanillaBiomes());
		added.removeAll(FIRST_BAND);
		assertEquals(added, VanillaBiomes.inThisBand());
	}

	/** No band drops a 26.1 biome, so the first band's map stays valid in every band. */
	@Test
	void everyFirstBandBiomeIsStillRegistered() {
		Set<String> dropped = new HashSet<>(FIRST_BAND);
		dropped.removeAll(vanillaBiomes());
		assertTrue(dropped.isEmpty(), "this band's vanilla no longer registers " + dropped);
	}

	@Test
	void theBandListIsPartOfTheNewerBiomes() {
		assertTrue(VanillaBiomes.NEWER.containsAll(VanillaBiomes.inThisBand()), VanillaBiomes.inThisBand().toString());
	}

	@Test
	void everyBiomeOnThisBandsMapIsVanilla() {
		Set<String> unknown = new HashSet<>(DefaultLayout.create().biomeIds());
		unknown.removeAll(vanillaBiomes());
		assertTrue(unknown.isEmpty(), "not in this band's vanilla registry: " + unknown);
	}
}
