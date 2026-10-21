package com.example.worldgen.levelgen;

import com.example.worldgen.biome.Climate;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensityFunctionParser;
import com.example.worldgen.density.Holder;
import com.example.worldgen.density.WorldgenRegistries;
import com.example.worldgen.json.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decodes {@code data/<ns>/worldgen/noise_settings/<path>.json} like the 26.3 {@code NoiseGeneratorSettings.DIRECT_CODEC}
 * (see {@link NoiseGeneratorSettings} for the field list). Sub-codecs ported: {@code NoiseRouter.CODEC} (eight
 * required functions), {@code Aquifer.Config.CODEC} (six required functions), {@code SpawnTargetPoint.CODEC}
 * ({@code unboundedMap(DensityFunction.REFERENCE_CODEC, Climate.Parameter.CODEC)}: keys are registry ids only;
 * a parameter is a float in [-2, 2], a {@code [min, max]} list or a {@code {min, max}} object, with
 * {@code min <= max}), {@code DebugFunctionEntry.CODEC} ({@code label}, {@code function}). Keys the codec does not
 * read are ignored, as by vanilla.
 */
public final class NoiseGeneratorSettingsLoader {

    private final WorldgenRegistries registries;

    public NoiseGeneratorSettingsLoader(WorldgenRegistries registries) {
        this.registries = registries;
    }

    public NoiseGeneratorSettings load(String id) {
        String key = WorldgenRegistries.normalize(id);
        int colon = key.indexOf(':');
        Path file = registries.dataRoot().resolve(key.substring(0, colon)).resolve("worldgen").resolve("noise_settings")
                .resolve(key.substring(colon + 1) + ".json");
        try {
            return decode(Json.parseWithNumberLiterals(WorldgenRegistries.read(file)));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid noise settings " + key + ": " + e.getMessage(), e);
        }
    }

