package com.wulfic.continent.gametest;

import com.wulfic.continent.world.AnchoredPlacement;
import com.wulfic.continent.world.Worldgen;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Finds structures in a world without generating it. The steps are world generation's own: the
 * placement picks candidate chunks, then {@code Structure.generate} decides whether the structure
 * really fits there (biome, terrain, jigsaw).
 */
final class StructureCensus {
	record Found(ChunkPos chunk, double distance) {
	}

	private static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(-64, 384);
	/** Spacing-1 placements (buried treasure) have one candidate per chunk, so the cheap biome check goes first. */
	private static final int DENSE_SPACING = 4;
	private static final int SEA_LEVEL = 63;

	private final RegistryAccess access;
	private final NoiseBasedChunkGenerator generator;
	private final BiomeSource biomes;
	private final RandomState random;
	private final ChunkGeneratorStructureState state;
	private final Worldgen.Biomes biomeLookup;
	private final StructureTemplateManager templates;
	private final long seed;

	StructureCensus(RegistryAccess access, NoiseBasedChunkGenerator generator, RandomState random,
			StructureTemplateManager templates, long seed) {
		this.access = access;
		this.generator = generator;
		this.biomes = generator.getBiomeSource();
		this.random = random;
		this.templates = templates;
		this.seed = seed;
		//? if >=26.3 {
		/*this.state = ChunkGeneratorStructureState.createForNormal(random, seed, generator.getOrigin(random), biomes,
				access.lookupOrThrow(Registries.STRUCTURE_SET));
		*///?} else {
		this.state = ChunkGeneratorStructureState.createForNormal(random, seed, biomes, access.lookupOrThrow(Registries.STRUCTURE_SET));
		//?}
		this.biomeLookup = Worldgen.biomes(biomes, random);
	}

	/** The nearest place, within {@code radius} blocks of the origin, where any of these placements puts {@code key}. */
	Optional<Found> nearest(ResourceKey<Structure> key, int radius, Predicate<StructurePlacement> placements) {
		Holder<Structure> structure = access.lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
		Optional<Found> best = Optional.empty();
		for (StructurePlacement placement : state.getPlacementsForStructure(structure)) {
			if (!placements.test(placement)) {
				continue;
			}
			for (ChunkPos chunk : candidates(placement, radius)) {
				if (best.isPresent() && distance(chunk) >= best.get().distance()) {
					break;
				}
				if (placement.isStructureChunk(state, chunk.x(), chunk.z()) && fits(structure, placement, chunk)) {
					best = Optional.of(new Found(chunk, distance(chunk)));
					break;
				}
			}
		}
		return best;
	}

	boolean generatesAt(ResourceKey<Structure> key, ChunkPos chunk) {
		Holder<Structure> structure = access.lookupOrThrow(Registries.STRUCTURE).getOrThrow(key);
		return generate(structure, chunk);
	}

	/** Candidate chunks within the radius, nearest first. */
	private List<ChunkPos> candidates(StructurePlacement placement, int radius) {
		List<ChunkPos> out = new ArrayList<>();
		if (placement instanceof RandomSpreadStructurePlacement spread) {
			int spacing = spread.spacing();
			int regions = radius / (16 * spacing) + 1;
			for (int rx = -regions; rx <= regions; rx++) {
				for (int rz = -regions; rz <= regions; rz++) {
					ChunkPos chunk = spread.getPotentialStructureChunk(seed, rx * spacing, rz * spacing);
					if (distance(chunk) <= radius) {
						out.add(chunk);
					}
				}
			}
		} else if (placement instanceof ConcentricRingsStructurePlacement rings) {
			List<ChunkPos> positions = state.getRingPositionsFor(rings);
			if (positions != null) {
				positions.stream().filter(c -> distance(c) <= radius).forEach(out::add);
			}
		} else if (placement instanceof AnchoredPlacement) {
			out.add(AnchoredPlacement.ANCHOR_CHUNK);
		}
		out.sort(Comparator.comparingDouble(StructureCensus::distance));
		return out;
	}

	private boolean fits(Holder<Structure> structure, StructurePlacement placement, ChunkPos chunk) {
		if (placement instanceof RandomSpreadStructurePlacement spread && spread.spacing() <= DENSE_SPACING) {
			// Generating costs a heightmap probe (~4 ms). Skip chunks whose sea-level biome can't hold it.
			int x = chunk.getMiddleBlockX();
			int z = chunk.getMiddleBlockZ();
			if (!structure.value().biomes().contains(biomeLookup.at(QuartPos.fromBlock(x), QuartPos.fromBlock(SEA_LEVEL),
					QuartPos.fromBlock(z)))) {
				return false;
			}
		}
		return generate(structure, chunk);
	}

	private boolean generate(Holder<Structure> structure, ChunkPos chunk) {
		//? if >=26.3 {
		/*return structure.value().generate(structure, Level.OVERWORLD, access, generator, biomes, Worldgen.climate(random), random,
				templates, seed, chunk, 0, HEIGHT, structure.value().biomes()::contains).isValid();
		*///?} else {
		return structure.value().generate(structure, Level.OVERWORLD, access, generator, biomes, random, templates, seed, chunk, 0,
				HEIGHT, structure.value().biomes()::contains).isValid();
		//?}
	}

	static double distance(ChunkPos chunk) {
		return Math.hypot(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
	}
}
