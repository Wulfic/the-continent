package com.wulfic.continent.world;

//? if <26.3 {
import net.minecraft.world.level.levelgen.DensityFunction;
//?}

/**
 * Recovers a per-world seed for the layout's domain warp. Minecraft never hands the world seed
 * to a {@code BiomeSource}: {@code RandomState} only wires seeded noise into density functions.
 * So the seed is a hash of a seeded climate function at fixed far-off probes.
 *
 * <p>Every adapter that samples the layout must derive its seed from the same function (vanilla
 * overworld temperature) through this class, or biomes and terrain warp differently.
 */
public final class LayoutSeed {
	/** Off-lattice, far from the origin, so noise is not pinned at 0. */
	private static final int[][] PROBES = {{7919, 0, -104_729}, {-31_337, 0, 65_537}, {271_828, 0, -314_159}};

	/** A seeded function's value at a block. */
	@FunctionalInterface
	public interface Seeded {
		double at(int x, int y, int z);
	}

	private LayoutSeed() {
	}

	public static long derive(Seeded seeded) {
		long h = 0x9E3779B97F4A7C15L;
		for (int[] p : PROBES) {
			h = mix(h ^ Double.doubleToLongBits(seeded.at(p[0], p[1], p[2])));
		}
		return h;
	}

	//? if <26.3 {
	public static long derive(DensityFunction seeded) {
		return derive((x, y, z) -> seeded.compute(new DensityFunction.SinglePointContext(x, y, z)));
	}
	//?}

	/** SplitMix64 finaliser. */
	private static long mix(long h) {
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		return h ^ (h >>> 31);
	}
}
