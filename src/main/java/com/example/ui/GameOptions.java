package com.example.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * User settings edited by the Options screens and persisted to {@code options.txt} in vanilla's own format:
 * one {@code key:value} per line with vanilla's key names (research: docs/research/options-and-screens.md).
 * FOV is stored as {@code (degrees - 70) / 40} like vanilla. A legacy {@code key=value} file from earlier
 * versions of this project is still read (and rewritten in the vanilla format on the next save).
 * Pure data + clamping, so it is unit-testable; {@code Game} applies the values to the engine.
 */
public final class GameOptions {
    public static final File DEFAULT_FILE = new File("options.txt");
    /** Vanilla's "Unlimited" frame-rate sentinel. */
    public static final int UNLIMITED_FPS = 260;
    /** Highest render distance the slider offers (vanilla also caps at 16 on weaker systems). */
    public static final int MAX_RENDER_DISTANCE = 16;

    /** 0 = Auto, 1..4 manual cap. */
    public int guiScale = 0;
    public int fov = 70;
    public int renderDistance = 6;
    public boolean viewBobbing = true;
    public boolean vsync = true;
    public boolean fullscreen = false;
    /** 0..1, default 0.5 = the project's original feel. */
    public float mouseSensitivity = 0.5f;
    public boolean invertMouse = false;
    /** Brightness: 0 "Moody" .. 1 "Bright", vanilla default 0.5. */
    public float gamma = 0.5f;
    /** 10..250 fps, {@link #UNLIMITED_FPS} = unlimited; vanilla default 120. */
    public int maxFps = 120;
    /** 0 off, 1 crosshair, 2 hotbar. */
    public int attackIndicator = 1;
    public boolean toggleSprint = false;
    public boolean toggleCrouch = false;
    public boolean autoJump = false;
    /** 0..1: how much the sprint speed change widens the FOV (vanilla "FOV Effects"). */
    public float fovEffectScale = 1f;

    public float masterVolume = 1f;
    public float musicVolume = 1f;
    public float recordVolume = 1f;
    public float weatherVolume = 1f;
    public float blocksVolume = 1f;
    public float hostileVolume = 1f;
    public float neutralVolume = 1f;
    public float playersVolume = 1f;
    public float ambientVolume = 1f;
    public float voiceVolume = 1f;

    public final KeyBindings keys = new KeyBindings();

    public void clamp() {
        guiScale = Math.max(0, Math.min(4, guiScale));
        fov = Math.max(30, Math.min(110, fov));
        renderDistance = Math.max(2, Math.min(MAX_RENDER_DISTANCE, renderDistance));
        mouseSensitivity = c01(mouseSensitivity);
        gamma = c01(gamma);
        maxFps = Math.max(10, Math.min(UNLIMITED_FPS, maxFps));
        attackIndicator = Math.max(0, Math.min(2, attackIndicator));
        fovEffectScale = c01(fovEffectScale);
        masterVolume = c01(masterVolume);
        musicVolume = c01(musicVolume);
        recordVolume = c01(recordVolume);
        weatherVolume = c01(weatherVolume);
        blocksVolume = c01(blocksVolume);
        hostileVolume = c01(hostileVolume);
        neutralVolume = c01(neutralVolume);
        playersVolume = c01(playersVolume);
        ambientVolume = c01(ambientVolume);
        voiceVolume = c01(voiceVolume);
    }

    private static float c01(float v) {
        return Float.isNaN(v) ? 0f : Math.max(0f, Math.min(1f, v));
    }

    /** Vanilla's sensitivity curve: slider 0.5 maps to exactly 1x the base mouse speed. */
    public float mouseSpeedMultiplier() {
        float f = mouseSensitivity * 0.6f + 0.2f;
        return f * f * f * 8f;
    }

    /** FOV multiplier for a sprint-speed change: vanilla blends 1.15x by the FOV Effects slider. */
    public float sprintFovFactor(boolean sprinting) {
        return sprinting ? 1f + 0.15f * fovEffectScale : 1f;
    }

    /** Vanilla's Max Framerate label. */
    public static String fpsLabel(int fps) {
        return fps >= UNLIMITED_FPS ? "Unlimited" : fps + " fps";
    }

    /** Vanilla's Brightness label: Moody at 0, Bright at 1, otherwise a percentage. */
    public static String gammaLabel(float gamma) {
        if (gamma <= 0f) return "Moody";
        if (gamma >= 1f) return "Bright";
        return Math.round(gamma * 100) + "%";
    }

    // ---- persistence ----

