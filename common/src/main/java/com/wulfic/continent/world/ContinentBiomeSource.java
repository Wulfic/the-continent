package com.wulfic.continent.world;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wulfic.continent.Continent;
import com.wulfic.continent.layout.ContinentLayout;
import com.wulfic.continent.layout.DefaultLayout;
import com.wulfic.continent.layout.LayoutSample;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
//? if >=26.3 {
/*import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
*///?} else {
import net.minecraft.world.level.levelgen.DensityFunction;
//?}
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Places biomes from the fixed {@link ContinentLayout}. The surface biome comes from the layout.
 * Underground, the column's cave biome takes over once vanilla's climate depth reaches
 * {@link #CAVE_DEPTH}, and the deep cave biome takes over at or below the layout's deep top y.
 *
 * <p>The codec carries no layout data: the map is {@link DefaultLayout} for every world (Q5). It
 * only resolves the layout's biome ids against the biome registry, and fails, naming the id,
 * if one is missing.
 */
public final class ContinentBiomeSource extends BiomeSource {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(Continent.MOD_ID, "continent");
	/** Climate depth at which vanilla starts placing Lush and Dripstone Caves. */
	public static final double CAVE_DEPTH = 0.2;
	public static final MapCodec<ContinentBiomeSource> CODEC = codecFor(DefaultLayout.create());

	private static final Logger LOGGER = LoggerFactory.getLogger(Continent.MOD_ID);

	private record Column(Holder<Biome> surface, Holder<Biome> cave, Holder<Biome> deep) {
	}

	//? if <26.3 {
	/** The layout seed derived from one sampler. Swapped whole, so readers never see a torn pair. */
	private record SeedBinding(Climate.Sampler sampler, long seed) {
	}
	//?}

	private final ContinentLayout layout;
	private final HolderGetter<Biome> biomeGetter;
	private final Map<String, Holder<Biome>> biomes;
	private final ColumnCache<Column> columns = new ColumnCache<>();
	//? if <26.3 {
	private volatile SeedBinding seedBinding;
	//?}

	private ContinentBiomeSource(ContinentLayout layout, HolderGetter<Biome> biomeGetter,
			Map<String, Holder<Biome>> biomes) {
		this.layout = layout;
		this.biomeGetter = biomeGetter;
		this.biomes = Map.copyOf(biomes);
	}

	/** A codec for any layout; {@link #CODEC} is the one for the built-in map. */
	static MapCodec<ContinentBiomeSource> codecFor(ContinentLayout layout) {
		MapCodec<HolderGetter<Biome>> getter = RecordCodecBuilder.mapCodec(
				instance -> instance.group(RegistryOps.<Biome, HolderGetter<Biome>>retrieveGetter(Registries.BIOME))
						.apply(instance, g -> g));
		return getter.flatXmap(g -> create(layout, g), source -> DataResult.success(source.biomeGetter));
	}

	static DataResult<ContinentBiomeSource> create(ContinentLayout layout, HolderGetter<Biome> getter) {
		Map<String, Holder<Biome>> resolved = new LinkedHashMap<>();
		for (String id : layout.biomeIds()) {
			Identifier parsed = Identifier.tryParse(id);
			if (parsed == null) {
				return fail("malformed biome id \"" + id + "\" in the continent layout");
			}
			Optional<Holder.Reference<Biome>> holder = getter.get(ResourceKey.create(Registries.BIOME, parsed));
			if (holder.isEmpty()) {
				return fail("unknown biome id " + id + " in the continent layout");
			}
			resolved.put(id, holder.get());
		}
		return DataResult.success(new ContinentBiomeSource(layout, getter, resolved));
	}

	private static DataResult<ContinentBiomeSource> fail(String message) {
		LOGGER.error("[{}] cannot build biome source {}: {}", Continent.MOD_ID, ID, message);
		return DataResult.error(() -> message);
	}

	@Override
	protected MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	@Override
	protected Stream<Holder<Biome>> collectPossibleBiomes() {
		return biomes.values().stream();
	}

	//? if >=26.3 {
	/*/^* One resolver per climate sampler, so the layout seed is derived once, here. ^/
	@Override
	public BiomeResolver createResolver(Climate.Sampler sampler) {
		long seed = layoutSeed(sampler);
		DensitySampler.Bound depth = sampler.depth();
		return (qx, qy, qz) -> biome(qx, qy, qz, seed, depth::sampleValue);
	}

	/^* The layout seed for the world {@code sampler} belongs to: three samples, cheap beside a chunk. ^/
	long layoutSeed(Climate.Sampler sampler) {
		DensitySampler.Bound temperature = sampler.temperature();
		return LayoutSeed.derive(temperature::sampleValue);
	}
	*///?} else {
	@Override
	public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
		return biome(qx, qy, qz, layoutSeed(sampler),
				(x, y, z) -> sampler.depth().compute(new DensityFunction.SinglePointContext(x, y, z)));
	}

	/** The layout seed for the world {@code sampler} belongs to. */
	long layoutSeed(Climate.Sampler sampler) {
		SeedBinding binding = seedBinding;
		if (binding == null || binding.sampler() != sampler) {
			binding = new SeedBinding(sampler, LayoutSeed.derive(sampler.temperature()));
			seedBinding = binding;
			LOGGER.debug("[{}] layout seed {} bound to a new climate sampler", Continent.MOD_ID, binding.seed());
		}
		return binding.seed();
	}
	//?}

	/** The biome at a quart position, given the world's layout seed and its climate depth. */
	private Holder<Biome> biome(int qx, int qy, int qz, long seed, LayoutSeed.Seeded climateDepth) {
		Column column = columns.get(qx, qz, seed, this::loadColumn);
		int y = QuartPos.toBlock(qy);
		if (y <= layout.caves().deepTopY()) {
			return column.deep();
		}
		double depth = climateDepth.at(QuartPos.toBlock(qx), y, QuartPos.toBlock(qz));
		return depth >= CAVE_DEPTH ? column.cave() : column.surface();
	}

	private Column loadColumn(int qx, int qz, long seed) {
		LayoutSample s = layout.sample(QuartPos.toBlock(qx), QuartPos.toBlock(qz), seed);
		return new Column(biomes.get(s.biome()), biomes.get(s.caveBiome()), biomes.get(s.deepCaveBiome()));
	}
}
