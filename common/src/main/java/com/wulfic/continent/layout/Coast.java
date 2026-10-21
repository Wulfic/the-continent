package com.wulfic.continent.layout;

/** Compass quadrant of a point relative to the origin. Diagonals go to NORTH/SOUTH. */
public enum Coast {
	NORTH, EAST, SOUTH, WEST;

	public static Coast of(double x, double z) {
		double ax = Math.abs(x);
		if (-z >= ax) {
			return NORTH;
		}
		if (z >= ax) {
			return SOUTH;
		}
		return x > 0 ? EAST : WEST;
	}
}
