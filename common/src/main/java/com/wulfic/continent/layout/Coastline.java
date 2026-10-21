package com.wulfic.continent.layout;

import com.wulfic.continent.Continent;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The mainland's shore: one radius per compass bearing, evenly spaced clockwise from north, joined by a
 * periodic Catmull-Rom spline into a smooth closed curve. It is hand-laid and the same on every seed; the
 * layout's domain warp wobbles it per seed on top. Immutable and thread-safe. {@link #distance} is O(1)
 * and allocation-free.
 *
 * @param radii the shore's distance from the origin at bearings 0, 360/n, 2·360/n, … degrees clockwise
 *        from north (−z). At least {@link #MIN_POINTS}, each finite and positive
 */
public record Coastline(List<Double> radii) {
	public static final int MIN_POINTS = 4;
	/** Steepest the shore may run against the radius, as |dr/dθ| / r. Steeper shores squeeze the bands. */
	public static final double MAX_SLOPE = 1.0;

	private static final Logger LOGGER = LoggerFactory.getLogger(Continent.MOD_ID);
	private static final double TAU = 2 * Math.PI;

	public Coastline {
		radii = List.copyOf(radii);
		if (radii.size() < MIN_POINTS) {
			throw invalid("it needs at least " + MIN_POINTS + " radii, got " + radii.size());
		}
		for (int i = 0; i < radii.size(); i++) {
			double r = radii.get(i);
			if (!(r > 0) || Double.isInfinite(r)) {
				throw invalid("radius " + i + " (bearing " + i * 360.0 / radii.size() + "°) must be finite and > 0, got " + r);
			}
		}
	}

	/** A round coast, for tests and as the simplest valid shore. */
	public static Coastline circle(double radius) {
		return new Coastline(List.of(radius, radius, radius, radius));
	}

	/** Bearing of (x, z) in radians in [0, 2π), clockwise from north: 0 at −z, π/2 at +x. */
	public static double bearing(double x, double z) {
		double b = Math.atan2(x, -z);
		return b < 0 ? b + TAU : b;
	}

	/** The shore's distance from the origin at a bearing (radians, clockwise from north). */
	public double radius(double bearing) {
		return spline(bearing, false);
	}

	/** dr/dθ at a bearing, in blocks per radian. */
	public double slope(double bearing) {
		return spline(bearing, true);
	}

	/**
	 * Signed distance to the shore: positive on land, negative at sea. It is {@code r(θ) − ρ} divided by
	 * that function's gradient length, so it measures across the shore, not along the radius. That keeps
	 * the beach, the coastal bands and the terrain ramps at their set widths where the shore runs at an
	 * angle. On a circle it is the exact distance. On {@link DefaultLayout}'s shore it is within 15% of the
	 * true distance up to 150 blocks either side, and changes by at most 1.25 per block over all the sea
	 * and the first 150 blocks inland, which covers everything that reads it.
	 *
	 * <p>Deep inland it is only a large positive number. Inside half the shore's radius the slope term is
	 * measured as if at that radius, and at the origin itself, where the bearing means nothing, the value
	 * differs by bearing. It stays above 200 there, past every inland threshold (the coast ramp is 128).
	 */
	public double distance(double x, double z) {
		double rho = Math.sqrt(x * x + z * z);
		double bearing = bearing(x, z);
		double r = radius(bearing);
		double s = slope(bearing) / Math.max(rho, r / 2);
		return (r - rho) / Math.sqrt(1 + s * s);
	}

	/** The steepest |dr/dθ| / r anywhere on the shore, sampled every 0.1°. */
	public double steepestSlope() {
		double steepest = 0;
		for (int i = 0; i < 3600; i++) {
			double b = TAU * i / 3600;
			steepest = Math.max(steepest, Math.abs(slope(b)) / radius(b));
		}
		return steepest;
	}

	/** Uniform Catmull-Rom through the control radii, wrapping around north. */
	private double spline(double bearing, boolean derivative) {
		int n = radii.size();
		double step = TAU / n;
		double t = bearing / step;
		int i = (int) Math.floor(t);
		double u = t - i;
		double p0 = radii.get(Math.floorMod(i - 1, n));
		double p1 = radii.get(Math.floorMod(i, n));
		double p2 = radii.get(Math.floorMod(i + 1, n));
		double p3 = radii.get(Math.floorMod(i + 2, n));
		double a = p2 - p0;
		double b = 2 * p0 - 5 * p1 + 4 * p2 - p3;
		double c = -p0 + 3 * p1 - 3 * p2 + p3;
		if (derivative) {
			return 0.5 * (a + 2 * b * u + 3 * c * u * u) / step;
		}
		return p1 + 0.5 * u * (a + u * (b + u * c));
	}

	private static IllegalArgumentException invalid(String message) {
		LOGGER.error("[{}] invalid coastline: {}", Continent.MOD_ID, message);
		return new IllegalArgumentException("Invalid coastline: " + message);
	}
}
