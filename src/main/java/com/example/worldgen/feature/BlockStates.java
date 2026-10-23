package com.example.worldgen.feature;

import com.example.worldgen.level.StateRef;
import com.example.worldgen.provider.CodecJson;
import com.example.worldgen.terrain.BlockProperties;

import java.util.Map;
import java.util.TreeMap;

/**
 * The 26.3 block-state codecs of the feature data ({@code BlockState.CODEC} and {@code BlockState.FULL_CODEC}, bytecode):
 * {@code CODEC} is {@code either(block id -> defaultBlockState(), FULL_CODEC)}; {@code FULL_CODEC} dispatches on
 * {@code "id"} and reads the optional {@code "properties"} map over the default state (a block without properties ignores
 * it). The block must be registered in {@link BlockProperties} (its default state is known); an unknown property name is
 * a decode error.
 */
public final class BlockStates {

    private BlockStates() {
    }

    /** {@code BlockState.CODEC}. */
    public static StateRef decode(Object json) {
        if (json instanceof String s) return BlockProperties.defaultState(normalize(s));
        return decodeFull(json);
    }

    /** {@code BlockState.FULL_CODEC} ({@code {"id": ..., "properties": {...}}}). */
    public static StateRef decodeFull(Object json) {
        Map<String, Object> m = CodecJson.object(json, "block state");
        Object id = CodecJson.field(m, "id");
        if (!(id instanceof String s)) throw new CodecJson.CodecException("Not a block id: " + id);
        StateRef def = BlockProperties.defaultState(normalize(s));
        Object p = m.get("properties");
        if (p == null || def.properties().isEmpty()) return def;
        Map<String, String> props = new TreeMap<>(def.properties());
        for (Map.Entry<String, Object> e : CodecJson.object(p, "block state properties").entrySet()) {
            if (!props.containsKey(e.getKey())) {
                throw new CodecJson.CodecException("unknown property " + e.getKey() + " of " + def.id());
            }
            props.put(e.getKey(), String.valueOf(e.getValue()));
        }
        return new StateRef(def.id(), props);
    }

    static String normalize(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }
}
