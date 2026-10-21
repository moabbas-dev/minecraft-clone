package com.example.worldgen.provider;

/**
 * Port of {@code net.minecraft.world.level.levelgen.WorldGenerationContext} (26.3): the generation height range
 * ({@code getMinGenY}, {@code getGenDepth}) and sea level that {@link VerticalAnchor}s resolve against. The generator
 * constructor takes {@code max(accessor.minY, generator.minY)} and {@code min(accessor.height, generator.genDepth)};
 * the overworld gives {@code -64}, {@code 384}, sea level {@code 63}.
 */
public final class WorldGenerationContext {

    private final int minY;
    private final int height;
    private final int seaLevel;

    public WorldGenerationContext(int minY, int height, int seaLevel) {
        this.minY = minY;
        this.height = height;
        this.seaLevel = seaLevel;
    }

    /** {@code new WorldGenerationContext(ChunkGenerator, LevelHeightAccessor)}. */
    public static WorldGenerationContext forGenerator(int accessorMinY, int accessorHeight,
                                                      int generatorMinY, int generatorGenDepth, int generatorSeaLevel) {
        return new WorldGenerationContext(Math.max(accessorMinY, generatorMinY),
                Math.min(accessorHeight, generatorGenDepth), generatorSeaLevel);
    }

    public int getMinGenY() {
        return minY;
    }

    public int getGenDepth() {
        return height;
    }

    public int seaLevel() {
        return seaLevel;
    }
}
