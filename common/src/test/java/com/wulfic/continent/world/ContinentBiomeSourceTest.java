package com.wulfic.continent.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DataResult;
import com.wulfic.continent.layout.BiomeSeed;
import com.wulfic.continent.layout.ContinentLayout;
import com.wulfic.continent.layout.DefaultLayout;
import com.wulfic.continent.layout.VanillaBiomes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
//? if <26.3 {
import net.minecraft.world.level.levelgen.DensityFunction;
//?}
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Runs the biome source against the real vanilla registries and overworld noise. */
class ContinentBiomeSourceTest {
	/** Block y high above any vanilla terrain, so climate depth is far below {@link ContinentBiomeSource#CAVE_DEPTH}. */
	private static final int SKY_Y = 300;

	private static RegistryOps<JsonElement> ops;

	/** {@link TestWorlds} bootstraps the game and builds the vanilla registries. */
	@BeforeAll
	static void bootstrap() {
		ops = TestWorlds.OPS;
	}

	private static DataResult<ContinentBiomeSource> decode(ContinentLayout layout) {
		return ContinentBiomeSource.codecFor(layout).codec().parse(ops, new JsonObject());
	}

	private static Climate.Sampler sampler(long seed) {
		return Worldgen.climate(TestWorlds.vanilla(seed));
	}

