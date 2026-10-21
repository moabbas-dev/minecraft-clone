package com.example.worldgen.provider;

import com.example.worldgen.Mth;
import com.example.worldgen.random.RandomSource;

import java.util.Map;

/**
 * Port of {@code net.minecraft.util.valueproviders.FloatProvider} (26.3, extends {@code SampledFloat}) and every
 * type registered by {@code FloatProviders.bootstrap}: {@code constant}, {@code uniform}, {@code clamped_normal},
 * {@code trapezoid}. ({@code MultipliedFloats} is a code-only {@link SampledFloat}, not registered.) The nested
 * records keep the vanilla class names; {@code sample} bodies are transcribed from the bytecode.
 */
public interface FloatProvider extends SampledFloat {

    float min();

    float max();

    /** {@code FloatProviders.CODEC}: {@code either(Codec.FLOAT, dispatch("type"))}; a bare number is a {@link ConstantFloat}. */
    static FloatProvider parse(Object json) {
        if (CodecJson.isNumber(json)) {
            return ConstantFloat.of(CodecJson.floatValue(json, "float provider"));
        }
        Map<String, Object> m = CodecJson.object(json, "float provider");
        String type = CodecJson.type(m);
        switch (type) {
            case "constant":
                return ConstantFloat.of(CodecJson.floatField(m, "value"));
            case "uniform": {
                float min = CodecJson.floatField(m, "min_inclusive");
                float max = CodecJson.floatField(m, "max_exclusive");
                if (max <= min) {
                    throw new CodecJson.CodecException("Max must be larger than min: [" + min + ", " + max + "]");
                }
                return new UniformFloat(min, max);
            }
            case "clamped_normal": {
                float mean = CodecJson.floatField(m, "mean");
                float deviation = CodecJson.floatField(m, "deviation");
                float min = CodecJson.floatField(m, "min");
                float max = CodecJson.floatField(m, "max");
                if (max < min) {
                    throw new CodecJson.CodecException("Max must be larger than min: [" + min + ", " + max + "]");
                }
                return new ClampedNormalFloat(mean, deviation, min, max);
            }
            case "trapezoid": {
                float min = CodecJson.floatField(m, "min");
                float max = CodecJson.floatField(m, "max");
                float plateau = CodecJson.floatField(m, "plateau");
                if (max < min) {
                    throw new CodecJson.CodecException("Max must be larger than min: [" + min + ", " + max + "]");
                }
                if (plateau > max - min) {
                    throw new CodecJson.CodecException("Plateau can at most be the full span: [" + min + ", " + max + "]");
                }
                return new TrapezoidFloat(min, max, plateau);
            }
            default:
                throw new CodecJson.CodecException("Unknown float provider type: " + type);
        }
    }

    /** {@code FloatProviders.codec(min)}: {@code provider.min() >= min}. */
    static FloatProvider parse(Object json, float min) {
        FloatProvider p = parse(json);
        if (p.min() < min) {
            throw new CodecJson.CodecException("Value provider too low: " + min + " [" + p.min() + "-" + p.max() + "]");
        }
        return p;
    }

    /** {@code FloatProviders.codec(min, max)}: {@code provider.min() >= min} and {@code provider.max() <= max}. */
    static FloatProvider parse(Object json, float min, float max) {
        FloatProvider p = parse(json, min);
        if (p.max() > max) {
            throw new CodecJson.CodecException("Value provider too high: " + max + " [" + p.min() + "-" + p.max() + "]");
        }
        return p;
    }

    /** {@code ConstantFloat}. */
    record ConstantFloat(float value) implements FloatProvider {
        public static final ConstantFloat ZERO = new ConstantFloat(0.0F);

        public static ConstantFloat of(float value) {
            return value == 0.0F ? ZERO : new ConstantFloat(value);
        }

        @Override
        public float sample(RandomSource random) {
            return value;
        }

        @Override
        public float min() {
            return value;
        }

        @Override
        public float max() {
            return value;
        }
    }

    /** {@code UniformFloat}: {@code Mth.randomBetween(random, min, max)}; {@code max} is exclusive. */
    record UniformFloat(float min, float max) implements FloatProvider {
        public static UniformFloat of(float min, float max) {
            if (max <= min) {
                throw new IllegalArgumentException("Max must exceed min");
            }
            return new UniformFloat(min, max);
        }

        @Override
        public float sample(RandomSource random) {
            return Mth.randomBetween(random, min, max);
        }
    }

    /** {@code ClampedNormalFloat}: {@code Mth.clamp(Mth.normal(random, mean, deviation), min, max)}. */
    record ClampedNormalFloat(float mean, float deviation, float min, float max) implements FloatProvider {
        @Override
        public float sample(RandomSource random) {
            return sample(random, mean, deviation, min, max);
        }

        public static float sample(RandomSource random, float mean, float deviation, float min, float max) {
            return Mth.clamp(Mth.normal(random, mean, deviation), min, max);
        }
    }

    /**
     * {@code TrapezoidFloat}: {@code range = max - min}, {@code slope = (range - plateau) / 2},
     * {@code min + nextFloat() * (range - slope) + nextFloat() * slope} (float arithmetic, left to right).
     */
    record TrapezoidFloat(float min, float max, float plateau) implements FloatProvider {
        @Override
        public float sample(RandomSource random) {
            float range = max - min;
            float slope = (range - plateau) / 2.0F;
            float top = range - slope;
            return min + random.nextFloat() * top + random.nextFloat() * slope;
        }
    }
}
