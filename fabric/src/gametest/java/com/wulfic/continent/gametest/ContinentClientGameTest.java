package com.wulfic.continent.gametest;

import com.mojang.datafixers.util.Pair;
import com.wulfic.continent.Continent;
import com.wulfic.continent.layout.BiomeSeed;
import com.wulfic.continent.layout.ContinentLayout;
import com.wulfic.continent.layout.DefaultLayout;
import com.wulfic.continent.layout.VanillaBiomes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EndPortalFrameBlock;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.structures.StrongholdPieces;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Plays a real singleplayer world on The Continent, the way the owner does, on a headless client
 * ({@code xvfb-run ./gradlew runClientGameTest}). Checks what only a client session shows: the day cycle as
 * the client sees it, and that the Nether and the End are reachable through their real portals.
 */
public class ContinentClientGameTest implements FabricClientGameTest {
	private static final ResourceKey<WorldPreset> CONTINENT_PRESET = ResourceKey.create(Registries.WORLD_PRESET,
			Identifier.fromNamespaceAndPath(Continent.MOD_ID, "continent"));
	/** Where the Nether portal frame is built: high in open air above spawn, so no terrain is in the way. */
	private static final BlockPos PORTAL_BASE = new BlockPos(0, 250, 0);
	private static final int PORTAL_TIMEOUT_TICKS = 600;
	/**
	 * CI's software renderer draws ~14 frames a second, and a client gametest runs one server tick per client frame,
	 * so the server there is always behind its 20 ticks a second. Every client tick is held to at least this long,
	 * so any machine runs the test the way the runner does.
	 */
	private static final long CLIENT_TICK_FLOOR_NANOS = 60_000_000L;
	/** The blocks each newer biome is the only natural source of. Every biome in {@link VanillaBiomes#NEWER} needs a list. */
	private static final Map<String, List<String>> GATED_BLOCKS = Map.of(
			VanillaBiomes.SULFUR_CAVES, List.of("minecraft:cinnabar", "minecraft:sulfur", "minecraft:potent_sulfur",
					"minecraft:sulfur_spike"),
			VanillaBiomes.DAPPLED_FOREST, List.of("minecraft:poplar_log", "minecraft:red_poplar_leaves",
					"minecraft:orange_poplar_leaves", "minecraft:yellow_poplar_leaves", "minecraft:red_shrub",
					"minecraft:shelf_mushroom"));
	/** The block walk generates this many chunks on each side of a newer biome's seed: 5 × 5 chunks. */
	private static final int BLOCK_WALK_RADIUS = 2;

	@Override
	public void runTest(ClientGameTestContext context) {
		holdClientTicksToTheFloor();
		sprintTheIntegratedServer();
		try (TestSingleplayerContext world = context.worldBuilder()
				// Consistent settings would switch the daylight cycle off and use a flat world.
				.setUseConsistentSettings(false)
				.adjustSettings(ui -> {
					Holder<WorldPreset> preset = ui.getSettings().worldgenLoadContext().lookupOrThrow(Registries.WORLD_PRESET)
							.getOrThrow(CONTINENT_PRESET);
					ui.setWorldType(new WorldCreationUiState.WorldTypeEntry(preset));
					ui.setSeed("12345");
					ui.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
				}).create()) {
			//? if >=26.2 {
			/*world.getConnection().waitForChunksDownload();
			*///?} else {
			world.getClientLevel().waitForChunksDownload();
			//?}
			dayCycleRuns(context, world);
			netherPortalWorks(context, world);
			endPortalWorks(context, world);
			newerBiomesGrowTheirBlocks(world);
			// Closing the world halts a sprinting server, which deadlocks against the gametest's tick phaser on 26.1.
			world.getServer().runOnServer(server -> server.tickRateManager().stopSprinting());
		}
	}

