package com.wulfic.continent.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wulfic.continent.layout.DefaultLayout;
import com.wulfic.continent.layout.Point;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
//? if >=26.3 {
/*import net.minecraft.world.level.levelgen.densityfunction.DensityFunctions;
*///?} else {
import net.minecraft.world.level.levelgen.DensityFunctions;
//?}
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** The guard that keeps the guaranteed village out of every world but a Continent one. */
class AnchoredPlacementTest {
	/**
	 * Bootstraps the game first. Static fields initialise in order, and loading {@link AnchoredPlacement}
	 * touches the built-in registries, which fail for the whole JVM if touched before the bootstrap.
	 */
	private static final RegistryOps<JsonElement> OPS = TestWorlds.OPS;
	/** The placement part of {@code guaranteed_village.json}. The gametest covers the registry dispatch. */
	private static final StructurePlacement PLACEMENT = AnchoredPlacement.CODEC.codec()
			.parse(OPS, JsonParser.parseString("{\"salt\": 1450294012}")).getOrThrow();

	/** Channels dressed as the datapack dresses them: a registry holder around a (flat) cache. */
	private static RandomState continent(long seed) {
		//? if >=26.3 {
		/*return TestWorlds.continent(seed, f -> new DensityFunctions.HolderHolder(Holder.direct(DensityFunctions.cache(f))));
		*///?} else {
		return TestWorlds.continent(seed, f -> new DensityFunctions.HolderHolder(Holder.direct(DensityFunctions.flatCache(f))));
		//?}
	}

	private static ChunkGeneratorStructureState state(RandomState random, long seed) {
		ContinentBiomeSource biomes = ContinentBiomeSource.codecFor(DefaultLayout.create()).codec()
				.parse(OPS, new JsonObject()).getOrThrow();
		//? if >=26.3 {
		/*return ChunkGeneratorStructureState.createForFlat(random, seed, ChunkPos.ZERO, biomes, Stream.empty());
		*///?} else {
		return ChunkGeneratorStructureState.createForFlat(random, seed, biomes, Stream.empty());
		//?}
	}

	@Test
	void anchorChunkHoldsTheLayoutsVillageAnchor() {
		Point anchor = DefaultLayout.create().villageAnchor();
		ChunkPos chunk = AnchoredPlacement.ANCHOR_CHUNK;
		assertTrue(anchor.x() >= chunk.getMinBlockX() && anchor.x() <= chunk.getMaxBlockX(), chunk + " vs " + anchor);
		assertTrue(anchor.z() >= chunk.getMinBlockZ() && anchor.z() <= chunk.getMaxBlockZ(), chunk + " vs " + anchor);
		//? if >=26.3 {
		/*assertEquals(AnchoredPlacement.TYPE, PLACEMENT.codec());
		*///?} else {
		assertEquals(AnchoredPlacement.TYPE, PLACEMENT.type());
		//?}
	}

	@ParameterizedTest
	@ValueSource(longs = {0L, 12345L})
	void firesAtTheAnchorChunkOnlyInAContinentWorld(long seed) {
		ChunkGeneratorStructureState state = state(continent(seed), seed);
		ChunkPos anchor = AnchoredPlacement.ANCHOR_CHUNK;
		assertTrue(PLACEMENT.isStructureChunk(state, anchor.x(), anchor.z()), "no village at the anchor chunk");
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				if (dx != 0 || dz != 0) {
					assertFalse(PLACEMENT.isStructureChunk(state, anchor.x() + dx, anchor.z() + dz),
							"village at neighbour (" + dx + ", " + dz + ")");
				}
			}
		}
		assertFalse(PLACEMENT.isStructureChunk(state, 0, 0), "village at the origin chunk");
	}

	/** The guard on normal worlds: same chunk, vanilla terrain, nothing placed. */
	@ParameterizedTest
	@ValueSource(longs = {0L, 12345L})
	void neverFiresInAVanillaWorld(long seed) {
		ChunkGeneratorStructureState state = state(TestWorlds.vanilla(seed), seed);
		ChunkPos anchor = AnchoredPlacement.ANCHOR_CHUNK;
		assertFalse(PLACEMENT.isStructureChunk(state, anchor.x(), anchor.z()), "village placed in a vanilla world");
		assertFalse(LayoutDensityFunction.drivesTerrainOf(TestWorlds.vanilla(seed)));
		assertTrue(LayoutDensityFunction.drivesTerrainOf(continent(seed)));
	}
}
