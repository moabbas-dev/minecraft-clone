package com.example.worldgen.noise;

import com.example.worldgen.random.RandomSource;

import java.util.List;

/**
 * Port of {@code net.minecraft.world.level.levelgen.synth.LegacyFbmInitializer} (26.3): builds
 * the pre-1.18 style octave stack (one shared random, octaves created from the highest frequency
 * index downward, absent octaves skipped by consuming 262 ints) used by
 * {@link NormalNoise#createForLegacyNetherBiome}.
 */
public final class LegacyFbmInitializer {

    private LegacyFbmInitializer() {
    }

    public static NoiseStack createForLegacyNetherBiome(RandomSource random, int baseOctave, List<Double> amplitudes) {
        int size = amplitudes.size();
        int top = -baseOctave;
        PerlinNoise[] levels = new PerlinNoise[size];
        PerlinNoise first = new PerlinNoise(random);
        if (top >= 0 && top < size) {
            double amplitude = amplitudes.get(top);
            if (amplitude != 0.0) {
                levels[top] = first;
            }
        }
        for (int i = top - 1; i >= 0; i--) {
            if (i < size) {
                double amplitude = amplitudes.get(i);
                if (amplitude != 0.0) {
                    levels[i] = new PerlinNoise(random);
                } else {
                    skipOctave(random);
                }
            } else {
                skipOctave(random);
            }
        }
        long created = 0L;
        for (PerlinNoise level : levels) {
            if (level != null) {
                created++;
            }
        }
        long nonZero = amplitudes.stream().filter(a -> a != 0.0).count();
        if (created != nonZero) {
            throw new IllegalStateException("Failed to create correct number of noise levels for given non-zero amplitudes");
        }
        if (top < size - 1) {
            throw new IllegalArgumentException("Positive octaves are temporarily disabled");
        }
        double frequency = Math.pow(2.0, (double) (-top));
        double amplitude = Math.pow(2.0, (double) (size - 1)) / (Math.pow(2.0, (double) size) - 1.0);
        NoiseStack.Builder builder = NoiseStack.builder();
        for (int i = 0; i < levels.length; i++) {
            PerlinNoise level = levels[i];
            if (level != null) {
                builder.add(level, frequency, (float) (amplitude * amplitudes.get(i)));
            }
            frequency *= 2.0;
            amplitude /= 2.0;
        }
        return builder.build();
    }

    private static void skipOctave(RandomSource random) {
        random.consumeCount(262);
    }
}
