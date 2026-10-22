package com.example.worldgen.material.condition;

/** Port of {@code net.minecraft.world.level.levelgen.material.condition.ConditionEvaluator} (26.3). */
@FunctionalInterface
public interface ConditionEvaluator {
    boolean test();
}
