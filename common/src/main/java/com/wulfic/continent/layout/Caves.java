package com.wulfic.continent.layout;

import java.util.List;

/**
 * Underground biomes. Cave regions are a nearest-seed Voronoi of {@code seeds}, sampled at the
 * same warped position as the surface. At or below block y {@code deepTopY}, columns north of
 * {@code deepEdgeZ} (z less than it) switch to {@code deepBiome}.
 *
 * @param seeds cave regions; at least one. Biomes may repeat, positions may not
 * @param deepBiome the deep biome under the north
 * @param deepEdgeZ the deep biome's southern edge
 * @param deepTopY highest block y of the deep biome
 */
public record Caves(List<BiomeSeed> seeds, String deepBiome, double deepEdgeZ, int deepTopY) {
	public Caves {
		seeds = List.copyOf(seeds);
	}
}
