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
 * not a crash" convention (see TextureAtlas).
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

        // Mob vocalizations/steps (research: docs/research/mob-skins-and-sounds.md). Cow volume 0.4 and the
        // chicken's 0.8 come straight from the vanilla sound definitions; steps are the quiet 0.15-ish layer.
        mobSounds(AudioCategory.NEUTRAL, "pig", "mob/pig", "say", 3, "say", 3, "death", 1, "step", 5, 0.9f, 0.2f);
        mobSounds(AudioCategory.NEUTRAL, "cow", "mob/cow", "say", 4, "hurt", 3, "hurt", 3, "step", 4, 0.5f, 0.2f);
        mobSounds(AudioCategory.NEUTRAL, "chicken", "mob/chicken", "say", 3, "hurt", 2, "hurt", 2, "step", 2, 0.8f, 0.1f);
        mobSounds(AudioCategory.HOSTILE, "zombie", "mob/zombie", "say", 3, "hurt", 2, "death", 1, "step", 5, 0.9f, 0.2f);
        mobSounds(AudioCategory.HOSTILE, "skeleton", "mob/skeleton", "say", 3, "hurt", 4, "death", 1, "step", 4, 0.9f, 0.2f);
        mobSounds(AudioCategory.HOSTILE, "spider", "mob/spider", "say", 4, "say", 4, "death", 1, "step", 4, 0.9f, 0.2f);
        // Creepers hiss-hurt with their "say" files and have no footstep sound of their own: borrow the grass steps.
        register(new SoundGroup("mob.creeper.say", files("mob/creeper", "say", 4), AudioCategory.HOSTILE, 0.9f, 0.8f, 1.2f, true, 24f));
        register(new SoundGroup("mob.creeper.hurt", files("mob/creeper", "say", 4), AudioCategory.HOSTILE, 0.9f, 0.8f, 1.2f, true, 24f));
        register(new SoundGroup("mob.creeper.death", new String[]{"mob/creeper/death.ogg"}, AudioCategory.HOSTILE, 0.9f, 0.8f, 1.2f, true, 24f));
        register(new SoundGroup("mob.creeper.step", files("step", "grass", 6), AudioCategory.HOSTILE, 0.2f, 0.95f, 1.05f, true, 16f));
        register(new SoundGroup("fuse", new String[]{"random/fuse.ogg"}, AudioCategory.HOSTILE, 1f, 1f, 1f, true, 32f));
        register(new SoundGroup("explode", files("random", "explode", 4), AudioCategory.HOSTILE, 1f, 0.9f, 1.1f, true, 64f));
        register(new SoundGroup("arrow.shoot", new String[]{"random/bow.ogg"}, AudioCategory.HOSTILE, 0.8f, 0.9f, 1.1f, true, 32f));
        register(new SoundGroup("arrow.hit", files("random", "bowhit", 4), AudioCategory.HOSTILE, 0.7f, 0.9f, 1.1f, true, 32f));
        register(new SoundGroup("mob.chicken.plop", new String[]{"mob/chicken/plop.ogg"}, AudioCategory.NEUTRAL, 0.9f, 0.95f, 1.05f, true, 16f));

        register(new SoundGroup("attack.strong", files("entity/player/attack", "strong", 6), AudioCategory.PLAYER, 0.65f, 1f, 1f, false, 0f));
        register(new SoundGroup("attack.weak", files("entity/player/attack", "weak", 4), AudioCategory.PLAYER, 0.7f, 1f, 1f, false, 0f));
        register(new SoundGroup("attack.crit", files("entity/player/attack", "crit", 3), AudioCategory.PLAYER, 0.7f, 1f, 1f, false, 0f));
        register(new SoundGroup("attack.sweep", files("entity/player/attack", "sweep", 7), AudioCategory.PLAYER, 0.7f, 1f, 1f, false, 0f));
        register(new SoundGroup("attack.knockback", files("entity/player/attack", "knockback", 4), AudioCategory.PLAYER, 0.7f, 1f, 1f, false, 0f));
        register(new SoundGroup("player.hurt", files("damage", "hit", 3), AudioCategory.PLAYER, 1f, 1f, 1f, false, 0f));
        for (String m : new String[]{"generic", "leather", "iron", "gold", "diamond"}) {
            register(new SoundGroup("armor.equip." + m, files("item/armor", "equip_" + m, 6), AudioCategory.PLAYER, 1f, 1f, 1f, false, 0f));
        }
        register(new SoundGroup("item.break", new String[]{"random/break.ogg"}, AudioCategory.PLAYER, 1f, 1f, 1f, false, 0f));

        register(new SoundGroup("xp.pickup", new String[]{"random/orb.ogg"}, AudioCategory.PLAYER, 0.5f, 0.9f, 1.4f, false, 0f));
        register(new SoundGroup("levelup", new String[]{"random/levelup.ogg"}, AudioCategory.PLAYER, 0.75f, 1f, 1f, false, 0f));
        register(new SoundGroup("eat", files("random", "eat", 3), AudioCategory.PLAYER, 0.6f, 0.9f, 1.1f, false, 0f));
        register(new SoundGroup("burp", new String[]{"random/burp.ogg"}, AudioCategory.PLAYER, 0.5f, 0.9f, 1.1f, false, 0f));

        register(new SoundGroup("item.pickup", new String[]{"random/pop.ogg"}, AudioCategory.PLAYER, 0.35f, 0.9f, 1.5f, false, 0f));

        register(new SoundGroup("ambient.cave", files("ambient/cave", "cave", 19), AudioCategory.AMBIENT, 0.6f, 0.95f, 1.05f, true, 32f));
        register(new SoundGroup("ambient.rain", files("ambient/weather", "rain", 8), AudioCategory.WEATHER, 0.5f, 1f, 1f, false, 0f));
        register(new SoundGroup("weather.thunder", files("ambient/weather", "thunder", 3), AudioCategory.WEATHER, 0.9f, 0.95f, 1.05f, false, 0f));
    }

    /** Registers mob.&lt;name&gt;.say/hurt/death/step; the numbers are the file counts for each event. */
    private void mobSounds(AudioCategory cat, String name, String dir, String sayPrefix, int sayCount, String hurtPrefix, int hurtCount,
                           String deathPrefix, int deathCount, String stepPrefix, int stepCount, float vocalVolume, float stepVolume) {
        register(new SoundGroup("mob." + name + ".say", files(dir, sayPrefix, sayCount), cat, vocalVolume, 0.8f, 1.2f, true, 24f));
        register(new SoundGroup("mob." + name + ".hurt", files(dir, hurtPrefix, hurtCount), cat, vocalVolume, 0.8f, 1.2f, true, 24f));
        register(new SoundGroup("mob." + name + ".death", deathCount == 1 && deathPrefix.equals("death")
                ? new String[]{dir + "/death.ogg"} : files(dir, deathPrefix, deathCount), cat, vocalVolume, 0.8f, 1.2f, true, 24f));
        register(new SoundGroup("mob." + name + ".step", files(dir, stepPrefix, stepCount), cat, stepVolume, 0.95f, 1.05f, true, 16f));
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
