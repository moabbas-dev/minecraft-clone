package com.example.worldgen.density;

import com.example.worldgen.Mth;

/** Port of {@code net.minecraft.world.level.levelgen.densityfunction.DistanceMetric} (26.3). */
public enum DistanceMetric {
    EUCLIDEAN("euclidean"),
    EUCLIDEAN_SQUARED("euclidean_squared"),
    MANHATTAN("manhattan"),
    CHEBYSHEV("chebyshev");

    private final String name;

    DistanceMetric(String name) {
        this.name = name;
    }

    public String getSerializedName() {
        return name;
    }

    public static DistanceMetric byName(String name) {
        for (DistanceMetric metric : values()) {
            if (metric.name.equals(name)) {
                return metric;
            }
        }
        return null;
    }

    public float compute(float deltaX, float deltaY, float deltaZ) {
        return switch (this) {
            case EUCLIDEAN -> Mth.length(deltaX, deltaY, deltaZ);
            case EUCLIDEAN_SQUARED -> Mth.lengthSquared(deltaX, deltaY, deltaZ);
            case MANHATTAN -> Math.abs(deltaX) + Math.abs(deltaY) + Math.abs(deltaZ);
            case CHEBYSHEV -> Math.max(Math.max(Math.abs(deltaX), Math.abs(deltaY)), Math.abs(deltaZ));
        };
    }

    public float compute(float deltaX, float deltaZ) {
        return switch (this) {
            case EUCLIDEAN -> Mth.length(deltaX, deltaZ);
            case EUCLIDEAN_SQUARED -> Mth.lengthSquared(deltaX, deltaZ);
            case MANHATTAN -> Math.abs(deltaX) + Math.abs(deltaZ);
            case CHEBYSHEV -> Math.max(Math.abs(deltaX), Math.abs(deltaZ));
        };
    }
}
