package com.example.worldgen.biome;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads biome JSON files ({@code data/minecraft/worldgen/biome/<name>.json}, extracted from the 26.3 client jar)
 * into {@link BiomeDefinition}s following the 26.3 codecs (see {@link BiomeDefinition}).
 *
 * <p>Colours follow {@code ExtraCodecs.STRING_RGB_COLOR}: a {@code "#rrggbb"} string (exactly six hex digits,
 * made opaque with {@code ARGB.opaque}) or, as the alternative, a plain integer ({@code Codec.INT}, used as is).
 * The further alternative of a 3-float vector is rejected with an exception (no 26.3 biome file uses it).
 * {@code carvers} and each {@code features} step are holder sets: a single id string or a list of ids;
 * {@code #tag} references and inline objects are rejected (none occur in the 26.3 biome files).
 */
public final class BiomeDefinitionLoader {
    private final Path dataRoot;

    /** @param dataRoot the directory that contains {@code minecraft/worldgen/biome} (the project's {@code data/}) */
    public BiomeDefinitionLoader(Path dataRoot) {
        this.dataRoot = dataRoot;
    }

    public Path fileFor(String biomeId) {
        String path = biomeId.startsWith("minecraft:") ? biomeId.substring("minecraft:".length()) : biomeId;
        return this.dataRoot.resolve("minecraft").resolve("worldgen").resolve("biome").resolve(path + ".json");
    }

    public BiomeDefinition load(String biomeId) throws IOException {
        String text = Files.readString(this.fileFor(biomeId), StandardCharsets.UTF_8);
        return parse(biomeId, text);
    }

    static BiomeDefinition parse(String biomeId, String json) {
        Map<String, Object> root = asObject(BiomeJson.parse(json), "biome");
        boolean hasPrecipitation = asBoolean(required(root, "has_precipitation"), "has_precipitation");
        float temperature = asNum(required(root, "temperature"), "temperature").floatValue();
        BiomeDefinition.TemperatureModifier temperatureModifier = root.containsKey("temperature_modifier")
            ? BiomeDefinition.TemperatureModifier.byName(asString(root.get("temperature_modifier"),
            "temperature_modifier"))
            : BiomeDefinition.TemperatureModifier.NONE;
        float downfall = asNum(required(root, "downfall"), "downfall").floatValue();
        Map<String, Object> attributes = root.containsKey("attributes")
            ? asObject(root.get("attributes"), "attributes") : Map.of();
        Map<String, Object> effectsJson = asObject(required(root, "effects"), "effects");
        BiomeDefinition.SpecialEffects effects = new BiomeDefinition.SpecialEffects(
            rgbColor(required(effectsJson, "water_color"), "water_color"),
            optionalColor(effectsJson, "foliage_color"),
            optionalColor(effectsJson, "dry_foliage_color"),
            optionalColor(effectsJson, "grass_color"),
            effectsJson.containsKey("grass_color_modifier")
                ? BiomeDefinition.GrassColorModifier.byName(asString(effectsJson.get("grass_color_modifier"),
                "grass_color_modifier"))
                : BiomeDefinition.GrassColorModifier.NONE);
        List<String> carvers = idList(required(root, "carvers"), "carvers");
        List<List<String>> features = new ArrayList<>();
        for (Object step : asList(required(root, "features"), "features")) {
            features.add(idList(step, "features step"));
        }
        return new BiomeDefinition(biomeId, hasPrecipitation, temperature, temperatureModifier, downfall,
            attributes, effects, carvers, List.copyOf(features));
    }

    private static Optional<Integer> optionalColor(Map<String, Object> object, String key) {
        return object.containsKey(key) ? Optional.of(rgbColor(object.get(key), key)) : Optional.empty();
    }

    /** {@code STRING_RGB_COLOR}: {@code hexColor(6).xmap(ARGB::opaque, ...)} or {@code Codec.INT}. */
    static int rgbColor(Object value, String what) {
        if (value instanceof String string) {
            if (!string.startsWith("#")) {
                throw new IllegalArgumentException(what + ": hex color must begin with #");
            }
            int digits = string.length() - 1;
            if (digits != 6) {
                throw new IllegalArgumentException(what + ": hex color is wrong size, expected 6 digits but got "
                    + digits);
            }
            long parsed = HexFormat.fromHexDigitsToLong(string, 1, string.length());
            if (parsed < 0L || parsed > 0xFFFFFFL) {
                throw new IllegalArgumentException(what + ": color value out of range: " + string);
            }
            return (int) parsed | -16777216;
        }
        if (value instanceof BiomeJson.Num num) {
            return new BigDecimal(num.text()).intValueExact();
        }
        throw new IllegalArgumentException(what + ": unsupported color form " + value);
    }

    private static List<String> idList(Object value, String what) {
        if (value instanceof String id) {
            return List.of(checkId(id, what));
        }
        List<String> ids = new ArrayList<>();
        for (Object element : asList(value, what)) {
            ids.add(checkId(asString(element, what), what));
        }
        return List.copyOf(ids);
    }

    private static String checkId(String id, String what) {
        if (id.startsWith("#")) {
            throw new IllegalArgumentException(what + ": tag references are not supported: " + id);
        }
        return id;
    }

    private static Object required(Map<String, Object> object, String key) {
        Object value = object.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing field " + key);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asObject(Object value, String what) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException(what + " must be an object");
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value, String what) {
        if (value instanceof List<?> list) {
            return (List<Object>) list;
        }
        throw new IllegalArgumentException(what + " must be a list");
    }

    private static String asString(Object value, String what) {
        if (value instanceof String string) {
            return string;
        }
        throw new IllegalArgumentException(what + " must be a string");
    }

    private static boolean asBoolean(Object value, String what) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        throw new IllegalArgumentException(what + " must be a boolean");
    }

    private static BiomeJson.Num asNum(Object value, String what) {
        if (value instanceof BiomeJson.Num num) {
            return num;
        }
        throw new IllegalArgumentException(what + " must be a number");
    }
}
