package com.example.worldgen.terrain;

import com.example.worldgen.json.Json;
import com.example.worldgen.level.StateRef;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Block tags read from the 26.3 data pack ({@code data/<namespace>/tags/block/<path>.json}): {@code values} entries
 * are block ids, {@code #tag} references (resolved recursively) or {@code {"id": ..., "required": ...}} objects.
 * Vanilla's {@code state.is(TagKey)} is {@link #is(StateRef, String)}. The tag ids the worldgen code uses are the
 * constants below (vanilla {@code BlockTags} fields of the same names).
 */
public final class BlockTags {

    /** {@code BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP} ({@code Heightmap.MATERIAL_MOTION_BLOCKING}). */
    public static final String BLOCKS_MOTION_IN_HEIGHTMAP = "minecraft:blocks_motion_in_heightmap";
    /** {@code BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP_NO_LEAVES} (heightmap {@code MOTION_BLOCKING_NO_LEAVES}). */
    public static final String BLOCKS_MOTION_IN_HEIGHTMAP_NO_LEAVES = "minecraft:blocks_motion_in_heightmap_no_leaves";
    /** {@code BlockTags.UNCARVABLE} ({@code applyCarvingMask}). */
    public static final String UNCARVABLE = "minecraft:uncarvable";

    private final Path dataRoot;
    private final Map<String, Set<String>> resolved = new ConcurrentHashMap<>();

    private BlockTags(Path dataRoot) {
        this.dataRoot = dataRoot;
    }

    /** @param dataRoot the folder containing the namespaces ({@code data/}) */
    public static BlockTags load(Path dataRoot) {
        return new BlockTags(dataRoot);
    }

    /** The data root the tags are read from (other tag registries, e.g. fluid tags, live next to it). */
    public Path dataRoot() {
        return dataRoot;
    }

    /** {@code state.is(tag)}. */
    public boolean is(StateRef state, String tag) {
        return members(tag).contains(state.id());
    }

    /** All block ids of a tag (an absent tag file is an error: vanilla tag keys the code uses always exist). */
    public Set<String> members(String tag) {
        Set<String> s = resolved.get(tag);
        if (s == null) {
            s = Collections.unmodifiableSet(resolve(tag, new HashSet<>()));
            resolved.put(tag, s);
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    private Set<String> resolve(String tag, Set<String> visiting) {
        if (!visiting.add(tag)) throw new IllegalStateException("tag cycle at " + tag);
        int colon = tag.indexOf(':');
        Path file = dataRoot.resolve(tag.substring(0, colon)).resolve("tags").resolve("block").resolve(tag.substring(colon + 1) + ".json");
        Object json;
        try {
            json = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalArgumentException("missing block tag " + tag + " (" + file + ")", e);
        }
        Set<String> out = new HashSet<>();
        for (Object v : (List<Object>) ((Map<String, Object>) json).get("values")) {
            String id = v instanceof Map<?, ?> m ? (String) m.get("id") : (String) v;
            if (id.startsWith("#")) {
                out.addAll(resolve(normalize(id.substring(1)), visiting));
            } else {
                out.add(normalize(id));
            }
        }
        visiting.remove(tag);
        return out;
    }

    private static String normalize(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }
}
