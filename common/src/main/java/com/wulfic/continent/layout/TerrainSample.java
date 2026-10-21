package com.wulfic.continent.layout;

/**
 * What {@link ContinentTerrain} wants vanilla's terrain splines to see at one position.
 *
 * @param continentalness target C
 * @param erosion target E
 * @param ridges target W
 * @param erosionDetail largest move local noise may make to E here, before {@code detail}
 * @param ridgesDetail largest move local noise may make to W here, before {@code detail}
 * @param uplift added to the spline's {@code offset}; 0 at sea and in the river valley
 * @param upliftDetail largest move the peaks noise may make to the uplift here, before {@code detail}; it fades
 *        out with the uplift
 * @param detail how much of that local noise to apply, in [0, 1]: 0 in the river channel, 1 elsewhere
 */
public record TerrainSample(double continentalness, double erosion, double ridges, double erosionDetail,
		double ridgesDetail, double uplift, double upliftDetail, double detail) {
}
