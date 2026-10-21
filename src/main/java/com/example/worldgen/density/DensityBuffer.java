package com.example.worldgen.density;

import java.util.Arrays;

/** Port of {@code net.minecraft.world.level.levelgen.densityfunction.DensityBuffer} (26.3): a float array view. */
public class DensityBuffer {

    protected final float[] values;
    protected int size;

    protected DensityBuffer(int size) {
        this.values = new float[size];
        this.size = size;
    }

    public static DensityBuffer createUnpooled(int size) {
        return new DensityBuffer(size);
    }

    public void set(int index, float value) {
        values[index] = value;
    }

    public void setRange(int index, int size, float value) {
        Arrays.fill(values, index, index + size, value);
    }

    public void addTo(int index, float value) {
        values[index] += value;
    }

    public float get(int index) {
        return values[index];
    }

    public void fill(float value) {
        Arrays.fill(values, 0, size, value);
    }

    public void copyFrom(DensityBuffer other) {
        if (size() != other.size()) {
            throw new IllegalArgumentException("Cannot copy from buffer with size=" + other.size() + ", expected" + size());
        }
        System.arraycopy(other.values, 0, values, 0, size());
    }

    public int capacity() {
        return values.length;
    }

    public int size() {
        return size;
    }
}
