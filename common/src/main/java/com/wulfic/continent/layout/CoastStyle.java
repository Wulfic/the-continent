package com.wulfic.continent.layout;

/**
 * How one stretch of coast meets the sea.
 *
 * @param beach whether a beach band runs along the land side of the coast
 * @param bandBiome the ocean biome just offshore, for {@code bandWidth} blocks
 * @param bandWidth 0 means the plain ocean starts right at the coast
 */
public record CoastStyle(boolean beach, String bandBiome, double bandWidth) {
}
