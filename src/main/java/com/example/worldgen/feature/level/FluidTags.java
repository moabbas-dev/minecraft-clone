package com.example.worldgen.feature.level;

import com.example.worldgen.json.Json;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Fluid tags of the 26.3 data pack ({@code data/<namespace>/tags/fluid/<path>.json}; entries are fluid ids or {@code #tag}
 * references, resolved in file order as {@code TagLoader.tryBuildTag} does with a {@code LinkedHashSet}). The tag keys the
 * survival rules read ({@code FluidTags} fields, ids from {@code FluidTags.<clinit>}): {@code WATER} = {@code water},
 * {@code LAVA} = {@code lava}, {@code SUPPORTS_LILY_PAD} = {@code supports_lily_pad},
 * {@code SUPPORTS_SUGAR_CANE_ADJACENTLY} = {@code supports_sugar_cane_adjacently}.
 */
public final class FluidTags {

    public static final String WATER = "minecraft:water";
    public static final String LAVA = "minecraft:lava";
    public static final String SUPPORTS_LILY_PAD = "minecraft:supports_lily_pad";
    public static final String SUPPORTS_SUGAR_CANE_ADJACENTLY = "minecraft:supports_sugar_cane_adjacently";

    private final Path dataRoot;
    private final Map<String, Set<String>> resolved = new ConcurrentHashMap<>();

    public FluidTags(Path dataRoot) {
        this.dataRoot = dataRoot;
    }

    /** {@code fluidState.is(tag)} for a fluid type id ({@code minecraft:empty} is in no tag of the data). */
    public boolean is(String fluid, String tag) {
        return members(tag).contains(fluid);
    }

    public Set<String> members(String tag) {
        Set<String> s = resolved.get(tag);
        if (s == null) {
            s = Collections.unmodifiableSet(resolve(tag, new LinkedHashSet<>()));
            resolved.put(tag, s);
        }
        return s;
    }

    @SuppressWarnings("unchecked")
    private Set<String> resolve(String tag, Set<String> visiting) {
        if (!visiting.add(tag)) throw new IllegalStateException("tag cycle at " + tag);
        int colon = tag.indexOf(':');
        Path file = dataRoot.resolve(tag.substring(0, colon)).resolve("tags").resolve("fluid").resolve(tag.substring(colon + 1) + ".json");
        Object json;
        try {
            json = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalArgumentException("missing fluid tag " + tag + " (" + file + ")", e);
        }
        Set<String> out = new LinkedHashSet<>();
        for (Object v : (List<Object>) ((Map<String, Object>) json).get("values")) {
            String id = v instanceof Map<?, ?> m ? (String) m.get("id") : (String) v;
            if (id.startsWith("#")) out.addAll(resolve(normalize(id.substring(1)), visiting));
            else out.add(normalize(id));
        }
        visiting.remove(tag);
        return out;
    }

    private static String normalize(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }
}
