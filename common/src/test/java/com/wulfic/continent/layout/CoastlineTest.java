package com.wulfic.continent.layout;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CoastlineTest {
	private static final Coastline COAST = DefaultLayout.coastline();
	private static final double EPS = 1e-9;

	@Test
	void aCircleIsTheExactDistance() {
		Coastline circle = Coastline.circle(1000);
		for (int x = -1500; x <= 1500; x += 37) {
			for (int z = -1500; z <= 1500; z += 41) {
				assertEquals(1000 - Math.hypot(x, z), circle.distance(x, z), EPS, "at " + x + "," + z);
			}
		}
	}

	@Test
	void bearingIsClockwiseFromNorth() {
		assertEquals(0, Coastline.bearing(0, -10), EPS);
		assertEquals(Math.PI / 2, Coastline.bearing(10, 0), EPS);
		assertEquals(Math.PI, Coastline.bearing(0, 10), EPS);
		assertEquals(3 * Math.PI / 2, Coastline.bearing(-10, 0), EPS);
		double justWestOfNorth = Coastline.bearing(-1e-6, -10);
		assertTrue(justWestOfNorth > 2 * Math.PI - 1e-6 && justWestOfNorth < 2 * Math.PI, "bearing " + justWestOfNorth);
	}

	@Test
	void theShorePassesThroughEveryControlRadius() {
		List<Double> radii = COAST.radii();
		for (int i = 0; i < radii.size(); i++) {
			assertEquals(radii.get(i), COAST.radius(2 * Math.PI * i / radii.size()), EPS, "control point " + i);
		}
	}

	@Test
	void distanceIsZeroOnTheShoreAndSignedEitherSide() {
		for (int deg = 0; deg < 360; deg++) {
			double b = Math.toRadians(deg);
			double r = COAST.radius(b);
			double x = r * Math.sin(b);
			double z = -r * Math.cos(b);
			assertEquals(0, COAST.distance(x, z), 1e-6, "on the shore at " + deg + "°");
			assertTrue(COAST.distance(0.9 * x, 0.9 * z) > 0, "inland of " + deg + "°");
			assertTrue(COAST.distance(1.1 * x, 1.1 * z) < 0, "offshore of " + deg + "°");
		}
	}

	/**
	 * Against the true distance to the shore curve, found by brute force. Dividing by the gradient is what
	 * keeps this within 15% where the shore runs at an angle to the radius. The raw {@code r(θ) − ρ} is up
	 * to ~25% off on this coastline, so this test fails without it.
	 */
	@Test
	void distanceMeasuresAcrossTheShore() {
		List<double[]> shore = new ArrayList<>();
		for (int i = 0; i < 36_000; i++) {
			double b = 2 * Math.PI * i / 36_000;
			double r = COAST.radius(b);
			shore.add(new double[] {r * Math.sin(b), -r * Math.cos(b)});
		}
		int checked = 0;
		for (int deg = 0; deg < 360; deg += 3) {
			double b = Math.toRadians(deg);
			for (double offset = -150; offset <= 150; offset += 25) {
				double rho = COAST.radius(b) + offset;
				double x = rho * Math.sin(b);
				double z = -rho * Math.cos(b);
				double truth = Double.MAX_VALUE;
				for (double[] p : shore) {
					truth = Math.min(truth, Math.hypot(x - p[0], z - p[1]));
				}
				double d = Math.abs(COAST.distance(x, z));
				assertEquals(truth, d, 0.15 * truth + 2, "at " + deg + "°, " + offset + " blocks off the radius");
				checked++;
			}
		}
		assertTrue(checked > 1000);
	}

	/**
	 * Everything that reads the distance does so offshore or within the coast ramp (128) inland. There it
	 * must be continuous and close to 1 block per block, across the shore and across the north seam where
	 * the bearing wraps. The bound is the measured worst case (1.18 at sea, 1.25 inland) plus a margin.
	 */
	@Test
	void distanceIsContinuousWhereItIsRead() {
		double step = 0.5;
		int checked = 0;
		for (int line = -1800; line <= 1800; line += 25) {
			for (int axis = 0; axis < 2; axis++) {
				double previous = Double.NaN;
				for (double t = -1800; t <= 1800; t += step) {
					double x = axis == 0 ? t : line;
					double z = axis == 0 ? line : t;
					double d = COAST.distance(x, z);
					if (!Double.isNaN(previous) && d < 150 && previous < 150) {
						assertTrue(Math.abs(d - previous) <= 1.3 * step, "distance steps by " + (d - previous) + " at (" + x
								+ ", " + z + ")");
						checked++;
					}
					previous = d;
				}
			}
		}
		assertTrue(checked > 1_000_000, "only " + checked + " steps were checked");
	}

	/** The coast ramp is 128 blocks. No point near the middle may look closer to the shore than that. */
	@Test
	void theMiddleStaysFarFromTheShore() {
		for (int x = -500; x <= 500; x += 10) {
			for (int z = -500; z <= 500; z += 10) {
				assertTrue(COAST.distance(x, z) > 200, "at " + x + "," + z + ": " + COAST.distance(x, z));
			}
		}
	}

	@Test
	void badRadiiThrow() {
		assertInvalid("at least 4", List.of(1000.0, 1000.0, 1000.0));
		assertInvalid("radius 2", List.of(1000.0, 1000.0, 0.0, 1000.0));
		assertInvalid("radius 1", List.of(1000.0, -5.0, 1000.0, 1000.0));
		assertInvalid("radius 3", List.of(1000.0, 1000.0, 1000.0, Double.NaN));
		assertInvalid("radius 0", List.of(Double.POSITIVE_INFINITY, 1000.0, 1000.0, 1000.0));
	}

	private static void assertInvalid(String expectedInMessage, List<Double> radii) {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new Coastline(radii));
		assertTrue(e.getMessage().contains(expectedInMessage),
				"message \"" + e.getMessage() + "\" does not name \"" + expectedInMessage + "\"");
	}
}
