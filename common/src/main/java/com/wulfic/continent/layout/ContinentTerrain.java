package com.wulfic.continent.layout;

import com.wulfic.continent.Continent;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns the layout into targets for vanilla's terrain splines: continentalness (C), erosion (E) and
 * ridges (W), the inputs of the overworld's {@code offset}, {@code factor} and {@code jaggedness}.
 * It samples at the same warped position as {@link ContinentLayout#sample}, and blends regions at the same
 * roughened one, so terrain and biome borders line up. Immutable and thread-safe. {@link #sample} is
 * O(seeds + islands + river points).
 *
 * <p>Applied in order:
 * <ol>
 * <li>Land: every region's {@link TerrainShape} (shape, detail amplitudes, uplift), blended continuously
 *     across borders. An island uses its own shape.
 * <li>Coast: a ramp from the shore up to the land shape, across which the uplift fades in. On beach coasts
 *     and islands, E, W and their detail also ease to a flat beach shape, so the shore sits at sea level. On
 *     cliff coasts the land keeps its shape and drops straight to the sea floor.
 * <li>Sea: a shelf from the shore down to the shallow floor, then a slope to the deep floor that ends
 *     where the deep-ocean biome starts. No uplift.
 * <li>River: a valley that pulls W and E to 0 and drops the uplift and detail, so the channel floor is below
 *     sea level in any region.
 * </ol>
 *
 * <p>Spline facts these numbers rest on (1.21.11, unchanged in 26.1.2 per TerrainResourcesTest; surface y ≈ 128 ·
 * (1.5 + offset) − 64): C ≤ −0.19 is sea floor at y 48 whatever E and W are, C ≤ −0.51 is the deep floor at y 35, and
 * the shore drop lies between C −0.19 and −0.15. E from 0.45 to 0.55 collapses vanilla's factor to 0.6, giving
 * floating, shattered terrain, so nothing here may reach it.
 */
public final class ContinentTerrain {
	/**
	 * Largest local-noise term the density function adds to continentalness, at {@code detail} 1. Erosion and
	 * ridges detail come from each region's {@link TerrainShape}.
	 */
	public static final double CONTINENTALNESS_DETAIL = 0.05;
	/**
	 * Largest uplift any point may get, {@link TerrainShape#uplift} plus its detail. On top of the splines'
	 * highest point (y ~250) that is ~y 315, so the raised top slide (288-304 in the noise settings) is what
	 * rounds the summits off, under the build limit (320).
	 */
	public static final double MAX_UPLIFT = 0.5;

	/** Lowest erosion of the band where vanilla terrain shatters. Profiles plus detail stay below it. */
	static final double SHATTERED_EROSION = 0.45;
	/** Distance difference, in blocks, over which a neighbouring region's shape fades out. */
	static final double REGION_BLEND = 96;
	/** Land-side width of the climb from the shore to the land shape. */
	static final double COAST_RAMP = 128;
	/** Sea-side width of the shelf from the shore down to the shallow floor. */
	static final double SHELF_WIDTH = 32;
	/** Width of the slope from the shallow floor to the deep floor, ending where the deep-ocean biome starts. */
	static final double DEEP_SLOPE = 96;
	/** How far past the river's edge the valley climbs back to the land shape. */
	static final double RIVER_BANK = 40;
	/** Distance from a quadrant diagonal over which the coast eases into its own quadrant's style. */
	static final double QUADRANT_BLEND = 64;

	/** Shore C on a beach coast: the shoreline sits at sea level. */
	static final double BEACH_SHORE = -0.15;
	/** Shore C on a cliff coast: the sea floor reaches the shore, and the land drops straight into it. */
	static final double CLIFF_SHORE = -0.19;
	static final double SHALLOW_FLOOR = -0.3;
	static final double DEEP_FLOOR = -0.6;
	/** E and W at a beach shoreline: flat, at sea level, with the default detail. */
	static final double BEACH_EROSION = 0.2;
	static final double BEACH_RIDGES = 0.3;
	static final double BEACH_EROSION_DETAIL = TerrainShape.DEFAULT_EROSION_DETAIL;
	static final double BEACH_RIDGES_DETAIL = TerrainShape.DEFAULT_RIDGES_DETAIL;
	/** E on the river's centre line. With W 0 it puts the channel floor near y 51 in any region. */
	static final double RIVER_EROSION = 0;

	private static final Logger LOGGER = LoggerFactory.getLogger(Continent.MOD_ID);
	private static final double SQRT_2 = Math.sqrt(2);

	private final ContinentLayout layout;
	private final Map<String, TerrainShape> profiles;
	/** Profiles in {@code layout.seeds()} and {@code layout.islands()} order, so sampling does no lookups. */
	private final TerrainShape[] seedProfiles;
	private final TerrainShape[] islandProfiles;
	/** Per {@link Coast} ordinal: 1 for a beach coast, 0 for a cliff; offshore distance where deep ocean starts. */
	private final double[] beachByCoast = new double[Coast.values().length];
	private final double[] deepStartByCoast = new double[Coast.values().length];

	/** @param profiles terrain shape per land biome: exactly the layout's seed and island biomes */
	public ContinentTerrain(ContinentLayout layout, Map<String, TerrainShape> profiles) {
		this.layout = layout;
		this.profiles = Map.copyOf(profiles);
		validate(layout, this.profiles);
		seedProfiles = layout.seeds().stream().map(s -> this.profiles.get(s.biome())).toArray(TerrainShape[]::new);
		islandProfiles = layout.islands().stream().map(i -> this.profiles.get(i.biome())).toArray(TerrainShape[]::new);
		for (Coast coast : Coast.values()) {
			CoastStyle style = layout.coasts().get(coast);
			beachByCoast[coast.ordinal()] = style.beach() ? 1 : 0;
			deepStartByCoast[coast.ordinal()] = style.bandWidth() + layout.shallowOceanWidth();
		}
	}

	public ContinentLayout layout() {
		return layout;
	}

	public Map<String, TerrainShape> profiles() {
		return profiles;
	}

	public TerrainSample sample(double x, double z, long seed) {
		double wx = layout.warpX(x, z, seed);
		double wz = layout.warpZ(x, z, seed);

		double mainland = layout.coastline().distance(wx, wz);
		double coast = mainland;
		TerrainShape land = null;
		for (int i = 0; i < islandProfiles.length; i++) {
			Island island = layout.islands().get(i);
			double d = island.radius() - Math.hypot(wx - island.x(), wz - island.z());
			if (d > coast) {
				coast = d;
				land = islandProfiles[i];
			}
		}

		// How much of a beach this shore has (1) versus a cliff (0), and where deep ocean starts. The biome
		// switches style hard at the quadrant diagonals. Terrain eases from this quadrant's style to the
		// beachier, nearer-deep of the two at the diagonal: continuous, and never shallower than a
		// deep-ocean biome above it.
		double toMain = (wx - wz) / SQRT_2;
		double toCross = (wx + wz) / SQRT_2;
		boolean nearMain = Math.abs(toMain) < Math.abs(toCross);
		int own = Coast.of(wx, wz).ordinal();
		int across = (nearMain ? Coast.of(wz, wx) : Coast.of(-wz, -wx)).ordinal();
		double ownWeight = Math.min(1, Math.abs(nearMain ? toMain : toCross) / QUADRANT_BLEND);
		double beach = land != null ? 1
				: lerp(ownWeight, Math.max(beachByCoast[own], beachByCoast[across]), beachByCoast[own]);
		double deepStart = lerp(ownWeight, Math.min(deepStartByCoast[own], deepStartByCoast[across]), deepStartByCoast[own]);

		if (land == null) {
			double u = layout.roughX(x, z, seed);
			double v = layout.roughZ(x, z, seed);
			double scale = layout.roughScale(u, v);
			land = blendRegions(wx + scale * u, wz + scale * v);
		}
		double c = land.continentalness();
		double e = land.erosion();
		double w = land.ridges();
		double eDetail = land.erosionDetail();
		double wDetail = land.ridgesDetail();
		double uplift = land.uplift();
		double upliftDetail = land.upliftDetail();
		double shore = lerp(beach, CLIFF_SHORE, BEACH_SHORE);
		// E and its detail always move together by the same weight, so E + detail stays a convex mix of
		// values below the shattered band, and never reaches it.
		// The uplift fades out across the coast ramp too, so it never lifts a shore or a sea floor.
		double toBeach;
		if (coast >= 0) {
			double inland = smooth(coast / COAST_RAMP);
			toBeach = beach * (1 - inland);
			c = lerp(inland, shore, c);
			uplift *= inland;
			upliftDetail *= inland;
		} else {
			double slope = smooth((-mainland - (deepStart - DEEP_SLOPE)) / DEEP_SLOPE);
			c = lerp(smooth(-coast / SHELF_WIDTH), shore, lerp(slope, SHALLOW_FLOOR, DEEP_FLOOR));
			toBeach = beach;
			uplift = 0;
			upliftDetail = 0;
		}
		e = lerp(toBeach, e, BEACH_EROSION);
		w = lerp(toBeach, w, BEACH_RIDGES);
		eDetail = lerp(toBeach, eDetail, BEACH_EROSION_DETAIL);
		wDetail = lerp(toBeach, wDetail, BEACH_RIDGES_DETAIL);

		// Last, so the valley cuts through the beach and reaches the sea.
		double valley = smooth(layout.riverDistance(wx, wz) / (layout.riverWidth() / 2 + RIVER_BANK));
		e = lerp(valley, RIVER_EROSION, e);
		w *= valley;
		uplift *= valley;
		upliftDetail *= valley;
		return new TerrainSample(c, e, w, eDetail, wDetail, uplift, upliftDetail, valley);
	}

	/**
	 * Every region's shape, weighted by {@code (1 − (|p − sᵢ| − d_min) / REGION_BLEND)²}. Each weight is
	 * continuous in p, so the blend is too, including at triple junctions, unlike a blend toward
	 * whichever neighbour's edge is nearest.
	 */
	private TerrainShape blendRegions(double x, double z) {
		List<BiomeSeed> seeds = layout.seeds();
		double nearest = Double.MAX_VALUE;
		for (BiomeSeed s : seeds) {
			nearest = Math.min(nearest, distance(s, x, z));
		}
		double total = 0;
		double c = 0;
		double e = 0;
		double w = 0;
		double eDetail = 0;
		double wDetail = 0;
		double uplift = 0;
		double upliftDetail = 0;
		for (int i = 0; i < seedProfiles.length; i++) {
			double t = (distance(seeds.get(i), x, z) - nearest) / REGION_BLEND;
			if (t < 1) {
				double weight = (1 - t) * (1 - t);
				TerrainShape p = seedProfiles[i];
				total += weight;
				c += weight * p.continentalness();
				e += weight * p.erosion();
				w += weight * p.ridges();
				eDetail += weight * p.erosionDetail();
				wDetail += weight * p.ridgesDetail();
				uplift += weight * p.uplift();
				upliftDetail += weight * p.upliftDetail();
			}
		}
		return new TerrainShape(c / total, e / total, w / total, eDetail / total, wDetail / total, uplift / total,
				upliftDetail / total);
	}

	private static double distance(BiomeSeed s, double x, double z) {
		double dx = x - s.x();
		double dz = z - s.z();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** Smoothstep of t clamped to [0, 1]: zero slope at both ends. */
	private static double smooth(double t) {
		if (t <= 0) {
			return 0;
		}
		if (t >= 1) {
			return 1;
		}
		return t * t * (3 - 2 * t);
	}

	private static double lerp(double t, double a, double b) {
		return a + t * (b - a);
	}

	// ---- validation: fail loud at construction, naming the offending entry ----

	private static void validate(ContinentLayout layout, Map<String, TerrainShape> profiles) {
		Set<String> landBiomes = new HashSet<>();
		layout.seeds().forEach(s -> landBiomes.add(s.biome()));
		layout.islands().forEach(i -> landBiomes.add(i.biome()));
		for (String biome : landBiomes) {
			require(profiles.containsKey(biome), "no terrain profile for " + biome);
		}
		for (Map.Entry<String, TerrainShape> entry : profiles.entrySet()) {
			String biome = entry.getKey();
			TerrainShape p = entry.getValue();
			require(landBiomes.contains(biome), "terrain profile for " + biome + ", which is not a land biome of the layout");
			requireUnit(p.continentalness(), biome + " continentalness");
			requireUnit(p.erosion(), biome + " erosion");
			requireUnit(p.ridges(), biome + " ridges");
			require(p.erosionDetail() >= 0 && p.erosionDetail() <= 1, biome + " erosion detail " + p.erosionDetail()
					+ " is outside [0, 1]");
			require(p.ridgesDetail() >= 0 && p.ridgesDetail() <= 1, biome + " ridges detail " + p.ridgesDetail()
					+ " is outside [0, 1]");
			require(p.erosion() + p.erosionDetail() < SHATTERED_EROSION, biome + " erosion " + p.erosion()
					+ " plus detail " + p.erosionDetail() + " reaches the shattered band at " + SHATTERED_EROSION);
			require(p.upliftDetail() >= 0 && p.uplift() - p.upliftDetail() >= 0
					&& p.uplift() + p.upliftDetail() <= MAX_UPLIFT, biome + " uplift " + p.uplift() + " ± detail "
					+ p.upliftDetail() + " is outside [0, " + MAX_UPLIFT + "]");
		}
		for (Island island : layout.islands()) {
			double channel = layout.channel(island);
			require(channel >= 2 * SHELF_WIDTH, "island " + island.biome() + " channel " + channel
					+ " is narrower than two shelves (" + 2 * SHELF_WIDTH + "), so the sea floor would step mid-channel");
		}
	}

	private static void requireUnit(double value, String what) {
		require(value >= -1 && value <= 1, what + " " + value + " is outside [-1, 1]");
	}

	private static void require(boolean ok, String message) {
		if (!ok) {
			LOGGER.error("[{}] invalid continent terrain: {}", Continent.MOD_ID, message);
			throw new IllegalArgumentException("Invalid continent terrain: " + message);
		}
	}
}
