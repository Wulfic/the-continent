package com.wulfic.continent.layout;

import static com.wulfic.continent.layout.VanillaBiomes.*;

import com.wulfic.continent.Continent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The built-in map. Coordinates are the plan's initial seeds (TODO.md), tuned in Stage 7. Biomes newer than 26.1
 * ({@link VanillaBiomes#NEWER}) join it only in the bands whose game has them; everything else is the same map in
 * every band.
 */
public final class DefaultLayout {
	private static final Logger LOGGER = LoggerFactory.getLogger(Continent.MOD_ID);

	private DefaultLayout() {
	}

	/** Terrain for this band's map. */
	public static ContinentTerrain terrain() {
		return terrain(VanillaBiomes.inThisBand());
	}

	/** Terrain for the map with {@code newer} of the newer biomes. */
	public static ContinentTerrain terrain(Set<String> newer) {
		ContinentLayout layout = create(newer);
		Set<String> land = new HashSet<>();
		layout.seeds().forEach(s -> land.add(s.biome()));
		layout.islands().forEach(i -> land.add(i.biome()));
		Map<String, TerrainShape> profiles = PROFILES.entrySet().stream().filter(e -> land.contains(e.getKey()))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		return new ContinentTerrain(layout, profiles);
	}

	/**
	 * Terrain for the built-in map. Each shape was picked from a table of the 1.21.11 splines (unchanged in 26.1.2).
	 * Detail says how far the region's interior rolls (E and W either way). Uplift raises it past the splines'
	 * ceiling: each 0.1 is about 13 blocks. Profile E plus its E detail stays below vanilla's shattered band (0.45),
	 * and so does every blend of profiles. The comments give the surface y measured in a real world (seed 12345).
	 * Every land biome of every band is here; {@link #terrain(Set)} keeps the ones its map has.
	 */
	private static final Map<String, TerrainShape> PROFILES = Map.ofEntries(
				// Lowlands, gently rolling: E 0.2 and W at most 0.5 is flat land.
				Map.entry(PLAINS, new TerrainShape(0.05, 0.2, 0.4).withDetail(0.2, 0.2)),
				Map.entry(SUNFLOWER_PLAINS, new TerrainShape(0.05, 0.2, 0.4).withDetail(0.2, 0.2)),
				Map.entry(DESERT, new TerrainShape(0.05, 0.2, 0.45).withDetail(0.2, 0.25)),
				Map.entry(MUSHROOM_FIELDS, new TerrainShape(0.05, 0.2, 0.4)),
				// Wetlands: low W sits around sea level, so detail leaves pools.
				Map.entry(SWAMP, new TerrainShape(0.05, 0.2, 0.15)),
				Map.entry(MANGROVE_SWAMP, new TerrainShape(0.0, 0.2, 0.12)),
				// Hill country: forests, jungles and savanna roll in real hills and hollows.
				Map.entry(SAVANNA, new TerrainShape(0.15, -0.2, 0.3).withDetail(0.35, 0.35)),
				Map.entry(FOREST, new TerrainShape(0.05, -0.45, 0.3).withDetail(0.35, 0.45)),
				Map.entry(BIRCH_FOREST, new TerrainShape(0.05, -0.45, 0.3).withDetail(0.3, 0.35)),
				Map.entry(DARK_FOREST, new TerrainShape(0.05, -0.45, 0.35).withDetail(0.3, 0.35)),
				Map.entry(BAMBOO_JUNGLE, new TerrainShape(0.05, -0.45, 0.35).withDetail(0.3, 0.35)),
				Map.entry(JUNGLE, new TerrainShape(0.15, -0.45, 0.3).withDetail(0.3, 0.35)),
				Map.entry(PALE_GARDEN, new TerrainShape(0.15, -0.45, 0.35).withDetail(0.3, 0.35)),
				// Foothills of the northern range.
				Map.entry(TAIGA, new TerrainShape(0.15, -0.45, 0.3).withDetail(0.3, 0.35).withUplift(0.05)),
				Map.entry(OLD_GROWTH_PINE_TAIGA, new TerrainShape(0.15, -0.45, 0.35).withDetail(0.3, 0.35).withUplift(0.05)),
				// Badlands straddles y 74, where vanilla's surface rule switches from red sand (below) to
				// terracotta bands (above): red-sand flats with terracotta mesas, y 65-85.
				Map.entry(BADLANDS, new TerrainShape(0.05, 0.0, 0.39)),
				// Highland plateaus (E -0.3 flattens W 0.2-0.45), rolling.
				Map.entry(MEADOW, new TerrainShape(0.3, -0.3, 0.3).withDetail(0.25, 0.3).withUplift(0.05)),
				Map.entry(CHERRY_GROVE, new TerrainShape(0.3, -0.3, 0.35).withDetail(0.25, 0.3).withUplift(0.05)),
				// The northern range, lifted past the splines' ceiling and broken into separate summits.
				Map.entry(GROVE, new TerrainShape(0.5, -0.6, 0.35).withDetail(0.25, 0.35).withUplift(0.2, 0.08)),
				Map.entry(SNOWY_SLOPES, new TerrainShape(0.5, -0.75, 0.45).withDetail(0.2, 0.4).withUplift(0.25, 0.1)),
				Map.entry(JAGGED_PEAKS, new TerrainShape(0.8, -0.9, 0.667).withDetail(0.2, 0.65).withUplift(0.3, 0.15)),
				// 26.3+: a rolling forest in the foothills, shaped like the Taiga beside it.
				Map.entry(DAPPLED_FOREST, new TerrainShape(0.15, -0.45, 0.3).withDetail(0.3, 0.35).withUplift(0.05)));

	/** The map for this band. */
	public static ContinentLayout create() {
		return create(VanillaBiomes.inThisBand());
	}

	/**
	 * The map with {@code newer} of the newer biomes. New seeds are appended, so the 26.1 map stays bit for bit
	 * what it was (a test holds it to a fingerprint).
	 *
	 * @param newer a subset of {@link VanillaBiomes#NEWER}
	 * @throws IllegalArgumentException naming any id that isn't a newer biome, so a typo can't silently drop one
	 */
	public static ContinentLayout create(Set<String> newer) {
		Set<String> unknown = new HashSet<>(newer);
		unknown.removeAll(VanillaBiomes.NEWER);
		if (!unknown.isEmpty()) {
			String message = unknown + " are not newer biomes " + VanillaBiomes.NEWER;
			LOGGER.error("[{}] invalid continent layout: {}", Continent.MOD_ID, message);
			throw new IllegalArgumentException("Invalid continent layout: " + message);
		}
		List<BiomeSeed> seeds = new ArrayList<>(List.of(
				// Center
				new BiomeSeed(PLAINS, 0, 0),
				new BiomeSeed(SUNFLOWER_PLAINS, 220, 120),
				new BiomeSeed(FOREST, -220, -150),
				// North
				new BiomeSeed(TAIGA, -250, -450),
				new BiomeSeed(OLD_GROWTH_PINE_TAIGA, 200, -450),
				new BiomeSeed(GROVE, -250, -700),
				new BiomeSeed(SNOWY_SLOPES, 220, -700),
				new BiomeSeed(JAGGED_PEAKS, 0, -880),
				// East
				new BiomeSeed(BIRCH_FOREST, 400, -50),
				new BiomeSeed(MEADOW, 550, -350),
				new BiomeSeed(CHERRY_GROVE, 750, -100),
				// South
				new BiomeSeed(SAVANNA, 200, 400),
				new BiomeSeed(DESERT, 50, 700),
				new BiomeSeed(BADLANDS, 400, 700),
				// West
				new BiomeSeed(DARK_FOREST, -400, -150),
				new BiomeSeed(PALE_GARDEN, -650, -300),
				new BiomeSeed(SWAMP, -450, 150),
				new BiomeSeed(MANGROVE_SWAMP, -750, 200),
				// Southwest
				new BiomeSeed(JUNGLE, -350, 450),
				new BiomeSeed(BAMBOO_JUNGLE, -500, 700)));
		if (newer.contains(DAPPLED_FOREST)) {
			// North-east, between the Old Growth Pine Taiga and the Meadow, under the Snowy Slopes: vanilla puts
			// it next to cold biomes.
			seeds.add(new BiomeSeed(DAPPLED_FOREST, 420, -520));
		}
		// Lush under the west, southwest and centre; Dripstone under the east and south.
		List<BiomeSeed> caves = new ArrayList<>(List.of(new BiomeSeed(LUSH_CAVES, -300, 50),
				new BiomeSeed(DRIPSTONE_CAVES, 400, 400)));
		if (newer.contains(SULFUR_CAVES)) {
			// Under the north-east hills (Meadow, Cherry Grove), as vanilla puts it inside hills. Under the north
			// it stays above y 0, where the Deep Dark takes over.
			caves.add(new BiomeSeed(SULFUR_CAVES, 650, -200));
		}

		return new ContinentLayout(
				seeds,
				coastline(),
				List.of(new Island(MUSHROOM_FIELDS, -1250, -50, 150)),
				24,
				Map.of(
						Coast.NORTH, new CoastStyle(false, FROZEN_OCEAN, 400),
						Coast.EAST, new CoastStyle(true, WARM_OCEAN, 600),
						Coast.SOUTH, new CoastStyle(true, WARM_OCEAN, 600),
						Coast.WEST, new CoastStyle(true, OCEAN, 0)),
				200,
				// Middle points sit 60/30 blocks east of the plan's (-30,-100)/(80,150): those put the
				// centre line 13 blocks from the origin, so spawn could land in the river.
				List.of(new Point(-100, -450), new Point(30, -100), new Point(110, 150), new Point(400, 300),
						new Point(1000, 350)),
				20,
				32,
				// Ragged borders: up to 24 blocks (~18 on average) at 96-12 block scales, with stray patches.
				24,
				// The point in Plains farthest from every border (135 blocks), clear of the river by 145. The
				// village needs 64 + 32·√2 + 24 = 133.3 from a border to keep 64 on every seed.
				new Point(-68, 104),
				// Deep Dark tops out at y 0 so it covers the ancient city start height (absolute y -27).
				new Caves(caves, DEEP_DARK, -350, 0));
	}

	/**
	 * The shore, every 15° clockwise from north, around a nominal radius of 1000: headlands under the mountain
	 * and outer regions, bays between them. Each region keeps ≥ 64k blocks² on every tested seed (floor 40k).
	 */
	static Coastline coastline() {
		return new Coastline(List.of(
				1080.0, // 0° N: Jagged Peaks headland
				1000.0, // 15°
				900.0, // 30° NE bay, between Snowy Slopes and Meadow
				960.0, // 45°
				1060.0, // 60° Meadow headland
				1000.0, // 75°
				930.0, // 90° E: Cherry Grove shore
				1010.0, // 105°: the river mouth (bearing 109°) stays offshore
				880.0, // 120° SE bay
				960.0, // 135°
				1120.0, // 150° Badlands peninsula
				1040.0, // 165°
				900.0, // 180° S: bay off the Desert
				960.0, // 195°
				1100.0, // 210° Bamboo Jungle headland
				1050.0, // 225°
				920.0, // 240° SW bay
				1000.0, // 255° Mangrove shore
				980.0, // 270° W: faces the Mushroom island across a ≥ 120-block channel
				1000.0, // 285°
				1060.0, // 300° Pale Garden headland
				940.0, // 315° NW bay
				1000.0, // 330°
				960.0)); // 345°
	}
}
