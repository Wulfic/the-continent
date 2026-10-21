package com.wulfic.continent;

import static com.wulfic.continent.Continent.LOGGER;
import static com.wulfic.continent.Continent.MOD_ID;

import com.wulfic.continent.command.ContinentCommand;
import com.wulfic.continent.world.AnchoredPlacement;
import com.wulfic.continent.world.ContinentBiomeSource;
import com.wulfic.continent.world.LayoutDensityFunction;
import net.minecraft.core.registries.Registries;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;

/**
 * Forge's entrypoint: registers the worldgen types and the command through Forge. The deferred registers fill
 * the registries during their registry event, which comes before any datapack loads, so the world preset always
 * finds its biome source.
 */
@Mod(MOD_ID)
public final class ContinentForge {
	public ContinentForge(FMLJavaModLoadingContext context) {
		var modBus = context.getModBusGroup();
		var biomeSources = DeferredRegister.create(Registries.BIOME_SOURCE, MOD_ID);
		biomeSources.register(ContinentBiomeSource.ID.getPath(), () -> ContinentBiomeSource.CODEC);
		biomeSources.register(modBus);
		var densityFunctionTypes = DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, MOD_ID);
		densityFunctionTypes.register(LayoutDensityFunction.ID.getPath(), () -> LayoutDensityFunction.MAP_CODEC);
		densityFunctionTypes.register(modBus);
		var placements = DeferredRegister.create(Registries.STRUCTURE_PLACEMENT, MOD_ID);
		placements.register(AnchoredPlacement.ID.getPath(), () -> AnchoredPlacement.TYPE);
		placements.register(modBus);
		LOGGER.info("[{}] registering biome source {}, density function type {}, structure placement {} "
				+ "(village anchor chunk {})", MOD_ID, ContinentBiomeSource.ID, LayoutDensityFunction.ID, AnchoredPlacement.ID,
				AnchoredPlacement.ANCHOR_CHUNK);
		RegisterCommandsEvent.BUS.addListener(event -> ContinentCommand.register(event.getDispatcher()));
		LOGGER.info("[{}] initialized", MOD_ID);
	}
}
