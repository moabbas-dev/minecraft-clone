package com.example.worldgen.density;

/**
 * The two members of {@code net.minecraft.world.level.levelgen.blending.Blender} (26.3) that
 * {@code minecraft:blend_density} calls through {@link DensityContextKeys#BLENDER}: {@code isEmpty()} and
 * {@code blendDensity(blockX, blockY, blockZ, density)}. The blender itself (old-chunk blending) belongs to the
 * NoiseChunk stage and implements this interface there; when no blender is in the context the input is returned
 * unchanged, exactly as vanilla does for an absent or empty blender.
 */
public interface DensityBlender {

    boolean isEmpty();

    float blendDensity(int blockX, int blockY, int blockZ, float density);
}