	/** Time advances on its own, and the client's sun and the server's sky light follow it. */
	private static void dayCycleRuns(ClientGameTestContext context, TestSingleplayerContext world) {
		long startDay = context.computeOnClient(client -> client.level.getOverworldClockTime());
		float startSun = sunAngle(context);
		context.waitTicks(100);
		long endDay = context.computeOnClient(client -> client.level.getOverworldClockTime());
		float endSun = sunAngle(context);
		Continent.LOGGER.info("[{}] day cycle: day time {} -> {}, sun angle {} -> {}", Continent.MOD_ID, startDay,
				endDay, startSun, endSun);
		check(endDay - startDay >= 50, "day time only went from " + startDay + " to " + endDay + " in 100 ticks");
		check(startSun != endSun, "the sun stayed at " + startSun + "° over 100 ticks");

		world.getServer().runCommand("time set noon");
		context.waitTicks(5);
		float noonSun = sunAngle(context);
		float noonLight = serverSkyLight(world);
		world.getServer().runCommand("time set midnight");
		context.waitTicks(5);
		float midnightSun = sunAngle(context);
		float midnightLight = serverSkyLight(world);
		Continent.LOGGER.info("[{}] noon: sun {}°, sky light {}; midnight: sun {}°, sky light {}", Continent.MOD_ID,
				noonSun, noonLight, midnightSun, midnightLight);
		check(noonLight >= 14, "sky light at noon is " + noonLight + ", expected full daylight");
		check(midnightLight <= 5, "sky light at midnight is " + midnightLight + ", so it never gets dark");
		float sunTravel = Math.abs(((midnightSun - noonSun) % 360 + 540) % 360 - 180);
		check(sunTravel > 150, "the sun moved only " + sunTravel + "° between noon and midnight");
		world.getServer().runCommand("time set day");
	}

