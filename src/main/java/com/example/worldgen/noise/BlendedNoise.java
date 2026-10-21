package com.example.worldgen.noise;

import com.example.worldgen.Interval;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DfRewriteRule;
import com.example.worldgen.density.generator.NoiseFunction;
import com.example.worldgen.density.op.BinaryFunction;
import com.example.worldgen.density.op.ClampFunction;
import com.example.worldgen.density.op.LerpFunction;
import com.example.worldgen.random.RandomSource;

/**
 * Port of {@code net.minecraft.world.level.levelgen.synth.BlendedNoise} (26.3), the density function
 * {@code minecraft:old_blended_noise} ({@code xz_scale}, {@code y_scale}, {@code xz_factor}, {@code y_factor} in
 * [0.001, 1000], {@code smear_scale_multiplier} in [1, 8]) behind {@code overworld/base_3d_noise}. Constants from
 * the 26.3 bytecode: {@code BASE_SCALE = 684.412}, limit noises start at octave -15 with amplitude factor
 * {@code 0.9999847412109375}, the main noise starts at octave -7 with factor {@code 12.75}; the random source is
 * {@code createRandom("minecraft:terrain")}.
 *
 * <p>{@link #compileSampler(RandomSource)} composes vanilla's samplers exactly:
 * {@code Lerp(Clamp(ConstAdd(main, 0.5), 0, 1), minLimit, maxLimit)} where the limit noises are sampled at
 * {@code (x, y, z) * (684.412 * xzScale, 684.412 * yScale)} and the main noise at those scales divided by
 * {@code xzFactor} / {@code yFactor}.
 */
public record BlendedNoise(double xzScale, double yScale, double xzFactor, double yFactor, double smearScaleMultiplier)
        implements DensityFunction {

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
     * {@code BlendedNoise.createFbm}: {@code 1 - firstOctave} smeared Perlin octaves created from the lowest
     * frequency layer index down (frequency 1, 1/2, 1/4, ... with amplitude doubling), each smeared at
     * {@code smearScale * frequency}.
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

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return compileSampler(context.createRandom(NOISE_SEED));
    }

    /** {@code BlendedNoise.compileSampler(RandomSource)}. */
    public DensitySampler compileSampler(RandomSource random) {
        FbmSet fbms = createFbmSet(random);
        double xz = xzMultiplier();
        double y = yMultiplier();
        DensitySampler minLimitNoise = new NoiseFunction.Sampler(fbms.minLimitNoise(), xz, y);
        DensitySampler maxLimitNoise = new NoiseFunction.Sampler(fbms.maxLimitNoise(), xz, y);
        DensitySampler mainNoise = new NoiseFunction.Sampler(fbms.mainNoise(), xz / xzFactor, y / yFactor);
        DensitySampler choice = new ClampFunction.Sampler(new BinaryFunction.ConstAddSampler(mainNoise, 0.5F), 0.0F, 1.0F);
        return new LerpFunction.Sampler(choice, minLimitNoise, maxLimitNoise);
    }

    /** The output range: that of one limit stack. */
    @Override
    public Interval range() {
        return computeFbmRange(LIMIT_FIRST_OCTAVE, yMultiplier() * smearScaleMultiplier, LIMIT_FACTOR);
    }

    @Override
    public int domainAxes() {
        return ALL_AXES;
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        return this;
    }

    /** {@code BlendedNoise.FbmSet}. */
    public record FbmSet(NoiseStack minLimitNoise, NoiseStack maxLimitNoise, NoiseStack mainNoise) {
    }
}
