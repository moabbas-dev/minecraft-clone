package com.example.audio;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stable project-specific sound IDs ("dig.stone", "step.grass", "ambient.cave", ...) mapped to
 * their real asset files under {@code sounds/} (the user's own local copy from
 * {@code minecraft-sounds/}, gitignored the same way {@code textures/} already is - see
 * TextureAtlas's REAL_TEXTURE_PATHS for the identical established pattern this mirrors). These
 * IDs are internal to this project, not a claim about Minecraft's own internal sound-event names.
 *
 * Lazily decodes and caches each {@link SoundBuffer} on first use (never re-decodes the same file
 * on repeat playback - Phase 18's explicit "avoid decoding the same file on every playback").
 * A missing/corrupt file for one variation just drops that variation; a sound ID with zero
 * loadable variations is reported once (warn-level) and every future play() for it is a silent
 * no-op - never a crash, matching this project's established "missing asset -> safe fallback,
 * not a crash" convention (see Colormap/TextureAtlas).
 */
@Slf4j
public class SoundRegistry {
    private static final File SOUNDS_ROOT = new File("sounds");

    private final Map<String, SoundGroup> definitions = new LinkedHashMap<>();
    private final Map<String, SoundBuffer[]> loaded = new LinkedHashMap<>();
    private final java.util.Set<String> warnedMissing = new java.util.HashSet<>();

    public SoundRegistry() {
        register(new SoundGroup("dig.stone", files("dig", "stone", 4), AudioCategory.BLOCKS, 0.8f, 0.9f, 1.1f, true, 24f));
        register(new SoundGroup("dig.gravel", files("dig", "gravel", 4), AudioCategory.BLOCKS, 0.8f, 0.9f, 1.1f, true, 24f));
        register(new SoundGroup("dig.grass", files("dig", "grass", 4), AudioCategory.BLOCKS, 0.8f, 0.9f, 1.1f, true, 24f));
        register(new SoundGroup("dig.sand", files("dig", "sand", 4), AudioCategory.BLOCKS, 0.8f, 0.9f, 1.1f, true, 24f));
        register(new SoundGroup("dig.snow", files("dig", "snow", 4), AudioCategory.BLOCKS, 0.8f, 0.9f, 1.1f, true, 24f));
        register(new SoundGroup("dig.wood", files("dig", "wood", 4), AudioCategory.BLOCKS, 0.8f, 0.9f, 1.1f, true, 24f));
        register(new SoundGroup("dig.glass", new String[]{"random/glass1.ogg", "random/glass2.ogg", "random/glass3.ogg"},
                AudioCategory.BLOCKS, 0.8f, 0.95f, 1.05f, true, 24f));

        register(new SoundGroup("step.stone", files("step", "stone", 6), AudioCategory.PLAYER, 0.5f, 0.9f, 1.1f, true, 16f));
        register(new SoundGroup("step.gravel", files("step", "gravel", 4), AudioCategory.PLAYER, 0.5f, 0.9f, 1.1f, true, 16f));
        register(new SoundGroup("step.grass", files("step", "grass", 6), AudioCategory.PLAYER, 0.5f, 0.9f, 1.1f, true, 16f));
        register(new SoundGroup("step.sand", files("step", "sand", 5), AudioCategory.PLAYER, 0.5f, 0.9f, 1.1f, true, 16f));
        register(new SoundGroup("step.snow", files("step", "snow", 4), AudioCategory.PLAYER, 0.5f, 0.9f, 1.1f, true, 16f));
        register(new SoundGroup("step.wood", files("step", "wood", 6), AudioCategory.PLAYER, 0.5f, 0.9f, 1.1f, true, 16f));

        register(new SoundGroup("item.pickup", new String[]{"random/pop.ogg"}, AudioCategory.PLAYER, 0.35f, 0.9f, 1.5f, false, 0f));

        register(new SoundGroup("ambient.cave", files("ambient/cave", "cave", 19), AudioCategory.AMBIENT, 0.6f, 0.95f, 1.05f, true, 32f));
        register(new SoundGroup("ambient.rain", files("ambient/weather", "rain", 8), AudioCategory.WEATHER, 0.5f, 1f, 1f, false, 0f));
        register(new SoundGroup("weather.thunder", files("ambient/weather", "thunder", 3), AudioCategory.WEATHER, 0.9f, 0.95f, 1.05f, false, 0f));
    }

    private static String[] files(String dir, String prefix, int count) {
        String[] result = new String[count];
        for (int i = 0; i < count; i++) {
            result[i] = dir + "/" + prefix + (i + 1) + ".ogg";
        }
        return result;
    }

    private void register(SoundGroup group) {
        definitions.put(group.id(), group);
    }

    public SoundGroup definitionOf(String id) {
        return definitions.get(id);
    }

    /** Loads (once) and returns every successfully-decoded variation for this ID, or an empty
     *  array if none are available - never null, so callers never need a null check. */
    public SoundBuffer[] buffersFor(String id) {
        SoundBuffer[] cached = loaded.get(id);
        if (cached != null) return cached;

        SoundGroup def = definitions.get(id);
        if (def == null) {
            if (warnedMissing.add(id)) log.warn("Unknown sound ID requested: {}", id);
            return new SoundBuffer[0];
        }

        java.util.List<SoundBuffer> buffers = new java.util.ArrayList<>();
        for (String fileName : def.fileNames()) {
            SoundBuffer buf = SoundBuffer.load(new File(SOUNDS_ROOT, fileName));
            if (buf != null) buffers.add(buf);
        }
        if (buffers.isEmpty() && warnedMissing.add(id)) {
            log.warn("Sound '{}' has no loadable variations under {} - it will play silently", id, SOUNDS_ROOT);
        }
        SoundBuffer[] result = buffers.toArray(new SoundBuffer[0]);
        loaded.put(id, result);
        return result;
    }

    public void destroy() {
        for (SoundBuffer[] group : loaded.values()) {
            for (SoundBuffer b : group) b.destroy();
        }
        loaded.clear();
    }
}