	private static Holder<Biome> biomeAt(ContinentBiomeSource source, Climate.Sampler sampler, int x, int y, int z) {
		//? if >=26.3 {
		/*return source.createResolver(sampler).getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z));
		*///?} else {
		return source.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), sampler);
		//?}
	}

	private static double depthAt(Climate.Sampler sampler, int x, int y, int z) {
		//? if >=26.3 {
		/*return sampler.depth().sampleValue(x, y, z);
		*///?} else {
		return sampler.depth().compute(new DensityFunction.SinglePointContext(x, y, z));
		//?}
	}

	private static String id(Holder<Biome> holder) {
		return holder.unwrapKey().orElseThrow().identifier().toString();
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 12345L, -4172144997902289642L})
	void everySeedPositionGetsItsBiome(long seed) {
		ContinentBiomeSource source = decode(DefaultLayout.create()).getOrThrow();
		Climate.Sampler sampler = sampler(seed);
		for (BiomeSeed s : DefaultLayout.create().seeds()) {
			assertEquals(s.biome(), id(biomeAt(source, sampler, (int) s.x(), SKY_Y, (int) s.z())), "at seed " + s);
		}
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 12345L})
	void surfaceMatchesTheLayoutEverywhere(long seed) {
		ContinentLayout layout = DefaultLayout.create();
		ContinentBiomeSource source = decode(layout).getOrThrow();
		Climate.Sampler sampler = sampler(seed);
		long layoutSeed = source.layoutSeed(sampler);
		for (int x = -1600; x <= 1600; x += 52) {
			for (int z = -1600; z <= 1600; z += 52) {
				String expected = layout.sample(x, z, layoutSeed).biome();
				assertEquals(expected, id(biomeAt(source, sampler, x, SKY_Y, z)), "at " + x + "," + z);
			}
		}
	}

	@Test
	void caveBiomeFollowsClimateDepth() {
		ContinentBiomeSource source = decode(DefaultLayout.create()).getOrThrow();
		Climate.Sampler sampler = sampler(0L);
		int caves = 0;
		for (int y = 4; y <= 200; y += 4) {
			double depth = depthAt(sampler, 0, y, 0);
			String expected = depth >= ContinentBiomeSource.CAVE_DEPTH ? VanillaBiomes.LUSH_CAVES : VanillaBiomes.PLAINS;
			assertEquals(expected, id(biomeAt(source, sampler, 0, y, 0)), "at y " + y + ", depth " + depth);
			caves += expected.equals(VanillaBiomes.LUSH_CAVES) ? 1 : 0;
		}
		assertTrue(caves > 0, "no cave biome anywhere under the origin");
	}

	@Test
	void deepDarkCoversTheAncientCityStartUnderTheNorthOnly() {
		ContinentBiomeSource source = decode(DefaultLayout.create()).getOrThrow();
		Climate.Sampler sampler = sampler(0L);
		for (int x = -400; x <= 400; x += 200) {
			assertEquals(VanillaBiomes.DEEP_DARK, id(biomeAt(source, sampler, x, -27, -700)), "x " + x);
		}
		assertEquals(VanillaBiomes.LUSH_CAVES, id(biomeAt(source, sampler, 0, -27, 0)));
		assertEquals(VanillaBiomes.DRIPSTONE_CAVES, id(biomeAt(source, sampler, 400, -27, 700)));
	}

	@Test
	void mushroomIslandHasNoCaves() {
		ContinentBiomeSource source = decode(DefaultLayout.create()).getOrThrow();
		Climate.Sampler sampler = sampler(0L);
		for (int y = -60; y <= 200; y += 8) {
			assertEquals(VanillaBiomes.MUSHROOM_FIELDS, id(biomeAt(source, sampler, -1250, y, -50)), "at y " + y);
		}
	}

	@Test
	void possibleBiomesAreExactlyTheLayoutBiomes() {
		ContinentLayout layout = DefaultLayout.create();
		Set<String> possible = decode(layout).getOrThrow().possibleBiomes().stream()
				.map(ContinentBiomeSourceTest::id).collect(Collectors.toSet());
		assertEquals(layout.biomeIds(), possible);
	}

	@Test
	void unknownBiomeIdIsAnErrorNamingIt() {
		DataResult<ContinentBiomeSource> result = decode(withExtraSeed("the_continent:no_such_biome"));
		assertTrue(result.isError(), "decoded a layout with an unknown biome");
		String message = result.error().orElseThrow().message();
		assertTrue(message.contains("the_continent:no_such_biome"), message);
	}

	@Test
	void malformedBiomeIdIsAnErrorNamingIt() {
		DataResult<ContinentBiomeSource> result = decode(withExtraSeed("Not A Biome!"));
		assertTrue(result.isError(), "decoded a layout with a malformed biome id");
		String message = result.error().orElseThrow().message();
		assertTrue(message.contains("Not A Biome!"), message);
	}

	@Test
	void encodesBackToAnEmptyObject() {
		ContinentBiomeSource source = decode(DefaultLayout.create()).getOrThrow();
		JsonElement json = ContinentBiomeSource.CODEC.codec().encodeStart(ops, source).getOrThrow();
		assertEquals(new JsonObject(), json);
	}

	@Test
	void layoutSeedIsStablePerWorldSeedAndVariesAcrossThem() {
		ContinentBiomeSource source = decode(DefaultLayout.create()).getOrThrow();
		long a = source.layoutSeed(sampler(1L));
		assertEquals(a, source.layoutSeed(sampler(1L)), "a fresh RandomState for the same seed must match");
		assertNotEquals(a, source.layoutSeed(sampler(2L)));
		assertNotEquals(a, source.layoutSeed(sampler(-1L)));
	}

	@Test
	void differentWorldSeedsWarpTheBorders() {
		ContinentBiomeSource source = decode(DefaultLayout.create()).getOrThrow();
		Climate.Sampler one = sampler(1L);
		Climate.Sampler two = sampler(2L);
		int differing = 0;
		for (int x = -1000; x <= 1000; x += 24) {
			for (int z = -1000; z <= 1000; z += 24) {
				if (!biomeAt(source, one, x, SKY_Y, z).equals(biomeAt(source, two, x, SKY_Y, z))) {
					differing++;
				}
			}
		}
		assertTrue(differing > 0, "the world seed does not reach the layout warp");
	}

	/** The default layout plus one far-west seed with {@code biome}; every other check still passes. */
	private static ContinentLayout withExtraSeed(String biome) {
		ContinentLayout base = DefaultLayout.create();
		List<BiomeSeed> seeds = new ArrayList<>(base.seeds());
		seeds.add(new BiomeSeed(biome, -900, -100));
		return new ContinentLayout(seeds, base.coastline(), base.islands(), base.beachWidth(), base.coasts(),
				base.shallowOceanWidth(), base.river(), base.riverWidth(), base.warpAmplitude(), base.borderRoughness(),
				base.villageAnchor(), base.caves());
	}
}
