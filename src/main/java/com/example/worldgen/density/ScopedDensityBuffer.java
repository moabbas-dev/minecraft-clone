package com.example.worldgen.density;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer} (26.3): a buffer
 * borrowed from a {@link DensityBufferArena} and handed back on {@link #close()}.
 */
public class ScopedDensityBuffer extends DensityBuffer implements AutoCloseable {

    private final DensityBufferArena arena;
    private int age;
    private boolean closed;

    ScopedDensityBuffer(DensityBufferArena arena, int capacity, int size) {
        super(capacity);
        this.size = size;
        this.arena = arena;
    }

    void restore(int size) {
        if (size > values.length) {
            throw new IllegalArgumentException("Cannot set size larger than buffer capacity");
        }
        if (!closed) {
            throw new IllegalArgumentException("Buffer is already in use");
        }
        this.age = 0;
        this.closed = false;
        this.size = size;
    }

    int incrementAge() {
        return ++age;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            arena.release(this);
        }
    }
}
