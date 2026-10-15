package com.example.game;

/**
 * The lightmap: the colour a surface gets for each sky light level (0-15) and block light level (0-15), computed as
 * {@code lightmap.fsh} of the 26.3 client does it, from the overworld's day timeline ({@code sky_light_factor} and
 * {@code sky_light_color}, which keyframe from day to night), the rain and thunder layers that pull both toward night,
 * the ambient colour {@code #0a0a0a}, the warm block light tint {@code #ffd88c}, the flickering block light factor
 * (1.4 plus a decaying random walk, updated 20 times a second) and the Brightness option. Pure maths, no GL.
 */
final class LightmapColors {
    private LightmapColors() {
    }

    static final float AMBIENT = 0x0a / 255f;
    static final float[] BLOCK_LIGHT_TINT = {1f, 0xD8 / 255f, 0x8C / 255f};
    static final float[] NIGHT_SKY_LIGHT_COLOR = {0x7a / 255f, 0x7a / 255f, 1f};
    static final float NIGHT_SKY_LIGHT_FACTOR = 0.24f;
    static final float RAIN_ALPHA = 0.3125f, THUNDER_ALPHA = 0.52734375f;

    private static final float[][] SKY_FACTOR = {{730, 1f}, {11270, 1f}, {13140, NIGHT_SKY_LIGHT_FACTOR}, {22860, NIGHT_SKY_LIGHT_FACTOR}};
    private static final float[][] SKY_COLOR_R = {{730, 1f}, {11270, 1f}, {13140, NIGHT_SKY_LIGHT_COLOR[0]}, {22860, NIGHT_SKY_LIGHT_COLOR[0]}};
    private static final float[][] SKY_COLOR_G = {{730, 1f}, {11270, 1f}, {13140, NIGHT_SKY_LIGHT_COLOR[1]}, {22860, NIGHT_SKY_LIGHT_COLOR[1]}};
    private static final float[][] SKY_COLOR_B = {{730, 1f}, {11270, 1f}, {13140, NIGHT_SKY_LIGHT_COLOR[2]}, {22860, NIGHT_SKY_LIGHT_COLOR[2]}};

    /** The inputs of the lightmap shader. */
    record Params(float skyFactor, float[] skyLightColor, float blockFactor, float brightness) {
    }

    /**
     * @param dayTick     tick of the vanilla day (0 = sunrise, 6000 = noon, 13000 = night)
     * @param rainLevel   0..1, how strongly it is raining
     * @param thunderLevel 0..1, how strongly it is thundering
     * @param flicker     the block light flicker (its random walk), see {@link #nextFlicker}
     * @param gamma       the Brightness option, 0 (moody) .. 1 (bright)
     */
    static Params params(float dayTick, float rainLevel, float thunderLevel, float flicker, float gamma) {
        float factor = SkyDetails.cyclic(SKY_FACTOR, dayTick);
        float[] color = {SkyDetails.cyclic(SKY_COLOR_R, dayTick), SkyDetails.cyclic(SKY_COLOR_G, dayTick), SkyDetails.cyclic(SKY_COLOR_B, dayTick)};
        // the weather layers, alpha-blended toward night by their strength
        float rain = clamp01(rainLevel) * RAIN_ALPHA, thunder = clamp01(thunderLevel) * THUNDER_ALPHA;
        factor = lerp(factor, NIGHT_SKY_LIGHT_FACTOR, rain);
        factor = lerp(factor, NIGHT_SKY_LIGHT_FACTOR, thunder);
        for (int i = 0; i < 3; i++) {
            color[i] = lerp(color[i], NIGHT_SKY_LIGHT_COLOR[i], rain);
            color[i] = lerp(color[i], NIGHT_SKY_LIGHT_COLOR[i], thunder);
        }
        return new Params(factor, color, 1.4f + flicker, gamma);
    }

    /** One 20 Hz step of the block light flicker: add (r1 - r2) * r3 * r4 * 0.1, then decay by 0.9. */
    static float nextFlicker(float flicker, java.util.Random random) {
        flicker += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * random.nextFloat() * 0.1f;
        return flicker * 0.9f;
    }

    /** {@code level / (4 - 3 * level)}: how bright a light level looks (lightmap.fsh get_brightness). */
    static float brightness(float level) {
        return level / (4f - 3f * level);
    }

    /** One texel: the colour for block light {@code block} and sky light {@code sky} (each 0-15), as lightmap.fsh computes it. */
    static float[] texel(Params p, int block, int sky) {
        float blockLevel = block / 15f, skyLevel = sky / 15f;
        float blockBrightness = brightness(blockLevel) * p.blockFactor();
        float skyBrightness = brightness(skyLevel) * p.skyFactor();
        float[] color = {AMBIENT, AMBIENT, AMBIENT};
        for (int i = 0; i < 3; i++) color[i] += p.skyLightColor()[i] * skyBrightness;
        float parabola = (2f * blockLevel - 1f) * (2f * blockLevel - 1f);
        for (int i = 0; i < 3; i++) {
            float blockColor = lerp(BLOCK_LIGHT_TINT[i], 1f, 0.9f * parabola);
            color[i] += blockColor * blockBrightness;
        }
        for (int i = 0; i < 3; i++) color[i] = clamp01(color[i]);
        float max = Math.max(color[0], Math.max(color[1], color[2]));
        if (max > 0f) {
            float inverted = 1f - max;
            float scaled = 1f - inverted * inverted * inverted * inverted;
            for (int i = 0; i < 3; i++) color[i] = lerp(color[i], color[i] * (scaled / max), p.brightness());
        }
        return color;
    }

    /** The whole 16 x 16 lightmap as RGBA bytes, row = sky light, column = block light (the layout the shaders sample). */
    static byte[] build(Params p) {
        byte[] out = new byte[16 * 16 * 4];
        for (int sky = 0; sky < 16; sky++) {
            for (int block = 0; block < 16; block++) {
                float[] c = texel(p, block, sky);
                int i = (sky * 16 + block) * 4;
                out[i] = (byte) Math.round(c[0] * 255f);
                out[i + 1] = (byte) Math.round(c[1] * 255f);
                out[i + 2] = (byte) Math.round(c[2] * 255f);
                out[i + 3] = (byte) 255;
            }
        }
        return out;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }
}
