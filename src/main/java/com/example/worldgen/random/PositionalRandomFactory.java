package com.example.worldgen.random;

/**
 * Port of {@code net.minecraft.world.level.levelgen.PositionalRandomFactory} (26.3): derives
 * independent random sources from a block position, a resource-name string or a seed.
 * Vanilla's {@code at(BlockPos)} and {@code fromHashOf(Identifier)} overloads delegate to the
 * int and {@code Identifier.toString()} forms; callers here pass the {@code namespace:path} string.
 */
public interface PositionalRandomFactory {

    RandomSource at(int x, int y, int z);

    RandomSource fromHashOf(String name);

    RandomSource fromSeed(long seed);

    /** Vanilla's debug description of the factory state (used by its parity tooling). */
    void parityConfigString(StringBuilder sb);
}
