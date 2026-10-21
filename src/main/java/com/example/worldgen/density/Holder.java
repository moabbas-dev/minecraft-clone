package com.example.worldgen.density;

import java.util.Objects;
import java.util.Optional;

/**
 * The part of {@code net.minecraft.core.Holder} (26.3) the density-function and noise registries use: a value
 * either inline ({@link Direct}, a record compared by value, never matching a key) or registered
 * ({@link Reference}, compared by identity, matching its key). Keys are {@code namespace:path} strings (vanilla
 * {@code ResourceKey}s are interned, so key identity and string equality coincide).
 *
 * @param <T> the held value type
 */
public sealed interface Holder<T> permits Holder.Direct, Holder.Reference {

    T value();

    /** {@code Holder.is(ResourceKey)}: only a reference with this key. */
    boolean is(String key);

    Optional<String> unwrapKey();

    static <T> Holder<T> direct(T value) {
        return new Direct<>(value);
    }

    /** {@code Holder.Direct}. */
    record Direct<T>(T value) implements Holder<T> {

        @Override
        public boolean is(String key) {
            return false;
        }

        @Override
        public Optional<String> unwrapKey() {
            return Optional.empty();
        }
    }

    /** {@code Holder.Reference}: a registry entry, bound to its value once the registry has been loaded. */
    final class Reference<T> implements Holder<T> {

        private final String key;
        private T value;

        public Reference(String key) {
            this.key = Objects.requireNonNull(key);
        }

        public String key() {
            return key;
        }

        public void bindValue(T value) {
            this.value = value;
        }

        public boolean isBound() {
            return value != null;
        }

        @Override
        public T value() {
            if (value == null) {
                throw new IllegalStateException("Trying to access unbound value '" + key + "' from registry");
            }
            return value;
        }

        @Override
        public boolean is(String key) {
            return this.key.equals(key);
        }

        @Override
        public Optional<String> unwrapKey() {
            return Optional.of(key);
        }

        @Override
        public String toString() {
            return "Reference{" + key + "=" + value + "}";
        }
    }
}
