package com.example.worldgen.density;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Port of {@code net.minecraft.util.context.ContextMap} (26.3): an immutable identity map from {@link ContextKey}
 * to value (vanilla uses fastutil's reference map). Setting a {@code null} value removes the key; an empty
 * builder yields {@link #EMPTY}. The key-set validation ({@code buildAndValidate}) is not needed here.
 */
public final class ContextMap {

    public static final ContextMap EMPTY = new ContextMap(Map.of());

    private final Map<ContextKey<?>, Object> params;

    private ContextMap(Map<ContextKey<?>, Object> params) {
        this.params = params;
    }

    public static Builder builder() {
        return new Builder();
    }

    public boolean has(ContextKey<?> key) {
        return params.containsKey(key);
    }

    public <T> T getOrThrow(ContextKey<T> key) {
        T value = get(key);
        if (value == null) {
            throw new NoSuchElementException(key.name());
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(ContextKey<T> key) {
        return (T) params.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getOrDefault(ContextKey<T> key, T defaultValue) {
        return (T) params.getOrDefault(key, defaultValue);
    }

    public static final class Builder {

        private final Map<ContextKey<?>, Object> params = new IdentityHashMap<>();

        private Builder() {
        }

        public <T> Builder set(ContextKey<T> key, T value) {
            if (value == null) {
                params.remove(key);
            } else {
                params.put(key, value);
            }
            return this;
        }

        @SuppressWarnings("unchecked")
        public <T> T get(ContextKey<T> key) {
            return (T) params.get(key);
        }

        public ContextMap build() {
            return params.isEmpty() ? EMPTY : new ContextMap(new IdentityHashMap<>(params));
        }
    }
}
