package com.wulfic.continent.gametest;

import com.wulfic.continent.Continent;
import com.wulfic.continent.layout.ContinentLayout;
import com.wulfic.continent.layout.DefaultLayout;
import com.wulfic.continent.layout.VanillaBiomes;
import com.wulfic.continent.world.AnchoredPlacement;
import com.wulfic.continent.world.Worldgen;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Stage 7.2: does a Continent world hold everything the plan promises? Biomes are measured on the real
 * biome source with real climate depth. Structures are found by world generation's own placement and
 * {@code Structure.generate}, so no world is created. Required items fail the test; the rest is only logged.
 */
public class ContinentCensusGameTest {
	static final int STRUCTURE_RADIUS = 3000;
	private static final int MAP_HALF = 1600;
	private static final int STEP = 16;
	/** Above all terrain: the surface biome. */
	private static final int SURFACE_Y = 320;
	/** Under land, deep enough for the cave biome; above the Deep Dark's top. */
	private static final int CAVE_Y = 16;
	/** Inside the Deep Dark's depth band (ancient cities start at y −27). */
	private static final int DEEP_Y = -30;
	private static final double REGION_FLOOR = 40_000;
	private static final double SUNFLOWER_FLOOR = 10_000;
	private static final double FILLER_FLOOR = 5_000;

	/** Each of these is the only source of some block (TODO's block checklist). */
	private static final Map<String, ResourceKey<Structure>> REQUIRED = Map.of(
			"ocean monument", BuiltinStructures.OCEAN_MONUMENT,
			"warm ocean ruin", BuiltinStructures.OCEAN_RUIN_WARM,
			"buried treasure", BuiltinStructures.BURIED_TREASURE,
			"trial chambers", BuiltinStructures.TRIAL_CHAMBERS,
			"stronghold", BuiltinStructures.STRONGHOLD);
	/** None of these gates a block; the log shows what each seed happens to have. */
	private static final Map<String, ResourceKey<Structure>> REPORTED = new LinkedHashMap<>();

	static {
		REPORTED.put("desert pyramid", BuiltinStructures.DESERT_PYRAMID);
		REPORTED.put("jungle temple", BuiltinStructures.JUNGLE_TEMPLE);
		REPORTED.put("swamp hut", BuiltinStructures.SWAMP_HUT);
		REPORTED.put("igloo", BuiltinStructures.IGLOO);
		REPORTED.put("pillager outpost", BuiltinStructures.PILLAGER_OUTPOST);
		REPORTED.put("ancient city", BuiltinStructures.ANCIENT_CITY);
		REPORTED.put("trail ruins", BuiltinStructures.TRAIL_RUINS);
		REPORTED.put("woodland mansion", BuiltinStructures.WOODLAND_MANSION);
		REPORTED.put("natural plains village", BuiltinStructures.VILLAGE_PLAINS);
		REPORTED.put("natural desert village", BuiltinStructures.VILLAGE_DESERT);
		REPORTED.put("natural savanna village", BuiltinStructures.VILLAGE_SAVANNA);
		REPORTED.put("natural snowy village", BuiltinStructures.VILLAGE_SNOWY);
		REPORTED.put("natural taiga village", BuiltinStructures.VILLAGE_TAIGA);
	}

	private static final Logger LOGGER = LoggerFactory.getLogger(Continent.MOD_ID);
	private static final ResourceKey<WorldPreset> PRESET = ResourceKey.create(Registries.WORLD_PRESET,
			Identifier.fromNamespaceAndPath(Continent.MOD_ID, "continent"));
	private static final ResourceKey<NoiseGeneratorSettings> SETTINGS = ResourceKey.create(Registries.NOISE_SETTINGS,
			Identifier.fromNamespaceAndPath(Continent.MOD_ID, "continent"));

	@GameTest(maxTicks = 20)
	public void censusSeed0(GameTestHelper helper) {
		census(helper, 0L);
	}

	@GameTest(maxTicks = 20)
	public void censusSeed12345(GameTestHelper helper) {
		census(helper, 12345L);
	}

	@GameTest(maxTicks = 20)
	public void censusSeedNegative(GameTestHelper helper) {
		census(helper, -4172144997902289642L);
	}

