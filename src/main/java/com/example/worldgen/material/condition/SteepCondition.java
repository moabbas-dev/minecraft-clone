package com.example.worldgen.material.condition;

import com.example.worldgen.material.MaterialRuleContext;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.condition.SteepCondition} (26.3, type {@code minecraft:steep},
 * no fields): a {@code LazyXZCondition} ({@code SteepCondition$1.compute}) {@code surfaceGradientX <= -4 ||
 * surfaceGradientZ >= 4}.
 */
public enum SteepCondition implements MaterialCondition {
    INSTANCE;

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        return new MaterialRuleContext.LazyXZCondition(context) {
            @Override
            protected boolean compute() {
                return context.surfaceGradientX() <= -4 || context.surfaceGradientZ() >= 4;
            }
        };
    }
}
