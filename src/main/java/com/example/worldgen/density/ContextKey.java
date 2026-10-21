package com.example.worldgen.density;

/**
 * Port of {@code net.minecraft.util.context.ContextKey} (26.3): an identity-compared typed key; {@code name} is
 * the {@code Identifier} string ({@link #vanilla(String)} prefixes {@code minecraft:}).
 *
 * @param <T> the value type stored under this key
 */
public final class ContextKey<T> {

    private final String name;

    public ContextKey(String name) {
        this.name = name;
    }

    public static <T> ContextKey<T> vanilla(String name) {
        return new ContextKey<>("minecraft:" + name);
    }

    public String name() {
        return name;
    }

    @Override
    public String toString() {
        return "<parameter " + name + ">";
    }
}
