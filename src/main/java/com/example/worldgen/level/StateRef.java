package com.example.worldgen.level;

import java.util.Map;
import java.util.TreeMap;

/**
 * A vanilla block state named the way the data files and the 26.3 code name it: a block id and its properties
 * (for example {@code minecraft:water[level=0]}). The worldgen stages work with these references so that their results can be compared
 * with the real game state for state; only the engine boundary turns a reference into a {@code BlockType}
 * (see {@code world/VanillaBlocks}).
 *
 * <p>Properties are kept sorted by name, so equal states always have equal canonical strings.
 */
public record StateRef(String id, Map<String, String> properties) {
    public static final StateRef AIR = of("minecraft:air");

    public StateRef {
        properties = Map.copyOf(new TreeMap<>(properties));
    }

    public static StateRef of(String id) {
        return new StateRef(id, Map.of());
    }

    public static StateRef of(String id, String... keyValues) {
        if (keyValues.length % 2 != 0) throw new IllegalArgumentException("properties come as key, value pairs");
        Map<String, String> p = new TreeMap<>();
        for (int i = 0; i < keyValues.length; i += 2) p.put(keyValues[i], keyValues[i + 1]);
        return new StateRef(id, p);
    }

    /** Parses {@code namespace:name} or {@code namespace:name[key=value,key=value]}. */
    public static StateRef parse(String text) {
        int open = text.indexOf('[');
        if (open < 0) return of(text);
        if (!text.endsWith("]")) throw new IllegalArgumentException("bad block state: " + text);
        Map<String, String> p = new TreeMap<>();
        String body = text.substring(open + 1, text.length() - 1);
        if (!body.isEmpty()) {
            for (String pair : body.split(",")) {
                int eq = pair.indexOf('=');
                if (eq <= 0) throw new IllegalArgumentException("bad block state property: " + text);
                p.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return new StateRef(text.substring(0, open), p);
    }

    public boolean is(String blockId) {
        return id.equals(blockId);
    }

    public boolean isAir() {
        return id.equals("minecraft:air") || id.equals("minecraft:cave_air") || id.equals("minecraft:void_air");
    }

    @Override
    public String toString() {
        if (properties.isEmpty()) return id;
        StringBuilder sb = new StringBuilder(id).append('[');
        boolean first = true;
        for (Map.Entry<String, String> e : properties.entrySet()) {
            if (!first) sb.append(',');
            sb.append(e.getKey()).append('=').append(e.getValue());
            first = false;
        }
        return sb.append(']').toString();
    }
}
