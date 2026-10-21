package com.wulfic.continent.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContinentLayoutTest {
	private static final ContinentLayout LAYOUT = DefaultLayout.create();
	private static final long[] SEEDS = {0L, 1L, 12345L, -4172144997902289642L};
	/** Blocks² a land region needs to host its biome's features. */
	private static final double MIN_REGION_AREA = 40_000;
	private static final double MIN_SUNFLOWER_AREA = 10_000;
	private static final double MIN_FILLER_AREA = 5_000;

	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void originIsPlains(long seed) {
		LayoutSample sample = LAYOUT.sample(0, 0, seed);
		assertEquals(VanillaBiomes.PLAINS, sample.biome());
		assertEquals(LayoutSample.Zone.LAND, sample.zone());
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void everyBiomeIsReachableWithEnoughArea(long seed) {
		int step = 8;
		Map<String, Double> area = new HashMap<>();
		for (int x = -1600; x <= 1600; x += step) {
			for (int z = -1600; z <= 1600; z += step) {
				area.merge(LAYOUT.sample(x, z, seed).biome(), (double) step * step, Double::sum);
			}
		}

		Set<String> expected = new HashSet<>();
		LAYOUT.seeds().forEach(s -> expected.add(s.biome()));
		LAYOUT.islands().forEach(i -> expected.add(i.biome()));
		expected.addAll(List.of(VanillaBiomes.BEACH, VanillaBiomes.RIVER, VanillaBiomes.OCEAN,
				VanillaBiomes.DEEP_OCEAN, VanillaBiomes.WARM_OCEAN, VanillaBiomes.FROZEN_OCEAN));
		assertEquals(expected, area.keySet(), "sampled biomes differ from the layout's biome set");

		for (BiomeSeed s : LAYOUT.seeds()) {
			double floor = s.biome().equals(VanillaBiomes.SUNFLOWER_PLAINS) ? MIN_SUNFLOWER_AREA : MIN_REGION_AREA;
			assertTrue(area.get(s.biome()) >= floor, s.biome() + " area " + area.get(s.biome()) + " < " + floor);
		}
		for (Island i : LAYOUT.islands()) {
			assertTrue(area.get(i.biome()) >= MIN_REGION_AREA, i.biome() + " area " + area.get(i.biome()));
		}
		for (String filler : List.of(VanillaBiomes.BEACH, VanillaBiomes.RIVER)) {
			assertTrue(area.get(filler) >= MIN_FILLER_AREA, filler + " area " + area.get(filler));
		}
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void southEastAndWestCoastsAreBeach(long seed) {
		// Rays avoid the river mouth at (1000, 350).
		for (double[] dir : new double[][] {{0, 1}, {1, 0}, {-1, 0}, {0.6, 0.8}, {-0.8, 0.6}}) {
			List<LayoutSample> ray = ray(dir[0], dir[1], seed);
			assertEquals(VanillaBiomes.BEACH, lastLand(ray).biome(), "no beach on ray " + dir[0] + "," + dir[1]);
		}
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void northCoastDropsIntoFrozenOcean(long seed) {
		List<LayoutSample> ray = ray(0, -1, seed);
		assertNotEquals(VanillaBiomes.BEACH, lastLand(ray).biome(), "north coast must not have a beach");
		assertEquals(VanillaBiomes.FROZEN_OCEAN, firstSea(ray).biome());
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void southAndEastBandsAreWarmOceanThenOceanThenDeepOcean(long seed) {
		for (double[] dir : new double[][] {{0, 1}, {1, 0}}) {
			List<String> seaBiomes = distinctRun(ray(dir[0], dir[1], seed).stream()
					.filter(s -> s.coastDistance() < 0).map(LayoutSample::biome).toList());
			assertEquals(List.of(VanillaBiomes.WARM_OCEAN, VanillaBiomes.OCEAN, VanillaBiomes.DEEP_OCEAN), seaBiomes,
					"sea sequence on ray " + dir[0] + "," + dir[1]);
		}
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void farPointsAreDeepOcean(long seed) {
		for (double[] p : new double[][] {{0, -5000}, {5000, 0}, {0, 5000}, {-5000, 0}, {30_000, -30_000}}) {
			LayoutSample sample = LAYOUT.sample(p[0], p[1], seed);
			assertEquals(VanillaBiomes.DEEP_OCEAN, sample.biome(), "at " + p[0] + "," + p[1]);
			assertEquals(LayoutSample.Zone.DEEP_OCEAN, sample.zone());
		}
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void mushroomIslandIsRingedByOcean(long seed) {
		Island island = LAYOUT.islands().get(0);
		assertEquals(VanillaBiomes.MUSHROOM_FIELDS, island.biome());
		assertEquals(VanillaBiomes.MUSHROOM_FIELDS, LAYOUT.sample(island.x(), island.z(), seed).biome());
		double ring = island.radius() + LAYOUT.warpAmplitude() + 4;
		for (int deg = 0; deg < 360; deg += 5) {
			double a = Math.toRadians(deg);
			LayoutSample s = LAYOUT.sample(island.x() + ring * Math.cos(a), island.z() + ring * Math.sin(a), seed);
			assertTrue(s.zone() == LayoutSample.Zone.OCEAN || s.zone() == LayoutSample.Zone.DEEP_OCEAN,
					"at " + deg + "° around the island: " + s.biome());
		}
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void villageAnchorIsSafelyInsidePlains(long seed) {
		Point anchor = LAYOUT.villageAnchor();
		// Check the whole 32-block footprint around the anchor, not just its centre.
		for (int dx = -16; dx <= 16; dx += 4) {
			for (int dz = -16; dz <= 16; dz += 4) {
				LayoutSample s = LAYOUT.sample(anchor.x() + dx, anchor.z() + dz, seed);
				assertEquals(VanillaBiomes.PLAINS, s.biome());
				assertTrue(s.borderDistance() >= ContinentLayout.VILLAGE_MARGIN - 16, "border " + s.borderDistance());
				assertTrue(s.riverDistance() >= ContinentLayout.VILLAGE_MARGIN, "river " + s.riverDistance());
			}
		}
		LayoutSample centre = LAYOUT.sample(anchor.x(), anchor.z(), seed);
		assertTrue(centre.borderDistance() >= ContinentLayout.VILLAGE_MARGIN, "border " + centre.borderDistance());
	}

	@Test
	void sameSeedGivesSameOutput() {
		ContinentLayout copy = DefaultLayout.create();
		for (long seed : SEEDS) {
			for (int x = -1200; x <= 1200; x += 97) {
				for (int z = -1200; z <= 1200; z += 89) {
					assertEquals(LAYOUT.sample(x, z, seed), copy.sample(x, z, seed));
				}
			}
		}
	}

	@Test
	void seedOnlyVariesLocalDetail() {
		int differing = 0;
		int total = 0;
		for (int x = -1000; x <= 1000; x += 16) {
			for (int z = -1000; z <= 1000; z += 16) {
				total++;
				if (!LAYOUT.sample(x, z, 1L).biome().equals(LAYOUT.sample(x, z, 2L).biome())) {
					differing++;
				}
			}
		}
		assertTrue(differing > 0, "the warp ignores the seed");
		assertTrue(differing < total / 4, "seeds disagree on " + differing + "/" + total + " samples: not local detail");
	}

	/** The coast is hand-laid with bays and headlands, not a circle with a 32-block wobble. */
	@Test
	void theCoastHasBaysAndHeadlands() {
		Coastline coast = LAYOUT.coastline();
		double nearest = Double.MAX_VALUE;
		double farthest = 0;
		for (int deg = 0; deg < 360; deg++) {
			double r = coast.radius(Math.toRadians(deg));
			nearest = Math.min(nearest, r);
			farthest = Math.max(farthest, r);
		}
		assertTrue(farthest - nearest >= 200, "shore radius only ranges " + nearest + "-" + farthest);
		// Named in DefaultLayout: the Badlands peninsula juts past the SE bay beside it.
		assertTrue(coast.radius(Math.toRadians(150)) - coast.radius(Math.toRadians(120)) >= 200, "no Badlands peninsula");
		assertTrue(coast.radius(Math.toRadians(0)) > coast.radius(Math.toRadians(30)) + 150, "no Jagged Peaks headland");
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void bayAndPeninsulaShowInTheBiomes(long seed) {
		// 1050 blocks out: sea in the SE bay (bearing 120°), land on the Badlands peninsula (bearing 150°).
		LayoutSample bay = LAYOUT.sample(1050 * Math.sin(Math.toRadians(120)), -1050 * Math.cos(Math.toRadians(120)), seed);
		LayoutSample peninsula = LAYOUT.sample(1050 * Math.sin(Math.toRadians(150)), -1050 * Math.cos(Math.toRadians(150)), seed);
		assertTrue(bay.coastDistance() < 0, "the SE bay is land: " + bay.biome());
		assertTrue(peninsula.coastDistance() > 0, "the Badlands peninsula is sea: " + peninsula.biome());
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void cavesFollowTheMap(long seed) {
		// Lush under the west, southwest and centre; Dripstone under the east and south. The north-east is
		// Sulfur Caves from 26.2 on (BandLayoutsTest), so no point here sits there.
		for (double[] p : new double[][] {{0, 0}, {-350, 450}, {-650, -250}, {-450, 150}}) {
			assertEquals(VanillaBiomes.LUSH_CAVES, LAYOUT.sample(p[0], p[1], seed).caveBiome(), "at " + p[0] + "," + p[1]);
		}
		for (double[] p : new double[][] {{400, 400}, {50, 700}, {400, 700}, {700, 300}}) {
			assertEquals(VanillaBiomes.DRIPSTONE_CAVES, LAYOUT.sample(p[0], p[1], seed).caveBiome(), "at " + p[0] + "," + p[1]);
		}
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void deepDarkIsOnlyUnderTheNorth(long seed) {
		// Stay a warp amplitude plus a margin away from the z = -350 edge.
		for (int x = -900; x <= 900; x += 50) {
			for (int z = -1000; z <= 1000; z += 50) {
				LayoutSample s = LAYOUT.sample(x, z, seed);
				if (s.zone() == LayoutSample.Zone.ISLAND || Math.abs(z + 350) < LAYOUT.warpAmplitude() + 8) {
					continue;
				}
				if (z < -350) {
					assertEquals(VanillaBiomes.DEEP_DARK, s.deepCaveBiome(), "at " + x + "," + z);
				} else {
					assertEquals(s.caveBiome(), s.deepCaveBiome(), "at " + x + "," + z);
				}
			}
		}
		// Every ancient-city-sized patch under the Jagged Peaks / Grove / Snowy Slopes band is Deep Dark.
		assertEquals(VanillaBiomes.DEEP_DARK, LAYOUT.sample(0, -700, seed).deepCaveBiome());
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void islandHasNoCaveBiome(long seed) {
		Island island = LAYOUT.islands().get(0);
		LayoutSample s = LAYOUT.sample(island.x(), island.z(), seed);
		assertEquals(island.biome(), s.caveBiome());
		assertEquals(island.biome(), s.deepCaveBiome());
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void biomeIdsAreExactlyWhatSampleEmits(long seed) {
		Set<String> emitted = new HashSet<>();
		for (int x = -1600; x <= 1600; x += 8) {
			for (int z = -1600; z <= 1600; z += 8) {
				LayoutSample s = LAYOUT.sample(x, z, seed);
				emitted.add(s.biome());
				emitted.add(s.caveBiome());
				emitted.add(s.deepCaveBiome());
			}
		}
		assertEquals(LAYOUT.biomeIds(), emitted);
	}

	// ---- validation: each bad input throws and names the offending entry ----

	@Test
	void emptyCaveSeedsThrow() {
		assertInvalid("cave seed list is empty", b -> b.caves(new Caves(List.of(), VanillaBiomes.DEEP_DARK, -350, 0)));
	}

	@Test
	void blankDeepCaveBiomeThrows() {
		assertInvalid("caves.deepBiome", b -> b.caves(new Caves(b.caves.seeds(), " ", -350, 0)));
	}

	@Test
	void coincidentCaveSeedsThrow() {
		assertInvalid("same position", b -> b.caves(new Caves(List.of(new BiomeSeed(VanillaBiomes.LUSH_CAVES, 0, 0),
				new BiomeSeed(VanillaBiomes.DRIPSTONE_CAVES, 0, 0)), VanillaBiomes.DEEP_DARK, -350, 0)));
	}

	@Test
	void emptySeedListThrows() {
		assertInvalid("seed list is empty", b -> b.seeds(List.of()));
	}

	@Test
	void duplicateBiomeThrows() {
		assertInvalid("minecraft:desert", b -> {
			List<BiomeSeed> seeds = new ArrayList<>(b.seeds);
			seeds.add(new BiomeSeed(VanillaBiomes.DESERT, -300, 300));
			return b.seeds(seeds);
		});
	}

	@Test
	void islandBiomeDuplicatingASeedThrows() {
		assertInvalid("minecraft:swamp", b -> b.islands(List.of(new Island(VanillaBiomes.SWAMP, -1250, -50, 150))));
	}

	@Test
	void landSeedOutsideCoastlineThrows() {
		assertInvalid("minecraft:cherry_grove", b -> {
			List<BiomeSeed> seeds = new ArrayList<>(b.seeds);
			seeds.replaceAll(s -> s.biome().equals(VanillaBiomes.CHERRY_GROVE) ? new BiomeSeed(s.biome(), 1100, 0) : s);
			return b.seeds(seeds);
		});
	}

	@Test
	void islandTouchingMainlandThrows() {
		assertInvalid("minecraft:mushroom_fields",
				b -> b.islands(List.of(new Island(VanillaBiomes.MUSHROOM_FIELDS, -1100, -50, 150))));
	}

	/**
	 * The channel follows the real shore: an island fits in the SE bay, where a 1000-block circle would put
	 * it 50 blocks into the land. Checked against the brute-force gap between its rim and the shore curve.
	 */
	@Test
	void islandInABayStandsClear() {
		Island inBay = islandAt(120, 880 + 100 + 70, 100);
		ContinentLayout layout = new Builder().islands(List.of(inBay)).build();
		Coastline coast = layout.coastline();
		double gap = Double.MAX_VALUE;
		for (int i = 0; i < 36_000; i++) {
			double b = 2 * Math.PI * i / 36_000;
			double sx = coast.radius(b) * Math.sin(b);
			double sz = -coast.radius(b) * Math.cos(b);
			gap = Math.min(gap, Math.hypot(sx - inBay.x(), sz - inBay.z()) - inBay.radius());
		}
		assertTrue(gap > 40, "the test island should stand clear of the shaped shore, gap " + gap);
		assertEquals(gap, layout.channel(inBay), 0.15 * gap, "channel to an island in the SE bay");
	}

	/** ... and refuses one that a 1000-block circle would allow, against the Badlands peninsula's tip. */
	@Test
	void islandAgainstAHeadlandThrows() {
		assertInvalid("overlaps the mainland", b -> b.islands(List.of(islandAt(150, 1000 + 100 + 50, 100))));
	}

	@Test
	void riverWithOnePointThrows() {
		assertInvalid("river", b -> b.river(List.of(new Point(0, 0))));
	}

	@Test
	void riverEndingInlandThrows() {
		assertInvalid("crosses the coastline 0 times", b -> b.river(List.of(new Point(-100, -450), new Point(400, 300))));
	}

	@Test
	void riverComingBackAshoreThrows() {
		// Out through the river mouth, round the SE bay, and back onto the land at (600, 600).
		assertInvalid("crosses the coastline 2 times", b -> {
			List<Point> river = new ArrayList<>(b.river);
			river.add(new Point(1200, 500));
			river.add(new Point(600, 600));
			return b.river(river);
		});
	}

	@Test
	void riverRisingAtSeaThrows() {
		assertInvalid("river source", b -> b.river(List.of(new Point(0, -1300), new Point(0, -1500))));
	}

	@Test
	void steepCoastlineThrows() {
		List<Double> jagged = new ArrayList<>();
		for (int i = 0; i < 24; i++) {
			jagged.add(i % 2 == 0 ? 1100.0 : 700.0);
		}
		assertInvalid("too steeply", b -> b.coastline(new Coastline(jagged)));
	}

	@Test
	void villageOutsidePlainsThrows() {
		assertInvalid("not in minecraft:plains", b -> b.villageAnchor(new Point(50, 700)));
	}

	@Test
	void villageNearRiverThrows() {
		// ~43 blocks from the river segment (30,-100)→(110,150), inside Plains.
		assertInvalid("river", b -> b.villageAnchor(new Point(25, 25)));
	}

	@Test
	void villageNearBorderThrows() {
		// ~10 blocks inside the Plains/Swamp bisector, far from the river.
		assertInvalid("border", b -> b.villageAnchor(new Point(-200, 120)));
	}

	@Test
	void villageMarginGrowsWithWarp() {
		// The default anchor clears the nearest border by ~135 blocks. It needs 64 + 32·√2 + 24 = 133.3 at
		// warp 32; at warp 34 it needs 136.1.
		assertInvalid("border", b -> b.warpAmplitude(34));
	}

	@Test
	void villageMarginGrowsWithBorderRoughness() {
		// Roughness 27 needs 64 + 32·√2 + 27 = 136.3 from a border; the anchor has ~135.
		assertInvalid("border", b -> b.borderRoughness(27));
	}

	@Test
	void negativeBorderRoughnessThrows() {
		assertInvalid("borderRoughness", b -> b.borderRoughness(-1));
	}

	/**
	 * Borders between regions are ragged, not ruled. Crossing a border along parallel lines 8 blocks apart, the
	 * crossing point bends from line to line. Without roughness, only the broad warp bends it, and barely.
	 */
	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void bordersBetweenRegionsAreRagged(long seed) {
		ContinentLayout smooth = new Builder().borderRoughness(0).build();
		double ragged = borderBend(LAYOUT, seed);
		double plain = borderBend(smooth, seed);
		assertTrue(ragged >= 3 * plain, "borders bend " + ragged + " blocks per 8 with roughness, " + plain + " without");
		assertTrue(ragged >= 2, "borders bend only " + ragged + " blocks per 8 blocks along them: still ruled lines");
	}

	/** The roughness frays region borders only. The coast, river, islands, bands and caves stay put. */
	@ParameterizedTest
	@ValueSource(longs = {0L, 12345L})
	void roughnessMovesOnlyRegionBorders(long seed) {
		ContinentLayout smooth = new Builder().borderRoughness(0).build();
		int moved = 0;
		for (int x = -1400; x <= 1400; x += 13) {
			for (int z = -1400; z <= 1400; z += 11) {
				LayoutSample a = smooth.sample(x, z, seed);
				LayoutSample b = LAYOUT.sample(x, z, seed);
				String at = " at (" + x + ", " + z + ")";
				assertEquals(a.zone(), b.zone(), "zone" + at);
				assertEquals(a.coastDistance(), b.coastDistance(), "coast distance" + at);
				assertEquals(a.riverDistance(), b.riverDistance(), "river distance" + at);
				assertEquals(a.caveBiome(), b.caveBiome(), "cave" + at);
				assertEquals(a.deepCaveBiome(), b.deepCaveBiome(), "deep cave" + at);
				if (a.zone() != LayoutSample.Zone.LAND) {
					assertEquals(a.biome(), b.biome(), "non-land biome" + at);
				}
				if (!a.primary().equals(b.primary())) {
					moved++;
				}
			}
		}
		assertTrue(moved > 0, "the roughness moved no border");
	}

	@Test
	void missingCoastThrows() {
		assertInvalid("WEST", b -> {
			Map<Coast, CoastStyle> coasts = new EnumMap<>(b.coasts);
			coasts.remove(Coast.WEST);
			return b.coasts(coasts);
		});
	}

	@Test
	void coincidentSeedsThrow() {
		assertInvalid("same position", b -> {
			List<BiomeSeed> seeds = new ArrayList<>(b.seeds);
			seeds.add(new BiomeSeed("minecraft:stony_shore", 0, 0));
			return b.seeds(seeds);
		});
	}

	@Test
	void coastClassifiesByQuadrant() {
		assertEquals(Coast.NORTH, Coast.of(0, -10));
		assertEquals(Coast.SOUTH, Coast.of(0, 10));
		assertEquals(Coast.EAST, Coast.of(10, 0));
		assertEquals(Coast.WEST, Coast.of(-10, 0));
		assertEquals(Coast.NORTH, Coast.of(10, -10), "the diagonal belongs to the north/south coast");
	}

	// ---- helpers ----

	/** Neighbouring region pairs whose shared border runs well inside the land. */
	private static final String[][] BORDERS = {
			{VanillaBiomes.PLAINS, VanillaBiomes.SUNFLOWER_PLAINS},
			{VanillaBiomes.DESERT, VanillaBiomes.BADLANDS},
			{VanillaBiomes.DARK_FOREST, VanillaBiomes.SWAMP}};

	/**
	 * Mean bend of the {@link #BORDERS}, in blocks: the second difference of where the border crosses lines 8
	 * blocks apart, along 200 blocks of each border's middle. A straight or gently curving border scores ~0.
	 */
	private static double borderBend(ContinentLayout layout, long seed) {
		double total = 0;
		int counted = 0;
		for (String[] pair : BORDERS) {
			BiomeSeed a = seedOf(layout, pair[0]);
			BiomeSeed b = seedOf(layout, pair[1]);
			double gap = Math.hypot(b.x() - a.x(), b.z() - a.z());
			double nx = (b.x() - a.x()) / gap;
			double nz = (b.z() - a.z()) / gap;
			double mx = (a.x() + b.x()) / 2;
			double mz = (a.z() + b.z()) / 2;
			List<Double> crossings = new ArrayList<>();
			for (int along = -100; along <= 100; along += 8) {
				// Walk from a's side towards b's: the first block in b's region is the crossing.
				for (int t = -80; t <= 80; t++) {
					double x = mx - nz * along + nx * t;
					double z = mz + nx * along + nz * t;
					if (layout.sample(x, z, seed).primary().equals(b.biome())) {
						crossings.add((double) t);
						break;
					}
				}
			}
			assertEquals(26, crossings.size(), pair[0] + "/" + pair[1] + ": the border left the 160-block search window");
			for (int i = 1; i + 1 < crossings.size(); i++) {
				total += Math.abs(crossings.get(i + 1) - 2 * crossings.get(i) + crossings.get(i - 1));
				counted++;
			}
		}
		return total / counted;
	}

	private static BiomeSeed seedOf(ContinentLayout layout, String biome) {
		return layout.seeds().stream().filter(s -> s.biome().equals(biome)).findFirst().orElseThrow();
	}

	private static void assertInvalid(String expectedInMessage, UnaryOperator<Builder> edit) {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> edit.apply(new Builder()).build());
		assertTrue(e.getMessage().contains(expectedInMessage),
				"message \"" + e.getMessage() + "\" does not name \"" + expectedInMessage + "\"");
	}

	/** One sample per block from the origin out to 2200 blocks: past land + widest band + shallows + warp. */
	private static List<LayoutSample> ray(double dx, double dz, long seed) {
		double len = Math.hypot(dx, dz);
		List<LayoutSample> out = new ArrayList<>();
		for (int t = 0; t <= 2200; t++) {
			out.add(LAYOUT.sample(t * dx / len, t * dz / len, seed));
		}
		return out;
	}

	/** A Mushroom Fields island centred {@code distance} blocks out at a compass bearing (degrees). */
	private static Island islandAt(double bearingDegrees, double distance, double radius) {
		double b = Math.toRadians(bearingDegrees);
		return new Island(VanillaBiomes.MUSHROOM_FIELDS, distance * Math.sin(b), -distance * Math.cos(b), radius);
	}

	private static LayoutSample lastLand(List<LayoutSample> ray) {
		LayoutSample last = null;
		for (LayoutSample s : ray) {
			if (s.coastDistance() < 0) {
				return last;
			}
			last = s;
		}
		throw new AssertionError("ray never reached the sea");
	}

	private static LayoutSample firstSea(List<LayoutSample> ray) {
		return ray.stream().filter(s -> s.coastDistance() < 0).findFirst().orElseThrow();
	}

	private static List<String> distinctRun(List<String> biomes) {
		List<String> out = new ArrayList<>();
		for (String b : biomes) {
			if (out.isEmpty() || !out.get(out.size() - 1).equals(b)) {
				out.add(b);
			}
		}
		return out;
	}

	/** Copies the default layout so each validation test changes exactly one thing. */
	private static final class Builder {
		private final ContinentLayout base = DefaultLayout.create();
		List<BiomeSeed> seeds = base.seeds();
		Coastline coastline = base.coastline();
		List<Island> islands = base.islands();
		Map<Coast, CoastStyle> coasts = base.coasts();
		List<Point> river = base.river();
		double warpAmplitude = base.warpAmplitude();
		double borderRoughness = base.borderRoughness();
		Point villageAnchor = base.villageAnchor();
		Caves caves = base.caves();

		Builder seeds(List<BiomeSeed> v) { seeds = v; return this; }
		Builder coastline(Coastline v) { coastline = v; return this; }
		Builder islands(List<Island> v) { islands = v; return this; }
		Builder coasts(Map<Coast, CoastStyle> v) { coasts = v; return this; }
		Builder river(List<Point> v) { river = v; return this; }
		Builder warpAmplitude(double v) { warpAmplitude = v; return this; }
		Builder borderRoughness(double v) { borderRoughness = v; return this; }
		Builder villageAnchor(Point v) { villageAnchor = v; return this; }
		Builder caves(Caves v) { caves = v; return this; }

		ContinentLayout build() {
			return new ContinentLayout(seeds, coastline, islands, base.beachWidth(), coasts,
					base.shallowOceanWidth(), river, base.riverWidth(), warpAmplitude, borderRoughness, villageAnchor, caves);
		}
	}
}
