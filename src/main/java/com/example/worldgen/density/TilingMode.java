package com.example.worldgen.density;

/** Port of {@code net.minecraft.world.level.levelgen.densityfunction.TilingMode} (26.3). */
public enum TilingMode {
    CLAMP_TO_EDGE("clamp_to_edge"),
    REPEAT("repeat"),
    MIRRORED_REPEAT("mirrored_repeat");

    private final String name;

    TilingMode(String name) {
        this.name = name;
    }

    public String getSerializedName() {
        return name;
    }

    public static TilingMode byName(String name) {
        for (TilingMode mode : values()) {
            if (mode.name.equals(name)) {
                return mode;
            }
        }
        return null;
    }
}
