package com.example.worldgen.material;

/**
 * Port of {@code net.minecraft.world.level.levelgen.WorldGenerationContext} (26.3) as the material rules use it:
 * {@code minY = max(heightAccessor.getMinY(), generator.getMinY())}, {@code height = min(heightAccessor.getHeight(),
 * generator.getGenDepth())}, {@code seaLevel = generator.getSeaLevel()} (public constructor, read from bytecode).
 * For the overworld noise settings and a -64..319 chunk this is {@code (-64, 384, 63)}.
 *
 * @param minGenY  {@code getMinGenY()}
 * @param genDepth {@code getGenDepth()}
 * @param seaLevel {@code seaLevel()}
 */
public record WorldGenerationContext(int minGenY, int genDepth, int seaLevel) {

    /** The public constructor of vanilla: chunk height range combined with the generator's noise settings. */
    public static WorldGenerationContext of(int chunkMinY, int chunkHeight, int generatorMinY, int generatorGenDepth,
                                            int generatorSeaLevel) {
        return new WorldGenerationContext(Math.max(chunkMinY, generatorMinY), Math.min(chunkHeight, generatorGenDepth),
                generatorSeaLevel);
    }
}
