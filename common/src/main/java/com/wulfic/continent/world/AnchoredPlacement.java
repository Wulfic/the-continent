package com.wulfic.continent.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.wulfic.continent.Continent;
import com.wulfic.continent.layout.DefaultLayout;
import com.wulfic.continent.layout.Point;
import java.util.Optional;
import net.minecraft.core.SectionPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
//? if >=26.3 {
/*import net.minecraft.world.level.levelgen.structure.placement.AbstractSpreadingStructurePlacement;
*///?} else {
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
//?}
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structure placement {@code the_continent:anchored}: exactly one chunk, the one holding the layout's
 * village anchor, and only in a world whose terrain is the continent layout. In every other world it
 * never fires, so the structure set that uses it is inert in normal worlds.
 *
 * <p>{@code ChunkGeneratorStructureState} keeps its biome source private, so the guard reads the world's
 * terrain instead (see {@link LayoutDensityFunction#drivesTerrainOf}), which is exactly as specific: only the
 * Continent noise settings route continentalness through the layout.
 */
//? if >=26.3 {
/*public final class AnchoredPlacement extends AbstractSpreadingStructurePlacement {
*///?} else {
public final class AnchoredPlacement extends StructurePlacement {
//?}
	public static final Identifier ID = Identifier.fromNamespaceAndPath(Continent.MOD_ID, "anchored");
	public static final MapCodec<AnchoredPlacement> CODEC = RecordCodecBuilder.mapCodec(
			instance -> placementCodec(instance).apply(instance, AnchoredPlacement::new));
	/** What the structure placement registry holds: the codec itself from 26.3, a type wrapping it before. */
	//? if >=26.3 {
	/*public static final MapCodec<AnchoredPlacement> TYPE = CODEC;
	*///?} else {
	public static final StructurePlacementType<AnchoredPlacement> TYPE = () -> CODEC;
	//?}
	/** The chunk holding the village anchor. A jigsaw village grows from that chunk's corner. */
	public static final ChunkPos ANCHOR_CHUNK = chunkOf(DefaultLayout.create().villageAnchor());

	private static final Logger LOGGER = LoggerFactory.getLogger(Continent.MOD_ID);

	private AnchoredPlacement(Vec3i locateOffset, FrequencyReductionMethod frequencyReductionMethod, float frequency,
			int salt, Optional<ExclusionZone> exclusionZone) {
		super(locateOffset, frequencyReductionMethod, frequency, salt, exclusionZone);
	}

	private static ChunkPos chunkOf(Point p) {
		return new ChunkPos(SectionPos.blockToSectionCoord(Mth.floor(p.x())), SectionPos.blockToSectionCoord(Mth.floor(p.z())));
	}

	@Override
	protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ) {
		// Position first: it's the cheap test, and it rules out every chunk but one.
		if (chunkX != ANCHOR_CHUNK.x() || chunkZ != ANCHOR_CHUNK.z()) {
			return false;
		}
		boolean continent = LayoutDensityFunction.drivesTerrainOf(state.randomState());
		LOGGER.debug("[{}] anchor chunk {} asked for a placement: {}", Continent.MOD_ID, ANCHOR_CHUNK,
				continent ? "continent world, placing" : "not a continent world, skipping");
		return continent;
	}

	//? if >=26.3 {
	/*@Override
	public MapCodec<AnchoredPlacement> codec() {
		return CODEC;
	}
	*///?} else {
	@Override
	public StructurePlacementType<?> type() {
		return TYPE;
	}
	//?}
}
