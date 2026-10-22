package com.example.worldgen.material.condition;

import com.example.worldgen.material.MaterialRuleContext;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.condition.HoleCondition} (26.3, type {@code minecraft:hole},
 * no fields): a {@code LazyXZCondition} ({@code HoleCondition$1.compute}) {@code surfaceDepth <= 0}.
 */
public enum HoleCondition implements MaterialCondition {
    INSTANCE;

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        return new MaterialRuleContext.LazyXZCondition(context) {
            @Override
            protected boolean compute() {
                return context.surfaceDepth() <= 0;
            }
        };
    }
}
