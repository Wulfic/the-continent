package com.wulfic.continent.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wulfic.continent.Continent;
import com.wulfic.continent.layout.ContinentTerrain;
import com.wulfic.continent.layout.DefaultLayout;
import com.wulfic.continent.layout.TerrainSample;
import java.util.Objects;
import java.util.function.ToDoubleFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.levelgen.RandomState;
//? if >=26.3 {
/*import java.util.Optional;
import net.minecraft.util.Interval;
import net.minecraft.world.level.levelgen.densityfunction.CachingDensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.ContextBoundSampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityBuffer;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.op.SliceFunction;
*///?} else {
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
//?}

/**
 * Density function type {@code the_continent:layout}: one terrain channel of the fixed map, from
 * {@link ContinentTerrain}: continentalness, erosion or ridges (the splines' inputs), or uplift (added to the
 * spline's {@code offset}). The first three add local detail from a seeded vanilla noise, scaled by the
 * region's amplitude. The output is {@code target + amplitude × detail weight × clamp(detail, −1, 1)},
 * clamped to [−1, 1], which are its bounds.
 *
 * <p>Like the biome source, the codec carries no map: it is {@link DefaultLayout#terrain()} for every
 * world. {@code seed_source} must be the overworld's temperature definition. The layout seed is derived
 * from it exactly as {@link ContinentBiomeSource} derives it from the climate sampler, so terrain and
 * biomes warp identically.
 */
