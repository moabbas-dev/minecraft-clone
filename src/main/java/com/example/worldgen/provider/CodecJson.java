package com.example.worldgen.provider;

import java.util.List;
import java.util.Map;

/**
 * Small decoding helpers that mirror the DataFixerUpper primitives the 26.3 codecs use for the value-provider and
 * carver JSON: {@code Codec.INT} / {@code Codec.FLOAT} ({@code Number.intValue()} / {@code floatValue()} of the JSON
 * number, so pass trees from {@link com.example.worldgen.json.Json#parseWithNumberLiterals(String)}),
 * {@code Codec.BOOL}, {@code fieldOf} (required key), {@code optionalFieldOf} (absent key gives the default, a present
 * but invalid value is an error), {@code intRange}/{@code floatRange} (inclusive bounds) and the registry
 * {@code byNameCodec} of a {@code "type"} key ({@code minecraft:} namespace optional).
 * <p>
 * DataFixerUpper is a separate library (not in the client jar); only what the data needs is reproduced, and any
 * decoding failure is reported with a {@link CodecException}.
 */
public final class CodecJson {

    private CodecJson() {
    }

    /** A decoding failure (the {@code DataResult.error} of the codec). */
    public static final class CodecException extends IllegalArgumentException {
        public CodecException(String message) {
            super(message);
        }
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object json, String what) {
        if (json instanceof Map<?, ?> m) {
            return (Map<String, Object>) m;
        }
        throw new CodecException("Not a JSON object for " + what + ": " + json);
    }

    @SuppressWarnings("unchecked")
    public static List<Object> list(Object json, String what) {
        if (json instanceof List<?> l) {
            return (List<Object>) l;
        }
        throw new CodecException("Not a JSON list for " + what + ": " + json);
    }

    /** {@code fieldOf(name)}: the key must be present. */
    public static Object field(Map<String, Object> map, String name) {
        Object v = map.get(name);
        if (v == null) {
            throw new CodecException("No key " + name + " in " + map);
        }
        return v;
    }

    public static boolean isNumber(Object json) {
        return json instanceof Number;
    }

    /** {@code Codec.INT}: {@code Number.intValue()}. */
    public static int intValue(Object json, String what) {
        if (json instanceof Number n) {
            return n.intValue();
        }
        throw new CodecException("Not a number for " + what + ": " + json);
    }

    /** {@code Codec.FLOAT}: {@code Number.floatValue()}. */
    public static float floatValue(Object json, String what) {
        if (json instanceof Number n) {
            return n.floatValue();
        }
        throw new CodecException("Not a number for " + what + ": " + json);
    }

    /** {@code Codec.BOOL}. */
    public static boolean boolValue(Object json, String what) {
        if (json instanceof Boolean b) {
            return b;
        }
        throw new CodecException("Not a boolean for " + what + ": " + json);
    }

    public static int intField(Map<String, Object> map, String name) {
        return intValue(field(map, name), name);
    }

    public static float floatField(Map<String, Object> map, String name) {
        return floatValue(field(map, name), name);
    }

    /** {@code Codec.intRange(min, max)}: inclusive. */
    public static int intInRange(Object json, int min, int max, String what) {
        int v = intValue(json, what);
        if (v < min || v > max) {
            throw new CodecException("Value " + v + " outside of range [" + min + ":" + max + "] for " + what);
        }
        return v;
    }

    /** {@code Codec.floatRange(min, max)}: inclusive. */
    public static float floatInRange(Object json, float min, float max, String what) {
        float v = floatValue(json, what);
        if (!(v >= min && v <= max)) {
            throw new CodecException("Value " + v + " outside of range [" + min + ":" + max + "] for " + what);
        }
        return v;
    }

    /** The registry id of the {@code "type"} key, without the {@code minecraft:} namespace. */
    public static String type(Map<String, Object> map) {
        Object t = field(map, "type");
        if (!(t instanceof String s)) {
            throw new CodecException("type is not a string: " + t);
        }
        if (s.startsWith("minecraft:")) {
            return s.substring("minecraft:".length());
        }
        if (s.indexOf(':') >= 0) {
            throw new CodecException("Unknown registry key: " + s);
        }
        return s;
    }
}
