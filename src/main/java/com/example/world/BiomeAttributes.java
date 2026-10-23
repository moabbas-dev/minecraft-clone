package com.example.world;

import com.example.worldgen.biome.BiomeDefinition;
import com.example.worldgen.biome.BiomeJson;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The biome part of the 26.3 environment attribute system for the atmosphere attributes a biome file can set
 * ({@code visual/sky_color}, {@code visual/fog_color}, {@code visual/water_fog_color}, {@code visual/water_fog_start_distance},
 * {@code visual/water_fog_end_distance}), as the camera's {@code EnvironmentAttributeProbe} reads them:
 * <ol>
 *   <li>the base value is the attribute's default ({@code EnvironmentAttributes}) with the dimension's constant layer applied
 *       ({@code data/minecraft/dimension_type/overworld.json}: sky {@code #78a7ff}, fog {@code #c0d8ff}; the overworld sets no
 *       water fog, so {@code #050533}, start -8, end 96 stay the defaults);</li>
 *   <li>{@code EnvironmentAttributeProbe.tick}: {@code GaussianSampler.sample(cameraPos * 0.25, getNoiseBiomeAtQuart, ...)} - the
 *       position minus 0.5 in quart units, a 6 x 6 x 6 block of quart cells from {@code floor - 2} to {@code floor + 3} on each axis,
 *       each weighted by the product of {@code lerp(fraction, K[i + 1], K[i])} with the kernel {@code K = 0 1 4 6 4 1 0}; the
 *       weights are summed per biome ({@code SpatialAttributeInterpolator.accumulate}, in first-seen order);</li>
 *   <li>{@code SpatialAttributeInterpolator.applyAttributeLayer}: no biome - the base; one biome - its modifier applied to the
 *       base; several - each biome's modified value folded in order with {@code lerp((float) (weight / weightSoFar), result,
 *       value)} (the spatial lerp of the rgb_color and float types is the plain linear lerp, {@code ARGB.srgbLerp} being
 *       {@code Vector3f.lerp});</li>
 *   <li>{@code EnvironmentAttribute.sanitizeValue}: the distances are clamped to {@code NON_NEGATIVE_FLOAT}.</li>
 * </ol>
 * The timeline and weather layers ({@code SkyColors}) and the per-tick probe lerp ({@link AtmosphereProbe}) apply after this.
 * Biome attribute entries are an override value or {@code {"modifier": ..., "argument": ...}} ({@code EnvironmentAttributeMap.Entry});
 * the modifiers the 26.3 biome files use (colour overrides, {@code multiply} on the water fog end) and the other plain float
 * operations ({@code FloatModifier}) are supported - any other form throws instead of being guessed.
 */
public final class BiomeAttributes {
    private BiomeAttributes() {
    }

    public static final String SKY_COLOR = "minecraft:visual/sky_color";
    public static final String FOG_COLOR = "minecraft:visual/fog_color";
    public static final String WATER_FOG_COLOR = "minecraft:visual/water_fog_color";
    public static final String WATER_FOG_START_DISTANCE = "minecraft:visual/water_fog_start_distance";
    public static final String WATER_FOG_END_DISTANCE = "minecraft:visual/water_fog_end_distance";

    /** {@code overworld.json} {@code minecraft:visual/sky_color}. */
    public static final int OVERWORLD_SKY_COLOR = 0x78A7FF;
    /** {@code overworld.json} {@code minecraft:visual/fog_color}. */
    public static final int OVERWORLD_FOG_COLOR = 0xC0D8FF;
    /** {@code EnvironmentAttributes.WATER_FOG_COLOR} default {@code ARGB.vector3fFromRGB24(-16448205)}. */
    public static final int DEFAULT_WATER_FOG_COLOR = 0x050533;
    /** {@code EnvironmentAttributes.WATER_FOG_START_DISTANCE} default. */
    public static final float DEFAULT_WATER_FOG_START = -8.0F;
    /** {@code EnvironmentAttributes.WATER_FOG_END_DISTANCE} default. */
    public static final float DEFAULT_WATER_FOG_END = 96.0F;

    /** {@code GaussianSampler.GAUSSIAN_SAMPLE_KERNEL}. */
    private static final double[] KERNEL = {0.0, 1.0, 4.0, 6.0, 4.0, 1.0, 0.0};

    /** The overworld's atmosphere values at one position (colours as 0-1 rgb). */
    public record Values(float[] sky, float[] fog, float[] waterFog, float waterFogStart, float waterFogEnd) {
        /** The overworld's dimension values (no biome). */
        public static Values overworld() {
            return new Values(rgb(OVERWORLD_SKY_COLOR), rgb(OVERWORLD_FOG_COLOR), rgb(DEFAULT_WATER_FOG_COLOR), DEFAULT_WATER_FOG_START,
                    DEFAULT_WATER_FOG_END);
        }

        /** The {@code partialTickLerp} of every attribute (linear for rgb_color and float). */
        public Values lerp(float alpha, Values to) {
            return new Values(lerp3(alpha, sky, to.sky), lerp3(alpha, fog, to.fog), lerp3(alpha, waterFog, to.waterFog),
                    BiomeAttributes.lerp(alpha, waterFogStart, to.waterFogStart), BiomeAttributes.lerp(alpha, waterFogEnd, to.waterFogEnd));
        }
    }

    /** {@code BiomeManager.getNoiseBiomeAtQuart}: the biome id stored at a quart cell. */
    @FunctionalInterface
    public interface QuartBiomes {
        String at(int quartX, int quartY, int quartZ);
    }

    /**
     * {@code GaussianSampler.sample(position * 0.25, ...)} accumulated per biome id in first-seen order (the
     * {@code SpatialAttributeInterpolator} weights).
     */
    public static LinkedHashMap<String, Double> gaussianWeights(double x, double y, double z, QuartBiomes biomes) {
        double px = x * 0.25 - 0.5, py = y * 0.25 - 0.5, pz = z * 0.25 - 0.5;
        int ix = (int) Math.floor(px), iy = (int) Math.floor(py), iz = (int) Math.floor(pz);
        double rx = px - ix, ry = py - iy, rz = pz - iz;
        LinkedHashMap<String, Double> weights = new LinkedHashMap<>();
        for (int dz = 0; dz < 6; dz++) {
            double wz = lerp(rz, KERNEL[dz + 1], KERNEL[dz]);
            int sz = iz - 2 + dz;
            for (int dx = 0; dx < 6; dx++) {
                double wx = lerp(rx, KERNEL[dx + 1], KERNEL[dx]);
                int sx = ix - 2 + dx;
                for (int dy = 0; dy < 6; dy++) {
                    double wy = lerp(ry, KERNEL[dy + 1], KERNEL[dy]);
                    int sy = iy - 2 + dy;
                    double w = wx * wy * wz;
                    weights.merge(biomes.at(sx, sy, sz), w, Double::sum);
                }
            }
        }
        return weights;
    }

    /** The probe's value of every atmosphere attribute at {@code (x, y, z)} (the camera position). */
    public static Values sample(double x, double y, double z, QuartBiomes biomes, Function<String, BiomeDefinition> definitions) {
        LinkedHashMap<String, Double> weights = gaussianWeights(x, y, z, biomes);
        Values base = Values.overworld();
        return new Values(
                blendRgb(weights, definitions, SKY_COLOR, base.sky()),
                blendRgb(weights, definitions, FOG_COLOR, base.fog()),
                blendRgb(weights, definitions, WATER_FOG_COLOR, base.waterFog()),
                blendFloat(weights, definitions, WATER_FOG_START_DISTANCE, base.waterFogStart(), false),
                blendFloat(weights, definitions, WATER_FOG_END_DISTANCE, base.waterFogEnd(), true));
    }

    /** One biome's value of an rgb attribute applied to {@code base} ({@code EnvironmentAttributeMap.applyModifier}). */
    public static float[] applyRgb(BiomeDefinition biome, String key, float[] base) {
        Object entry = biome.attributes().get(key);
        if (entry == null) return base;
        if (entry instanceof String hex) return rgb(parseHex(hex, key));
        throw new IllegalArgumentException(biome.id() + " " + key + ": unsupported colour modifier form " + entry);
    }

    /** One biome's value of a float attribute applied to {@code base} (override or a {@code FloatModifier}). */
    public static float applyFloat(BiomeDefinition biome, String key, float base) {
        Object entry = biome.attributes().get(key);
        if (entry == null) return base;
        if (entry instanceof BiomeJson.Num num) return num.floatValue();
        if (entry instanceof Map<?, ?> map && map.get("modifier") instanceof String modifier
                && map.get("argument") instanceof BiomeJson.Num arg) {
            float a = arg.floatValue();
            switch (modifier) { // AttributeModifier.OperationId names
                case "override": return a;
                case "add": return base + a;
                case "subtract": return base - a;
                case "multiply": return base * a;
                case "minimum": return Math.min(base, a);
                case "maximum": return Math.max(base, a);
                default: break;
            }
        }
        throw new IllegalArgumentException(biome.id() + " " + key + ": unsupported float modifier form " + entry);
    }

    private static float[] blendRgb(LinkedHashMap<String, Double> weights, Function<String, BiomeDefinition> definitions, String key,
                                    float[] base) {
        if (weights.isEmpty()) return base;
        float[] result = null;
        double accumulated = 0.0;
        for (Map.Entry<String, Double> e : weights.entrySet()) {
            float[] value = applyRgb(definitions.apply(e.getKey()), key, base);
            if (weights.size() == 1) return value;
            accumulated += e.getValue();
            if (result == null) {
                result = value;
            } else {
                result = lerp3((float) (e.getValue() / accumulated), result, value);
            }
        }
        return result;
    }

    private static float blendFloat(LinkedHashMap<String, Double> weights, Function<String, BiomeDefinition> definitions, String key,
                                    float base, boolean nonNegative) {
        float result = base;
        if (!weights.isEmpty()) {
            boolean first = true;
            double accumulated = 0.0;
            for (Map.Entry<String, Double> e : weights.entrySet()) {
                float value = applyFloat(definitions.apply(e.getKey()), key, base);
                accumulated += e.getValue();
                if (first) {
                    result = value;
                    first = false;
                } else {
                    result = lerp((float) (e.getValue() / accumulated), result, value);
                }
            }
        }
        return nonNegative ? Math.max(0.0F, result) : result; // AttributeRange.NON_NEGATIVE_FLOAT
    }

    /** {@code ExtraCodecs.STRING_RGB_VEC3_COLOR} for a {@code "#rrggbb"} string. */
    static int parseHex(String hex, String what) {
        if (!hex.startsWith("#") || hex.length() != 7) throw new IllegalArgumentException(what + ": not a #rrggbb colour: " + hex);
        return Integer.parseInt(hex.substring(1), 16);
    }

    /** {@code ARGB.vector3fFromRGB24}. */
    public static float[] rgb(int rgb24) {
        return new float[]{((rgb24 >> 16) & 0xFF) / 255.0F, ((rgb24 >> 8) & 0xFF) / 255.0F, (rgb24 & 0xFF) / 255.0F};
    }

    /** {@code Vector3f.lerp}: {@code a + (b - a) * t} per channel. */
    static float[] lerp3(float t, float[] a, float[] b) {
        return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
    }

    /** {@code Mth.lerp(float)}. */
    static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }

    /** {@code Mth.lerp(double)}. */
    static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }
}
