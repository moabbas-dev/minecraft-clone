package com.example.worldgen.material.rule;

import com.example.worldgen.level.StateRef;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.rule.RuleEvaluator} (26.3): a compiled rule;
 * {@code null} means "this rule does not apply, try the next one of the enclosing sequence".
 */
@FunctionalInterface
public interface RuleEvaluator {
    StateRef tryApply(int x, int y, int z);
}
