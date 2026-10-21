package com.wulfic.continent.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Every band's map, checked in every band: the 26.1 map (no newer biomes), 26.2 (+ Sulfur Caves) and 26.3
 * (+ Dappled Forest). {@link ContinentLayoutTest} checks this band's map in depth; this checks the others hold too.
 */
class BandLayoutsTest {
	/** {@link #fingerprint} of the map before Stage C added any biome, taken on 2026-10-20. */
	private static final long FIRST_BAND_FINGERPRINT = -8799973257437191727L;
	private static final long[] SEEDS = {0L, 1L, 12345L, -4172144997902289642L};
	private static final double MIN_REGION_AREA = 40_000;
	private static final double MIN_SUNFLOWER_AREA = 10_000;

	static Stream<Set<String>> bands() {
		return Stream.of(Set.of(), Set.of(VanillaBiomes.SULFUR_CAVES),
				Set.of(VanillaBiomes.SULFUR_CAVES, VanillaBiomes.DAPPLED_FOREST));
	}

	@Test
	void theFirstBandsMapIsUnchanged() {
		assertEquals(FIRST_BAND_FINGERPRINT, fingerprint(DefaultLayout.terrain(Set.of())));
	}

	@Test
	void thisBandsMapHasExactlyItsOwnNewerBiomes() {
		Set<String> ids = DefaultLayout.create().biomeIds();
		for (String newer : VanillaBiomes.NEWER) {
			assertEquals(VanillaBiomes.inThisBand().contains(newer), ids.contains(newer), newer);
		}
	}

	@Test
	void anIdThatIsNotANewerBiomeIsRefused() {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> DefaultLayout.create(Set.of("minecraft:sulfur_cave")));
		assertTrue(e.getMessage().contains("minecraft:sulfur_cave"), e.getMessage());
	}

	/** Every surface region and every cave biome has room for its features, on every seed. */
	@ParameterizedTest
	@MethodSource("bands")
	void everyRegionAndCaveBiomeClearsItsFloor(Set<String> newer) {
		ContinentLayout layout = DefaultLayout.terrain(newer).layout();
		int step = 8;
		for (long seed : SEEDS) {
			Map<String, Double> surface = new HashMap<>();
			Map<String, Double> cave = new HashMap<>();
			for (int x = -1600; x <= 1600; x += step) {
				for (int z = -1600; z <= 1600; z += step) {
					LayoutSample s = layout.sample(x, z, seed);
					surface.merge(s.biome(), (double) step * step, Double::sum);
					if (s.zone() == LayoutSample.Zone.LAND) {
						cave.merge(s.caveBiome(), (double) step * step, Double::sum);
					}
				}
			}
			for (BiomeSeed s : layout.seeds()) {
				double floor = s.biome().equals(VanillaBiomes.SUNFLOWER_PLAINS) ? MIN_SUNFLOWER_AREA : MIN_REGION_AREA;
				double area = surface.getOrDefault(s.biome(), 0.0);
				assertTrue(area >= floor, s.biome() + " covers " + area + " < " + floor + " on seed " + seed);
			}
			for (BiomeSeed s : layout.caves().seeds()) {
				double area = cave.getOrDefault(s.biome(), 0.0);
				assertTrue(area >= MIN_REGION_AREA, s.biome() + " covers " + area + " under land on seed " + seed);
			}
		}
	}

	@ParameterizedTest
	@MethodSource("bands")
	void biomeIdsAreExactlyWhatSampleEmits(Set<String> newer) {
		ContinentLayout layout = DefaultLayout.create(newer);
		Set<String> emitted = new HashSet<>();
		for (int x = -1600; x <= 1600; x += 8) {
			for (int z = -1600; z <= 1600; z += 8) {
				LayoutSample s = layout.sample(x, z, 12345L);
				emitted.add(s.biome());
				emitted.add(s.caveBiome());
				emitted.add(s.deepCaveBiome());
			}
		}
		assertEquals(layout.biomeIds(), emitted);
	}

	@ParameterizedTest
	@MethodSource("bands")
	void theNewBiomesSitWherePlanned(Set<String> newer) {
		ContinentLayout layout = DefaultLayout.create(newer);
		for (long seed : SEEDS) {
			// Sulfur Caves under the north-east hills, above the Deep Dark further north.
			LayoutSample hills = layout.sample(650, -200, seed);
			assertEquals(newer.contains(VanillaBiomes.SULFUR_CAVES) ? VanillaBiomes.SULFUR_CAVES
					: VanillaBiomes.DRIPSTONE_CAVES, hills.caveBiome(), "cave under (650, -200)");
			LayoutSample north = layout.sample(600, -600, seed);
			assertEquals(VanillaBiomes.DEEP_DARK, north.deepCaveBiome(), "deep cave under (600, -600)");
			// Dappled Forest between the pine taiga, the meadow and the snowy slopes.
			String forest = layout.sample(420, -520, seed).biome();
			if (newer.contains(VanillaBiomes.DAPPLED_FOREST)) {
				assertEquals(VanillaBiomes.DAPPLED_FOREST, forest);
			} else {
				assertTrue(!forest.equals(VanillaBiomes.DAPPLED_FOREST), forest);
			}
		}
	}

	/** Every layer of the map and every terrain input, on a 16-block grid over two seeds, folded into one number. */
	static long fingerprint(ContinentTerrain terrain) {
		long h = 1;
		for (long seed : new long[] {0L, -4172144997902289642L}) {
			for (int x = -1600; x <= 1600; x += 16) {
				for (int z = -1600; z <= 1600; z += 16) {
					LayoutSample s = terrain.layout().sample(x, z, seed);
					h = 31 * h + (s.biome() + '|' + s.caveBiome() + '|' + s.deepCaveBiome()).hashCode();
					TerrainSample t = terrain.sample(x, z, seed);
					for (double v : new double[] {t.continentalness(), t.erosion(), t.ridges(), t.erosionDetail(),
							t.ridgesDetail(), t.uplift(), t.upliftDetail(), t.detail()}) {
						h = 31 * h + Double.doubleToLongBits(v);
					}
				}
			}
		}
		return h;
	}
}
