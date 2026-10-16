package com.example.worldgen.biome;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data of one {@code data/minecraft/worldgen/biome/<name>.json} plus the climate behaviour of
 * {@code net.minecraft.world.level.biome.Biome} (Minecraft Java Edition 26.3).
 *
 * <p>Fields follow the 26.3 codecs read from bytecode: {@code Biome.DIRECT_CODEC} =
 * {@code ClimateSettings.CODEC} ({@code has_precipitation}, {@code temperature},
 * {@code temperature_modifier} optional default {@code none}, {@code downfall}) +
 * {@code attributes} (optional {@code EnvironmentAttributeMap}, default empty) +
 * {@code effects} ({@code BiomeSpecialEffects.CODEC}: {@code water_color} required, optional
 * {@code foliage_color}, {@code dry_foliage_color}, {@code grass_color}, {@code grass_color_modifier}
 * default {@code none}) + {@code BiomeGenerationSettings.CODEC} ({@code carvers}, {@code features}).
 *
 * <p>In 26.3 the sky colour, fog colour, water fog colour, particles, sounds, music and the mob spawn
 * settings ({@code minecraft:gameplay/natural_mob_spawns}) are environment attributes, not
 * {@code effects}; they are kept here as raw parsed JSON in {@link #attributes()} (values are
 * {@code Map}/{@code List}/{@code String}/{@code Boolean}/number objects from the loader).
 *
 * <p>Not ported (they need the world or colour-map textures): {@code shouldFreeze}, {@code shouldSnow}
 * (block/light/fluid queries) and {@code getGrassColor}/{@code getFoliageColor} without an override
 * ({@code GrassColor.get}/{@code FoliageColor.get} sample the colormap images).
 * Vanilla's per-thread 1024-entry temperature cache in {@code getTemperature} is a pure memo of
 * {@code getHeightAdjustedTemperature} and is not reproduced.
 */
