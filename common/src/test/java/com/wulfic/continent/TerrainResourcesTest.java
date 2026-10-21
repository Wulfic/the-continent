package com.wulfic.continent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Guards the terrain JSON of the band under test. Every vanilla overworld function that leads to the spline
 * graph's inputs has a copy of ours: the running version's own file, with only the references re-pointed and the
 * top slide raised (twice in all). Every vanilla reference left must be one we meant to keep. The jar's files
 * are on the test classpath, and the set of copies is worked out from them, so this holds on every band.
 */
class TerrainResourcesTest {
	private static final String OURS = "data/the_continent/worldgen/";
	private static final String VANILLA = "data/minecraft/worldgen/";
	/** What the spline graph reads: re-pointed in every band, along with every function that leads to these. */
	private static final List<String> SEEDS = List.of("continents", "erosion", "ridges", "ridges_folded", "offset",
			"factor", "jaggedness", "depth", "sloped_cheese");
	/** Ours, not copies: they put the layout in place of vanilla's noise. */
	private static final Set<String> CHANNEL_FILES = Set.of("continents", "erosion", "ridges");
	/** Vanilla offset is copied as offset_spline: our offset.json adds the layout's uplift to it. */
	private static final Map<String, String> RENAMED = Map.of("offset", "offset_spline");
	/**
	 * Vanilla fades terrain to air from y 240 to 256, which caps mountains at ~256. Ours fades from 288 to 304, so
	 * the uplifted peaks fit under the 320 build limit. 26.3 writes the slide as a gradient's coordinates.
	 */
	private static final Pattern RAISED_TOP_SLIDE = Pattern.compile(
			"(\"from_value\": 1\\.0,\\s*\"from_y\": )288(,\\s*\"to_value\": 0\\.0,\\s*\"to_y\": )304"
					+ "|(\"from_coordinate\": )288(,\\s*\"from_value\": 1\\.0,\\s*\"to_coordinate\": )304(,\\s*\"to_value\": 0\\.0)");
	private static final Pattern VANILLA_FUNCTION = Pattern.compile("\"minecraft:overworld/([a-z0-9_/]+)\"");
	private static final Pattern ANY_FUNCTION = Pattern.compile("\"([a-z_]+:overworld/[a-z0-9_/]+)\"");
	private static final Map<String, String> CHANNELS = Map.of("continents", "continentalness", "erosion", "erosion",
			"ridges", "ridges");
	// How each band writes the wrappers our own files use.
	//? if >=26.3 {
	/*private static final String CACHE = "minecraft:cache";
	private static final String CACHE_INPUT = "input";
	private static final String ADD_LEFT = "left";
	private static final String ADD_RIGHT = "right";
	private static final String NOISE = "minecraft:noise";
	*///?} else {
	private static final String CACHE = "minecraft:flat_cache";
	private static final String CACHE_INPUT = "argument";
	private static final String ADD_LEFT = "argument1";
	private static final String ADD_RIGHT = "argument2";
	private static final String NOISE = "minecraft:shifted_noise";
	//?}

	/** Every vanilla overworld function we re-point: {@link #SEEDS} and all that lead to them. */
	private static Set<String> rewired;

