package com.example.worldgen.density;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.DensityBufferArena} (26.3). {@link #GLOBAL}
 * allocates a fresh buffer every time and ignores releases.
 */
public interface DensityBufferArena {

    DensityBufferArena GLOBAL = new DensityBufferArena() {
        @Override
        public ScopedDensityBuffer acquire(int size) {
            return new ScopedDensityBuffer(this, size, size);
        }

        @Override
        public void release(ScopedDensityBuffer buffer) {
        }
    };

    ScopedDensityBuffer acquire(int size);

    void release(ScopedDensityBuffer buffer);
}
