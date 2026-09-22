package com.example.world;

public record WorldSettings(String name, WorldType type) {
    public enum WorldType {SURVIVAL, FLAT}
}
