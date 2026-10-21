package com.wulfic.continent.gametest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import com.wulfic.continent.Continent;
import com.wulfic.continent.layout.ContinentLayout;
import com.wulfic.continent.layout.DefaultLayout;
import com.wulfic.continent.layout.Point;
import com.wulfic.continent.layout.VanillaBiomes;
import com.wulfic.continent.map.MapFiles;
import com.wulfic.continent.world.AnchoredPlacement;
import com.wulfic.continent.world.ContinentBiomeSource;
import com.wulfic.continent.world.LayoutSeed;
import com.wulfic.continent.world.Worldgen;
import com.mojang.brigadier.tree.CommandNode;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.permissions.LevelBasedPermissionSet;
import net.minecraft.tags.WorldPresetTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.storage.LevelResource;

/** Runs on a live dedicated server with the mod loaded, so it sees real initialisation order. */
public class ContinentGameTest {
	private static final ResourceKey<WorldPreset> CONTINENT_PRESET = ResourceKey.create(Registries.WORLD_PRESET,
			Identifier.fromNamespaceAndPath(Continent.MOD_ID, "continent"));
	private static final ResourceKey<NoiseGeneratorSettings> CONTINENT_SETTINGS = ResourceKey.create(
			Registries.NOISE_SETTINGS, Identifier.fromNamespaceAndPath(Continent.MOD_ID, "continent"));
	private static final ResourceKey<StructureSet> GUARANTEED_VILLAGE = ResourceKey.create(Registries.STRUCTURE_SET,
			Identifier.fromNamespaceAndPath(Continent.MOD_ID, "guaranteed_village"));
	private static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(-64, 384);
	private static final int SEA_LEVEL = 63;
	/** How long the /continent map test waits, in real time, for the off-thread render to write its file. */
	private static final Duration MAP_RENDER_BUDGET = Duration.ofSeconds(60);
	/** Vanilla's badlands surface rule: red sand below this y, terracotta bands above. */
	private static final int RED_SAND_LINE = 74;

	@GameTest
	public void biomeSourceIsRegisteredAndDecodesFromTheServerRegistries(GameTestHelper helper) {
		helper.assertValueEqual(BuiltInRegistries.BIOME_SOURCE.getValue(ContinentBiomeSource.ID),
				ContinentBiomeSource.CODEC, "codec registered under " + ContinentBiomeSource.ID);

		// The same dispatch path the world preset JSON goes through in Stage 4.
		RegistryOps<JsonElement> ops = helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE);
		JsonObject json = new JsonObject();
		json.addProperty("type", ContinentBiomeSource.ID.toString());
		BiomeSource source = BiomeSource.CODEC.parse(ops, json).getOrThrow();
		helper.assertTrue(source instanceof ContinentBiomeSource, "decoded " + source);

