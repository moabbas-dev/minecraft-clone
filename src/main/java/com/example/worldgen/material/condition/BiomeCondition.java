package com.example.worldgen.material.condition;

import com.example.worldgen.material.MaterialRuleContext;

import java.util.Set;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.condition.BiomeCondition} (26.3, type {@code minecraft:biome},
 * field {@code biome_is}, a biome holder set; here the resolved set of biome ids). {@code compile}: when the context has
 * a {@code possibleBiomes} set, a condition none of whose biomes is possible is constant {@code false}
 * ({@code canNeverMatch}), and one that contains every possible biome is constant {@code true} ({@code willAlwaysMatch});
 * otherwise a {@code LazyYCondition} testing {@code biomes.contains(context.getBiome())}.
 */
public record BiomeCondition(Set<String> biomes) implements MaterialCondition {

    public BiomeCondition {
        biomes = Set.copyOf(biomes);
    }

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        Set<String> possibleBiomes = context.possibleBiomes();
        if (possibleBiomes != null) {
            if (canNeverMatch(possibleBiomes)) {
                return () -> false;
            }
            if (willAlwaysMatch(possibleBiomes)) {
                return () -> true;
            }
        }
        return new MaterialRuleContext.LazyYCondition(context) {
            @Override
            protected boolean compute() {
                return biomes.contains(context.getBiome());
            }
        };
    }

    private boolean canNeverMatch(Set<String> possibleBiomes) {
        for (String biome : biomes) {
            if (possibleBiomes.contains(biome)) {
                return false;
            }
        }
        return true;
    }

    private boolean willAlwaysMatch(Set<String> possibleBiomes) {
        for (String biome : possibleBiomes) {
            if (!biomes.contains(biome)) {
                return false;
            }
        }
        return true;
    }
}