	/** An obsidian frame lit with fire becomes a portal, and stepping in leads to the Nether. */
	private static void netherPortalWorks(ClientGameTestContext context, TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerLevel overworld = server.overworld();
			// A 4 wide, 5 tall frame along X: the smallest a portal can be.
			for (int dx = 0; dx < 4; dx++) {
				for (int dy = 0; dy < 5; dy++) {
					boolean edge = dx == 0 || dx == 3 || dy == 0 || dy == 4;
					overworld.setBlockAndUpdate(PORTAL_BASE.offset(dx, dy, 0),
							edge ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.AIR.defaultBlockState());
				}
			}
			BlockPos inside = PORTAL_BASE.offset(1, 1, 0);
			// Fire placed inside a frame lights it, the same way flint and steel does.
			overworld.setBlockAndUpdate(inside, BaseFireBlock.getState(overworld, inside));
			check(overworld.getBlockState(inside).is(Blocks.NETHER_PORTAL), "fire in an obsidian frame did not light a portal");
		});
		world.getServer().runCommand("tp @a " + (PORTAL_BASE.getX() + 1.5) + " " + (PORTAL_BASE.getY() + 1) + " "
				+ (PORTAL_BASE.getZ() + 0.5));
		waitForDimension(context, Level.NETHER, "stepping into the lit portal");
	}

	/**
	 * The nearest stronghold has a portal room with all twelve frames. Filling them with eyes of ender opens the
	 * portal, and stepping in leads to the End. On this map strongholds sit under the sea, so this is the check that
	 * matters.
	 */
	private static void endPortalWorks(ClientGameTestContext context, TestSingleplayerContext world) {
		List<BlockPos> frames = world.getServer().computeOnServer(server -> {
			ServerLevel overworld = server.overworld();
			Holder<Structure> stronghold = overworld.registryAccess().lookupOrThrow(Registries.STRUCTURE)
					.getOrThrow(BuiltinStructures.STRONGHOLD);
			Pair<BlockPos, Holder<Structure>> found = overworld.getChunkSource().getGenerator()
					.findNearestMapStructure(overworld, HolderSet.direct(stronghold), BlockPos.ZERO, 100, false);
			check(found != null, "no stronghold within 100 chunks of the origin");
			ChunkPos startChunk = ChunkPos.containing(found.getFirst());
			StructureStart start = overworld.getChunk(startChunk.x(), startChunk.z(), ChunkStatus.STRUCTURE_STARTS)
					.getStartForStructure(stronghold.value());
			check(start != null && start.isValid(), "the stronghold at " + found.getFirst() + " has no valid start");
			BoundingBox room = null;
			for (StructurePiece piece : start.getPieces()) {
				if (piece instanceof StrongholdPieces.PortalRoom) {
					room = piece.getBoundingBox();
				}
			}
			check(room != null, "the stronghold at " + found.getFirst() + " has no portal room");

			// Generating every chunk the room touches places its blocks.
			for (int cx = room.minX() >> 4; cx <= room.maxX() >> 4; cx++) {
				for (int cz = room.minZ() >> 4; cz <= room.maxZ() >> 4; cz++) {
					overworld.getChunk(cx, cz);
				}
			}
			List<BlockPos> inRoom = new ArrayList<>();
			for (BlockPos pos : BlockPos.betweenClosed(room.minX(), room.minY(), room.minZ(), room.maxX(), room.maxY(), room.maxZ())) {
				if (overworld.getBlockState(pos).is(Blocks.END_PORTAL_FRAME)) {
					inRoom.add(pos.immutable());
				}
			}
			Continent.LOGGER.info("[{}] stronghold at {}, portal room {} with {} frames", Continent.MOD_ID,
					found.getFirst(), room, inRoom.size());
			check(inRoom.size() == 12, "the portal room at " + room + " has " + inRoom.size() + " end portal frames, not 12");
			return inRoom;
		});

		// Back from the Nether, standing by the frames: an eye of ender acts on the level its user is in.
		BlockPos first = frames.getFirst();
		world.getServer().runCommand("execute in minecraft:overworld run tp @a " + first.getX() + " " + (first.getY() + 1) + " "
				+ first.getZ());
		waitForDimension(context, Level.OVERWORLD, "teleporting back from the Nether");
		BlockPos portal = world.getServer().computeOnServer(server -> {
			ServerLevel overworld = server.overworld();
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			check(player.level() == overworld, "the player is in " + player.level().dimension().identifier() + ", not the overworld");
			// Use real eyes of ender on every empty frame, as a player would.
			player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.ENDER_EYE, 12));
			for (BlockPos frame : frames) {
				if (!overworld.getBlockState(frame).getValue(EndPortalFrameBlock.HAS_EYE)) {
					player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
							new BlockHitResult(Vec3.atCenterOf(frame), Direction.UP, frame, false)));
				}
			}
			// The portal fills the 3×3 the frames ring, on the frames' own layer.
			for (BlockPos pos : BlockPos.betweenClosed(frames.getFirst().offset(-4, 0, -4), frames.getFirst().offset(4, 0, 4))) {
				if (overworld.getBlockState(pos).is(Blocks.END_PORTAL)) {
					return pos.immutable();
				}
			}
			throw fail("twelve eyes of ender in the frames at " + frames + " did not open an end portal");
		});
		Continent.LOGGER.info("[{}] end portal opened at {}", Continent.MOD_ID, portal);
		world.getServer().runOnServer(server -> {
			// Coming back from the Nether a moment ago left a portal cooldown that walking over would outlast.
			ServerPlayer player = server.getPlayerList().getPlayers().getFirst();
			player.setPortalCooldown(0);
			player.teleportTo(portal.getX() + 0.5, portal.getY(), portal.getZ() + 0.5);
		});
		waitForDimension(context, Level.END, "stepping into the end portal");
	}

	/**
	 * The block walk: every newer biome on this band's map generates, in real chunks around its region's seed, each
	 * block that only it has (TODO's block checklist). The 26.1 band has none, so there this checks nothing.
	 */
	private static void newerBiomesGrowTheirBlocks(TestSingleplayerContext world) {
		world.getServer().runOnServer(server -> {
			ServerLevel overworld = server.overworld();
			ContinentLayout layout = DefaultLayout.create();
			for (String biome : VanillaBiomes.inThisBand()) {
				check(GATED_BLOCKS.containsKey(biome), biome + " has no block checklist in the block walk");
				Map<Block, Integer> found = new LinkedHashMap<>();
				for (String id : GATED_BLOCKS.get(biome)) {
					Block block = BuiltInRegistries.BLOCK.get(Identifier.parse(id))
							.orElseThrow(() -> fail("no block " + id + " in this band's registry")).value();
					found.put(block, 0);
				}
				BiomeSeed seed = Stream.concat(layout.seeds().stream(), layout.caves().seeds().stream())
						.filter(s -> s.biome().equals(biome)).findFirst()
						.orElseThrow(() -> fail(biome + " is in this band but has no seed on the map"));
				ChunkPos centre = ChunkPos.containing(BlockPos.containing(seed.x(), 0, seed.z()));
				for (int cx = centre.x() - BLOCK_WALK_RADIUS; cx <= centre.x() + BLOCK_WALK_RADIUS; cx++) {
					for (int cz = centre.z() - BLOCK_WALK_RADIUS; cz <= centre.z() + BLOCK_WALK_RADIUS; cz++) {
						count(overworld.getChunk(cx, cz), found);
					}
				}
				Continent.LOGGER.info("[{}] block walk in {} around chunk {}: {}", Continent.MOD_ID, biome, centre, found);
				List<Block> missing = found.entrySet().stream().filter(e -> e.getValue() == 0).map(Map.Entry::getKey)
						.toList();
				check(missing.isEmpty(), biome + " generated none of " + missing + " in the "
						+ (2 * BLOCK_WALK_RADIUS + 1) + "² chunks around " + centre);
			}
		});
	}

	/** Adds every block of {@code chunk} that is a key of {@code counts} to its count. */
	private static void count(LevelChunk chunk, Map<Block, Integer> counts) {
		for (LevelChunkSection section : chunk.getSections()) {
			if (section.hasOnlyAir()) {
				continue;
			}
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						counts.computeIfPresent(section.getBlockState(x, y, z).getBlock(), (b, n) -> n + 1);
					}
				}
			}
		}
	}

	private static void holdClientTicksToTheFloor() {
		long[] lastTickEnd = {System.nanoTime()};
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			long wait = CLIENT_TICK_FLOOR_NANOS - (System.nanoTime() - lastTickEnd[0]);
			if (wait > 0) {
				try {
					Thread.sleep(Duration.ofNanos(wait));
				} catch (InterruptedException interrupted) {
					Continent.LOGGER.warn("[{}] client tick floor interrupted; this tick runs short", Continent.MOD_ID);
					Thread.currentThread().interrupt();
				}
			}
			lastTickEnd[0] = System.nanoTime();
		});
	}

	/**
	 * A server behind real time only finishes chunks on its own thread while it sprints
	 * ({@code MinecraftServer.pollTaskInternal}). Paced by a slower client, it is always behind, so without the sprint
	 * spawn prep stalls at 16% and the world never loads. Game time still moves one step per tick.
	 */
	private static void sprintTheIntegratedServer() {
		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			server.tickRateManager().requestGameToSprint(Integer.MAX_VALUE);
			Continent.LOGGER.info("[{}] client gametest: the integrated server sprints, so it keeps finishing chunks",
					Continent.MOD_ID);
		});
	}

	/** Waits until the client is in {@code dimension}, failing with what was supposed to take it there. */
	private static void waitForDimension(ClientGameTestContext context, ResourceKey<Level> dimension, String after) {
		try {
			context.waitFor(client -> client.level != null && client.level.dimension() == dimension, PORTAL_TIMEOUT_TICKS);
		} catch (AssertionError timedOut) {
			String now = context.computeOnClient(client -> client.level == null ? "no level" : client.level.dimension().identifier().toString());
			Continent.LOGGER.error("[{}] client gametest failed: still in {} {} ticks after {}", Continent.MOD_ID, now,
					PORTAL_TIMEOUT_TICKS, after);
			throw new AssertionError("still in " + now + " " + PORTAL_TIMEOUT_TICKS + " ticks after " + after + ", expected "
					+ dimension.identifier(), timedOut);
		}
		Continent.LOGGER.info("[{}] {}: arrived in {}", Continent.MOD_ID, after, dimension.identifier());
	}

	private static float sunAngle(ClientGameTestContext context) {
		return context.computeOnClient(client -> client.level.environmentAttributes()
				.getValue(EnvironmentAttributes.SUN_ANGLE, client.player.blockPosition()));
	}

	private static float serverSkyLight(TestSingleplayerContext world) {
		return world.getServer().computeOnServer(server -> server.overworld().environmentAttributes()
				.getValue(EnvironmentAttributes.SKY_LIGHT_LEVEL, BlockPos.ZERO));
	}

	private static void check(boolean ok, String message) {
		if (!ok) {
			throw fail(message);
		}
	}

	private static AssertionError fail(String message) {
		Continent.LOGGER.error("[{}] client gametest failed: {}", Continent.MOD_ID, message);
		return new AssertionError(message);
	}
}
