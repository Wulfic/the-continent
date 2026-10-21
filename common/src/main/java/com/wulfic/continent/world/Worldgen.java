package com.wulfic.continent.world;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
//? if >=26.3 {
/*import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
*///?}

/**
 * The few worldgen reads whose API changed between bands (26.3 compiles density functions and resolves biomes
 * through a resolver), behind one face. The command, the tests and the gametests call these and stay the same
 * on every band.
 */
public final class Worldgen {
	/** A world's biomes by quart position. */
	@FunctionalInterface
	public interface Biomes {
		Holder<Biome> at(int qx, int qy, int qz);
	}

	private Worldgen() {
	}

	/** The world's climate sampler, uncached, so any thread may use it. */
	public static Climate.Sampler climate(RandomState state) {
		//? if >=26.3 {
		/*return state.createClimateSampler(SamplerContext.EMPTY_UNCACHED);
		*///?} else {
		return state.sampler();
		//?}
	}

	/** Biome lookups on one world, uncached, so any thread may use them. */
	public static Biomes biomes(BiomeSource source, RandomState state) {
		//? if >=26.3 {
		/*BiomeResolver resolver = source.createUncachedResolver(state);
		return resolver::getNoiseBiome;
		*///?} else {
		Climate.Sampler sampler = state.sampler();
		return (qx, qy, qz) -> source.getNoiseBiome(qx, qy, qz, sampler);
		//?}
	}

	/** A world's {@link RandomState}: the named noise settings, seeded. */
	public static RandomState randomState(HolderLookup.Provider access, ResourceKey<NoiseGeneratorSettings> settings,
			long seed) {
		//? if >=26.3 {
		/*return RandomState.create(access.lookupOrThrow(Registries.NOISE), seed,
				access.lookupOrThrow(Registries.NOISE_SETTINGS).getOrThrow(settings).value());
		*///?} else {
		return RandomState.create(access, settings, seed);
		//?}
	}

	/** The layout seed of the world {@code state} belongs to, as terrain and biomes derive it. */
	public static long layoutSeed(RandomState state) {
		//? if >=26.3 {
		/*return LayoutSeed.derive(climate(state).temperature()::sampleValue);
		*///?} else {
		return LayoutSeed.derive(state.sampler().temperature());
		//?}
	}
}
