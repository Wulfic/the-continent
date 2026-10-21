package com.wulfic.continent.layout;

import com.wulfic.continent.Continent;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The fixed map of The Continent: the single source of truth that the biome source and the
 * terrain density function both read. Immutable and thread-safe. {@link #sample} is
 * O(seeds + islands + river points + cave seeds) and allocates only its result.
 *
 * <p>Mainland biomes are a nearest-seed Voronoi diagram inside the {@code coastline}. Both are
 * evaluated at a domain-warped position, so borders and the coast wobble by up to
 * {@code warpAmplitude} blocks per seed while the layout itself is the same for every seed.
 * The Voronoi lookup alone takes a second, finer warp of up to {@code borderRoughness} blocks on
 * top, so borders between regions are ragged while the coast, river and islands keep their shape.
 *
 * @param seeds mainland regions; at least one, distinct biomes, all inside the {@code coastline}
 * @param coastline the mainland's shore; no steeper than {@link Coastline#MAX_SLOPE}
 * @param islands round landmasses that must not touch the mainland
 * @param beachWidth width of the beach band on coasts whose style has one
 * @param coasts style per compass quadrant; all four required
 * @param shallowOceanWidth width of plain ocean between a coastal band and the deep ocean
 * @param river centre-line polyline, at least two points; only carved on the mainland. It starts on
 *        land and crosses the shore exactly once, so it ends at sea
 * @param riverWidth full width of the river
 * @param warpAmplitude maximum domain-warp displacement in blocks, per axis
 * @param borderRoughness maximum extra displacement of the region lookup in blocks, from a fine noise
 *        (cells 96 to 12 blocks) that frays the borders between regions
 * @param villageAnchor the guaranteed village's centre: inside the Plains region, at least
 *        {@link #VILLAGE_MARGIN} blocks from every border and from the river at any seed
 * @param caves underground regions
 */
public record ContinentLayout(
		List<BiomeSeed> seeds,
		Coastline coastline,
		List<Island> islands,
		double beachWidth,
		Map<Coast, CoastStyle> coasts,
		double shallowOceanWidth,
		List<Point> river,
		double riverWidth,
		double warpAmplitude,
		double borderRoughness,
		Point villageAnchor,
		Caves caves) {

	/** Blocks of clearance the village keeps from any biome border and from the river. */
	public static final double VILLAGE_MARGIN = 64;
	public static final String VILLAGE_BIOME = VanillaBiomes.PLAINS;

	private static final Logger LOGGER = LoggerFactory.getLogger(Continent.MOD_ID);
	private static final long WARP_Z_SALT = 0x5DEECE66DL;
	private static final long REGION_X_SALT = 0x3C6EF372FE94F82BL;
	private static final long REGION_Z_SALT = 0x1F83D9ABFB41BD6BL;
	private static final double SQRT_2 = Math.sqrt(2);
	private static final double ROUGH_GAIN = 2.5;

	public ContinentLayout {
		seeds = List.copyOf(seeds);
		islands = List.copyOf(islands);
		coasts = Map.copyOf(coasts);
		river = List.copyOf(river);
		validate(seeds, coastline, islands, beachWidth, coasts, shallowOceanWidth, river, riverWidth,
				warpAmplitude, borderRoughness, villageAnchor);
		validateCaves(caves);
	}

	public LayoutSample sample(double x, double z, long seed) {
		double wx = warpX(x, z, seed);
		double wz = warpZ(x, z, seed);
		double u = roughX(x, z, seed);
		double v = roughZ(x, z, seed);
		double scale = roughScale(u, v);
		return classify(wx, wz, wx + scale * u, wz + scale * v);
	}

	/** Where (x, z) lands after the domain warp. Everything that reads the map samples there. */
	double warpX(double x, double z, long seed) {
		return x + warpAmplitude * WarpNoise.sample(seed, x, z);
	}

	double warpZ(double x, double z, long seed) {
		return z + warpAmplitude * WarpNoise.sample(seed ^ WARP_Z_SALT, x, z);
	}

	/**
	 * The fine border noise at (x, z), per axis. The region lookup reads at the warped position plus
	 * {@code roughScale(u, v) · (u, v)}. Only the Voronoi lookup and the terrain's region blend read there.
	 */
	double roughX(double x, double z, long seed) {
		return WarpNoise.fine(seed ^ REGION_X_SALT, x, z);
	}

	double roughZ(double x, double z, long seed) {
		return WarpNoise.fine(seed ^ REGION_Z_SALT, x, z);
	}

	/**
	 * Blocks per unit of rough noise. The noise is boosted by {@link #ROUGH_GAIN}, since it rarely strays far from 0,
	 * then held to the unit disk, so the push never exceeds {@code borderRoughness} and averages about 3/4 of it.
	 */
	double roughScale(double u, double v) {
		return borderRoughness * ROUGH_GAIN / Math.max(1, ROUGH_GAIN * Math.hypot(u, v));
	}

	/** Distance from an already-warped position to the river's centre line. */
	double riverDistance(double x, double z) {
		return polylineDistance(river, x, z);
	}

	/** Width of the open water between an island and the mainland shore, on the unwarped map. */
	public double channel(Island island) {
		return channel(coastline, island);
	}

	/** Every biome id {@link #sample} can emit, surface and cave, in a stable order. */
	public Set<String> biomeIds() {
		Set<String> ids = new LinkedHashSet<>();
		seeds.forEach(s -> ids.add(s.biome()));
		islands.forEach(i -> ids.add(i.biome()));
		ids.add(VanillaBiomes.RIVER);
		ids.add(VanillaBiomes.BEACH);
		coasts.values().forEach(c -> ids.add(c.bandBiome()));
		ids.add(VanillaBiomes.OCEAN);
		ids.add(VanillaBiomes.DEEP_OCEAN);
		caves.seeds().forEach(s -> ids.add(s.biome()));
		ids.add(caves.deepBiome());
		return ids;
	}

	/** Classifies an already-warped position (x, z) whose region lookup reads at (rx, rz). */
	private LayoutSample classify(double x, double z, double rx, double rz) {
		int first = nearestSeed(seeds, rx, rz);
		int second = borderNeighbour(seeds, first, rx, rz);
		String primary = seeds.get(first).biome();
		double border = second < 0 ? Double.MAX_VALUE : bisectorDistance(seeds.get(first), seeds.get(second), rx, rz);

		double mainlandCoast = coastline.distance(x, z);
		double coast = mainlandCoast;
		Island onIsland = null;
		for (int i = 0; i < islands.size(); i++) {
			Island island = islands.get(i);
			double d = island.radius() - Math.hypot(x - island.x(), z - island.z());
			if (d > coast) {
				coast = d;
				if (d > 0) {
					onIsland = island;
				}
			}
		}
		double riverDistance = polylineDistance(river, x, z);
		String cave = caves.seeds().get(nearestSeed(caves.seeds(), x, z)).biome();
		String deepCave = z < caves.deepEdgeZ() ? caves.deepBiome() : cave;

		String biome;
		LayoutSample.Zone zone;
		CoastStyle style = coasts.get(Coast.of(x, z));
		if (onIsland != null) {
			biome = onIsland.biome();
			zone = LayoutSample.Zone.ISLAND;
			cave = biome;
			deepCave = biome;
		} else if (mainlandCoast > 0) {
			if (riverDistance < riverWidth / 2) {
				biome = VanillaBiomes.RIVER;
				zone = LayoutSample.Zone.RIVER;
			} else if (style.beach() && mainlandCoast < beachWidth) {
				biome = VanillaBiomes.BEACH;
				zone = LayoutSample.Zone.BEACH;
			} else {
				biome = primary;
				zone = LayoutSample.Zone.LAND;
			}
		} else {
			double offshore = -mainlandCoast;
			if (offshore < style.bandWidth()) {
				biome = style.bandBiome();
				zone = LayoutSample.Zone.COASTAL_BAND;
			} else if (offshore < style.bandWidth() + shallowOceanWidth) {
				biome = VanillaBiomes.OCEAN;
				zone = LayoutSample.Zone.OCEAN;
			} else {
				biome = VanillaBiomes.DEEP_OCEAN;
				zone = LayoutSample.Zone.DEEP_OCEAN;
			}
		}
		return new LayoutSample(biome, zone, primary, border, coast, riverDistance, cave, deepCave);
	}

	// ---- geometry (static so the constructor can use it before the record exists) ----

	private static double squaredDistance(double ax, double az, double bx, double bz) {
		double dx = ax - bx;
		double dz = az - bz;
		return dx * dx + dz * dz;
	}

	private static int nearestSeed(List<BiomeSeed> seeds, double x, double z) {
		int nearest = 0;
		double best = Double.MAX_VALUE;
		for (int i = 0; i < seeds.size(); i++) {
			double d = squaredDistance(seeds.get(i).x(), seeds.get(i).z(), x, z);
			if (d < best) {
				best = d;
				nearest = i;
			}
		}
		return nearest;
	}

	/**
	 * The seed across the nearest edge of {@code nearest}'s cell, or −1 if there is only one
	 * seed. A Voronoi cell is the intersection of half-planes, so the distance to its boundary is
	 * the smallest bisector distance over <em>all</em> other seeds, not just the second-nearest.
	 */
	private static int borderNeighbour(List<BiomeSeed> seeds, int nearest, double x, double z) {
		int neighbour = -1;
		double best = Double.MAX_VALUE;
		for (int i = 0; i < seeds.size(); i++) {
			if (i == nearest) {
				continue;
			}
			double d = bisectorDistance(seeds.get(nearest), seeds.get(i), x, z);
			if (d < best) {
				best = d;
				neighbour = i;
			}
		}
		return neighbour;
	}

	/** Signed distance from (x, z) to the perpendicular bisector of a and b; positive on a's side. */
	private static double bisectorDistance(BiomeSeed a, BiomeSeed b, double x, double z) {
		double gap = Math.sqrt(squaredDistance(a.x(), a.z(), b.x(), b.z()));
		return (squaredDistance(b.x(), b.z(), x, z) - squaredDistance(a.x(), a.z(), x, z)) / (2 * gap);
	}

	private static double polylineDistance(List<Point> line, double x, double z) {
		double best = Double.MAX_VALUE;
		for (int i = 0; i + 1 < line.size(); i++) {
			Point a = line.get(i);
			Point b = line.get(i + 1);
			double vx = b.x() - a.x();
			double vz = b.z() - a.z();
			double t = ((x - a.x()) * vx + (z - a.z()) * vz) / (vx * vx + vz * vz);
			t = Math.max(0, Math.min(1, t));
			best = Math.min(best, Math.hypot(x - (a.x() + t * vx), z - (a.z() + t * vz)));
		}
		return best;
	}

	// ---- validation: fail loud at construction, naming the offending entry ----

	private static void validate(List<BiomeSeed> seeds, Coastline coastline, List<Island> islands, double beachWidth,
			Map<Coast, CoastStyle> coasts, double shallowOceanWidth, List<Point> river, double riverWidth,
			double warpAmplitude, double borderRoughness, Point villageAnchor) {
		if (seeds.isEmpty()) {
			throw invalid("seed list is empty");
		}
		require(coastline != null, "coastline is missing");
		double steepest = coastline.steepestSlope();
		require(steepest <= Coastline.MAX_SLOPE, "coastline runs too steeply against the radius (|dr/dθ|/r " + steepest
				+ " > " + Coastline.MAX_SLOPE + "), which would squeeze the coastal bands");
		require(beachWidth >= 0, "beachWidth must be >= 0, got " + beachWidth);
		require(shallowOceanWidth >= 0, "shallowOceanWidth must be >= 0, got " + shallowOceanWidth);
		require(riverWidth >= 0, "riverWidth must be >= 0, got " + riverWidth);
		require(warpAmplitude >= 0, "warpAmplitude must be >= 0, got " + warpAmplitude);
		require(borderRoughness >= 0, "borderRoughness must be >= 0, got " + borderRoughness);

		Set<String> biomes = new HashSet<>();
		for (BiomeSeed s : seeds) {
			requireBiomeId(s.biome(), s);
			require(biomes.add(s.biome()), "duplicate biome " + s.biome() + " in " + s);
			require(coastline.distance(s.x(), s.z()) > 0,
					"land seed " + s.biome() + " at (" + s.x() + ", " + s.z() + ") is outside the coastline");
		}
		requireDistinctPositions(seeds, "seeds");
		for (Island island : islands) {
			requireBiomeId(island.biome(), island);
			require(biomes.add(island.biome()), "duplicate biome " + island.biome() + " in " + island);
			require(island.radius() > 0, "island " + island.biome() + " radius must be > 0");
			double channel = channel(coastline, island);
			require(channel > 0, "island " + island.biome() + " overlaps the mainland (channel " + channel + " blocks)");
		}
		for (Coast c : Coast.values()) {
			CoastStyle style = coasts.get(c);
			require(style != null, "no coast style for " + c);
			requireBiomeId(style.bandBiome(), c);
			require(style.bandWidth() >= 0, "coast " + c + " bandWidth must be >= 0");
		}
		require(river.size() >= 2, "river needs at least 2 points, got " + river.size());
		validateRiverMouth(coastline, river);
		validateVillage(seeds, river, riverWidth, warpAmplitude, borderRoughness, villageAnchor);
	}

	/**
	 * The river starts on land and crosses the shore exactly once. Ending inland would leave a channel
	 * that stops short of the sea, and re-entering the land would cut a stray channel across a headland.
	 * Walked along the unwarped centre line, one block at a time.
	 */
	private static void validateRiverMouth(Coastline coastline, List<Point> river) {
		Point source = river.get(0);
		require(coastline.distance(source.x(), source.z()) > 0, "river source " + source + " is not on the mainland");
		int crossings = 0;
		boolean onLand = true;
		for (int i = 0; i + 1 < river.size(); i++) {
			Point a = river.get(i);
			Point b = river.get(i + 1);
			int steps = Math.max(1, (int) Math.ceil(Math.hypot(b.x() - a.x(), b.z() - a.z())));
			for (int k = 1; k <= steps; k++) {
				double t = (double) k / steps;
				boolean land = coastline.distance(a.x() + t * (b.x() - a.x()), a.z() + t * (b.z() - a.z())) > 0;
				if (land != onLand) {
					crossings++;
					onLand = land;
				}
			}
		}
		require(crossings == 1, "river crosses the coastline " + crossings + " times; it must leave the land exactly once"
				+ " and end at sea (mouth " + river.get(river.size() - 1) + ")");
	}

	/**
	 * Open water between an island's rim and the mainland shore: minus the largest shore distance on the
	 * rim, sampled every degree. Positive means the island stands clear.
	 */
	private static double channel(Coastline coastline, Island island) {
		double closest = Double.NEGATIVE_INFINITY;
		for (int deg = 0; deg < 360; deg++) {
			double a = Math.toRadians(deg);
			closest = Math.max(closest, coastline.distance(island.x() + island.radius() * Math.cos(a),
					island.z() + island.radius() * Math.sin(a)));
		}
		return -closest;
	}

	/**
	 * Checks the unwarped layout with the margin widened by how far the warps can move the point each distance
	 * is read at. The broad warp moves x and z by up to {@code warpAmplitude} each, so up to √2 times that; the
	 * river is read there. The region lookup moves at most {@code borderRoughness} further; borders are read there.
	 * Both distances are 1-Lipschitz in their point, so the anchor keeps {@link #VILLAGE_MARGIN} on every seed.
	 */
	private static void validateVillage(List<BiomeSeed> seeds, List<Point> river, double riverWidth,
			double warpAmplitude, double borderRoughness, Point anchor) {
		require(anchor != null, "villageAnchor is missing");
		int nearestIndex = nearestSeed(seeds, anchor.x(), anchor.z());
		BiomeSeed nearest = seeds.get(nearestIndex);
		int neighbour = borderNeighbour(seeds, nearestIndex, anchor.x(), anchor.z());
		require(nearest.biome().equals(VILLAGE_BIOME),
				"village anchor " + anchor + " is not in " + VILLAGE_BIOME + " (nearest seed is " + nearest.biome() + ")");

		double riverNeeded = VILLAGE_MARGIN + SQRT_2 * warpAmplitude;
		double riverClearance = polylineDistance(river, anchor.x(), anchor.z()) - riverWidth / 2;
		require(riverClearance >= riverNeeded,
				"village anchor " + anchor + " is " + riverClearance + " blocks from the river, needs " + riverNeeded);
		if (neighbour >= 0) {
			BiomeSeed next = seeds.get(neighbour);
			double borderNeeded = riverNeeded + borderRoughness;
			double border = bisectorDistance(nearest, next, anchor.x(), anchor.z());
			require(border >= borderNeeded, "village anchor " + anchor + " is " + border + " blocks from the "
					+ next.biome() + " border, needs " + borderNeeded);
		}
	}

	private static void validateCaves(Caves caves) {
		require(caves != null, "caves are missing");
		require(!caves.seeds().isEmpty(), "cave seed list is empty");
		requireBiomeId(caves.deepBiome(), "caves.deepBiome");
		for (BiomeSeed s : caves.seeds()) {
			requireBiomeId(s.biome(), s);
		}
		requireDistinctPositions(caves.seeds(), "cave seeds");
	}

	/** Two seeds at one point make their bisector undefined (division by zero). */
	private static void requireDistinctPositions(List<BiomeSeed> seeds, String what) {
		for (int i = 0; i < seeds.size(); i++) {
			for (int j = i + 1; j < seeds.size(); j++) {
				BiomeSeed a = seeds.get(i);
				BiomeSeed b = seeds.get(j);
				require(a.x() != b.x() || a.z() != b.z(),
						what + ": " + a.biome() + " and " + b.biome() + " share the same position (" + a.x() + ", " + a.z() + ")");
			}
		}
	}

	private static void requireBiomeId(String id, Object owner) {
		require(id != null && !id.isBlank(), "blank biome id in " + owner);
	}

	private static void require(boolean ok, String message) {
		if (!ok) {
			throw invalid(message);
		}
	}

	private static IllegalArgumentException invalid(String message) {
		LOGGER.error("[{}] invalid continent layout: {}", Continent.MOD_ID, message);
		return new IllegalArgumentException("Invalid continent layout: " + message);
	}
}
