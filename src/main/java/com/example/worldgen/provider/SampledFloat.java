package com.example.worldgen.provider;

import com.example.worldgen.random.RandomSource;

/** Port of {@code net.minecraft.util.valueproviders.SampledFloat} (26.3): anything that samples a float. */
@FunctionalInterface
public interface SampledFloat {

    float sample(RandomSource random);
}
