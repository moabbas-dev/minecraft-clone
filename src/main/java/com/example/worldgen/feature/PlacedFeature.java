package com.example.worldgen.feature;

import com.example.worldgen.feature.placement.PlacementModifier;

import java.util.List;

/**
 * Port of {@code net.minecraft.world.level.levelgen.placement.PlacedFeature} (26.3, {@code record(Holder<Feature> feature,
 * List<PlacementModifier> placement)}). {@code id} is the registry id of a named placed feature (null for an inline one).
 * For a feature that is {@link UnportedFeature unported} the modifiers are not decoded ({@code placement} is empty and
 * {@code rawPlacement} keeps the JSON).
 */
public record PlacedFeature(String id, Feature feature, List<PlacementModifier> placement, Object rawPlacement) {

    /** {@code PlacedFeature.place(level, generator, random, origin)}: a {@link FeaturePlacer} without biome check. */
    public boolean place(com.example.worldgen.feature.level.GenRegion level, DecorationGenerator generator,
                         com.example.worldgen.random.RandomSource random, Pos origin) {
        return new FeaturePlacer(level, generator).place(this, random, origin);
    }

    @Override
    public String toString() {
        return id != null ? id : "Placed " + feature.type();
    }
}