public record BiomeDefinition(String id, boolean hasPrecipitation, float temperature,
                              TemperatureModifier temperatureModifier, float downfall,
                              Map<String, Object> attributes, SpecialEffects effects,
                              List<String> carvers, List<List<String>> features) {

    /** Vanilla {@code BiomeSpecialEffects} record; colours are ARGB ints as decoded by {@code STRING_RGB_COLOR}. */
    public record SpecialEffects(int waterColor, Optional<Integer> foliageColorOverride,
                                 Optional<Integer> dryFoliageColorOverride, Optional<Integer> grassColorOverride,
                                 GrassColorModifier grassColorModifier) {
    }

    /** A 2D noise as vanilla {@code levelgen.synth.Noise.get(double, double)} returning {@code float}. */
    @FunctionalInterface
    public interface Noise2D {
        float get(double x, double z);
    }

    /**
     * The three static noises of vanilla {@code Biome}, built in its static initialiser (26.3 bytecode):
     * <ul>
     *   <li>{@code TEMPERATURE_NOISE = new SimplexNoise(new WorldgenRandom(new LegacyRandomSource(1234L)), true)}</li>
     *   <li>{@code FROZEN_TEMPERATURE_NOISE}: {@code WorldgenRandom(LegacyRandomSource(3456L))} shared by
     *       {@code NoiseStack.builder().add(new SimplexNoise(random, true), 1.0, 0.14285715F)
     *       .add(new SimplexNoise(random, true), 0.5, 0.2857143F)
     *       .add(new SimplexNoise(random, true), 0.25, 0.5714286F).build()}</li>
     *   <li>{@code BIOME_INFO_NOISE = new SimplexNoise(new WorldgenRandom(new LegacyRandomSource(2345L)), true)}</li>
     * </ul>
     * The noise engine supplies these implementations.
     */
    public record Noises(Noise2D temperatureNoise, Noise2D frozenTemperatureNoise, Noise2D biomeInfoNoise) {
    }

    /** Vanilla {@code Biome.Precipitation}. */
    public enum Precipitation {
        NONE, RAIN, SNOW
    }

    /** Vanilla {@code Biome.TemperatureModifier} ({@code none}, {@code frozen}). */
    public enum TemperatureModifier {
        NONE("none"),
        FROZEN("frozen");

        private final String serializedName;

        TemperatureModifier(String serializedName) {
            this.serializedName = serializedName;
        }

        public String getSerializedName() {
            return this.serializedName;
        }

        static TemperatureModifier byName(String name) {
            for (TemperatureModifier modifier : values()) {
                if (modifier.serializedName.equals(name)) {
                    return modifier;
                }
            }
            throw new IllegalArgumentException("Unknown temperature_modifier " + name);
        }

        /** Vanilla {@code modifyTemperature(BlockPos, float)}; {@code FROZEN} is {@code Biome$TemperatureModifier$2}. */
        public float modifyTemperature(int blockX, int blockZ, float baseTemperature, Noises noises) {
            if (this == NONE) {
                return baseTemperature;
            }
            double groundValueLargeVariation =
                (double) (noises.frozenTemperatureNoise().get((double) blockX * 0.05, (double) blockZ * 0.05) * 7.0F);
            double groundValueEdgeVariation =
                (double) noises.biomeInfoNoise().get((double) blockX * 0.2, (double) blockZ * 0.2);
            double icePatches = groundValueLargeVariation + groundValueEdgeVariation;
            if (icePatches < 0.3) {
                double groundValueSmallVariation =
                    (double) noises.biomeInfoNoise().get((double) blockX * 0.09, (double) blockZ * 0.09);
                if (groundValueSmallVariation < 0.8) {
                    return 0.2F;
                }
            }
            return baseTemperature;
        }
    }

    /** Vanilla {@code BiomeSpecialEffects.GrassColorModifier} ({@code none}, {@code dark_forest}, {@code swamp}). */
    public enum GrassColorModifier {
        NONE("none"),
        DARK_FOREST("dark_forest"),
        SWAMP("swamp");

        private final String serializedName;

        GrassColorModifier(String serializedName) {
            this.serializedName = serializedName;
        }

        public String getSerializedName() {
            return this.serializedName;
        }

        static GrassColorModifier byName(String name) {
            for (GrassColorModifier modifier : values()) {
                if (modifier.serializedName.equals(name)) {
                    return modifier;
                }
            }
            throw new IllegalArgumentException("Unknown grass_color_modifier " + name);
        }

        /**
         * Vanilla {@code modifyColor(double x, double z, int baseColor)}:
         * dark_forest {@code ARGB.opaque((base & 16711422) + 2634762 >> 1)};
         * swamp {@code BIOME_INFO_NOISE.get(x * 0.0225, z * 0.0225) < -0.1 ? -11766212 : -9801671}.
         */
        public int modifyColor(double x, double z, int baseColor, Noises noises) {
            switch (this) {
                case DARK_FOREST:
                    return ((baseColor & 16711422) + 2634762 >> 1) | -16777216;
                case SWAMP:
                    double groundValue = (double) noises.biomeInfoNoise().get(x * 0.0225, z * 0.0225);
                    return groundValue < -0.1 ? -11766212 : -9801671;
                default:
                    return baseColor;
            }
        }
    }

    public float getBaseTemperature() {
        return this.temperature;
    }

    /** Vanilla {@code getHeightAdjustedTemperature(BlockPos, int seaLevel)}; snow level is {@code seaLevel + 17}. */
    public float getHeightAdjustedTemperature(int blockX, int blockY, int blockZ, int seaLevel, Noises noises) {
        float adjustedTemperature = this.temperatureModifier.modifyTemperature(blockX, blockZ,
            this.getBaseTemperature(), noises);
        int snowLevel = seaLevel + 17;
        if (blockY > snowLevel) {
            float v = noises.temperatureNoise().get((double) ((float) blockX / 8.0F), (double) ((float) blockZ / 8.0F))
                * 8.0F;
            return adjustedTemperature - (v + (float) blockY - (float) snowLevel) * 0.05F / 40.0F;
        }
        return adjustedTemperature;
    }

    /** Vanilla {@code warmEnoughToRain}: temperature {@code >= 0.15F}. */
    public boolean warmEnoughToRain(int blockX, int blockY, int blockZ, int seaLevel, Noises noises) {
        return this.getHeightAdjustedTemperature(blockX, blockY, blockZ, seaLevel, noises) >= 0.15F;
    }

    /** Vanilla {@code coldEnoughToSnow}: {@code !warmEnoughToRain}. */
    public boolean coldEnoughToSnow(int blockX, int blockY, int blockZ, int seaLevel, Noises noises) {
        return !this.warmEnoughToRain(blockX, blockY, blockZ, seaLevel, noises);
    }

    /** Vanilla {@code shouldMeltFrozenOceanIcebergSlightly}: temperature {@code > 0.1F}. */
    public boolean shouldMeltFrozenOceanIcebergSlightly(int blockX, int blockY, int blockZ, int seaLevel,
                                                        Noises noises) {
        return this.getHeightAdjustedTemperature(blockX, blockY, blockZ, seaLevel, noises) > 0.1F;
    }

    /** Vanilla {@code getPrecipitationAt}. */
    public Precipitation getPrecipitationAt(int blockX, int blockY, int blockZ, int seaLevel, Noises noises) {
        if (!this.hasPrecipitation) {
            return Precipitation.NONE;
        }
        return this.coldEnoughToSnow(blockX, blockY, blockZ, seaLevel, noises) ? Precipitation.SNOW
            : Precipitation.RAIN;
    }

    /** Vanilla {@code getWaterColor}. */
    public int getWaterColor() {
        return this.effects.waterColor();
    }

    /** Raw value of one environment attribute such as {@code minecraft:visual/sky_color}, if present. */
    public Optional<Object> attribute(String key) {
        return Optional.ofNullable(this.attributes.get(key));
    }
}
