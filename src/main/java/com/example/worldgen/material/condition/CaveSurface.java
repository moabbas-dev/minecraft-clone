package com.example.worldgen.material.condition;

/**
 * The serialized names of {@code net.minecraft.world.level.levelgen.placement.CaveSurface} (26.3): {@code CEILING}
 * is {@code "ceiling"}, {@code FLOOR} is {@code "floor"} (only the identity matters to {@link StoneDepthCondition}).
 */
public enum CaveSurface {
    CEILING("ceiling"),
    FLOOR("floor");

    private final String serializedName;

    CaveSurface(String serializedName) {
        this.serializedName = serializedName;
    }

    public String getSerializedName() {
        return serializedName;
    }

    public static CaveSurface byName(String name) {
        for (CaveSurface surface : values()) {
            if (surface.serializedName.equals(name)) {
                return surface;
            }
        }
        throw new IllegalArgumentException("Unknown element name: " + name);
    }
}
