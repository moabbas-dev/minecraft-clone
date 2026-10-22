package com.example.worldgen.material.condition;

import com.example.worldgen.material.MaterialRuleContext;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.condition.NotCondition} (26.3, type {@code minecraft:not},
 * field {@code invert}): the negation of the compiled target.
 */
public record NotCondition(MaterialCondition target) implements MaterialCondition {

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        ConditionEvaluator evaluator = target.compile(context);
        return () -> !evaluator.test();
    }
}