	private static void census(GameTestHelper helper, long seed) {
		RegistryAccess access = helper.getLevel().registryAccess();
		NoiseBasedChunkGenerator generator = (NoiseBasedChunkGenerator) access.lookupOrThrow(Registries.WORLD_PRESET)
				.getOrThrow(PRESET).value().overworld().orElseThrow().generator();
		RandomState random = Worldgen.randomState(access, SETTINGS, seed);
		List<String> missing = new ArrayList<>();
		StringBuilder report = new StringBuilder("census for seed ").append(seed).append(':');

		Map<String, Double> areas = biomeAreas(Worldgen.biomes(generator.getBiomeSource(), random));
		ContinentLayout layout = DefaultLayout.create();
		// The band's newer biomes are required even if the layout lost them, so dropping a seed can't hide one.
		Set<String> required = new LinkedHashSet<>(layout.biomeIds());
		required.addAll(VanillaBiomes.inThisBand());
		for (String biome : required) {
			double area = areas.getOrDefault(biome, 0.0);
			double floor = floor(biome);
			report.append("\n  biome ").append(biome).append(": ").append((long) area).append(" blocks²");
			if (area < floor) {
				missing.add(biome + " covers " + (long) area + " blocks², under its " + (long) floor + " floor");
			}
		}

		//? if >=26.3 {
		/*StructureCensus structures = new StructureCensus(access, generator, random,
				helper.getLevel().getServer().getStructureTemplateManager(), seed);
		*///?} else {
		StructureCensus structures = new StructureCensus(access, generator, random,
				helper.getLevel().getServer().getStructureManager(), seed);
		//?}
		for (Map.Entry<String, ResourceKey<Structure>> entry : new TreeMap<>(REQUIRED).entrySet()) {
			Optional<StructureCensus.Found> found = structures.nearest(entry.getValue(), STRUCTURE_RADIUS, p -> true);
			report.append("\n  required ").append(entry.getKey()).append(": ").append(describe(found));
			if (found.isEmpty()) {
				missing.add(entry.getKey() + " within " + STRUCTURE_RADIUS + " blocks");
			}
		}
		boolean village = structures.generatesAt(BuiltinStructures.VILLAGE_PLAINS, AnchoredPlacement.ANCHOR_CHUNK);
		report.append("\n  required guaranteed village at ").append(AnchoredPlacement.ANCHOR_CHUNK).append(": ")
				.append(village ? "valid" : "MISSING");
		if (!village) {
			missing.add("guaranteed village at " + AnchoredPlacement.ANCHOR_CHUNK);
		}
		for (Map.Entry<String, ResourceKey<Structure>> entry : REPORTED.entrySet()) {
			// Natural only: the guaranteed village is checked above.
			Optional<StructureCensus.Found> found = structures.nearest(entry.getValue(), STRUCTURE_RADIUS,
					p -> p instanceof RandomSpreadStructurePlacement);
			report.append("\n  reported ").append(entry.getKey()).append(": ").append(describe(found));
		}
		reportAbandonedCamps(access, structures, layout, report);

		LOGGER.info("[{}] {}", Continent.MOD_ID, report);
		helper.assertTrue(missing.isEmpty(), "seed " + seed + " is missing: " + String.join("; ", missing));
		helper.succeed();
	}

	/**
	 * 26.3+: an Abandoned Camp per biome, for the biomes on our map that have one. Camps gate no block (straw beds
	 * are crafted, cushions are entities), so they're only reported. Read from the registry, so older bands, which
	 * have none, report that instead of failing.
	 */
	private static void reportAbandonedCamps(RegistryAccess access, StructureCensus structures, ContinentLayout layout,
			StringBuilder report) {
		HolderGetter<Structure> registry = access.lookupOrThrow(Registries.STRUCTURE);
		int camps = 0;
		for (String biome : layout.biomeIds()) {
			ResourceKey<Structure> camp = ResourceKey.create(Registries.STRUCTURE,
					Identifier.withDefaultNamespace("abandoned_camp_" + Identifier.parse(biome).getPath()));
			if (registry.get(camp).isEmpty()) {
				continue;
			}
			camps++;
			Optional<StructureCensus.Found> found = structures.nearest(camp, STRUCTURE_RADIUS, p -> true);
			report.append("\n  reported abandoned camp in ").append(biome).append(": ").append(describe(found));
		}
		if (camps == 0) {
			report.append("\n  reported abandoned camps: none in this band");
		}
	}

	/**
	 * Blocks² per biome: surface over the whole map, then cave and deep layers wherever they differ from
	 * the layer above, so an island column (one biome all the way down) isn't counted three times.
	 */
	private static Map<String, Double> biomeAreas(Worldgen.Biomes biomes) {
		Map<String, Double> areas = new TreeMap<>();
		double cell = (double) STEP * STEP;
		for (int x = -MAP_HALF; x <= MAP_HALF; x += STEP) {
			for (int z = -MAP_HALF; z <= MAP_HALF; z += STEP) {
				String surface = biomeAt(biomes, x, SURFACE_Y, z);
				String cave = biomeAt(biomes, x, CAVE_Y, z);
				String deep = biomeAt(biomes, x, DEEP_Y, z);
				areas.merge(surface, cell, Double::sum);
				if (!cave.equals(surface)) {
					areas.merge(cave, cell, Double::sum);
				}
				if (!deep.equals(cave) && !deep.equals(surface)) {
					areas.merge(deep, cell, Double::sum);
				}
			}
		}
		return areas;
	}

	private static String biomeAt(Worldgen.Biomes biomes, int x, int y, int z) {
		return biomes.at(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z))
				.unwrapKey().orElseThrow().identifier().toString();
	}

	private static double floor(String biome) {
		if (biome.equals(VanillaBiomes.SUNFLOWER_PLAINS)) {
			return SUNFLOWER_FLOOR;
		}
		if (biome.equals(VanillaBiomes.BEACH) || biome.equals(VanillaBiomes.RIVER)) {
			return FILLER_FLOOR;
		}
		return REGION_FLOOR;
	}

	private static String describe(Optional<StructureCensus.Found> found) {
		return found.map(f -> "chunk " + f.chunk() + ", " + Math.round(f.distance()) + " blocks from the origin")
				.orElse("none within " + STRUCTURE_RADIUS);
	}
}
