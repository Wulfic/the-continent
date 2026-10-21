package com.wulfic.continent.world;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.util.function.UnaryOperator;
import net.minecraft.SharedConstants;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
//? if >=26.3 {
/*import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
*///?} else {
import net.minecraft.world.level.levelgen.DensityFunction;
//?}

/**
 * Worlds built from the real vanilla registries, with or without our terrain channels, for unit tests. Also the
 * tests' one seam over the worldgen API that changed between bands: {@link #router}, {@link #sample} and
 * {@link #layoutSeed} read a world the same way on every band.
 */
final class TestWorlds {
	static final HolderLookup.Provider LOOKUP;
	static final RegistryOps<JsonElement> OPS;
	//? if >=26.3 {
	/*/^* 26.3 keeps a world's router private, so every world built here remembers its own. ^/
	private static final Map<RandomState, NoiseRouter> ROUTERS = Collections.synchronizedMap(new WeakHashMap<>());
	/^* The overworld's temperature, as 26.3's density_function/overworld/temperature.json writes it. ^/
	private static final String SEED_SOURCE = """
			{"type": "minecraft:noise", "noise": "minecraft:temperature", "shift_x": "minecraft:shift_x",
			 "shift_z": "minecraft:shift_z", "xz_scale": 0.25, "y_scale": 0.0}""";
	*///?} else {
	private static final String SEED_SOURCE = """
			{"type": "minecraft:shifted_noise", "noise": "minecraft:temperature", "shift_x": "minecraft:shift_x",
			 "shift_y": 0.0, "shift_z": "minecraft:shift_z", "xz_scale": 0.25, "y_scale": 0.0}""";
	//?}

	static {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		//? if >=26.3 {
		/*LOOKUP = VanillaRegistries.createWorldLookup();
		*///?} else {
		LOOKUP = VanillaRegistries.createLookup();
		//?}
		OPS = LOOKUP.createSerializationContext(JsonOps.INSTANCE);
	}

	private TestWorlds() {
	}

	/** The JSON shape our {@code the_continent:overworld/<channel>} files use, minus the cache. */
	static LayoutDensityFunction decode(String channel, String vanillaDetail) {
		String json = """
				{"channel": "%s", "detail": "minecraft:overworld/%s", "seed_source": %s}"""
				.formatted(channel, vanillaDetail, SEED_SOURCE);
		return LayoutDensityFunction.MAP_CODEC.codec().parse(OPS, JsonParser.parseString(json)).getOrThrow();
	}

	static NoiseGeneratorSettings vanillaSettings() {
		return LOOKUP.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(NoiseGeneratorSettings.OVERWORLD).value();
	}

	static RandomState vanilla(long seed) {
		//? if >=26.3 {
		/*NoiseGeneratorSettings vanilla = vanillaSettings();
		RandomState state = RandomState.create(LOOKUP.lookupOrThrow(Registries.NOISE), seed, vanilla);
		ROUTERS.put(state, vanilla.noiseRouter());
		return state;
		*///?} else {
		return RandomState.create(vanillaSettings(), LOOKUP.lookupOrThrow(Registries.NOISE), seed);
		//?}
	}

	/**
	 * A world's router with our three channels in place of vanilla's, wired by RandomState like a real
	 * world. {@code wrap} dresses each channel the way the datapack does, or leaves it bare.
	 */
	static RandomState continent(long seed, UnaryOperator<DensityFunction> wrap) {
		NoiseGeneratorSettings vanilla = vanillaSettings();
		NoiseRouter r = vanilla.noiseRouter();
		//? if >=26.3 {
		/*NoiseRouter ours = new NoiseRouter(r.temperature(), r.vegetation(), wrap.apply(decode("continentalness", "continents")),
				wrap.apply(decode("erosion", "erosion")), r.depth(), wrap.apply(decode("ridges", "ridges")),
				r.chunkSurfaceLevel(), r.finalDensity());
		RandomState state = RandomState.create(LOOKUP.lookupOrThrow(Registries.NOISE), seed,
				vanilla.useLegacyRandomSource(), vanilla.defaultBlock(), vanilla.seaLevel(), ours);
		ROUTERS.put(state, ours);
		return state;
		*///?} else {
		NoiseRouter ours = new NoiseRouter(r.barrierNoise(), r.fluidLevelFloodednessNoise(), r.fluidLevelSpreadNoise(),
				r.lavaNoise(), r.temperature(), r.vegetation(), wrap.apply(decode("continentalness", "continents")),
				wrap.apply(decode("erosion", "erosion")), r.depth(), wrap.apply(decode("ridges", "ridges")),
				r.preliminarySurfaceLevel(), r.finalDensity(), r.veinToggle(), r.veinRidged(), r.veinGap());
		NoiseGeneratorSettings settings = new NoiseGeneratorSettings(vanilla.noiseSettings(), vanilla.defaultBlock(),
				vanilla.defaultFluid(), ours, vanilla.surfaceRule(), vanilla.spawnTarget(), vanilla.seaLevel(),
				vanilla.disableMobGeneration(), vanilla.aquifersEnabled(), vanilla.oreVeinsEnabled(),
				vanilla.useLegacyRandomSource());
		return RandomState.create(settings, LOOKUP.lookupOrThrow(Registries.NOISE), seed);
		//?}
	}

	/** The world's noise router: wired by RandomState before 26.3, as built (and compiled on use) from 26.3. */
	static NoiseRouter router(RandomState state) {
		//? if >=26.3 {
		/*NoiseRouter router = ROUTERS.get(state);
		if (router == null) {
			throw new IllegalArgumentException("not a world TestWorlds built: " + state);
		}
		return router;
		*///?} else {
		return state.router();
		//?}
	}

	/** A function of {@link #router}{@code (state)} at a block. */
	static double sample(RandomState state, DensityFunction function, int x, int y, int z) {
		//? if >=26.3 {
		/*return state.sampleBlockValueUncached(function, x, y, z);
		*///?} else {
		return function.compute(new DensityFunction.SinglePointContext(x, y, z));
		//?}
	}

	/** The layout seed one of {@link #router}{@code (state)}'s channels uses in that world. */
	static long layoutSeed(RandomState state, DensityFunction channel) {
		//? if >=26.3 {
		/*return LayoutDensityFunction.find(state.getSampler(channel))
				.orElseThrow(() -> new IllegalArgumentException("not a layout channel: " + channel)).seed();
		*///?} else {
		if (!(channel instanceof LayoutDensityFunction layout)) {
			throw new IllegalArgumentException("not a layout channel: " + channel);
		}
		return layout.layoutSeed();
		//?}
	}
}
