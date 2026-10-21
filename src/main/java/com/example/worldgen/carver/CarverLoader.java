package com.example.worldgen.carver;

import com.example.worldgen.json.Json;
import com.example.worldgen.provider.CodecJson;
import com.example.worldgen.provider.FloatProvider;
import com.example.worldgen.provider.HeightProvider;
import com.example.worldgen.provider.IntProvider;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Decodes {@code data/<ns>/worldgen/carver/*.json} like {@code WorldCarver.DIRECT_CODEC} (26.3): dispatch on
 * {@code type} ({@code cave} -> {@link CaveWorldCarver}, {@code canyon} -> {@link CanyonWorldCarver}), fields at
 * the top level. Field codecs and defaults are those of the records' {@code MAP_CODEC} lambdas.
 */
public final class CarverLoader {

    private CarverLoader() {
    }

    /** Loads {@code <dataRoot>/minecraft/worldgen/carver/<name>.json}. */
    public static WorldCarver load(Path dataRoot, String name) throws IOException {
        Path file = dataRoot.resolve("minecraft").resolve("worldgen").resolve("carver").resolve(name + ".json");
        return parse(Json.parseWithNumberLiterals(Files.readString(file, StandardCharsets.UTF_8)));
    }

    public static WorldCarver parse(Object json) {
        Map<String, Object> m = CodecJson.object(json, "carver");
        String type = CodecJson.type(m);
        switch (type) {
            case "cave":
                return parseCave(m);
            case "canyon":
                return parseCanyon(m);
            default:
                throw new CodecJson.CodecException("Unknown carver type: " + type);
        }
    }

    private static CaveWorldCarver parseCave(Map<String, Object> m) {
        float probability = CodecJson.floatInRange(CodecJson.field(m, "probability"), 0.0F, 1.0F, "probability");
        HeightProvider y = HeightProvider.parse(CodecJson.field(m, "y"));
        IntProvider count = IntProvider.parseNonNegative(CodecJson.field(m, "count"));
        FloatProvider thickness = FloatProvider.parse(CodecJson.field(m, "thickness"), 0.0F);
        Object weird = m.get("weird_thickness_bias");
        boolean weirdThicknessBias = weird != null && CodecJson.boolValue(weird, "weird_thickness_bias");
        FloatProvider room = FloatProvider.parse(CodecJson.field(m, "room_vertical_radius_multiplier"));
        FloatProvider horizontal = FloatProvider.parse(CodecJson.field(m, "horizontal_radius_multiplier"));
        FloatProvider vertical = FloatProvider.parse(CodecJson.field(m, "vertical_radius_multiplier"));
        Object start = m.get("start_vertical_radius_multiplier");
        FloatProvider startVertical = start == null ? FloatProvider.ConstantFloat.of(1.0F) : FloatProvider.parse(start);
        FloatProvider floor = FloatProvider.parse(CodecJson.field(m, "floor_level"), -1.0F, 1.0F);
        return new CaveWorldCarver(probability, y, count, thickness, weirdThicknessBias, room, horizontal, vertical,
                startVertical, floor);
    }

    private static CanyonWorldCarver parseCanyon(Map<String, Object> m) {
        float probability = CodecJson.floatInRange(CodecJson.field(m, "probability"), 0.0F, 1.0F, "probability");
        HeightProvider y = HeightProvider.parse(CodecJson.field(m, "y"));
        FloatProvider verticalRotation = FloatProvider.parse(CodecJson.field(m, "vertical_rotation"));
        Map<String, Object> s = CodecJson.object(CodecJson.field(m, "shape"), "shape");
        CanyonWorldCarver.Shape shape = new CanyonWorldCarver.Shape(
                FloatProvider.parse(CodecJson.field(s, "distance_factor")),
                FloatProvider.parse(CodecJson.field(s, "thickness")),
                CodecJson.intInRange(CodecJson.field(s, "width_smoothness"), 1, Integer.MAX_VALUE, "width_smoothness"),
                FloatProvider.parse(CodecJson.field(s, "horizontal_radius_factor")),
                CodecJson.floatField(s, "vertical_radius_default_factor"),
                CodecJson.floatField(s, "vertical_radius_center_factor"),
                FloatProvider.parse(CodecJson.field(s, "y_scale")));
        return new CanyonWorldCarver(probability, y, verticalRotation, shape);
    }
}
