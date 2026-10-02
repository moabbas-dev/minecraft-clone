package com.example.ui;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;

/** User settings edited by the Options screens and persisted to {@code options.txt}. Pure data +
 *  clamping, so it is unit-testable; {@code Game} applies the values to the engine. */
public final class GameOptions {
    public static final File DEFAULT_FILE = new File("options.txt");

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
    public float masterVolume = 1f;
    public float blocksVolume = 1f;
    public float playersVolume = 1f;
    public float ambientVolume = 1f;
    public float weatherVolume = 1f;

    public void clamp() {
        guiScale = Math.max(0, Math.min(4, guiScale));
        fov = Math.max(30, Math.min(110, fov));
        renderDistance = Math.max(2, Math.min(12, renderDistance));
        mouseSensitivity = c01(mouseSensitivity);
        masterVolume = c01(masterVolume);
        blocksVolume = c01(blocksVolume);
        playersVolume = c01(playersVolume);
        ambientVolume = c01(ambientVolume);
        weatherVolume = c01(weatherVolume);
    }

    private static float c01(float v) {
        return Float.isNaN(v) ? 0f : Math.max(0f, Math.min(1f, v));
    }

    /** Vanilla's sensitivity curve: slider 0.5 maps to exactly 1x the base mouse speed. */
    public float mouseSpeedMultiplier() {
        float f = mouseSensitivity * 0.6f + 0.2f;
        return f * f * f * 8f;
    }

    public void save(File file) {
        Properties p = new Properties();
        p.setProperty("guiScale", Integer.toString(guiScale));
        p.setProperty("fov", Integer.toString(fov));
        p.setProperty("renderDistance", Integer.toString(renderDistance));
        p.setProperty("viewBobbing", Boolean.toString(viewBobbing));
        p.setProperty("vsync", Boolean.toString(vsync));
        p.setProperty("fullscreen", Boolean.toString(fullscreen));
        p.setProperty("mouseSensitivity", Float.toString(mouseSensitivity));
        p.setProperty("invertMouse", Boolean.toString(invertMouse));
        p.setProperty("masterVolume", Float.toString(masterVolume));
        p.setProperty("blocksVolume", Float.toString(blocksVolume));
        p.setProperty("playersVolume", Float.toString(playersVolume));
        p.setProperty("ambientVolume", Float.toString(ambientVolume));
        p.setProperty("weatherVolume", Float.toString(weatherVolume));
        try (FileOutputStream out = new FileOutputStream(file)) {
            p.store(out, "Minecraft clone options");
        } catch (IOException ignored) {
            // an unwritable options file must never crash the game
        }
    }

    /** Missing/corrupt file or values fall back to defaults field by field. */
    public static GameOptions load(File file) {
        GameOptions o = new GameOptions();
        if (!file.isFile()) return o;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            p.load(in);
        } catch (IOException | IllegalArgumentException e) {
            return o;
        }
        o.guiScale = i(p, "guiScale", o.guiScale);
        o.fov = i(p, "fov", o.fov);
        o.renderDistance = i(p, "renderDistance", o.renderDistance);
        o.viewBobbing = b(p, "viewBobbing", o.viewBobbing);
        o.vsync = b(p, "vsync", o.vsync);
        o.fullscreen = b(p, "fullscreen", o.fullscreen);
        o.mouseSensitivity = f(p, "mouseSensitivity", o.mouseSensitivity);
        o.invertMouse = b(p, "invertMouse", o.invertMouse);
        o.masterVolume = f(p, "masterVolume", o.masterVolume);
        o.blocksVolume = f(p, "blocksVolume", o.blocksVolume);
        o.playersVolume = f(p, "playersVolume", o.playersVolume);
        o.ambientVolume = f(p, "ambientVolume", o.ambientVolume);
        o.weatherVolume = f(p, "weatherVolume", o.weatherVolume);
        o.clamp();
        return o;
    }

    private static int i(Properties p, String k, int d) {
        try {
            return Integer.parseInt(p.getProperty(k, "").trim());
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private static float f(Properties p, String k, float d) {
        try {
            return Float.parseFloat(p.getProperty(k, "").trim());
        } catch (NumberFormatException e) {
            return d;
        }
    }

    private static boolean b(Properties p, String k, boolean d) {
        String v = p.getProperty(k);
        return v == null ? d : Boolean.parseBoolean(v.trim());
    }
}
