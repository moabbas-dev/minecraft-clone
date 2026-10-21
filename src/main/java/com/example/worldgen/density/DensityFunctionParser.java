package com.example.worldgen.density;

import com.example.worldgen.CubicSpline;
import com.example.worldgen.DimensionType;
import com.example.worldgen.density.generator.ConstantFunction;
import com.example.worldgen.density.generator.DistanceToPointFunction;
import com.example.worldgen.density.generator.EndIslandFunction;
import com.example.worldgen.density.generator.GradientFunction;
import com.example.worldgen.density.generator.NoiseFunction;
import com.example.worldgen.density.generator.ShiftNoiseFunction;
import com.example.worldgen.density.generator.SimpleDensityFunction;
import com.example.worldgen.density.op.BinaryFunction;
import com.example.worldgen.density.op.BlendDensityFunction;
import com.example.worldgen.density.op.CacheFunction;
import com.example.worldgen.density.op.ClampFunction;
import com.example.worldgen.density.op.FindTopSurfaceFunction;
import com.example.worldgen.density.op.InterpolatedFunction;
import com.example.worldgen.density.op.IntervalSelectFunction;
import com.example.worldgen.density.op.LerpFunction;
import com.example.worldgen.density.op.PowFunction;
import com.example.worldgen.density.op.RangeChoiceFunction;
import com.example.worldgen.density.op.RoundFunction;
import com.example.worldgen.density.op.SliceFunction;
import com.example.worldgen.density.op.SplineFunction;
import com.example.worldgen.density.op.UnaryFunction;
import com.example.worldgen.json.Json;
import com.example.worldgen.noise.NoiseParametersLoader;
import com.example.worldgen.noise.NormalNoise;
import com.example.worldgen.noise.BlendedNoise;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Decodes density functions from JSON the way the 26.3 codecs do ({@code DensityFunction.CODEC},
 * {@code DensityFunctions.DIRECT_CODEC} and each type's {@code MapCodec}, read from the bytecode):
 * <ul>
 *     <li>a function position accepts a string (a registry reference, wrapped in {@link DensityFunctions.HolderHolder}),
 *     a number (a constant, {@code NOISE_VALUE_CODEC} = float in [-1e6, 1e6]) or an object dispatched on
 *     {@code "type"}; a registry file itself is a number or an object;</li>
 *     <li>numbers are read from their literal ({@link Json.NumberLiteral}); {@code Codec.FLOAT}/{@code DOUBLE}/
 *     {@code INT} are {@code floatValue}/{@code doubleValue}/{@code intValue} of it;</li>
 *     <li>unknown fields are ignored; optional fields take vanilla's defaults; validations (clamp {@code max >= min},
 *     gradient {@code from != to}, interval-select threshold count and order, positive cell sizes, range codecs)
 *     reject the input like vanilla's {@code DataResult} errors.</li>
 * </ul>
 * The registered type ids are {@link #TYPES} (the order of {@code DensityFunctions.bootstrap}).
 */
public final class DensityFunctionParser {

    /** The 44 {@code minecraft:worldgen/density_function_type} ids of 26.3, in bootstrap order. */
    public static final List<String> TYPES;

    private final WorldgenRegistries registries;
    private final Map<String, BiFunction<DensityFunctionParser, Map<String, Object>, DensityFunction>> decoders;

    static {
        List<String> types = new ArrayList<>();
        types.add("constant");
        for (SimpleDensityFunction simple : SimpleDensityFunction.values()) {
            types.add(simple.id());
        }
        types.addAll(List.of("noise", "end_outer_islands", "distance_to_point", "gradient", "shift_a", "shift_b", "shift"));
        for (UnaryFunction.Type type : UnaryFunction.Type.values()) {
            types.add(type.id);
        }
        for (RoundFunction.Type type : RoundFunction.Type.values()) {
            types.add(type.id);
        }
        for (BinaryFunction.Type type : BinaryFunction.Type.values()) {
            types.add(type.id);
        }
        types.addAll(List.of("pow", "spline", "lerp", "clamp", "range_choice", "interval_select", "cache", "blend_density",
                "interpolated", "slice", "find_top_surface", "old_blended_noise"));
        TYPES = List.copyOf(types);
    }

    DensityFunctionParser(WorldgenRegistries registries) {
        this.registries = registries;
        Map<String, BiFunction<DensityFunctionParser, Map<String, Object>, DensityFunction>> d = new LinkedHashMap<>();
        d.put("constant", (p, m) -> new ConstantFunction(p.noiseValue(m, "value")));
        for (SimpleDensityFunction simple : SimpleDensityFunction.values()) {
            d.put(simple.id(), (p, m) -> simple);
        }
        d.put("noise", (p, m) -> new NoiseFunction(p.noiseHolder(m, "noise"), p.doubleField(m, "xz_scale"), p.doubleField(m, "y_scale"),
                p.optionalFunction(m, "shift_x"), p.optionalFunction(m, "shift_y"), p.optionalFunction(m, "shift_z")));
        d.put("end_outer_islands", (p, m) -> new EndIslandFunction());
        d.put("distance_to_point", (p, m) -> {
            int[] point = p.vec3i(m, "point");
            return new DistanceToPointFunction(point[0], point[1], point[2], p.distanceMetric(m, "metric"));
        });
        d.put("gradient", (p, m) -> {
            GradientFunction gradient = new GradientFunction(p.axis(m, "axis"), p.tiling(m), p.intField(m, "from_coordinate"),
                    p.intField(m, "to_coordinate"), p.noiseValue(m, "from_value"), p.noiseValue(m, "to_value"));
            if (gradient.fromCoordinate() == gradient.toCoordinate()) {
                throw new IllegalArgumentException("from_coordinate cannot be equal to to_coordinate");
            }
            return gradient;
        });
        d.put("shift_a", (p, m) -> new ShiftNoiseFunction.ShiftA(p.noiseHolder(m, "noise")));
        d.put("shift_b", (p, m) -> new ShiftNoiseFunction.ShiftB(p.noiseHolder(m, "noise")));
        d.put("shift", (p, m) -> new ShiftNoiseFunction.Shift(p.noiseHolder(m, "noise")));
        for (UnaryFunction.Type type : UnaryFunction.Type.values()) {
            d.put(type.id, (p, m) -> new UnaryFunction(type, p.function(m, "input")));
        }
        for (RoundFunction.Type type : RoundFunction.Type.values()) {
            d.put(type.id, (p, m) -> new RoundFunction(type, p.function(m, "input"),
                    m.containsKey("multiple") ? p.function(m, "multiple") : DensityFunctions.constant(1.0F)));
        }
        for (BinaryFunction.Type type : BinaryFunction.Type.values()) {
            d.put(type.id, (p, m) -> new BinaryFunction(type, p.function(m, "left"), p.function(m, "right")));
        }
        d.put("pow", (p, m) -> new PowFunction(p.function(m, "base"), p.function(m, "exponent")));
        d.put("spline", (p, m) -> new SplineFunction(p.spline(p.required(m, "spline"))));
        d.put("lerp", (p, m) -> new LerpFunction(p.function(m, "alpha"), p.function(m, "first"), p.function(m, "second")));
        d.put("clamp", (p, m) -> {
            ClampFunction clamp = new ClampFunction(p.function(m, "input"), p.noiseValue(m, "min"), p.noiseValue(m, "max"));
            if (clamp.max() < clamp.min()) {
                throw new IllegalArgumentException("min (" + clamp.min() + ") must be less than or equal to max (" + clamp.max() + ")");
            }
            return clamp;
        });
        d.put("range_choice", (p, m) -> new RangeChoiceFunction(p.function(m, "input"), p.noiseValue(m, "min_inclusive"),
                p.noiseValue(m, "max_exclusive"), p.function(m, "when_in_range"), p.function(m, "when_out_of_range")));
        d.put("interval_select", (p, m) -> p.intervalSelect(m));
        d.put("cache", (p, m) -> new CacheFunction(p.function(m, "input")));
        d.put("blend_density", (p, m) -> new BlendDensityFunction(p.function(m, "input")));
        d.put("interpolated", (p, m) -> new InterpolatedFunction(p.function(m, "input"), p.positiveInt(m, "cell_size_xz"),
                p.positiveInt(m, "cell_size_y")));
        d.put("slice", (p, m) -> new SliceFunction(p.axis(m, "axis"), p.intField(m, "coordinate"), p.function(m, "input")));
        d.put("find_top_surface", (p, m) -> new FindTopSurfaceFunction(p.function(m, "density"), p.function(m, "upper_bound"),
                p.intInRange(m, "lower_bound", DimensionType.MIN_Y * 2, DimensionType.MAX_Y * 2), p.positiveInt(m, "cell_height")));
        d.put("old_blended_noise", (p, m) -> new BlendedNoise(p.doubleInRange(m, "xz_scale", 0.001, 1000.0),
                p.doubleInRange(m, "y_scale", 0.001, 1000.0), p.doubleInRange(m, "xz_factor", 0.001, 1000.0),
                p.doubleInRange(m, "y_factor", 0.001, 1000.0), p.doubleInRange(m, "smear_scale_multiplier", 1.0, 8.0)));
        if (!d.keySet().equals(new java.util.LinkedHashSet<>(TYPES))) {
            throw new IllegalStateException("Decoder table does not match the type list");
        }
        this.decoders = d;
    }

    /** {@code DensityFunction.CODEC}: a reference id, a constant, or an inline typed object. */
    public DensityFunction parse(Object json) {
        if (json instanceof String id) {
            return new DensityFunctions.HolderHolder(registries.densityFunction(id));
        }
        return parseDirect(json);
    }

    /** {@code DensityFunctions.DIRECT_CODEC}: a constant or an inline typed object (the form of a registry file). */
    public DensityFunction parseDirect(Object json) {
        if (json instanceof Number number) {
            return DensityFunctions.constant(noiseValue(number, "constant"));
        }
        if (!(json instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Expected a density function, got " + json);
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> m = (Map<String, Object>) map;
        Object type = m.get("type");
        if (!(type instanceof String typeId)) {
            throw new IllegalArgumentException("Missing density function type in " + m.keySet());
        }
        String key = WorldgenRegistries.normalize(typeId);
        if (!key.startsWith("minecraft:")) {
            throw new IllegalArgumentException("Unknown density function type " + typeId);
        }
        BiFunction<DensityFunctionParser, Map<String, Object>, DensityFunction> decoder = decoders.get(key.substring("minecraft:".length()));
        if (decoder == null) {
            throw new IllegalArgumentException("Unknown density function type " + typeId);
        }
        return decoder.apply(this, m);
    }

    /** {@code NormalNoise.CODEC}: a noise registry id, or an inline noise definition (a {@link Holder.Direct}). */
    public Holder<NormalNoise> parseNoise(Object json) {
        if (json instanceof String id) {
            return registries.noise(id);
        }
        return Holder.direct(NormalNoise.of(NoiseParametersLoader.decode(toPlainNumbers(json))));
    }

    /**
     * {@code CubicSpline.codec(SplineFunction.Coordinate.CODEC)}: a bare float ({@code Codec.FLOAT}, no range) is a
     * constant; otherwise {@code coordinate} and a non-empty {@code points} list of {@code location},
     * {@code value} (recursive) and {@code derivative}.
     */
    public CubicSpline<SplineFunction.Coordinate> spline(Object json) {
        if (json instanceof Number n) {
            return CubicSpline.constant(n.floatValue());
        }
        Map<String, Object> m = object(json, "spline");
        SplineFunction.Coordinate coordinate = new SplineFunction.Coordinate(function(m, "coordinate"));
        List<?> points = list(required(m, "points"), "points");
        if (points.isEmpty()) {
            throw new IllegalArgumentException("List must have contents");
        }
        float[] locations = new float[points.size()];
        List<CubicSpline<SplineFunction.Coordinate>> values = new ArrayList<>(points.size());
        float[] derivatives = new float[points.size()];
        for (int i = 0; i < points.size(); i++) {
            Map<String, Object> point = object(points.get(i), "point");
            locations[i] = number(required(point, "location"), "location").floatValue();
            values.add(spline(required(point, "value")));
            derivatives[i] = number(required(point, "derivative"), "derivative").floatValue();
        }
        return new CubicSpline.Multipoint<>(coordinate, locations, List.copyOf(values), derivatives);
    }

    private DensityFunction intervalSelect(Map<String, Object> m) {
        DensityFunction input = function(m, "input");
        List<?> thresholdJson = list(required(m, "thresholds"), "thresholds");
        List<Float> thresholds = new ArrayList<>(thresholdJson.size());
        for (Object o : thresholdJson) {
            thresholds.add(noiseValue(number(o, "thresholds"), "thresholds"));
        }
        List<?> functionJson = list(required(m, "functions"), "functions");
        if (functionJson.size() < 2) {
            throw new IllegalArgumentException("List is too short: " + functionJson.size() + ", expected range [2-2147483647]");
        }
        List<DensityFunction> functions = new ArrayList<>(functionJson.size());
        for (Object o : functionJson) {
            functions.add(parse(o));
        }
        if (thresholds.size() != functions.size() - 1) {
            throw new IllegalArgumentException("Expected " + (functions.size() - 1) + " thresholds for " + functions.size()
                    + " functions, but got " + thresholds.size());
        }
        for (int i = 1; i < thresholds.size(); i++) {
            if (Float.compare(thresholds.get(i - 1), thresholds.get(i)) > 0) {
                throw new IllegalArgumentException("Threshold values must be ordered from smallest to largest");
            }
        }
        return new IntervalSelectFunction(input, thresholds, functions);
    }

    DensityFunction function(Map<String, Object> m, String field) {
        return parse(required(m, field));
    }

    private DensityFunction optionalFunction(Map<String, Object> m, String field) {
        return m.containsKey(field) ? parse(m.get(field)) : DensityFunctions.zero();
    }

    private Holder<NormalNoise> noiseHolder(Map<String, Object> m, String field) {
        return parseNoise(required(m, field));
    }

    private float noiseValue(Map<String, Object> m, String field) {
        return noiseValue(number(required(m, field), field), field);
    }

    private static float noiseValue(Number n, String field) {
        float v = n.floatValue();
        if (!(v >= -DensityFunctions.MAX_REASONABLE_NOISE_VALUE && v <= DensityFunctions.MAX_REASONABLE_NOISE_VALUE)) {
            throw new IllegalArgumentException(field + ": value " + v + " outside of range [-1000000.0:1000000.0]");
        }
        return v;
    }

    private double doubleField(Map<String, Object> m, String field) {
        return number(required(m, field), field).doubleValue();
    }

    private double doubleInRange(Map<String, Object> m, String field, double min, double max) {
        double v = doubleField(m, field);
        if (!(v >= min && v <= max)) {
            throw new IllegalArgumentException(field + ": value " + v + " outside of range [" + min + ":" + max + "]");
        }
        return v;
    }

    private int intField(Map<String, Object> m, String field) {
        return number(required(m, field), field).intValue();
    }

    private int intInRange(Map<String, Object> m, String field, int min, int max) {
        int v = intField(m, field);
        if (v < min || v > max) {
            throw new IllegalArgumentException(field + ": value " + v + " outside of range [" + min + ":" + max + "]");
        }
        return v;
    }

    private int positiveInt(Map<String, Object> m, String field) {
        int v = intField(m, field);
        if (v < 1) {
            throw new IllegalArgumentException("Value must be positive: " + v);
        }
        return v;
    }

    private Axis axis(Map<String, Object> m, String field) {
        Axis axis = Axis.byName(string(required(m, field), field));
        if (axis == null) {
            throw new IllegalArgumentException("Unknown axis " + m.get(field));
        }
        return axis;
    }

    private TilingMode tiling(Map<String, Object> m) {
        if (!m.containsKey("tiling")) {
            return TilingMode.CLAMP_TO_EDGE;
        }
        TilingMode mode = TilingMode.byName(string(m.get("tiling"), "tiling"));
        if (mode == null) {
            throw new IllegalArgumentException("Unknown tiling " + m.get("tiling"));
        }
        return mode;
    }

    private DistanceMetric distanceMetric(Map<String, Object> m, String field) {
        DistanceMetric metric = DistanceMetric.byName(string(required(m, field), field));
        if (metric == null) {
            throw new IllegalArgumentException("Unknown metric " + m.get(field));
        }
        return metric;
    }

    /** {@code Vec3i.CODEC}: an int stream of exactly three values. */
    private int[] vec3i(Map<String, Object> m, String field) {
        List<?> values = list(required(m, field), field);
        if (values.size() != 3) {
            throw new IllegalArgumentException("Input is not a list of 3 elements");
        }
        int[] out = new int[3];
        for (int i = 0; i < 3; i++) {
            out[i] = number(values.get(i), field).intValue();
        }
        return out;
    }

    static Object required(Map<String, Object> m, String field) {
        Object value = m.get(field);
        if (value == null) {
            throw new IllegalArgumentException("No key " + field + " in " + m.keySet());
        }
        return value;
    }

    static Number number(Object value, String field) {
        if (!(value instanceof Number n)) {
            throw new IllegalArgumentException(field + ": not a number: " + value);
        }
        return n;
    }

    static String string(Object value, String field) {
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException(field + ": not a string: " + value);
        }
        return s;
    }

    static List<?> list(Object value, String field) {
        if (!(value instanceof List<?> l)) {
            throw new IllegalArgumentException(field + ": not a list: " + value);
        }
        return l;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> object(Object value, String field) {
        if (!(value instanceof Map<?, ?> m)) {
            throw new IllegalArgumentException(field + ": not an object: " + value);
        }
        return (Map<String, Object>) m;
    }

    /** Converts {@link Json.NumberLiteral}s to the {@code Long}/{@code Double} form {@link Json#parse} produces. */
    static Object toPlainNumbers(Object json) {
        if (json instanceof Json.NumberLiteral literal) {
            return Json.parse(literal.literal());
        }
        if (json instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                out.put((String) e.getKey(), toPlainNumbers(e.getValue()));
            }
            return Collections.unmodifiableMap(out);
        }
        if (json instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object o : list) {
                out.add(toPlainNumbers(o));
            }
            return Collections.unmodifiableList(out);
        }
        return json;
    }
}
