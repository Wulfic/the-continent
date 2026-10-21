package com.wulfic.continent.world;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Per-thread, direct-mapped cache of one value per quart column, keyed by (qx, qz, seed). It
 * has 16 slots, one per quart column of a chunk. A chunk section fills its biomes with z inside
 * y inside x, so consecutive calls change column and a single-entry cache would always miss.
 * Each thread owns its slots, so there is no locking. Values must be immutable.
 */
final class ColumnCache<T> {
	@FunctionalInterface
	interface Loader<T> {
		T load(int qx, int qz, long seed);
	}

	private record Entry<T>(int qx, int qz, long seed, T value) {
	}

	private static final int SLOTS = 16;

	private final ThreadLocal<List<Entry<T>>> slots =
			ThreadLocal.withInitial(() -> new ArrayList<>(Collections.nCopies(SLOTS, null)));

	T get(int qx, int qz, long seed, Loader<T> loader) {
		List<Entry<T>> table = slots.get();
		int index = (qx & 3) | (qz & 3) << 2;
		Entry<T> entry = table.get(index);
		if (entry == null || entry.qx() != qx || entry.qz() != qz || entry.seed() != seed) {
			entry = new Entry<>(qx, qz, seed, loader.load(qx, qz, seed));
			table.set(index, entry);
		}
		return entry.value();
	}
}
