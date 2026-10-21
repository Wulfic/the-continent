package com.wulfic.continent.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ContinentTerrainTest {
	private static final ContinentTerrain TERRAIN = DefaultLayout.terrain();
	private static final ContinentLayout LAYOUT = TERRAIN.layout();
	private static final double EPS = 1e-9;

	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void regionsKeepTheirProfileAwayFromBordersCoastsAndTheRiver(long seed) {
		// How far the warp can move a point (x and z each by the amplitude), and the region lookup on top.
		double warp = Math.sqrt(2) * LAYOUT.warpAmplitude();
		double region = warp + LAYOUT.borderRoughness();
		int checked = 0;
		for (BiomeSeed s : LAYOUT.seeds()) {
			// Margins on the unwarped map, widened by the warps, so they hold on every seed.
			boolean clearOfNeighbours = LAYOUT.seeds().stream().filter(o -> o != s)
					.allMatch(o -> Math.hypot(o.x() - s.x(), o.z() - s.z()) >= ContinentTerrain.REGION_BLEND + 2 * region);
			boolean clearOfCoast = LAYOUT.coastline().distance(s.x(), s.z()) >= ContinentTerrain.COAST_RAMP + warp;
			boolean clearOfRiver = LAYOUT.riverDistance(s.x(), s.z())
					>= LAYOUT.riverWidth() / 2 + ContinentTerrain.RIVER_BANK + warp;
			if (!(clearOfNeighbours && clearOfCoast && clearOfRiver)) {
				continue;
			}
			TerrainShape profile = TERRAIN.profiles().get(s.biome());
			TerrainSample at = TERRAIN.sample(s.x(), s.z(), seed);
			assertEquals(profile.continentalness(), at.continentalness(), EPS, s.biome() + " C");
			assertEquals(profile.erosion(), at.erosion(), EPS, s.biome() + " E");
			assertEquals(profile.ridges(), at.ridges(), EPS, s.biome() + " W");
			assertEquals(profile.erosionDetail(), at.erosionDetail(), EPS, s.biome() + " E detail");
			assertEquals(profile.ridgesDetail(), at.ridgesDetail(), EPS, s.biome() + " W detail");
			assertEquals(profile.uplift(), at.uplift(), EPS, s.biome() + " uplift");
			assertEquals(profile.upliftDetail(), at.upliftDetail(), EPS, s.biome() + " uplift detail");
			assertEquals(1, at.detail(), EPS, s.biome() + " detail");
			checked++;
		}
		// Guards against the margins quietly excluding everything.
		assertTrue(checked >= 10, "only " + checked + " regions were far enough from every edge to check");
	}

	/**
	 * Terrain blends regions where the biome is decided, after the border roughness, so a ragged border is ragged
	 * in the hills too. Wherever the region point is a full blend width deeper into one region than any other,
	 * the terrain is exactly that region's profile and the biome is that region's.
	 */
	@ParameterizedTest
	@ValueSource(longs = {0L, 12345L})
	void terrainFollowsTheRaggedBorders(long seed) {
		int checked = 0;
		for (int x = -900; x <= 900; x += 7) {
			for (int z = -900; z <= 900; z += 7) {
				LayoutSample biome = LAYOUT.sample(x, z, seed);
				if (biome.zone() != LayoutSample.Zone.LAND || biome.coastDistance() < ContinentTerrain.COAST_RAMP
						|| biome.riverDistance() < LAYOUT.riverWidth() / 2 + ContinentTerrain.RIVER_BANK) {
					continue;
				}
				double wx = LAYOUT.warpX(x, z, seed);
				double wz = LAYOUT.warpZ(x, z, seed);
				double u = LAYOUT.roughX(x, z, seed);
				double v = LAYOUT.roughZ(x, z, seed);
				double scale = LAYOUT.roughScale(u, v);
				double rx = wx + scale * u;
				double rz = wz + scale * v;
				// Only points the roughness decides: at the broadly warped point alone they'd be a blend.
				if (blendGap(rx, rz) < ContinentTerrain.REGION_BLEND || blendGap(wx, wz) >= ContinentTerrain.REGION_BLEND) {
					continue;
				}
				BiomeSeed nearest = LAYOUT.seeds().stream()
						.min(Comparator.comparingDouble(s -> Math.hypot(s.x() - rx, s.z() - rz))).orElseThrow();
				String at = " at (" + x + ", " + z + ")";
				assertEquals(nearest.biome(), biome.biome(), "biome" + at);
				TerrainShape profile = TERRAIN.profiles().get(nearest.biome());
				TerrainSample terrain = TERRAIN.sample(x, z, seed);
				assertEquals(profile.erosion(), terrain.erosion(), EPS, nearest.biome() + " E" + at);
				assertEquals(profile.ridges(), terrain.ridges(), EPS, nearest.biome() + " W" + at);
				checked++;
			}
		}
		assertTrue(checked >= 50, "only " + checked + " points were decided by the roughness alone");
	}

	/** How much farther the second-nearest seed is than the nearest: at least REGION_BLEND means no blend. */
	private static double blendGap(double x, double z) {
		double[] d = LAYOUT.seeds().stream().mapToDouble(s -> Math.hypot(s.x() - x, s.z() - z)).sorted().toArray();
		return d[1] - d[0];
	}

	/**
	 * Walks straight lines across the whole map in quarter-block steps. Every term is continuous, so no
	 * step may change a channel by more than a gradient bound allows. A step at a border or triple
	 * junction (the failure of a nearest-edge blend) changes it by far more.
	 */
	@ParameterizedTest
	@ValueSource(longs = {0L, 12345L})
	void everyChannelIsContinuous(long seed) {
		double step = 0.25;
		double maxDelta = 0.05;
		for (int line = -1500; line <= 1500; line += 64) {
			for (int axis = 0; axis < 2; axis++) {
				TerrainSample previous = null;
				for (double t = -1500; t <= 1500; t += step) {
					double x = axis == 0 ? t : line;
					double z = axis == 0 ? line : t;
					TerrainSample s = TERRAIN.sample(x, z, seed);
					if (previous != null) {
						String where = "at (" + x + ", " + z + ")";
						assertTrue(Math.abs(s.continentalness() - previous.continentalness()) <= maxDelta, "C steps " + where);
						assertTrue(Math.abs(s.erosion() - previous.erosion()) <= maxDelta, "E steps " + where);
						assertTrue(Math.abs(s.ridges() - previous.ridges()) <= maxDelta, "W steps " + where);
						assertTrue(Math.abs(s.erosionDetail() - previous.erosionDetail()) <= maxDelta, "E detail steps " + where);
						assertTrue(Math.abs(s.ridgesDetail() - previous.ridgesDetail()) <= maxDelta, "W detail steps " + where);
						assertTrue(Math.abs(s.uplift() - previous.uplift()) <= maxDelta, "uplift steps " + where);
						assertTrue(Math.abs(s.upliftDetail() - previous.upliftDetail()) <= maxDelta, "uplift detail steps " + where);
					}
					previous = s;
				}
			}
		}
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void everyValueStaysInRange(long seed) {
		for (int x = -1600; x <= 1600; x += 13) {
			for (int z = -1600; z <= 1600; z += 17) {
				TerrainSample s = TERRAIN.sample(x, z, seed);
				assertUnit(s.continentalness(), "C");
				assertUnit(s.erosion(), "E");
				assertUnit(s.ridges(), "W");
				assertTrue(s.detail() >= 0 && s.detail() <= 1, "detail " + s.detail());
				assertTrue(s.erosionDetail() >= 0 && s.ridgesDetail() >= 0, "negative detail amplitude");
				// The density function adds at most upliftDetail · detail either way.
				assertTrue(s.uplift() - s.upliftDetail() * s.detail() >= -EPS
						&& s.uplift() + s.upliftDetail() * s.detail() <= ContinentTerrain.MAX_UPLIFT + EPS,
						"uplift " + s.uplift() + " ± " + s.upliftDetail() + " at (" + x + ", " + z + ")");
				// The density function adds at most erosionDetail · detail, so this is the most E can reach.
				assertTrue(s.erosion() + s.erosionDetail() * s.detail() < ContinentTerrain.SHATTERED_EROSION,
						"E " + s.erosion() + " + detail " + s.erosionDetail() + " at (" + x + ", " + z + ") can reach the shattered band");
			}
		}
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void offshoreIsSeaFloorAndTheDeepZoneIsDeep(long seed) {
		for (int deg = 0; deg < 360; deg += 5) {
			double dx = Math.cos(Math.toRadians(deg));
			double dz = Math.sin(Math.toRadians(deg));
			LayoutSample farOff = null;
			for (int r = 1100; r <= 2400; r += 10) {
				LayoutSample biome = LAYOUT.sample(r * dx, r * dz, seed);
				TerrainSample s = TERRAIN.sample(r * dx, r * dz, seed);
				if (biome.zone() == LayoutSample.Zone.ISLAND || biome.coastDistance() > -ContinentTerrain.SHELF_WIDTH) {
					continue;
				}
				assertTrue(s.continentalness() <= ContinentTerrain.SHALLOW_FLOOR + EPS,
						"C " + s.continentalness() + " past the shelf at r " + r + ", " + deg + "°");
				assertEquals(0, s.uplift(), "uplift on the sea floor at r " + r + ", " + deg + "°");
				assertEquals(0, s.upliftDetail(), "uplift detail on the sea floor at r " + r + ", " + deg + "°");
				if (biome.zone() == LayoutSample.Zone.DEEP_OCEAN) {
					farOff = biome;
					assertEquals(ContinentTerrain.DEEP_FLOOR, s.continentalness(), EPS,
							"deep-ocean biome over a shallow floor at r " + r + ", " + deg + "°");
				}
			}
			assertTrue(farOff != null, "no deep ocean along " + deg + "°");
		}
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void riverChannelIsAValleyFloorWithNoDetail(long seed) {
		int found = 0;
		for (int x = -1000; x <= 1000; x += 2) {
			for (int z = -1000; z <= 1000; z += 2) {
				LayoutSample biome = LAYOUT.sample(x, z, seed);
				if (biome.riverDistance() < 1 && biome.coastDistance() > 0) {
					TerrainSample s = TERRAIN.sample(x, z, seed);
					assertEquals(0, s.ridges(), 0.01, "W on the river at (" + x + ", " + z + ")");
					assertEquals(ContinentTerrain.RIVER_EROSION, s.erosion(), 0.01, "E on the river at (" + x + ", " + z + ")");
					assertEquals(0, s.detail(), 0.01, "detail on the river at (" + x + ", " + z + ")");
					assertEquals(0, s.uplift(), 0.01, "uplift on the river at (" + x + ", " + z + ")");
					found++;
				}
			}
		}
		assertTrue(found > 100, "found only " + found + " river centre samples");
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void northShoreKeepsItsMountainsAndTheOthersGetABeach(long seed) {
		TerrainSample north = shore(0, -1, seed);
		assertTrue(north.erosion() < -0.5, "north shore E " + north.erosion() + " is not mountainous");
		assertTrue(north.ridges() > 0.5, "north shore W " + north.ridges() + " is not a peak");
		assertEquals(ContinentTerrain.CLIFF_SHORE, north.continentalness(), 0.01, "north shore C");
		for (double[] dir : new double[][] {{0, 1}, {1, 0}, {-1, 0}}) {
			TerrainSample s = shore(dir[0], dir[1], seed);
			String where = "shore toward (" + dir[0] + ", " + dir[1] + ")";
			assertEquals(ContinentTerrain.BEACH_EROSION, s.erosion(), 0.02, where + " E");
			assertEquals(ContinentTerrain.BEACH_RIDGES, s.ridges(), 0.02, where + " W");
			assertEquals(ContinentTerrain.BEACH_SHORE, s.continentalness(), 0.01, where + " C");
		}
	}

	@Test
	void shapesSitClearOfTheShatteredBand() {
		assertTrue(ContinentTerrain.BEACH_EROSION + ContinentTerrain.BEACH_EROSION_DETAIL < ContinentTerrain.SHATTERED_EROSION);
		assertTrue(ContinentTerrain.RIVER_EROSION < ContinentTerrain.SHATTERED_EROSION);
	}

	/** Every land region gets uplift only through the coast ramp: the shore itself is never lifted. */
	@ParameterizedTest
	@ValueSource(longs = {0L, 1L, 12345L, -4172144997902289642L})
	void theShoreIsNeverLifted(long seed) {
		for (int deg = 0; deg < 360; deg += 3) {
			TerrainSample s = shore(Math.sin(Math.toRadians(deg)), -Math.cos(Math.toRadians(deg)), seed);
			assertTrue(s.uplift() + s.upliftDetail() < 0.01, "uplift " + s.uplift() + " ± " + s.upliftDetail()
					+ " at the shore at bearing " + deg + "°");
		}
	}

	/** The mountains are what the uplift is for: the peaks get the most, the lowlands none. */
	@Test
	void theMountainsAreLifted() {
		TerrainShape peaks = TERRAIN.profiles().get(VanillaBiomes.JAGGED_PEAKS);
		assertTrue(peaks.uplift() >= 0.2, "Jagged Peaks uplift " + peaks.uplift());
		for (String low : List.of(VanillaBiomes.PLAINS, VanillaBiomes.SWAMP, VanillaBiomes.DESERT, VanillaBiomes.BADLANDS)) {
			assertEquals(0, TERRAIN.profiles().get(low).uplift(), low + " is lifted");
		}
		for (TerrainShape p : TERRAIN.profiles().values()) {
			assertTrue(p.uplift() <= peaks.uplift(), "a region is lifted above the Jagged Peaks: " + p);
		}
	}

	@Test
	void missingProfileThrows() {
		assertInvalid("no terrain profile for " + VanillaBiomes.DESERT, p -> p.remove(VanillaBiomes.DESERT));
	}

	@Test
	void profileForANonLandBiomeThrows() {
		assertInvalid(VanillaBiomes.BEACH + ", which is not a land biome",
				p -> p.put(VanillaBiomes.BEACH, new TerrainShape(0, 0, 0)));
	}

	@Test
	void erosionNearTheShatteredBandThrows() {
		assertInvalid(VanillaBiomes.SWAMP + " erosion 0.4", p -> p.put(VanillaBiomes.SWAMP, new TerrainShape(0, 0.4, 0.2)));
	}

	@Test
	void erosionDetailReachingTheShatteredBandThrows() {
		assertInvalid(VanillaBiomes.PLAINS + " erosion 0.2 plus detail 0.3",
				p -> p.put(VanillaBiomes.PLAINS, new TerrainShape(0.05, 0.2, 0.4).withDetail(0.3, 0.1)));
	}

	@Test
	void detailOutOfRangeThrows() {
		assertInvalid(VanillaBiomes.FOREST + " ridges detail -0.1",
				p -> p.put(VanillaBiomes.FOREST, new TerrainShape(0.05, -0.45, 0.3).withDetail(0.1, -0.1)));
	}

	@Test
	void upliftOutOfRangeThrows() {
		assertInvalid(VanillaBiomes.JAGGED_PEAKS + " uplift 0.4 ± detail 0.2",
				p -> p.put(VanillaBiomes.JAGGED_PEAKS, new TerrainShape(0.8, -0.9, 0.667).withUplift(0.4, 0.2)));
		assertInvalid(VanillaBiomes.GROVE + " uplift 0.1 ± detail 0.15",
				p -> p.put(VanillaBiomes.GROVE, new TerrainShape(0.5, -0.6, 0.35).withUplift(0.1, 0.15)));
		assertInvalid(VanillaBiomes.TAIGA + " uplift -0.1",
				p -> p.put(VanillaBiomes.TAIGA, new TerrainShape(0.15, -0.45, 0.3).withUplift(-0.1)));
	}

	@Test
	void outOfRangeValueThrows() {
		assertInvalid(VanillaBiomes.PLAINS + " continentalness 1.5",
				p -> p.put(VanillaBiomes.PLAINS, new TerrainShape(1.5, 0, 0.3)));
	}

	@Test
	void narrowIslandChannelThrows() {
		ContinentLayout base = LAYOUT;
		Island island = base.islands().get(0);
		double tooClose = base.coastline().radius(Coastline.bearing(-1, 0)) + island.radius() + ContinentTerrain.SHELF_WIDTH;
		ContinentLayout narrow = new ContinentLayout(base.seeds(), base.coastline(),
				List.of(new Island(island.biome(), -tooClose, 0, island.radius())), base.beachWidth(), base.coasts(),
				base.shallowOceanWidth(), base.river(), base.riverWidth(), base.warpAmplitude(), base.borderRoughness(),
				base.villageAnchor(), base.caves());
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
				() -> new ContinentTerrain(narrow, TERRAIN.profiles()));
		assertTrue(e.getMessage().contains("narrower than two shelves"), e.getMessage());
	}

	// ---- helpers ----

	/** The last land sample along a ray from the origin, so it sits right at the shoreline. */
	private static TerrainSample shore(double dx, double dz, long seed) {
		double len = Math.hypot(dx, dz);
		TerrainSample last = null;
		for (double t = 0; t <= 1200; t += 0.5) {
			double x = t * dx / len;
			double z = t * dz / len;
			if (LAYOUT.sample(x, z, seed).coastDistance() < 0) {
				return last;
			}
			last = TERRAIN.sample(x, z, seed);
		}
		throw new AssertionError("ray never reached the sea");
	}

	private static void assertUnit(double value, String what) {
		assertTrue(value >= -1 && value <= 1, what + " " + value + " is outside [-1, 1]");
	}

	private static void assertInvalid(String expectedInMessage, Consumer<Map<String, TerrainShape>> edit) {
		Map<String, TerrainShape> profiles = new HashMap<>(TERRAIN.profiles());
		edit.accept(profiles);
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new ContinentTerrain(LAYOUT, profiles));
		assertTrue(e.getMessage().contains(expectedInMessage),
				"message \"" + e.getMessage() + "\" does not name \"" + expectedInMessage + "\"");
	}
}
