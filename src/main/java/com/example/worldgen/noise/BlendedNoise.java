package com.example.worldgen.noise;

import com.example.worldgen.Interval;
import com.example.worldgen.Mth;
import com.example.worldgen.random.RandomSource;

/**
 * Port of {@code net.minecraft.world.level.levelgen.synth.BlendedNoise} (26.3), the
 * {@code minecraft:old_blended_noise} density function behind {@code overworld/base_3d_noise}.
 * Constants from the 26.3 bytecode: {@code BASE_SCALE = 684.412}, limit noises start at octave
 * -15 with amplitude factor {@code 0.9999847412109375}, the main noise starts at octave -7 with
 * factor {@code 12.75}; the random source comes from {@code createRandom("minecraft:terrain")}.
 *
 * <p>Evaluation ({@code compileSampler}): with {@code xz = 684.412 * xzScale} and
 * {@code yv = 684.412 * yScale}, {@code alpha = clamp(main(x*xz/xzFactor, y*yv/yFactor,
 * z*xz/xzFactor) + 0.5, 0, 1)} and the result is {@code min} when alpha is 0, {@code max} when it
 * is 1, otherwise {@code lerp(alpha, min, max)} of the two limit noises sampled at
 * {@code (x*xz, y*yv, z*xz)}, all in float. Vanilla composes this from its
 * {@code NoiseFunction/ConstAdd/Clamp/Lerp} samplers; {@link Sampler} reproduces that composition
 * for single block positions.
 */
public record BlendedNoise(double xzScale, double yScale, double xzFactor, double yFactor, double smearScaleMultiplier) {

    /** The {@code Identifier} vanilla hashes for this noise's random source. */
    public static final String NOISE_SEED = "minecraft:terrain";

    private static final double BASE_SCALE = 684.412;
    private static final double LIMIT_FACTOR = 0.9999847412109375;
    private static final double MAIN_FACTOR = 12.75;
    private static final int LIMIT_FIRST_OCTAVE = -15;
    private static final int MAIN_FIRST_OCTAVE = -7;

    private double xzMultiplier() {
        return BASE_SCALE * xzScale;
    }

    private double yMultiplier() {
        return BASE_SCALE * yScale;
    }

    /** Creates min-limit, max-limit and main stacks, in that order, from one random source. */
    public FbmSet createFbmSet(RandomSource random) {
        double limitSmear = yMultiplier() * smearScaleMultiplier;
        double mainSmear = limitSmear / yFactor;
        return new FbmSet(
                createFbm(random, LIMIT_FIRST_OCTAVE, limitSmear, LIMIT_FACTOR),
                createFbm(random, LIMIT_FIRST_OCTAVE, limitSmear, LIMIT_FACTOR),
                createFbm(random, MAIN_FIRST_OCTAVE, mainSmear, MAIN_FACTOR));
    }

    /**
     * {@code BlendedNoise.createFbm}: {@code 1 - firstOctave} smeared Perlin octaves created from
     * the lowest frequency layer index down (frequency 1, 1/2, 1/4, ... with amplitude doubling),
     * each smeared at {@code smearScale * frequency}.
     */
    public static NoiseStack createFbm(RandomSource random, int firstOctave, double smearScale, double factor) {
        if (firstOctave > 0) {
            throw new IllegalArgumentException("firstOctave>0");
        }
        int count = -firstOctave + 1;
        double frequency = 1.0;
        double amplitude = factor / (Math.pow(2.0, (double) count) - 1.0);
        NoiseStack.Builder builder = NoiseStack.builder();
        for (int i = count - 1; i >= 0; i--) {
            builder.add(new SmearedPerlinNoise(random, smearScale * frequency), frequency, (float) amplitude);
            frequency /= 2.0;
            amplitude *= 2.0;
        }
        return builder.build();
    }

    private static Interval computeFbmRange(int firstOctave, double smearScale, double factor) {
        int count = -firstOctave + 1;
        double frequency = 1.0;
        double amplitude = factor / (Math.pow(2.0, (double) count) - 1.0);
        Interval total = Interval.ofExact(0.0F);
        for (int i = count - 1; i >= 0; i--) {
            Interval layer = Interval.mul(SmearedPerlinNoise.range(smearScale * frequency), Interval.ofExact((float) amplitude));
            total = Interval.add(total, layer);
            frequency /= 2.0;
            amplitude *= 2.0;
        }
        return total;
    }

    /** The output range: that of one limit stack. */
    public Interval range() {
        return computeFbmRange(LIMIT_FIRST_OCTAVE, yMultiplier() * smearScaleMultiplier, LIMIT_FACTOR);
    }

    /** Vanilla's {@code compileSampler(RandomSource)} for point evaluation. */
    public Sampler compileSampler(RandomSource random) {
        FbmSet set = createFbmSet(random);
        double xz = xzMultiplier();
        double y = yMultiplier();
        return new Sampler(set, xz, y, xz / xzFactor, y / yFactor);
    }

    /** {@code BlendedNoise.FbmSet}. */
    public record FbmSet(NoiseStack minLimitNoise, NoiseStack maxLimitNoise, NoiseStack mainNoise) {
    }

    /** The compiled {@code Lerp(Clamp(ConstAdd(main, 0.5), 0, 1), minLimit, maxLimit)} sampler. */
    public record Sampler(FbmSet noises, double limitXzScale, double limitYScale, double mainXzScale, double mainYScale) {

        public float sampleValue(int blockX, int blockY, int blockZ) {
            float main = noises.mainNoise.get((double) blockX * mainXzScale, (double) blockY * mainYScale,
                    (double) blockZ * mainXzScale);
            float alpha = Mth.clamp(main + 0.5F, 0.0F, 1.0F);
            if (alpha == 0.0F) {
                return limit(noises.minLimitNoise, blockX, blockY, blockZ);
            }
            if (alpha == 1.0F) {
                return limit(noises.maxLimitNoise, blockX, blockY, blockZ);
            }
            return Mth.lerp(alpha, limit(noises.minLimitNoise, blockX, blockY, blockZ),
                    limit(noises.maxLimitNoise, blockX, blockY, blockZ));
        }

        private float limit(NoiseStack noise, int blockX, int blockY, int blockZ) {
            return noise.get((double) blockX * limitXzScale, (double) blockY * limitYScale, (double) blockZ * limitXzScale);
        }
    }
}
