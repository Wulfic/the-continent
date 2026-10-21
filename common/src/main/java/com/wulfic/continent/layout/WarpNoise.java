package com.wulfic.continent.layout;

/**
 * 2D value noise in [−1, 1] for the domain warps. Deterministic per seed, stateless, allocation-free.
 *
 * <p>{@link #sample} has two octaves with cells of 256 and 128 blocks, so the slope is at most ~0.016 per
 * block: a warp of amplitude 32 squeezes a band to no less than about half its width. {@link #fine} has
 * four octaves with cells of 96, 48, 24 and 12 blocks, for ragged region borders: a meander with jags on it.
 * Its slope reaches ~0.07 per block, so a warp of more than ~15 blocks folds over and leaves small stray
 * patches along a border. That is wanted there, and why only the region lookup uses it.
 */
final class WarpNoise {
	private static final double OCTAVE_1 = 1.0 / 256;
	private static final double OCTAVE_2 = 1.0 / 128;
	private static final long OCTAVE_2_SALT = 0x632BE59BD9B4E019L;
	private static final double FINE_1 = 1.0 / 96;
	private static final double FINE_2 = 1.0 / 48;
	private static final double FINE_3 = 1.0 / 24;
	private static final double FINE_4 = 1.0 / 12;
	private static final long FINE_1_SALT = 0x2545F4914F6CDD1DL;
	private static final long FINE_2_SALT = 0x4F1BBCDCBFA53E0BL;
	private static final long FINE_3_SALT = 0x7A646E4D9B1A3C55L;
	private static final long FINE_4_SALT = 0x61C8864680B583EBL;

	private WarpNoise() {
	}

	static double sample(long seed, double x, double z) {
		double a = value(seed, x * OCTAVE_1, z * OCTAVE_1);
		double b = value(seed ^ OCTAVE_2_SALT, x * OCTAVE_2, z * OCTAVE_2);
		return (a + 0.5 * b) / 1.5;
	}

	/** Four octaves, each half the amplitude of the one before, so the large wiggles carry the short ones. */
	static double fine(long seed, double x, double z) {
		double a = value(seed ^ FINE_1_SALT, x * FINE_1, z * FINE_1);
		double b = value(seed ^ FINE_2_SALT, x * FINE_2, z * FINE_2);
		double c = value(seed ^ FINE_3_SALT, x * FINE_3, z * FINE_3);
		double d = value(seed ^ FINE_4_SALT, x * FINE_4, z * FINE_4);
		return (a + 0.5 * b + 0.25 * c + 0.125 * d) / 1.875;
	}

	private static double value(long seed, double x, double z) {
		double fx = Math.floor(x);
		double fz = Math.floor(z);
		int x0 = (int) fx;
		int z0 = (int) fz;
		double u = smooth(x - fx);
		double v = smooth(z - fz);
		double top = lerp(u, lattice(seed, x0, z0), lattice(seed, x0 + 1, z0));
		double bottom = lerp(u, lattice(seed, x0, z0 + 1), lattice(seed, x0 + 1, z0 + 1));
		return lerp(v, top, bottom);
	}

	/** Hash of a lattice corner, uniform in [−1, 1). */
	private static double lattice(long seed, int x, int z) {
		long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (z * 0xC2B2AE3D27D4EB4FL);
		h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
		h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
		h ^= h >>> 31;
		return (h >>> 11) * 0x1.0p-52 - 1.0;
	}

	private static double smooth(double t) {
		return t * t * (3 - 2 * t);
	}

	private static double lerp(double t, double a, double b) {
		return a + t * (b - a);
	}
}
