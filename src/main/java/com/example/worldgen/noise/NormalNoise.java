package com.example.worldgen.noise;

import com.example.worldgen.Interval;
import com.example.worldgen.Mth;
import com.example.worldgen.random.PositionalRandomFactory;
import com.example.worldgen.random.RandomSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Port of {@code net.minecraft.world.level.levelgen.synth.NormalNoise} (26.3). In 26.3 this class
 * is the decoded form of a {@code worldgen/noise/*.json} entry (fields {@code base_amplitude},
 * {@code base_octave}, {@code octave_count}, {@code normalize}, {@code amplitude_modifiers}); it
 * precomputes the octave list and a normalisation factor and {@link #create(RandomSource)} turns
 * it into a {@link NoiseStack} of two interleaved Perlin octave sets, the second sampled at
 * {@code INPUT_FACTOR = 1.0181268882175227} times the frequency. Each octave's random source is
 * {@code factory.fromHashOf("octave_" + octaveIndex)}. Constants from the 26.3 bytecode:
 * {@code TARGET_DEVIATION = 1/3}, Perlin {@code STANDARD_DEVIATION = 0.2702247831245211}.
 */
public final class NormalNoise {

    private static final double INPUT_FACTOR = 1.0181268882175227;
    public static final double TARGET_DEVIATION = 0.3333333333333333;

    private final Parameters parameters;
    private final List<OctaveInfo> octaves;
    private final double normalizationFactor;
    private final Interval range;

    private NormalNoise(Parameters parameters) {
        this.parameters = parameters;
        this.octaves = buildOctaves(parameters.baseOctave, parameters.baseAmplitude, parameters.octaveCount,
                parameters.normalize != Normalization.DISABLED, parameters.amplitudeModifiers);
        double amplitudeSum = octaves.stream().mapToDouble(OctaveInfo::absAmplitude).sum();
        double factor = computeNormalizationFactor(amplitudeSum, octaves);
        if (parameters.normalize == Normalization.LEGACY && factor != 0.0) {
            double parityFactor = computeParityNormalizationFactor(parameters.baseAmplitude, parameters.octaveCount,
                    parameters.amplitudeModifiers);
            amplitudeSum *= parityFactor / factor;
            factor = parityFactor;
        }
        this.normalizationFactor = factor;
        this.range = Interval.ofSymmetric((float) (amplitudeSum * TARGET_DEVIATION * 6.0));
    }

    /**
     * Builds a NormalNoise from decoded parameters (vanilla's {@code DIRECT_CODEC} maps
     * {@code Parameters} to {@code new NormalNoise(parameters)} the same way).
     */
    public static NormalNoise of(Parameters parameters) {
        return new NormalNoise(parameters);
    }

    private static List<OctaveInfo> buildOctaves(int baseOctave, double baseAmplitude, int octaveCount,
                                                 boolean normalize, List<Double> amplitudeModifiers) {
        double frequency = Math.pow(2.0, (double) baseOctave);
        double amplitude = baseAmplitude;
        if (normalize) {
            amplitude *= Math.pow(0.5, (double) (-(octaveCount - 1))) / (Math.pow(0.5, (double) (-octaveCount)) - 1.0);
        }
        List<OctaveInfo> list = new ArrayList<>(octaveCount);
        for (int i = 0; i < octaveCount; i++) {
            double modifier = getAmplitudeModifier(amplitudeModifiers, i);
            if (modifier != 0.0) {
                double octaveAmplitude = amplitude * modifier;
                list.add(new OctaveInfo(baseOctave + i, frequency, octaveAmplitude));
            }
            frequency *= 2.0;
            amplitude *= 0.5;
        }
        return Collections.unmodifiableList(list);
    }

    private static double getAmplitudeModifier(List<Double> modifiers, int index) {
        return modifiers.isEmpty() ? 1.0 : modifiers.get(index);
    }

    private static double computeNormalizationFactor(double amplitudeSum, List<OctaveInfo> octaves) {
        double deviation = estimateDeviation(octaves);
        if (deviation == 0.0) {
            return 0.0;
        }
        double sampledDeviation = deviation * Math.sqrt(2.0);
        double target = amplitudeSum * TARGET_DEVIATION;
        return target / sampledDeviation;
    }

    private static double estimateDeviation(List<OctaveInfo> octaves) {
        double sum = 0.0;
        for (OctaveInfo octave : octaves) {
            double d = PerlinNoise.STANDARD_DEVIATION * octave.absAmplitude();
            sum += Mth.square(d);
        }
        return Math.sqrt(sum);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** {@code NormalNoise.createParity(int, DoubleList)}: the legacy-normalised form of the old API. */
    public static NormalNoise createParity(int baseOctave, List<Double> amplitudes) {
        if (amplitudes.isEmpty()) {
            throw new IllegalArgumentException("Need at least 1 amplitude");
        }
        Builder builder = builder();
        builder.setBaseOctave(baseOctave);
        builder.setOctaveCount(amplitudes.size());
        builder.setBaseAmplitude(computeParityBaseAmplitude(builder.baseOctave, amplitudes));
        for (int i = 0; i < amplitudes.size(); i++) {
            double a = amplitudes.get(i);
            if (a != 1.0) {
                builder.setAmplitudeModifier(i, a);
            }
        }
        return builder.build();
    }

    public static NormalNoise createParity(int baseOctave, double... amplitudes) {
        List<Double> list = new ArrayList<>(amplitudes.length);
        for (double a : amplitudes) {
            list.add(a);
        }
        return createParity(baseOctave, list);
    }

    private static double computeParityBaseAmplitude(int baseOctave, List<Double> amplitudes) {
        List<OctaveInfo> list = buildOctaves(baseOctave, 1.0, amplitudes.size(), true, amplitudes);
        double sum = list.stream().mapToDouble(OctaveInfo::absAmplitude).sum();
        double factor = computeNormalizationFactor(sum, list);
        if (factor == 0.0) {
            return 1.0;
        }
        double parity = computeParityNormalizationFactor(1.0, amplitudes.size(), amplitudes);
        return parity / factor;
    }

    private static double computeParityNormalizationFactor(double baseAmplitude, int octaveCount, List<Double> modifiers) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < octaveCount; i++) {
            double m = getAmplitudeModifier(modifiers, i);
            if (m != 0.0) {
                min = Math.min(min, i);
                max = Math.max(max, i);
            }
        }
        return baseAmplitude * 0.5 * TARGET_DEVIATION / parityExpectedDeviation(max - min);
    }

    private static double parityExpectedDeviation(int span) {
        return 0.1 * (1.0 + 1.0 / (double) (span + 1));
    }

    /** Builds the sampled noise: two positional forks, one Perlin octave per entry from each, interleaved. */
    public Noise create(RandomSource random) {
        PositionalRandomFactory first = random.forkPositional();
        PositionalRandomFactory second = random.forkPositional();
        NoiseStack.Builder builder = NoiseStack.builder();
        for (OctaveInfo octave : octaves) {
            String seed = octave.seed();
            PerlinNoise a = new PerlinNoise(first.fromHashOf(seed));
            PerlinNoise b = new PerlinNoise(second.fromHashOf(seed));
            float amplitude = (float) (normalizationFactor * octave.amplitude);
            builder.add(a, octave.frequency, amplitude);
            builder.add(b, octave.frequency * INPUT_FACTOR, amplitude);
        }
        return builder.build();
    }

    /** The pre-1.18 construction (shared random, {@link LegacyFbmInitializer}) used by the legacy nether biome source. */
    public Noise createForLegacyNetherBiome(RandomSource random) {
        List<Double> modifiers = parameters.amplitudeModifiers;
        if (modifiers.isEmpty()) {
            modifiers = new ArrayList<>(Collections.nCopies(parameters.octaveCount, 1.0));
        }
        NoiseStack a = LegacyFbmInitializer.createForLegacyNetherBiome(random, parameters.baseOctave, modifiers);
        NoiseStack b = LegacyFbmInitializer.createForLegacyNetherBiome(random, parameters.baseOctave, modifiers);
        float amplitude = (float) (normalizationFactor * parameters.baseAmplitude);
        return NoiseStack.builder().addStack(a, 1.0, amplitude).addStack(b, INPUT_FACTOR, amplitude).build();
    }

    public Interval range() {
        return range;
    }

    public Parameters parameters() {
        return parameters;
    }

    @Override
    public boolean equals(Object o) {
        return o == this || o instanceof NormalNoise other && parameters.equals(other.parameters);
    }

    @Override
    public int hashCode() {
        return parameters.hashCode();
    }

    @Override
    public String toString() {
        return "NormalNoise[" + parameters + "]";
    }

    /** Vanilla's debug description; formats amplitudes with {@code %.2f} like the 26.3 code. */
    public void parityConfigString(StringBuilder sb, RandomSource random) {
        sb.append("NormalNoise {");
        sb.append("first: ");
        perlinParityConfigString(sb, random);
        sb.append(", second: ");
        perlinParityConfigString(sb, random);
        sb.append("}");
    }

    private void perlinParityConfigString(StringBuilder sb, RandomSource random) {
        sb.append("PerlinNoise{");
        List<String> amplitudes = new ArrayList<>();
        for (int i = 0; i < parameters.octaveCount; i++) {
            amplitudes.add(String.format(Locale.ROOT, "%.2f", getAmplitudeModifier(parameters.amplitudeModifiers, i)));
        }
        sb.append("first octave: ").append(parameters.baseOctave).append(", amplitudes: ").append(amplitudes)
                .append(", noise levels: [");
        PositionalRandomFactory factory = random.forkPositional();
        for (int i = 0; i < parameters.octaveCount; i++) {
            sb.append(i).append(": ");
            int index = parameters.baseOctave + i;
            OctaveInfo found = null;
            for (OctaveInfo octave : octaves) {
                if (octave.octaveIndex == index) {
                    found = octave;
                    break;
                }
            }
            if (found != null) {
                new PerlinNoise(factory.fromHashOf(found.seed())).parityConfigString(sb);
            } else {
                sb.append("null");
            }
            sb.append(", ");
        }
        sb.append("]");
        sb.append("}");
    }

    /** {@code NormalNoise.Normalization}: JSON {@code true}, {@code false} or {@code "legacy"}. */
    public enum Normalization {
        DISABLED,
        ENABLED,
        LEGACY
    }

    /**
     * {@code NormalNoise.Parameters}: the decoded noise definition. {@code amplitudeModifiers} is
     * either empty (all 1.0) or exactly {@code octaveCount} long.
     */
    public record Parameters(double baseAmplitude, int baseOctave, int octaveCount, Normalization normalize,
                             List<Double> amplitudeModifiers) {

        public Parameters {
            Objects.requireNonNull(normalize, "normalize");
            amplitudeModifiers = List.copyOf(amplitudeModifiers);
        }
    }

    /** {@code NormalNoise.OctaveInfo}: one present octave. */
    record OctaveInfo(int octaveIndex, double frequency, double amplitude) {

        String seed() {
            return "octave_" + octaveIndex;
        }

        double absAmplitude() {
            return Math.abs(amplitude);
        }
    }

    /** {@code NormalNoise.Builder} with vanilla's defaults (amplitude 1, one octave, normalisation on). */
    public static class Builder {

        private double baseAmplitude = 1.0;
        private int baseOctave;
        private int octaveCount = 1;
        private Normalization normalize = Normalization.ENABLED;
        private List<Double> amplitudeModifiers = List.of();

        private Builder() {
        }

        public Builder setBaseAmplitude(double baseAmplitude) {
            this.baseAmplitude = baseAmplitude;
            return this;
        }

        public Builder setBaseOctave(int baseOctave) {
            this.baseOctave = baseOctave;
            return this;
        }

        public Builder setOctaveCount(int octaveCount) {
            if (!amplitudeModifiers.isEmpty()) {
                throw new IllegalArgumentException("Cannot set octave count after setting amplitude modifier");
            }
            this.octaveCount = octaveCount;
            return this;
        }

        public Builder setNormalize(boolean normalize) {
            this.normalize = normalize ? Normalization.ENABLED : Normalization.DISABLED;
            return this;
        }

        public Builder setLegacyNormalization() {
            this.normalize = Normalization.LEGACY;
            return this;
        }

        public Builder setAmplitudeModifier(int index, double modifier) {
            if (index < 0 || index >= octaveCount) {
                throw new IllegalArgumentException(index + " outside of octave range [0; " + octaveCount + ")");
            }
            if (amplitudeModifiers.isEmpty()) {
                amplitudeModifiers = new ArrayList<>(octaveCount);
                for (int i = 0; i < octaveCount; i++) {
                    amplitudeModifiers.add(1.0);
                }
            }
            amplitudeModifiers.set(index, modifier);
            return this;
        }

        public NormalNoise build() {
            return new NormalNoise(new Parameters(baseAmplitude, baseOctave, octaveCount, normalize, amplitudeModifiers));
        }
    }
}
