package com.example.worldgen.density;

/**
 * Port of the parts of {@code net.minecraft.core.Direction.Axis} (26.3) the density functions use: the three
 * axes with their serialized names {@code "x"}, {@code "y"}, {@code "z"} (its {@code StringRepresentable} codec)
 * and {@code choose(int, int, int)}.
 */
public enum Axis {
    X("x"),
    Y("y"),
    Z("z");

    private final String name;

    Axis(String name) {
        this.name = name;
    }

    public String getSerializedName() {
        return name;
    }

    /** {@code Direction.Axis.byName}: the axis with this serialized name, or {@code null}. */
    public static Axis byName(String name) {
        for (Axis axis : values()) {
            if (axis.name.equals(name)) {
                return axis;
            }
        }
        return null;
    }

    /** {@code Direction.Axis.choose(int, int, int)}. */
    public int choose(int x, int y, int z) {
        return switch (this) {
            case X -> x;
            case Y -> y;
            case Z -> z;
        };
    }

    @Override
    public String toString() {
        return name;
    }
}