public final class LayoutDensityFunction implements DensityFunction {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(Continent.MOD_ID, "layout");
	/** A function written inline or a reference to a registered one. */
	//? if >=26.2 {
	/*private static final Codec<DensityFunction> FUNCTION_CODEC = DensityFunction.CODEC;
	*///?} else {
	private static final Codec<DensityFunction> FUNCTION_CODEC = DensityFunction.HOLDER_HELPER_CODEC;
	//?}
	public static final MapCodec<LayoutDensityFunction> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
			Channel.CODEC.fieldOf("channel").forGetter(f -> f.channel),
			FUNCTION_CODEC.fieldOf("detail").forGetter(f -> f.detail),
			FUNCTION_CODEC.fieldOf("seed_source").forGetter(f -> f.seedSource))
			.apply(instance, LayoutDensityFunction::new));
	//? if <26.3 {
	private static final KeyDispatchDataCodec<LayoutDensityFunction> CODEC = KeyDispatchDataCodec.of(MAP_CODEC);
	//?}

	private static final ContinentTerrain TERRAIN = DefaultLayout.terrain();
	/** The last sample per thread. The spline graph asks every channel for the same column many times over. */
	private static final ThreadLocal<Memo> LAST = ThreadLocal.withInitial(Memo::new);

	public enum Channel implements StringRepresentable {
		CONTINENTALNESS("continentalness", TerrainSample::continentalness, s -> ContinentTerrain.CONTINENTALNESS_DETAIL),
		EROSION("erosion", TerrainSample::erosion, TerrainSample::erosionDetail),
		RIDGES("ridges", TerrainSample::ridges, TerrainSample::ridgesDetail),
		/** Added to the spline's {@code offset}, not one of its inputs. Its detail is the peaks noise. */
		UPLIFT("uplift", TerrainSample::uplift, TerrainSample::upliftDetail);

		static final Codec<Channel> CODEC = StringRepresentable.fromEnum(Channel::values);

		private final String name;
		private final ToDoubleFunction<TerrainSample> target;
		/** Largest move the detail noise may make, before the sample's {@link TerrainSample#detail()} weight. */
		private final ToDoubleFunction<TerrainSample> detailAmplitude;

		Channel(String name, ToDoubleFunction<TerrainSample> target, ToDoubleFunction<TerrainSample> detailAmplitude) {
			this.name = name;
			this.target = target;
			this.detailAmplitude = detailAmplitude;
		}

		/** The amplitude this channel's detail noise gets at a sample, before the river weight. */
		public double detailAmplitude(TerrainSample s) {
			return detailAmplitude.applyAsDouble(s);
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	private static final class Memo {
		int x;
		int z;
		long seed;
		TerrainSample sample;
	}

	private final Channel channel;
	private final DensityFunction detail;
	private final DensityFunction seedSource;
	//? if <26.3 {
	/**
	 * Derived on first use, never in the constructor: the codec builds this while the registry is still
	 * loading, and {@code seed_source}'s references are unbound until it finishes. Each copy that
	 * {@link #mapAll} makes derives its own, so the wired copy hashes the world's seeded noise. A
	 * volatile, immutable box, so a racing thread sees either null or the whole value.
	 */
	private volatile Long layoutSeed;
	//?}

	LayoutDensityFunction(Channel channel, DensityFunction detail, DensityFunction seedSource) {
		this.channel = channel;
		this.detail = detail;
		this.seedSource = seedSource;
	}

	/** The channel's value at a column of the world with this layout seed, given the detail noise there. */
	static double value(Channel channel, long seed, int x, int z, double detailNoise) {
		TerrainSample s = sample(x, z, seed);
		double noise = Mth.clamp(detailNoise, -1, 1);
		return Mth.clamp(channel.target.applyAsDouble(s) + channel.detailAmplitude(s) * s.detail() * noise, -1, 1);
	}

	private static TerrainSample sample(int x, int z, long seed) {
		Memo memo = LAST.get();
		if (memo.sample == null || memo.x != x || memo.z != z || memo.seed != seed) {
			memo.sample = TERRAIN.sample(x, z, seed);
			memo.x = x;
			memo.z = z;
			memo.seed = seed;
		}
		return memo.sample;
	}

	//? if >=26.3 {
	/*/^*
	 * Whether this world's continentalness is the continent layout, which is true only of worlds on the
	 * Continent noise settings. 26.3 keeps the noise router private, so this reads the compiled climate sampler,
	 * unwrapping the cache and the axis slice the compiler puts around the function.
	 ^/
	public static boolean drivesTerrainOf(RandomState state) {
		return find(state.createClimateSampler(SamplerContext.EMPTY_UNCACHED).continentalness().sampler()).isPresent();
	}

	/^* Our sampler inside a compiled one, under the caches and axis slices the compiler adds; empty if not ours. ^/
	static Optional<Sampler> find(DensitySampler s) {
		while (true) {
			switch (s) {
				case Sampler ours -> {
					return Optional.of(ours);
				}
				case CachingDensitySampler cache -> s = cache.input();
				case ContextBoundSampler bound -> s = bound.fallbackSampler();
				case SliceFunction.XSampler slice -> s = slice.input();
				case SliceFunction.YSampler slice -> s = slice.input();
				case SliceFunction.ZSampler slice -> s = slice.input();
				case SliceFunction.XzSampler slice -> s = slice.input();
				default -> {
					return Optional.empty();
				}
			}
		}
	}

	/^*
	 * The world's seeded noise is bound here, so the layout seed is derived here, once per world, from the
	 * compiled {@code seed_source}.
	 ^/
	@Override
	public DensitySampler compileSampler(CompileContext context) {
		DensitySampler seeded = seedSource.compileSampler(context);
		long seed = LayoutSeed.derive((x, y, z) -> seeded.sampleValue(SamplerContext.EMPTY_UNCACHED, x, y, z));
		return new Sampler(channel, detail.compileSampler(context), seed);
	}

	@Override
	public DensityFunction rewriteChildren(DfRewriteRule rule) {
		DensityFunction newDetail = rule.rewrite(detail);
		DensityFunction newSeedSource = rule.rewrite(seedSource);
		return newDetail == detail && newSeedSource == seedSource ? this
				: new LayoutDensityFunction(channel, newDetail, newSeedSource);
	}

	@Override
	public Interval range() {
		return Interval.of(-1, 1);
	}

	/^* The layout is a map: it varies over x and z only, plus whatever the detail noise varies over. ^/
	@Override
	public @Axes int domainAxes() {
		return AXIS_X | AXIS_Z | detail.domainAxes();
	}

	@Override
	public MapCodec<LayoutDensityFunction> codec() {
		return MAP_CODEC;
	}

	/^* One channel of one world: its layout seed is fixed, and its detail noise compiled. ^/
	record Sampler(Channel channel, DensitySampler detail, long seed) implements DensitySampler {
		@Override
		public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
			return (float) value(channel, seed, blockX, blockZ, detail.sampleValue(context, blockX, blockY, blockZ));
		}

		@Override
		public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
			DensitySampler.sampleVolumeNaive(context, outputBuffer, volume, this);
		}
	}
	*///?} else {
	/**
	 * Whether this world's continentalness is the continent layout, which is true only of worlds on the
	 * Continent noise settings. Wiring leaves the registry holder and the flat cache around the function,
	 * so both are unwrapped.
	 */
	public static boolean drivesTerrainOf(RandomState state) {
		DensityFunction f = state.router().continents();
		while (true) {
			if (f instanceof LayoutDensityFunction) {
				return true;
			}
			if (f instanceof DensityFunctions.HolderHolder holder) {
				f = holder.function().value();
			} else if (f instanceof DensityFunctions.MarkerOrMarked marked) {
				f = marked.wrapped();
			} else {
				return false;
			}
		}
	}

	long layoutSeed() {
		Long seed = layoutSeed;
		if (seed == null) {
			seed = LayoutSeed.derive(seedSource);
			layoutSeed = seed;
		}
		return seed;
	}

	@Override
	public double compute(FunctionContext context) {
		return value(channel, layoutSeed(), context.blockX(), context.blockZ(), detail.compute(context));
	}

	@Override
	public void fillArray(double[] array, ContextProvider contextProvider) {
		contextProvider.fillAllDirectly(array, this);
	}

	//? if >=26.2 {
	/*@Override
	public DensityFunction mapChildren(Visitor visitor) {
		return new LayoutDensityFunction(channel, visitor.apply(detail), visitor.apply(seedSource));
	}
	*///?} else {
	@Override
	public DensityFunction mapAll(Visitor visitor) {
		return visitor.apply(new LayoutDensityFunction(channel, detail.mapAll(visitor), seedSource.mapAll(visitor)));
	}
	//?}

	@Override
	public double minValue() {
		return -1;
	}

	@Override
	public double maxValue() {
		return 1;
	}

	@Override
	public KeyDispatchDataCodec<? extends DensityFunction> codec() {
		return CODEC;
	}
	//?}

	/**
	 * Value equality, like vanilla's record-based functions. {@code RandomState} and {@code NoiseChunk}
	 * dedupe the graph through a map keyed on the function (26.3's compiler too). The spline graph references
	 * the ridges and erosion channels ~100 times, and with identity equality each reference became its own
	 * cache, which tripled the cost of a noise chunk. The layout seed is a pure function of {@code seedSource},
	 * so leaving it out is exact.
	 */
	@Override
	public boolean equals(Object o) {
		return o instanceof LayoutDensityFunction other && channel == other.channel && detail.equals(other.detail)
				&& seedSource.equals(other.seedSource);
	}

	@Override
	public int hashCode() {
		return Objects.hash(channel, detail, seedSource);
	}
}