    private Map<String, String> toMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("version", "1");
        m.put("autoJump", Boolean.toString(autoJump));
        m.put("enableVsync", Boolean.toString(vsync));
        m.put("invertYMouse", Boolean.toString(invertMouse));
        m.put("fullscreen", Boolean.toString(fullscreen));
        m.put("bobView", Boolean.toString(viewBobbing));
        m.put("toggleCrouch", Boolean.toString(toggleCrouch));
        m.put("toggleSprint", Boolean.toString(toggleSprint));
        m.put("mouseSensitivity", Float.toString(mouseSensitivity));
        m.put("fov", Float.toString((fov - 70) / 40f));
        m.put("fovEffectScale", Float.toString(fovEffectScale));
        m.put("gamma", Float.toString(gamma));
        m.put("renderDistance", Integer.toString(renderDistance));
        m.put("guiScale", Integer.toString(guiScale));
        m.put("maxFps", Integer.toString(maxFps));
        m.put("attackIndicator", Integer.toString(attackIndicator));
        m.put("soundCategory_master", Float.toString(masterVolume));
        m.put("soundCategory_music", Float.toString(musicVolume));
        m.put("soundCategory_record", Float.toString(recordVolume));
        m.put("soundCategory_weather", Float.toString(weatherVolume));
        m.put("soundCategory_block", Float.toString(blocksVolume));
        m.put("soundCategory_hostile", Float.toString(hostileVolume));
        m.put("soundCategory_neutral", Float.toString(neutralVolume));
        m.put("soundCategory_player", Float.toString(playersVolume));
        m.put("soundCategory_ambient", Float.toString(ambientVolume));
        m.put("soundCategory_voice", Float.toString(voiceVolume));
        for (KeyBindings.Bind b : KeyBindings.Bind.values()) m.put(b.optionKey(), KeyBindings.idOf(keys.get(b)));
        return m;
    }

    /** The exact text written to options.txt (exposed for tests). */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : toMap().entrySet()) sb.append(e.getKey()).append(':').append(e.getValue()).append('\n');
        return sb.toString();
    }

    public void save(File file) {
        try {
            Files.writeString(file.toPath(), serialize(), StandardCharsets.UTF_8);
        } catch (IOException ignored) {
            // an unwritable options file must never crash the game
        }
    }

    /** Legacy (pre-vanilla-format) key names -> vanilla names. */
    private static final Map<String, String> LEGACY = Map.of(
            "viewBobbing", "bobView", "vsync", "enableVsync", "invertMouse", "invertYMouse",
            "masterVolume", "soundCategory_master", "blocksVolume", "soundCategory_block",
            "playersVolume", "soundCategory_player", "ambientVolume", "soundCategory_ambient",
            "weatherVolume", "soundCategory_weather");

    /** Missing/corrupt file or values fall back to defaults field by field. */
    public static GameOptions load(File file) {
        GameOptions o = new GameOptions();
        if (!file.isFile()) return o;
        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return o;
        }
        Map<String, String> m = new LinkedHashMap<>();
        boolean legacyFov = false;
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty() || t.startsWith("#")) continue;
            int colon = t.indexOf(':'), eq = t.indexOf('=');
            boolean vanilla = colon > 0 && (eq < 0 || colon < eq);
            int cut = vanilla ? colon : eq;
            if (cut <= 0) continue;
            String key = t.substring(0, cut).trim(), value = t.substring(cut + 1).trim();
            if (!vanilla) {
                if (key.equals("fov")) legacyFov = true; // legacy stored whole degrees
                key = LEGACY.getOrDefault(key, key);
            }
            m.put(key, value);
        }
        o.guiScale = i(m, "guiScale", o.guiScale);
        if (m.containsKey("fov")) {
            float raw = f(m, "fov", 0f);
            o.fov = legacyFov && raw > 1.0001f ? Math.round(raw) : Math.round(70 + 40 * raw);
        }
        o.renderDistance = i(m, "renderDistance", o.renderDistance);
        o.viewBobbing = b(m, "bobView", o.viewBobbing);
        o.vsync = b(m, "enableVsync", o.vsync);
        o.fullscreen = b(m, "fullscreen", o.fullscreen);
        o.mouseSensitivity = f(m, "mouseSensitivity", o.mouseSensitivity);
        o.invertMouse = b(m, "invertYMouse", o.invertMouse);
        o.gamma = f(m, "gamma", o.gamma);
        o.maxFps = i(m, "maxFps", o.maxFps);
        o.attackIndicator = i(m, "attackIndicator", o.attackIndicator);
        o.toggleSprint = b(m, "toggleSprint", o.toggleSprint);
        o.toggleCrouch = b(m, "toggleCrouch", o.toggleCrouch);
        o.autoJump = b(m, "autoJump", o.autoJump);
        o.fovEffectScale = f(m, "fovEffectScale", o.fovEffectScale);
        o.masterVolume = f(m, "soundCategory_master", o.masterVolume);
        o.musicVolume = f(m, "soundCategory_music", o.musicVolume);
        o.recordVolume = f(m, "soundCategory_record", o.recordVolume);
        o.weatherVolume = f(m, "soundCategory_weather", o.weatherVolume);
        o.blocksVolume = f(m, "soundCategory_block", o.blocksVolume);
        o.hostileVolume = f(m, "soundCategory_hostile", o.hostileVolume);
        o.neutralVolume = f(m, "soundCategory_neutral", o.neutralVolume);
        o.playersVolume = f(m, "soundCategory_player", o.playersVolume);
        o.ambientVolume = f(m, "soundCategory_ambient", o.ambientVolume);
        o.voiceVolume = f(m, "soundCategory_voice", o.voiceVolume);
        for (KeyBindings.Bind bind : KeyBindings.Bind.values()) {
            String id = m.get(bind.optionKey());
            if (id == null) continue;
            String lower = id.trim().toLowerCase(Locale.ROOT);
            int code = KeyBindings.codeOf(lower);
            if (code != KeyBindings.UNBOUND || lower.equals("key.keyboard.unknown")) o.keys.set(bind, code);
        }
        o.clamp();
        return o;
    }

    private static int i(Map<String, String> m, String k, int d) {
        try {
            return Integer.parseInt(m.getOrDefault(k, "").trim());
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private static float f(Map<String, String> m, String k, float d) {
        try {
            return Float.parseFloat(m.getOrDefault(k, "").trim());
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private static boolean b(Map<String, String> m, String k, boolean d) {
        String v = m.get(k);
        return v == null ? d : Boolean.parseBoolean(v.trim());
    }
}
