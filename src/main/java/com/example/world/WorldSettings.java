package com.example.world;

public record WorldSettings(String name, WorldType type, Difficulty difficulty) {
    /** No difficulty-selection UI exists yet - every caller that doesn't care gets NORMAL. */
    public WorldSettings(String name, WorldType type) {
        this(name, type, Difficulty.NORMAL);
    }

    public enum WorldType {SURVIVAL, FLAT}
}
