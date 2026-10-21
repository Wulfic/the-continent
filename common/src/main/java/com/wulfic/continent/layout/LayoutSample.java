package com.wulfic.continent.layout;

/**
 * What the layout says about one horizontal position.
 *
 * @param biome the surface biome id to place here
 * @param zone which rule picked {@code biome}
 * @param primary the nearest mainland seed's biome, even offshore or on a river
 * @param borderDistance distance to the nearest edge of the primary region, ≥ 0
 * @param coastDistance signed distance to the nearest coast (mainland or island): positive on land
 * @param riverDistance distance to the river's centre line
 * @param caveBiome the cave biome under this column
 * @param deepCaveBiome the biome at or below {@link Caves#deepTopY()}: the deep biome in the
 *        north, otherwise {@code caveBiome}. On an island both are the island's biome
 */
public record LayoutSample(
		String biome,
		Zone zone,
		String primary,
		double borderDistance,
		double coastDistance,
		double riverDistance,
		String caveBiome,
		String deepCaveBiome) {

	public enum Zone {
		LAND, RIVER, BEACH, ISLAND, COASTAL_BAND, OCEAN, DEEP_OCEAN
	}
}
