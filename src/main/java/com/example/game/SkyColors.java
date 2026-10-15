package com.example.game;

import org.joml.Vector3f;

/**
 * The colours of the sky, the fog, the clouds and the sunrise glow, computed as the 26.3 client does it (all numbers read from
 * {@code data/minecraft/timeline/day.json}, the overworld dimension type, the biome files, {@code WeatherAttributes},
 * {@code AtmosphericFogEnvironment} and {@code ClientLevel.addEnvironmentAttributeLayers}):
 * <ul>
 *   <li>the biome gives the base sky colour (and the dimension the base fog colour {@code #c0d8ff});</li>
 *   <li>the day timeline multiplies them: the sky goes from white to black, the fog to {@code #0f0f16}, the clouds to
 *       {@code #191926}, between ticks 11867 and 13670 and back between 22330 and 24133;</li>
 *   <li>rain and thunder layer on top, each scaled by how strong the weather is: the sky and clouds blend toward grey, the fog
 *       and the sunrise glow are multiplied;</li>
 *   <li>a lightning flash lerps the sky 22% toward (0.8, 0.8, 1);</li>
 *   <li>the fog colour then takes on the sunrise colour when looking toward the sun, and mixes toward the sky colour by an
 *       amount that depends on the render distance.</li>
 * </ul>
 * Pure maths, no GL.
 */
final class SkyColors {
    private SkyColors() {
    }

    /** The overworld dimension's base fog colour. */
    static final int OVERWORLD_FOG = 0xC0D8FF;
    /** The overworld dimension's base cloud colour (alpha 0.8). */
    static final float CLOUD_ALPHA = 0xCC / 255f;

    private static final float[][] SKY_TRACK = {{133, 1f}, {11867, 1f}, {13670, 0f}, {22330, 0f}};
    private static final float[][] FOG_R = {{133, 1f}, {11867, 1f}, {13670, 0x0F / 255f}, {22330, 0x0F / 255f}};
    private static final float[][] FOG_B = {{133, 1f}, {11867, 1f}, {13670, 0x16 / 255f}, {22330, 0x16 / 255f}};

    /** Sunrise and sunset glow keyframes in the file's #AARRGGBB order: tick, alpha, r, g, b (data/minecraft/timeline/day.json, visual/sunrise_sunset_color). */
    private static final float[][] SUNRISE = {
            {71, 0x5F, 0xEF, 0xA3, 0x33}, {310, 0x29, 0xF5, 0xBA, 0x33}, {565, 0x06, 0xFB, 0xD4, 0x33}, {730, 0x00, 0xFF, 0xE5, 0x33},
            {11270, 0x00, 0xFF, 0xE5, 0x33}, {11397, 0x04, 0xFC, 0xD8, 0x33}, {11522, 0x0F, 0xF9, 0xCB, 0x33}, {11690, 0x29, 0xF5, 0xBA, 0x33},
            {11929, 0x5F, 0xEF, 0xA3, 0x33}, {12243, 0xB1, 0xE7, 0x87, 0x33}, {12358, 0xCC, 0xE4, 0x7E, 0x33}, {12512, 0xE9, 0xE0, 0x72, 0x33},
            {12613, 0xF6, 0xDD, 0x6B, 0x33}, {12732, 0xFE, 0xDA, 0x63, 0x33}, {12841, 0xFE, 0xD7, 0x5C, 0x33}, {13035, 0xEC, 0xD2, 0x51, 0x33},
            {13252, 0xC1, 0xCC, 0x47, 0x33}, {13775, 0x36, 0xBE, 0x37, 0x33}, {13888, 0x1F, 0xBB, 0x35, 0x33}, {14039, 0x09, 0xB7, 0x33, 0x33},
            {14192, 0x00, 0xB3, 0x33, 0x33}, {21807, 0x00, 0xB2, 0x33, 0x33}, {21961, 0x09, 0xB7, 0x33, 0x33}, {22112, 0x1F, 0xBB, 0x35, 0x33},
            {22225, 0x36, 0xBE, 0x37, 0x33}, {22748, 0xC1, 0xCC, 0x47, 0x33}, {22965, 0xEC, 0xD2, 0x51, 0x33}, {23159, 0xFE, 0xD7, 0x5C, 0x33},
            {23272, 0xFE, 0xDA, 0x63, 0x33}, {23488, 0xE9, 0xE0, 0x72, 0x33}, {23642, 0xCC, 0xE4, 0x7E, 0x33}, {23757, 0xB1, 0xE7, 0x87, 0x33}};

    /** The sky colour (what the sky disc is drawn with) for a base biome sky colour. */
    static Vector3f sky(float[] biomeSky, float tick, float rain, float thunder, boolean flashing) {
        float t = SkyDetails.cyclic(SKY_TRACK, tick);
        Vector3f c = new Vector3f(biomeSky[0] * t, biomeSky[1] * t, biomeSky[2] * t);
        c = layer(c, rain, blendToGray(c, 0.6f, 0.75f));
        c = layer(c, thunder, blendToGray(c, 0.24f, 0.94f));
        if (flashing) c.lerp(new Vector3f(0.8f, 0.8f, 1f), 0.22f);
        return c;
    }

    /** The fog colour before the sun and sky mixing ({@link #fogForView}): the biome fog times the timeline and the weather. */
    static Vector3f fog(float[] biomeFog, float tick, float rain, float thunder) {
        Vector3f c = new Vector3f(biomeFog[0] * SkyDetails.cyclic(FOG_R, tick), biomeFog[1] * SkyDetails.cyclic(FOG_R, tick),
                biomeFog[2] * SkyDetails.cyclic(FOG_B, tick));
        c = layer(c, rain, new Vector3f(c).mul(0.5f, 0.5f, 0.6f));
        c = layer(c, thunder, new Vector3f(c).mul(0.25f, 0.25f, 0.3f));
        return c;
    }

