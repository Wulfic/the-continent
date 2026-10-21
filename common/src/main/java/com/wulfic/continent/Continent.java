package com.wulfic.continent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The mod's identity, shared by every loader. Nothing here may touch a loader's API. */
public final class Continent {
	public static final String MOD_ID = "the_continent";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private Continent() {
	}
}
