package com.example.worldgen.material;

/**
 * Optional capability of the chunk handed to {@link MaterialSystem}: vanilla's {@code BlockColumn.setBlock} in
 * {@code MaterialSystem.buildSurface} calls {@code ChunkAccess.markPosForPostProcessing(pos)} after placing a state whose
 * fluid state is not empty ({@code MaterialSystem$1.setBlock}, bytecode). A chunk that keeps post-processing marks
 * implements this; others are written without marks.
 */
public interface FluidPostProcessing {
    void markPosForPostProcessing(int x, int y, int z);
}
