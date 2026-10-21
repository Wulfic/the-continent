package com.wulfic.continent.layout;

/** A mainland region: every land point belongs to the biome of its nearest seed. */
public record BiomeSeed(String biome, double x, double z) {
}