    /**
     * The fog colour seen when looking along {@code forwardX} (the x component of the horizontal view direction) with the sun at
     * {@code sunAngleDegrees}: facing the sunrise or sunset tints it toward the glow colour, and a render distance of 4 chunks or
     * more mixes it toward the (weather-darkened) sky colour by {@code 1 - f^0.25}, f = 0.25 + 0.75 min(1, min(32, d) / 32).
     */
    static Vector3f fogForView(Vector3f fog, Vector3f skyColor, float[] sunrise, float sunAngleDegrees, float forwardX, int renderDistanceChunks,
                               float rain, float thunder) {
        Vector3f c = new Vector3f(fog);
        if (renderDistanceChunks >= 4) {
            float side = (float) Math.sin(Math.toRadians(sunAngleDegrees)) > 0f ? -1f : 1f;
            float dot = forwardX * side;
            if (dot > 0f && sunrise[3] > 0f) c.lerp(new Vector3f(sunrise[0], sunrise[1], sunrise[2]), dot * sunrise[3]);
        }
        Vector3f s = new Vector3f(skyColor);
        if (rain > 0f) s.mul(1f - rain * 0.5f, 1f - rain * 0.5f, 1f - rain * 0.4f);
        if (thunder > 0f) s.mul(1f - thunder * 0.5f);
        float f = Math.min(512f / 16f, renderDistanceChunks);
        float k = 0.25f + 0.75f * Math.max(0f, Math.min(1f, f / 32f));
        k = 1f - (float) Math.pow(k, 0.25);
        return c.lerp(s, k);
    }

    /** The cloud colour multiplier (rgb; the alpha is {@link #CLOUD_ALPHA}): the timeline tint, blended toward grey in rain and thunder. */
    static Vector3f cloud(float tick, float rain, float thunder) {
        float[] t = SkyDetails.cloudTint(tick);
        Vector3f c = new Vector3f(t[0], t[1], t[2]);
        c = layer(c, rain, blendToGray(c, 0.24f, 0.5f));
        c = layer(c, thunder, blendToGray(c, 0.095f, 0.94f));
        return c;
    }

    /** The sunrise / sunset glow (r, g, b, alpha), 0 outside the dawn and dusk; rain and thunder multiply the colour. */
    static float[] sunrise(float tick, float rain, float thunder) {
        float[] out = new float[4];
        for (int ch = 0; ch < 4; ch++) {
            float[][] keys = new float[SUNRISE.length][2];
            int column = ch == 3 ? 1 : 2 + ch; // r, g, b, then alpha
            for (int i = 0; i < SUNRISE.length; i++) keys[i] = new float[]{SUNRISE[i][0], SUNRISE[i][column] / 255f};
            out[ch] = SkyDetails.cyclic(keys, tick);
        }
        float[] w = {1f, 1f, 1f, 1f};
        float[] rainMul = {0.5f, 0.5f, 0.6f, 1f}, thunderMul = {0.25f, 0.25f, 0.3f, 1f};
        for (int i = 0; i < 4; i++) {
            float v = out[i];
            v += (v * rainMul[i] - v) * clamp01(rain);
            v += (v * thunderMul[i] - v) * clamp01(thunder);
            out[i] = v * w[i];
        }
        return out;
    }

    /** ColorModifier.BlendToGray: lerp(factor, colour, greyscale(colour) * brightness), greyscale = 0.3 r + 0.59 g + 0.11 b. */
    static Vector3f blendToGray(Vector3f c, float brightness, float factor) {
        float grey = (c.x * 0.3f + c.y * 0.59f + c.z * 0.11f) * brightness;
        return new Vector3f(c).lerp(new Vector3f(grey, grey, grey), factor);
    }

    /** A weather layer: the value moved toward what the layer makes of it by the weather's level. */
    private static Vector3f layer(Vector3f base, float level, Vector3f modified) {
        float l = clamp01(level);
        return l <= 0f ? base : new Vector3f(base).lerp(modified, l);
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** {@code #rrggbb} as 0-1 floats. */
    static float[] rgb(int hex) {
        return new float[]{((hex >> 16) & 0xFF) / 255f, ((hex >> 8) & 0xFF) / 255f, (hex & 0xFF) / 255f};
    }

    /**
     * The sunrise fan of SkyRenderer.buildSunriseFan: a triangle fan of 18 vertices around (0, 100, 0) with 17 rim vertices at
     * (sin a * 120, cos a * 120, -cos a * 40), a = i * 2 pi / 16, in the model space of
     * {@code rotateX(90) . rotateZ(180 or 0) . rotateZ(90)}. Returns x y z a per vertex (the alpha is 1 in the middle, 0 at the rim).
     */
    static float[] sunriseFan() {
        float[] v = new float[18 * 4];
        int n = 0;
        v[n++] = 0f;
        v[n++] = 100f;
        v[n++] = 0f;
        v[n++] = 1f;
        for (int i = 0; i <= 16; i++) {
            float a = i * 6.2831855f / 16f;
            float s = (float) Math.sin(a), c = (float) Math.cos(a);
            v[n++] = s * 120f;
            v[n++] = c * 120f;
            v[n++] = -c * 40f;
            v[n++] = 0f;
        }
        return v;
    }
}
