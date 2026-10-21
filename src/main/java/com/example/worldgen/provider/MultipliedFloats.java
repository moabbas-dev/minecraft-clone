package com.example.worldgen.provider;

import com.example.worldgen.random.RandomSource;

/**
 * Port of {@code net.minecraft.util.valueproviders.MultipliedFloats} (26.3): {@code result = 1.0f}, then
 * {@code result *= value.sample(random)} for each value in order. Not a registered JSON type.
 */
public final class MultipliedFloats implements SampledFloat {

    private final SampledFloat[] values;

    public MultipliedFloats(SampledFloat... values) {
        this.values = values;
    }

    @Override
    public float sample(RandomSource random) {
        float result = 1.0F;
        for (SampledFloat value : values) {
            result *= value.sample(random);
        }
        return result;
    }
}
