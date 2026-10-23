package com.example.worldgen.feature.placement;

import com.example.worldgen.feature.DecorationGenerator;
import com.example.worldgen.feature.PlacedFeature;
import com.example.worldgen.feature.Pos;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.provider.WorldGenerationContext;

/**
 * Port of {@code net.minecraft.world.level.levelgen.placement.PlacementContext} (26.3): extends
 * {@code WorldGenerationContext(generator, level)} = {@code minY = max(level.getMinY(), generator.getMinY())},
 * {@code height = min(level.getHeight(), generator.getGenDepth())}, {@code seaLevel = generator.getSeaLevel()};
 * {@code getHeight} is the region's live heightmap, {@code topFeature} is null for {@code FeaturePlacer.place} without biome
 * check.
 */
public final class PlacementContext {

    private final GenRegion level;
    private final DecorationGenerator generator;
    private final PlacedFeature topFeature;
    private final WorldGenerationContext worldGenerationContext;

    public PlacementContext(GenRegion level, DecorationGenerator generator, PlacedFeature topFeature) {
        this.level = level;
        this.generator = generator;
        this.topFeature = topFeature;
        this.worldGenerationContext = WorldGenerationContext.forGenerator(level.getMinY(), level.getHeight(), generator.minY(),
                generator.genDepth(), generator.seaLevel());
    }

    public int getHeight(HeightmapType type, int x, int z) {
        return level.getHeight(type, x, z);
    }

    public StateRef getBlockState(Pos pos) {
        return level.getBlockState(pos);
    }

    public int getMinY() {
        return level.getMinY();
    }

    public GenRegion getLevel() {
        return level;
    }

    public DecorationGenerator generator() {
        return generator;
    }

    /** {@code topFeature()}: null when absent. */
    public PlacedFeature topFeature() {
        return topFeature;
    }

    public WorldGenerationContext worldGenerationContext() {
        return worldGenerationContext;
    }
}
