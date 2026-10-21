package com.wulfic.continent.world;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ColumnCacheTest {
	private final List<String> loads = new ArrayList<>();
	private final ColumnCache<String> cache = new ColumnCache<>();

	private String load(int qx, int qz, long seed) {
		String value = qx + "," + qz + "@" + seed;
		loads.add(value);
		return value;
	}

	@Test
	void returnsTheLoadedValueForEveryKey() {
		for (int qx = -9; qx <= 9; qx++) {
			for (int qz = -9; qz <= 9; qz++) {
				assertEquals(qx + "," + qz + "@7", cache.get(qx, qz, 7, this::load));
			}
		}
	}

	@Test
	void sectionFillOrderLoadsEachColumnOnce() {
		// LevelChunkSection fills biomes with z inside y inside x.
		for (int qx = 0; qx < 4; qx++) {
			for (int qy = 0; qy < 4; qy++) {
				for (int qz = 0; qz < 4; qz++) {
					cache.get(16 + qx, -32 + qz, 1, this::load);
				}
			}
		}
		assertEquals(16, loads.size());
	}

	@Test
	void collidingColumnsAndSeedsDoNotAlias() {
		// (0,0) and (4,0) share a slot; so do the same column under two seeds.
		assertEquals("0,0@1", cache.get(0, 0, 1, this::load));
		assertEquals("4,0@1", cache.get(4, 0, 1, this::load));
		assertEquals("0,0@1", cache.get(0, 0, 1, this::load));
		assertEquals("0,0@2", cache.get(0, 0, 2, this::load));
		assertEquals(4, loads.size());
	}

	@Test
	void threadsDoNotShareSlots() throws InterruptedException {
		cache.get(0, 0, 1, this::load);
		Thread other = new Thread(() -> cache.get(0, 0, 1, this::load));
		other.start();
		other.join();
		assertEquals(2, loads.size(), "a second thread must load its own copy");
	}
}
