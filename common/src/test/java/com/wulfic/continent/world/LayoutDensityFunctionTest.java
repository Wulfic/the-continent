package com.wulfic.continent.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.wulfic.continent.layout.ContinentTerrain;
import com.wulfic.continent.layout.DefaultLayout;
import com.wulfic.continent.layout.TerrainSample;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
//? if >=26.3 {
/*import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
*///?} else {
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
//?}
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** The terrain density function against the real vanilla registries and noise wiring. */
class LayoutDensityFunctionTest {
	private static final ContinentTerrain TERRAIN = DefaultLayout.terrain();
	/** 26.3 samples density functions in floats. */
	//? if >=26.3 {
	/*private static final double PRECISION = 1e-6;
	*///?} else {
	private static final double PRECISION = 1e-12;
	//?}

	private static LayoutDensityFunction decode(String channel, String vanillaDetail) {
		return TestWorlds.decode(channel, vanillaDetail);
	}

	/** Bare channels, so the wired router holds our functions directly. */
	private static RandomState world(long seed) {
		return TestWorlds.continent(seed, f -> f);
	}

	@Test
	void wiredChannelsShareTheBiomeSourcesLayoutSeed() {
		ContinentBiomeSource biomes = ContinentBiomeSource.codecFor(DefaultLayout.create()).codec()
				.parse(TestWorlds.OPS, new JsonObject()).getOrThrow();
		long previous = 0;
		for (long seed : new long[] {1L, 12345L}) {
			RandomState state = world(seed);
			long expected = biomes.layoutSeed(Worldgen.climate(state));
			NoiseRouter router = TestWorlds.router(state);
			for (DensityFunction channel : new DensityFunction[] {router.continents(), router.erosion(), router.ridges()}) {
				assertTrue(channel instanceof LayoutDensityFunction, "wired channel is " + channel);
				assertEquals(expected, TestWorlds.layoutSeed(state, channel), "world seed " + seed);
			}
			assertEquals(expected, Worldgen.layoutSeed(state), "world seed " + seed);
			assertNotEquals(previous, expected, "two world seeds gave one layout seed");
			previous = expected;
		}
	}

	/**
	 * RandomState and NoiseChunk dedupe the graph by equality. With identity equality each of the spline
	 * graph's ~100 channel references got its own flat cache, and a noise chunk cost three times vanilla's.
	 */
	@Test
	void equalDefinitionsAreEqualSoWiringDedupesThem() {
		LayoutDensityFunction a = decode("ridges", "ridges");
		LayoutDensityFunction b = decode("ridges", "ridges");
		assertEquals(a, b);
		assertEquals(a.hashCode(), b.hashCode());
		assertNotEquals(a, decode("erosion", "ridges"));
		assertNotEquals(a, decode("ridges", "erosion"));
	}

	@ParameterizedTest
	@EnumSource(LayoutDensityFunction.Channel.class)
	void computeIsTheTargetPlusScaledClampedDetail(LayoutDensityFunction.Channel channel) {
		// Exact in float as well as double, so every band's constant holds the same detail.
		RandomState world = TestWorlds.vanilla(7L);
		for (float detail : new float[] {-3, -0.375f, 0, 0.75f, 2.5f}) {
			LayoutDensityFunction f = new LayoutDensityFunction(channel, DensityFunctions.constant(detail),
					DensityFunctions.constant(0.25f));
			long seed = TestWorlds.layoutSeed(world, f);
			for (int x = -1300; x <= 1300; x += 97) {
				for (int z = -1300; z <= 1300; z += 89) {
					TerrainSample s = TERRAIN.sample(x, z, seed);
					double target = switch (channel) {
						case CONTINENTALNESS -> s.continentalness();
						case EROSION -> s.erosion();
						case RIDGES -> s.ridges();
						case UPLIFT -> s.uplift();
					};
					double amplitude = switch (channel) {
						case CONTINENTALNESS -> ContinentTerrain.CONTINENTALNESS_DETAIL;
						case EROSION -> s.erosionDetail();
						case RIDGES -> s.ridgesDetail();
						case UPLIFT -> s.upliftDetail();
					};
					double expected = Math.clamp(target + amplitude * s.detail() * Math.clamp(detail, -1, 1), -1, 1);
					double actual = TestWorlds.sample(world, f, x, 64, z);
					assertEquals(expected, actual, PRECISION, channel + " at (" + x + ", " + z + ") with detail " + detail);
					assertTrue(actual >= -1 && actual <= 1, "outside the bounds [-1, 1]: " + actual);
				}
			}
		}
	}

	@Test
	void realDetailNoiseVariesTheOutputAndStaysInBounds() {
		RandomState state = world(42L);
		DensityFunction ridges = TestWorlds.router(state).ridges();
		double min = Double.MAX_VALUE;
		double max = -Double.MAX_VALUE;
		// Deep inside the Desert, away from borders, coast and river, where the target is constant.
		for (int x = 0; x <= 100; x += 4) {
			for (int z = 650; z <= 750; z += 4) {
				double v = TestWorlds.sample(state, ridges, x, 64, z);
				min = Math.min(min, v);
				max = Math.max(max, v);
			}
		}
		assertTrue(min >= -1 && max <= 1, "range " + min + ".." + max);
		assertTrue(max - min > 0.001, "detail noise is not wired: ridges is constant " + min);
	}
}
