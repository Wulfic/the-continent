package com.wulfic.continent;

import static com.wulfic.continent.Continent.LOGGER;
import static com.wulfic.continent.Continent.MOD_ID;

import com.wulfic.continent.command.ContinentCommand;
import com.wulfic.continent.world.AnchoredPlacement;
import com.wulfic.continent.world.ContinentBiomeSource;
import com.wulfic.continent.world.LayoutDensityFunction;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

/** Fabric's entrypoint: registers the worldgen types and the command through Fabric. */
public final class ContinentMod implements ModInitializer {
	@Override
	public void onInitialize() {
		// Must run before datapacks load, or the world preset fails to parse and the world type
		// silently disappears from the list.
		Registry.register(BuiltInRegistries.BIOME_SOURCE, ContinentBiomeSource.ID, ContinentBiomeSource.CODEC);
		LOGGER.info("[{}] registered biome source {}", MOD_ID, ContinentBiomeSource.ID);
		Registry.register(BuiltInRegistries.DENSITY_FUNCTION_TYPE, LayoutDensityFunction.ID, LayoutDensityFunction.MAP_CODEC);
		LOGGER.info("[{}] registered density function type {}", MOD_ID, LayoutDensityFunction.ID);
		Registry.register(BuiltInRegistries.STRUCTURE_PLACEMENT, AnchoredPlacement.ID, AnchoredPlacement.TYPE);
		LOGGER.info("[{}] registered structure placement {} (village anchor chunk {})", MOD_ID, AnchoredPlacement.ID,
				AnchoredPlacement.ANCHOR_CHUNK);
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> ContinentCommand.register(dispatcher));
		LOGGER.info("[{}] initialized", MOD_ID);
	}
}
