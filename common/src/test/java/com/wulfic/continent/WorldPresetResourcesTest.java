package com.wulfic.continent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

/**
 * Guards the static JSON behind the World Type button. The gametest proves the preset loads on a
 * server; this covers what a server never reads (the client's label) and the tag's shape.
 */
class WorldPresetResourcesTest {
	private static final Identifier PRESET = Identifier.fromNamespaceAndPath(Continent.MOD_ID, "continent");

	/**
	 * Reads our copy of {@code path}: the one in this project's resource directory, not a jar's. Vanilla ships a
	 * file at the same path as our tag, and the classpath order decides which one a plain lookup finds.
	 */
	private static JsonObject read(String path) throws IOException {
		List<URL> ours = new ArrayList<>();
		Enumeration<URL> all = WorldPresetResourcesTest.class.getClassLoader().getResources(path);
		while (all.hasMoreElements()) {
			URL url = all.nextElement();
			if ("file".equals(url.getProtocol())) {
				ours.add(url);
			}
		}
		assertEquals(1, ours.size(), "copies of " + path + " outside a jar: " + ours);
		try (Reader reader = new InputStreamReader(ours.get(0).openStream(), StandardCharsets.UTF_8)) {
			return JsonParser.parseReader(reader).getAsJsonObject();
		}
	}

	@Test
	void presetFileExists() throws IOException {
		JsonObject preset = read("data/" + PRESET.getNamespace() + "/worldgen/world_preset/" + PRESET.getPath() + ".json");
		assertTrue(preset.getAsJsonObject("dimensions").has("minecraft:overworld"), "preset has an overworld");
	}

	/** The create-world screen labels a preset with {@code generator.<namespace>.<path>}. */
	@Test
	void presetHasAnEnglishName() throws IOException {
		JsonObject lang = read("assets/" + Continent.MOD_ID + "/lang/en_us.json");
		String key = PRESET.toLanguageKey("generator");
		assertEquals("generator.the_continent.continent", key);
		assertTrue(lang.has(key), "en_us.json has no " + key);
		assertFalse(lang.get(key).getAsString().isBlank(), key + " is blank");
	}

	/** {@code replace: true} would wipe the vanilla presets from the World Type button. */
	@Test
	void normalTagAppendsThePreset() throws IOException {
		JsonObject tag = read("data/minecraft/tags/worldgen/world_preset/normal.json");
		assertTrue(tag.has("replace") && !tag.get("replace").getAsBoolean(), "tag must set \"replace\": false");
		JsonArray values = tag.getAsJsonArray("values");
		boolean listed = false;
		for (JsonElement value : values) {
			listed |= PRESET.toString().equals(value.getAsString());
		}
		assertTrue(listed, "minecraft:normal tag does not list " + PRESET);
	}
}