    public NoiseGeneratorSettings decode(Object json) {
        DensityFunctionParser parser = registries.parser();
        Map<String, Object> m = object(json, "noise_settings");
        Map<String, Object> noise = object(required(m, "noise"), "noise");
        NoiseSettings noiseSettings = NoiseSettings.create(intValue(required(noise, "min_y")), intValue(required(noise, "height")));
        Object defaultBlock = required(m, "default_block");
        Object defaultFluid = required(m, "default_fluid");
        Map<String, Object> r = object(required(m, "noise_router"), "noise_router");
        NoiseRouter router = new NoiseRouter(parser.parse(required(r, "temperature")), parser.parse(required(r, "vegetation")),
                parser.parse(required(r, "continents")), parser.parse(required(r, "erosion")), parser.parse(required(r, "depth")),
                parser.parse(required(r, "ridges")), parser.parse(required(r, "chunk_surface_level")),
                parser.parse(required(r, "final_density")));
        Object materialRuleJson = required(m, "material_rule");
        Optional<String> materialRuleId = Optional.empty();
        Object materialRule = materialRuleJson;
        if (materialRuleJson instanceof String ruleId) {
            String ruleKey = WorldgenRegistries.normalize(ruleId);
            int colon = ruleKey.indexOf(':');
            Path ruleFile = registries.dataRoot().resolve(ruleKey.substring(0, colon)).resolve("worldgen")
                    .resolve("material_rule").resolve(ruleKey.substring(colon + 1) + ".json");
            if (!Files.isRegularFile(ruleFile)) {
                throw new IllegalArgumentException("Unknown registry key in minecraft:worldgen/material_rule: " + ruleKey);
            }
            materialRuleId = Optional.of(ruleKey);
            materialRule = Json.parseWithNumberLiterals(WorldgenRegistries.read(ruleFile));
        }
        List<SpawnTargetPoint> spawnTarget = new ArrayList<>();
        for (Object entry : list(required(m, "spawn_target"), "spawn_target")) {
            Map<Holder<DensityFunction>, Climate.Parameter> parameters = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : object(entry, "spawn_target entry").entrySet()) {
                parameters.put(registries.densityFunction(e.getKey()), parameter(e.getValue()));
            }
            spawnTarget.add(new SpawnTargetPoint(parameters));
        }
        int seaLevel = intValue(required(m, "sea_level"));
        boolean disableMobGeneration = bool(required(m, "disable_mob_generation"), "disable_mob_generation");
        Optional<AquiferConfig> aquifers = Optional.empty();
        if (m.containsKey("aquifers")) {
            Map<String, Object> a = object(m.get("aquifers"), "aquifers");
            aquifers = Optional.of(new AquiferConfig(parser.parse(required(a, "barrier")),
                    parser.parse(required(a, "fluid_level_floodedness")), parser.parse(required(a, "fluid_level_spread")),
                    parser.parse(required(a, "lava")), parser.parse(required(a, "exclusion")),
                    parser.parse(required(a, "surface_level"))));
        }
        boolean legacyRandomSource = bool(required(m, "legacy_random_source"), "legacy_random_source");
        List<NoiseGeneratorSettings.DebugFunctionEntry> debugFunctions = new ArrayList<>();
        if (m.containsKey("debug_functions")) {
            for (Object entry : list(m.get("debug_functions"), "debug_functions")) {
                Map<String, Object> e = object(entry, "debug function");
                Object label = required(e, "label");
                if (!(label instanceof String labelString)) {
                    throw new IllegalArgumentException("label: not a string");
                }
                debugFunctions.add(new NoiseGeneratorSettings.DebugFunctionEntry(labelString, parser.parse(required(e, "function"))));
            }
        }
        return new NoiseGeneratorSettings(noiseSettings, defaultBlock, defaultFluid, router, materialRuleId, materialRule,
                spawnTarget, seaLevel, disableMobGeneration, aquifers, legacyRandomSource, debugFunctions);
    }

    /** {@code Climate.Parameter.CODEC} ({@code ExtraCodecs.intervalCodec(floatRange(-2, 2), "min", "max", ...)}). */
    static Climate.Parameter parameter(Object json) {
        float min;
        float max;
        if (json instanceof Number n) {
            min = max = point(n);
        } else if (json instanceof List<?> l) {
            if (l.size() != 2) {
                throw new IllegalArgumentException("Input is not a list of 2 elements");
            }
            min = point(l.get(0));
            max = point(l.get(1));
        } else {
            Map<String, Object> o = object(json, "parameter");
            min = point(required(o, "min"));
            max = point(required(o, "max"));
        }
        if (Float.compare(min, max) > 0) {
            throw new IllegalArgumentException("Cannon construct interval, min > max (" + min + " > " + max + ")");
        }
        return new Climate.Parameter(Climate.quantizeCoord(min), Climate.quantizeCoord(max));
    }

    private static float point(Object json) {
        if (!(json instanceof Number n)) {
            throw new IllegalArgumentException("Not a number: " + json);
        }
        float v = n.floatValue();
        if (!(v >= -2.0F && v <= 2.0F)) {
            throw new IllegalArgumentException("Value " + v + " outside of range [-2.0:2.0]");
        }
        return v;
    }

    private static int intValue(Object json) {
        if (!(json instanceof Number n)) {
            throw new IllegalArgumentException("Not a number: " + json);
        }
        return n.intValue();
    }

    private static boolean bool(Object json, String field) {
        if (!(json instanceof Boolean b)) {
            throw new IllegalArgumentException(field + ": not a boolean: " + json);
        }
        return b;
    }

    private static Object required(Map<String, Object> m, String field) {
        Object value = m.get(field);
        if (value == null) {
            throw new IllegalArgumentException("No key " + field + " in " + m.keySet());
        }
        return value;
    }

    private static List<?> list(Object value, String field) {
        if (!(value instanceof List<?> l)) {
            throw new IllegalArgumentException(field + ": not a list");
        }
        return l;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(field + ": not an object");
        }
        return (Map<String, Object>) map;
    }
}