	private static String text(String path) throws IOException {
		InputStream in = TerrainResourcesTest.class.getClassLoader().getResourceAsStream(path);
		assertNotNull(in, "missing resource " + path);
		try (in) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static String vanillaFunction(String name) throws IOException {
		return text(VANILLA + "density_function/overworld/" + name + ".json");
	}

	/**
	 * Walks every vanilla function the overworld noise settings reach, then keeps those that lead to a seed. The
	 * copies have to cover exactly these, or vanilla terrain leaks back in through a function we didn't copy.
	 */
	@BeforeAll
	static void findRewired() throws IOException {
		Map<String, Set<String>> refs = new HashMap<>();
		Deque<String> todo = new ArrayDeque<>(references(text(VANILLA + "noise_settings/overworld.json")));
		while (!todo.isEmpty()) {
			String name = todo.pop();
			if (!refs.containsKey(name)) {
				Set<String> out = references(vanillaFunction(name));
				refs.put(name, out);
				todo.addAll(out);
			}
		}
		Set<String> found = new HashSet<>(SEEDS);
		boolean grew = true;
		while (grew) {
			grew = false;
			for (Map.Entry<String, Set<String>> e : refs.entrySet()) {
				if (!found.contains(e.getKey()) && e.getValue().stream().anyMatch(found::contains)) {
					found.add(e.getKey());
					grew = true;
				}
			}
		}
		rewired = Set.copyOf(found);
	}

	private static Set<String> references(String json) {
		Set<String> out = new TreeSet<>();
		Matcher m = VANILLA_FUNCTION.matcher(json);
		while (m.find()) {
			out.add(m.group(1));
		}
		return out;
	}

	private static String mapBack(String text) {
		for (String name : rewired) {
			text = text.replace("\"the_continent:overworld/" + name + "\"", "\"minecraft:overworld/" + name + "\"");
		}
		return text;
	}

	/** Puts the vanilla top slide back, counting the raised ones. */
	private static String lowerTopSlides(String text, int[] count) {
		Matcher slides = RAISED_TOP_SLIDE.matcher(text);
		StringBuilder undone = new StringBuilder();
		while (slides.find()) {
			String vanilla = slides.group(1) != null ? slides.group(1) + "240" + slides.group(2) + "256"
					: slides.group(3) + "240" + slides.group(4) + "256" + slides.group(5);
			slides.appendReplacement(undone, Matcher.quoteReplacement(vanilla));
			count[0]++;
		}
		slides.appendTail(undone);
		return undone.toString();
	}

	static List<String> copies() throws IOException {
		if (rewired == null) {
			findRewired();
		}
		return rewired.stream().filter(name -> !CHANNEL_FILES.contains(name)).sorted().toList();
	}

	private static String ourCopy(String name) throws IOException {
		return text(OURS + "density_function/overworld/" + RENAMED.getOrDefault(name, name) + ".json");
	}

	@ParameterizedTest
	@MethodSource("copies")
	void copiesAreTheJarsWithOnlyReferencesRepointed(String name) throws IOException {
		String ours = lowerTopSlides(ourCopy(name), new int[1]);
		assertEquals(JsonParser.parseString(vanillaFunction(name)), JsonParser.parseString(mapBack(ours)),
				name + " differs from vanilla beyond the re-pointed references and the raised top slide");
	}

	@Test
	void noiseSettingsAreTheJarsOverworldWithOnlyReferencesRepointed() throws IOException {
		String ours = lowerTopSlides(text(OURS + "noise_settings/continent.json"), new int[1]);
		assertEquals(JsonParser.parseString(text(VANILLA + "noise_settings/overworld.json")),
				JsonParser.parseString(mapBack(ours)), "noise settings differ from vanilla beyond the re-pointed references");
	}

	/** Once in final_density and once in preliminary_surface_level, wherever the band keeps those. */
	@Test
	void theTopSlideIsRaisedTwice() throws IOException {
		int[] raised = {0};
		lowerTopSlides(text(OURS + "noise_settings/continent.json"), raised);
		for (String name : copies()) {
			lowerTopSlides(ourCopy(name), raised);
		}
		assertEquals(2, raised[0], "expected the top slide raised in final_density and preliminary_surface_level");
	}

	/** offset is vanilla's spline plus the layout's uplift, seeded like the other channels, varied by the peaks noise. */
	@Test
	void offsetIsTheVanillaSplinePlusTheLayoutUplift() throws IOException {
		JsonObject root = JsonParser.parseString(text(OURS + "density_function/overworld/offset.json")).getAsJsonObject();
		assertEquals(CACHE, root.get("type").getAsString(), "offset is cached per column");
		JsonObject add = root.getAsJsonObject(CACHE_INPUT);
		assertEquals("minecraft:add", add.get("type").getAsString());
		assertEquals("the_continent:overworld/offset_spline", add.get(ADD_LEFT).getAsString());
		JsonObject uplift = add.getAsJsonObject(ADD_RIGHT);
		assertEquals("the_continent:layout", uplift.get("type").getAsString());
		assertEquals("uplift", uplift.get("channel").getAsString());
		JsonObject peaks = uplift.getAsJsonObject("detail");
		assertEquals(NOISE, peaks.get("type").getAsString());
		assertEquals("the_continent:peaks", peaks.get("noise").getAsString(), "uplift detail is the peaks noise");
		assertEquals(routerTemperature(), uplift.get("seed_source"), "uplift seed_source is not the router's temperature");
	}

	/**
	 * The peaks noise is octaves -8, -7 and -6 at amplitudes 1, 1 and 0.5 on every band. 26.3 writes a noise's
	 * scale explicitly; vanilla's parity form gives the value that keeps the old noise.
	 */
	@Test
	void peaksIsTheSameNoiseOnEveryBand() throws IOException {
		// Touching a worldgen class before the bootstrap poisons every later test in the JVM (gotchas.md).
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		//? if >=26.3 {
		/*JsonElement expected = NormalNoise.DIRECT_CODEC.encodeStart(JsonOps.INSTANCE, NormalNoise.createParity(-8, 1.0, 1.0, 0.5))
				.getOrThrow();
		*///?} else {
		JsonElement expected = NormalNoise.NoiseParameters.DIRECT_CODEC
				.encodeStart(JsonOps.INSTANCE, new NormalNoise.NoiseParameters(-8, 1.0, 1.0, 0.5)).getOrThrow();
		//?}
		assertEquals(expected, JsonParser.parseString(text(OURS + "noise/peaks.json")));
	}

	private static JsonElement routerTemperature() throws IOException {
		return JsonParser.parseString(text(VANILLA + "noise_settings/overworld.json")).getAsJsonObject()
				.getAsJsonObject("noise_router").get("temperature");
	}

	/**
	 * A missed re-point quietly feeds vanilla terrain into part of the graph. Our channel files may name only
	 * their own vanilla namesake, as the detail noise.
	 */
	@Test
	void noReferenceToARewiredVanillaFunctionIsLeft() throws IOException {
		Set<String> unexpected = new TreeSet<>();
		Map<String, String> files = new HashMap<>();
		files.put("noise_settings/continent.json", "");
		files.put("density_function/overworld/offset.json", "");
		for (String name : copies()) {
			files.put("density_function/overworld/" + RENAMED.getOrDefault(name, name) + ".json", "");
		}
		for (String channel : CHANNEL_FILES) {
			files.put("density_function/overworld/" + channel + ".json", channel);
		}
		for (Map.Entry<String, String> file : files.entrySet()) {
			Matcher m = ANY_FUNCTION.matcher(text(OURS + file.getKey()));
			while (m.find()) {
				String ref = m.group(1);
				if (ref.startsWith("minecraft:overworld/")) {
					String name = ref.substring("minecraft:overworld/".length());
					if (rewired.contains(name) && !name.equals(file.getValue())) {
						unexpected.add(file.getKey() + " -> " + ref);
					}
				} else if (ref.startsWith("the_continent:overworld/")) {
					String name = ref.substring("the_continent:overworld/".length());
					if (!rewired.contains(name) && !name.equals("offset_spline")) {
						unexpected.add(file.getKey() + " -> " + ref + " (no such copy)");
					}
				}
			}
		}
		assertTrue(unexpected.isEmpty(), "unexpected references: " + unexpected);
	}

	/**
	 * The layout seed must come from the same function the biome source hashes (the climate sampler's
	 * temperature), or biomes and terrain warp differently.
	 */
	@ParameterizedTest
	@ValueSource(strings = {"continents", "erosion", "ridges"})
	void channelsSeedFromTheRoutersTemperatureAndAddTheirVanillaNoise(String name) throws IOException {
		JsonObject root = JsonParser.parseString(text(OURS + "density_function/overworld/" + name + ".json")).getAsJsonObject();
		assertEquals(CACHE, root.get("type").getAsString(), name + " is cached per column");
		JsonObject layout = root.getAsJsonObject(CACHE_INPUT);
		assertEquals("the_continent:layout", layout.get("type").getAsString());
		assertEquals(CHANNELS.get(name), layout.get("channel").getAsString());
		assertEquals("minecraft:overworld/" + name, layout.get("detail").getAsString());
		assertEquals(routerTemperature(), layout.get("seed_source"), name + " seed_source is not the router's temperature");
	}

	@Test
	void presetUsesTheContinentNoiseSettings() throws IOException {
		JsonObject generator = JsonParser.parseString(text(OURS + "world_preset/continent.json")).getAsJsonObject()
				.getAsJsonObject("dimensions").getAsJsonObject("minecraft:overworld").getAsJsonObject("generator");
		assertEquals("the_continent:continent", generator.get("settings").getAsString());
	}
}
