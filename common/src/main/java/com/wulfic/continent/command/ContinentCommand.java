package com.wulfic.continent.command;

import static com.mojang.brigadier.arguments.IntegerArgumentType.getInteger;
import static com.mojang.brigadier.arguments.IntegerArgumentType.integer;

import com.mojang.brigadier.CommandDispatcher;
import com.wulfic.continent.Continent;
import com.wulfic.continent.layout.DefaultLayout;
import com.wulfic.continent.layout.Point;
import com.wulfic.continent.map.MapFiles;
import com.wulfic.continent.map.MapRenderer;
import com.wulfic.continent.world.LayoutDensityFunction;
import com.wulfic.continent.world.Worldgen;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Util;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * {@code /continent map [radius] [blocksPerPixel]}: writes a biome map of the current dimension to
 * {@code <world>/continent-maps/map-<timestamp>.png}. Admins only: it writes to the server's disk. It
 * renders off the server thread, and every file write goes through {@link MapFiles}, which never
 * overwrites.
 */
public final class ContinentCommand {
	public static final int DEFAULT_RADIUS = 1600;
	public static final int DEFAULT_BLOCKS_PER_PIXEL = 4;

	private static final Logger LOGGER = LoggerFactory.getLogger(Continent.MOD_ID);
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");
	/** The build limit, above any terrain, so climate depth is below the cave threshold and biomes are surface ones. */
	private static final int SKY_Y = 320;

	private ContinentCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("continent")
				.requires(Commands.hasPermission(Commands.LEVEL_ADMINS))
				.then(Commands.literal("map")
						.executes(c -> map(c.getSource(), DEFAULT_RADIUS, DEFAULT_BLOCKS_PER_PIXEL))
						.then(Commands.argument("radius", integer(MapRenderer.MIN_RADIUS, MapRenderer.MAX_RADIUS))
								.executes(c -> map(c.getSource(), getInteger(c, "radius"), DEFAULT_BLOCKS_PER_PIXEL))
								.then(Commands.argument("blocksPerPixel", integer(1, MapRenderer.MAX_BLOCKS_PER_PIXEL))
										.executes(c -> map(c.getSource(), getInteger(c, "radius"),
												getInteger(c, "blocksPerPixel")))))));
	}

	private static int map(CommandSourceStack source, int radius, int blocksPerPixel) {
		int side;
		try {
			side = MapRenderer.side(radius, blocksPerPixel);
		} catch (IllegalArgumentException e) {
			source.sendFailure(Component.literal(e.getMessage()));
			return 0;
		}
		ServerLevel level = source.getLevel();
		BiomeSource biomes = level.getChunkSource().getGenerator().getBiomeSource();
		RandomState random = level.getChunkSource().randomState();
		Worldgen.Biomes lookup = Worldgen.biomes(biomes, random);
		Point marker = LayoutDensityFunction.drivesTerrainOf(random) ? DefaultLayout.create().villageAnchor() : null;
		Path worldDir = source.getServer().getWorldPath(LevelResource.ROOT);
		String name = "map-" + LocalDateTime.now().format(STAMP) + ".png";

		LOGGER.info("[{}] rendering a {}x{} biome map of {} (radius {}, {} blocks per pixel) to {}/{}", Continent.MOD_ID,
				side, side, level.dimension().identifier(), radius, blocksPerPixel, MapFiles.FOLDER, name);
		source.sendSuccess(() -> Component.literal("Rendering a " + side + "x" + side + " biome map, radius " + radius
				+ ", " + blocksPerPixel + " blocks per pixel..."), true);
		CompletableFuture.supplyAsync(() -> {
			byte[] png = MapRenderer.png(MapRenderer.render(radius, blocksPerPixel,
					(x, z) -> id(lookup.at(QuartPos.fromBlock(x), QuartPos.fromBlock(SKY_Y), QuartPos.fromBlock(z))), marker));
			try {
				return MapFiles.writeNew(worldDir, name, png);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}, Util.backgroundExecutor()).whenCompleteAsync((written, error) -> {
			if (error != null) {
				Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
				LOGGER.error("[{}] map export to {}/{} failed", Continent.MOD_ID, MapFiles.FOLDER, name, cause);
				source.sendFailure(Component.literal("Map export failed: " + cause.getMessage()));
			} else {
				source.sendSuccess(() -> Component.literal("Wrote " + MapFiles.FOLDER + "/" + name), true);
			}
		}, source.getServer());
		return 1;
	}

	private static String id(Holder<Biome> biome) {
		return biome.unwrapKey().map(key -> key.identifier().toString()).orElse("unregistered");
	}
}