		Holder<Biome> origin = Worldgen.biomes(source, helper.getLevel().getChunkSource().randomState())
				.at(0, QuartPos.fromBlock(300), 0);
		helper.assertTrue(origin.is(Biomes.PLAINS), "origin is " + origin);
		helper.succeed();
	}

	@GameTest
	public void worldPresetLoadsAndIsListedWithTheVanillaOnes(GameTestHelper helper) {
		HolderLookup.RegistryLookup<WorldPreset> presets =
				helper.getLevel().registryAccess().lookupOrThrow(Registries.WORLD_PRESET);
		Holder.Reference<WorldPreset> preset = presets.get(CONTINENT_PRESET).orElse(null);
		helper.assertTrue(preset != null, CONTINENT_PRESET + " is in the world preset registry");
		helper.assertTrue(preset.is(WorldPresetTags.NORMAL), "preset is in the minecraft:normal tag");
		// The tag appends: if it replaced, the vanilla presets would vanish from the World Type button.
		helper.assertTrue(presets.getOrThrow(WorldPresets.NORMAL).is(WorldPresetTags.NORMAL),
				"vanilla minecraft:normal preset is still in the tag");

		LevelStem overworld = preset.value().overworld().orElse(null);
		helper.assertTrue(overworld != null, "preset has an overworld");
		helper.assertTrue(overworld.generator() instanceof NoiseBasedChunkGenerator,
				"overworld generator is " + overworld.generator());
		helper.assertTrue(overworld.generator().getBiomeSource() instanceof ContinentBiomeSource,
				"overworld biome source is " + overworld.generator().getBiomeSource());
		helper.assertTrue(((NoiseBasedChunkGenerator) overworld.generator()).generatorSettings().is(CONTINENT_SETTINGS),
				"overworld uses the " + CONTINENT_SETTINGS.identifier() + " noise settings");
		helper.succeed();
	}

	/** Real generation through our noise settings: the datapack graph, wired with seeded noise. */
	@GameTest
	public void terrainFollowsTheMap(GameTestHelper helper) {
		NoiseBasedChunkGenerator generator = continentGenerator(helper);
		for (long seed : new long[] {0L, 12345L}) {
			RandomState random = Worldgen.randomState(helper.getLevel().registryAccess(), CONTINENT_SETTINGS, seed);
			int plains = surface(generator, random, 0, 0);
			int peaks = surface(generator, random, 0, -880);
			helper.assertTrue(plains > SEA_LEVEL && plains < SEA_LEVEL + 20, "seed " + seed + ": plains at y " + plains);
			helper.assertTrue(peaks > plains + 60, "seed " + seed + ": jagged peaks y " + peaks + " vs plains y " + plains);
			// The raised top slide and the uplift take the summits past vanilla's ceiling (~256), under the build limit.
			int summit = 0;
			for (int dx = -160; dx <= 160; dx += 16) {
				for (int dz = -120; dz <= 120; dz += 16) {
					summit = Math.max(summit, surface(generator, random, dx, -880 + dz));
				}
			}
			helper.assertTrue(summit > 270 && summit < 315, "seed " + seed + ": highest Jagged Peaks summit at y " + summit);
			// Every compass point past the coast is sea. West is checked past the mushroom island.
			for (int[] p : new int[][] {{0, -1300}, {1300, 0}, {0, 1300}, {-1500, 0}, {950, 950}, {-950, -950}}) {
				int y = surface(generator, random, p[0], p[1]);
				helper.assertTrue(y < SEA_LEVEL, "seed " + seed + ": (" + p[0] + ", " + p[1] + ") is land at y " + y);
			}
			int island = surface(generator, random, -1250, -50);
			helper.assertTrue(island > SEA_LEVEL, "seed " + seed + ": mushroom island centre at y " + island);
			assertRiverIsAChannel(helper, generator, random, seed);
		}
		helper.succeed();
	}

	/**
	 * At the midpoint of every river segment, the warped centre line is below sea level and below the land
	 * on both banks. The centre is found where the biome source's own layout sample puts it.
	 */
	private static void assertRiverIsAChannel(GameTestHelper helper, NoiseBasedChunkGenerator generator,
			RandomState random, long seed) {
		ContinentLayout layout = DefaultLayout.create();
		long layoutSeed = Worldgen.layoutSeed(random);
		List<Point> river = layout.river();
		for (int i = 0; i + 1 < river.size(); i++) {
			Point a = river.get(i);
			Point b = river.get(i + 1);
			double len = Math.hypot(b.x() - a.x(), b.z() - a.z());
			double nx = -(b.z() - a.z()) / len;
			double nz = (b.x() - a.x()) / len;
			double mx = (a.x() + b.x()) / 2;
			double mz = (a.z() + b.z()) / 2;
			int cx = 0;
			int cz = 0;
			double best = Double.MAX_VALUE;
			for (int t = -60; t <= 60; t++) {
				int x = (int) Math.round(mx + t * nx);
				int z = (int) Math.round(mz + t * nz);
				double d = layout.sample(x, z, layoutSeed).riverDistance();
				if (d < best) {
					best = d;
					cx = x;
					cz = z;
				}
			}
			int bed = surface(generator, random, cx, cz);
			int left = surface(generator, random, (int) Math.round(cx + 80 * nx), (int) Math.round(cz + 80 * nz));
			int right = surface(generator, random, (int) Math.round(cx - 80 * nx), (int) Math.round(cz - 80 * nz));
			String where = "seed " + seed + ", river segment " + i + " at (" + cx + ", " + cz + ")";
			helper.assertTrue(bed < SEA_LEVEL, where + ": bed at y " + bed);
			helper.assertTrue(left > bed && right > bed, where + ": bed y " + bed + ", banks y " + left + " / " + right);
		}
	}

	/**
	 * Red sand comes only from badlands, and vanilla's surface rule puts it only below about y 74; above
	 * that it lays terracotta bands. So the Badlands must straddle y 74, with a real share on each side.
	 */
	@GameTest
	public void badlandsHasRedSandFlatsAndTerracottaHills(GameTestHelper helper) {
		NoiseBasedChunkGenerator generator = continentGenerator(helper);
		ContinentLayout layout = DefaultLayout.create();
		for (long seed : new long[] {0L, 12345L}) {
			RandomState random = Worldgen.randomState(helper.getLevel().registryAccess(), CONTINENT_SETTINGS, seed);
			long layoutSeed = Worldgen.layoutSeed(random);
			int low = 0;
			int high = 0;
			int total = 0;
			for (int x = 100; x <= 800; x += 24) {
				for (int z = 400; z <= 1000; z += 24) {
					if (!layout.sample(x, z, layoutSeed).biome().equals(VanillaBiomes.BADLANDS)) {
						continue;
					}
					int y = surface(generator, random, x, z);
					total++;
					low += y < RED_SAND_LINE - 2 && y >= SEA_LEVEL ? 1 : 0;
					high += y > RED_SAND_LINE + 2 ? 1 : 0;
				}
			}
			String counts = "seed " + seed + ": " + low + " red-sand and " + high + " terracotta columns of " + total;
			Continent.LOGGER.info("[{}] badlands {}", Continent.MOD_ID, counts);
			helper.assertTrue(total > 100, counts + " (too few samples)");
			helper.assertTrue(low >= total / 4, counts + ": too little ground below y " + RED_SAND_LINE);
			helper.assertTrue(high >= total / 10, counts + ": too little ground above y " + RED_SAND_LINE);
		}
		helper.succeed();
	}

	/** Spawn search runs in climate space, not on biomes, so it must land in Plains through terrain alone. */
	@GameTest
	public void spawnSearchLandsInPlainsNearTheOrigin(GameTestHelper helper) {
		NoiseBasedChunkGenerator generator = continentGenerator(helper);
		for (long seed : new long[] {0L, 1L, 12345L, -4172144997902289642L}) {
			RandomState random = Worldgen.randomState(helper.getLevel().registryAccess(), CONTINENT_SETTINGS, seed);
			//? if >=26.3 {
			/*ChunkPos spawnChunk = generator.getOrigin(random);
			BlockPos spawn = new BlockPos(spawnChunk.getMiddleBlockX(), 0, spawnChunk.getMiddleBlockZ());
			*///?} else {
			BlockPos spawn = random.sampler().findSpawnPosition();
			//?}
			helper.assertTrue(Math.hypot(spawn.getX(), spawn.getZ()) <= 128, "seed " + seed + ": spawn search at " + spawn);
			int y = surface(generator, random, spawn.getX(), spawn.getZ());
			Holder<Biome> biome = Worldgen.biomes(generator.getBiomeSource(), random).at(QuartPos.fromBlock(spawn.getX()),
					QuartPos.fromBlock(y + 1), QuartPos.fromBlock(spawn.getZ()));
			helper.assertTrue(biome.is(Biomes.PLAINS), "seed " + seed + ": spawn at " + spawn + " is in " + biome);
			helper.assertTrue(y >= SEA_LEVEL, "seed " + seed + ": spawn column is under water at y " + y);
		}
		helper.succeed();
	}

	/**
	 * The guaranteed village, end to end without a world: the structure set dispatches to our placement,
	 * the placement fires at the anchor chunk in a Continent world and nowhere else, and the village
	 * generated there is a valid jigsaw start, centred near the anchor, with a bell in its layout.
	 */
	@GameTest
	public void guaranteedVillageStartsAtTheAnchor(GameTestHelper helper) {
		RegistryAccess access = helper.getLevel().registryAccess();
		StructureSet set = access.lookupOrThrow(Registries.STRUCTURE_SET).getOrThrow(GUARANTEED_VILLAGE).value();
		helper.assertTrue(set.placement() instanceof AnchoredPlacement, "placement is " + set.placement());
		helper.assertValueEqual(set.structures().size(), 1, "structures in the set");
		Holder<Structure> village = set.structures().get(0).structure();
		helper.assertTrue(village.is(BuiltinStructures.VILLAGE_PLAINS), "structure is " + village);

		NoiseBasedChunkGenerator generator = continentGenerator(helper);
		BiomeSource biomes = generator.getBiomeSource();
		ChunkPos anchor = AnchoredPlacement.ANCHOR_CHUNK;
		Point target = DefaultLayout.create().villageAnchor();
		for (long seed : new long[] {0L, 12345L}) {
			RandomState random = Worldgen.randomState(access, CONTINENT_SETTINGS, seed);
			//? if >=26.3 {
			/*ChunkGeneratorStructureState state = ChunkGeneratorStructureState.createForNormal(random, seed,
					generator.getOrigin(random), biomes, access.lookupOrThrow(Registries.STRUCTURE_SET));
			*///?} else {
			ChunkGeneratorStructureState state = ChunkGeneratorStructureState.createForNormal(random, seed, biomes,
					access.lookupOrThrow(Registries.STRUCTURE_SET));
			//?}
			helper.assertTrue(state.possibleStructureSets().stream().anyMatch(h -> h.is(GUARANTEED_VILLAGE)),
					"seed " + seed + ": the guaranteed village set was filtered out of this world");
			helper.assertTrue(set.placement().isStructureChunk(state, anchor.x(), anchor.z()), "seed " + seed + ": not at the anchor");
			helper.assertFalse(set.placement().isStructureChunk(state, anchor.x() + 1, anchor.z()), "seed " + seed + ": beside it");

			RandomState vanilla = Worldgen.randomState(access, NoiseGeneratorSettings.OVERWORLD, seed);
			//? if >=26.3 {
			/*ChunkGeneratorStructureState vanillaState = ChunkGeneratorStructureState.createForNormal(vanilla, seed,
					ChunkPos.ZERO, biomes, access.lookupOrThrow(Registries.STRUCTURE_SET));
			*///?} else {
			ChunkGeneratorStructureState vanillaState = ChunkGeneratorStructureState.createForNormal(vanilla, seed,
					biomes, access.lookupOrThrow(Registries.STRUCTURE_SET));
			//?}
			helper.assertFalse(set.placement().isStructureChunk(vanillaState, anchor.x(), anchor.z()),
					"seed " + seed + ": fired in a vanilla-terrain world");

			//? if >=26.3 {
			/*StructureStart start = village.value().generate(village, Level.OVERWORLD, access, generator, biomes,
					Worldgen.climate(random), random, helper.getLevel().getServer().getStructureTemplateManager(), seed,
					anchor, 0, HEIGHT, village.value().biomes()::contains);
			*///?} else {
			StructureStart start = village.value().generate(village, Level.OVERWORLD, access, generator, biomes, random,
					helper.getLevel().getServer().getStructureManager(), seed, anchor, 0, HEIGHT, village.value().biomes()::contains);

			//?}
			helper.assertTrue(start.isValid(), "seed " + seed + ": no valid village start at " + anchor);
			BlockPos centre = start.getBoundingBox().getCenter();
			double offset = Math.hypot(centre.getX() - target.x(), centre.getZ() - target.z());
			helper.assertTrue(offset < 96, "seed " + seed + ": village centre " + centre + " is " + offset + " from the anchor");
			helper.assertTrue(hasBell(start, helper), "seed " + seed + ": no bell in the village's pieces");
		}
		helper.succeed();
	}

	private static boolean hasBell(StructureStart start, GameTestHelper helper) {
		//? if >=26.3 {
		/*StructureTemplateManager templates = helper.getLevel().getServer().getStructureTemplateManager();
		*///?} else {
		StructureTemplateManager templates = helper.getLevel().getServer().getStructureManager();
		//?}
		for (StructurePiece piece : start.getPieces()) {
			if (piece instanceof PoolElementStructurePiece pool && pool.getElement() instanceof SinglePoolElement single
					&& !templates.getOrCreate(single.getTemplateLocation())
							.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), Blocks.BELL).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * The real command, end to end: admins only, it renders off-thread, and it adds exactly one new,
	 * decodable PNG of the requested size to the world's map folder.
	 *
	 * <p>The render waits on the worker pool behind the other tests' chunk generation, which takes wall-clock
	 * time, while the test server runs ticks far faster than 20 a second (CI ran 400 in about a second). So the
	 * deadline is {@link #MAP_RENDER_BUDGET} of real time. The tick limit is only a backstop the clock reaches
	 * first.
	 */
	@GameTest(maxTicks = 1_000_000)
	public void mapCommandWritesOneNewPngForAdminsOnly(GameTestHelper helper) {
		long deadline = System.nanoTime() + MAP_RENDER_BUDGET.toNanos();
		MinecraftServer server = helper.getLevel().getServer();
		CommandSourceStack admin = server.createCommandSourceStack().withPermission(LevelBasedPermissionSet.ADMIN);
		CommandNode<CommandSourceStack> node = server.getCommands().getDispatcher().getRoot().getChild("continent");
		helper.assertTrue(node != null && node.canUse(admin), "admins can run /continent");
		helper.assertFalse(node.canUse(admin.withPermission(LevelBasedPermissionSet.GAMEMASTER)),
				"gamemasters (and so command blocks) can run /continent");

		Path folder = server.getWorldPath(LevelResource.ROOT).resolve(MapFiles.FOLDER);
		Set<Path> before = pngs(folder);
		server.getCommands().performPrefixedCommand(admin, "continent map 64 8");
		helper.succeedWhen(() -> {
			Set<Path> added = new HashSet<>(pngs(folder));
			added.removeAll(before);
			if (added.isEmpty() && System.nanoTime() > deadline) {
				// Not an assertion: succeedWhen retries those until maxTicks, while any other exception fails now.
				String message = "no new map file in " + folder + " after " + MAP_RENDER_BUDGET.toSeconds() + " s";
				Continent.LOGGER.error("[{}] {}", Continent.MOD_ID, message);
				throw new IllegalStateException(message);
			}
			helper.assertValueEqual(added.size(), 1, "new map files in " + folder);
			BufferedImage image;
			try {
				image = ImageIO.read(added.iterator().next().toFile());
			} catch (IOException e) {
				throw helper.assertionException("map is not readable yet: " + e.getMessage());
			}
			helper.assertTrue(image != null && image.getWidth() == 16 && image.getHeight() == 16,
					"map is not a 16x16 PNG: " + image);
		});
	}

	private static Set<Path> pngs(Path folder) {
		if (!Files.isDirectory(folder)) {
			return Set.of();
		}
		try (Stream<Path> files = Files.list(folder)) {
			return files.filter(f -> f.toString().endsWith(".png")).collect(Collectors.toSet());
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static NoiseBasedChunkGenerator continentGenerator(GameTestHelper helper) {
		return (NoiseBasedChunkGenerator) helper.getLevel().registryAccess().lookupOrThrow(Registries.WORLD_PRESET)
				.getOrThrow(CONTINENT_PRESET).value().overworld().orElseThrow().generator();
	}

	/** Top solid block, ignoring water: below {@link #SEA_LEVEL} means the column is sea floor. */
	private static int surface(NoiseBasedChunkGenerator generator, RandomState random, int x, int z) {
		return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, HEIGHT, random) - 1;
	}
}
