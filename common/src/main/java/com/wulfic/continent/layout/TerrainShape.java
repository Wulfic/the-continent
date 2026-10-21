package com.wulfic.continent.layout;

/**
 * A point in the input space of vanilla's overworld terrain splines ({@code offset}, {@code factor},
 * {@code jaggedness}). Picking one per region picks vanilla's terrain for it: high continentalness and
 * low erosion with ridges near ±2/3 is a peak, low ridges a valley, high erosion flat land. The detail
 * amplitudes say how far local noise may push the region off that point, which gives its interior hills
 * and dips. The uplift raises the whole region past what the splines alone reach, and its own detail lifts
 * separate summits and drops the saddles between them.
 *
 * @param continentalness C, in [−1, 1]: below −0.19 is sea floor, above about −0.11 is inland
 * @param erosion E, in [−1, 1]: low is mountainous, high is flat
 * @param ridges W, in [−1, 1]: 0 is a valley floor, ±2/3 a peak
 * @param erosionDetail how far local noise may move E either way, in [0, 1]. E plus this must stay below
 *        vanilla's shattered band
 * @param ridgesDetail how far local noise may move W either way, in [0, 1]
 * @param uplift added to the spline's {@code offset}; each 0.1 lifts the surface about 13 blocks
 * @param upliftDetail how far the peaks noise may move the uplift either way. Uplift ± this stays in
 *        [0, {@link ContinentTerrain#MAX_UPLIFT}]
 */
public record TerrainShape(double continentalness, double erosion, double ridges, double erosionDetail,
		double ridgesDetail, double uplift, double upliftDetail) {
	/** Detail for a region that sets none: enough to break up a surface without changing its character. */
	public static final double DEFAULT_EROSION_DETAIL = 0.1;
	public static final double DEFAULT_RIDGES_DETAIL = 0.1;

	public TerrainShape(double continentalness, double erosion, double ridges) {
		this(continentalness, erosion, ridges, DEFAULT_EROSION_DETAIL, DEFAULT_RIDGES_DETAIL, 0, 0);
	}

	public TerrainShape withDetail(double erosionDetail, double ridgesDetail) {
		return new TerrainShape(continentalness, erosion, ridges, erosionDetail, ridgesDetail, uplift, upliftDetail);
	}

	public TerrainShape withUplift(double uplift) {
		return withUplift(uplift, 0);
	}

	public TerrainShape withUplift(double uplift, double upliftDetail) {
		return new TerrainShape(continentalness, erosion, ridges, erosionDetail, ridgesDetail, uplift, upliftDetail);
	}
}
