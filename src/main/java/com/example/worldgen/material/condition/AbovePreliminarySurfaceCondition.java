package com.example.worldgen.material.condition;

import com.example.worldgen.material.MaterialRuleContext;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.condition.AbovePreliminarySurfaceCondition} (26.3, type
 * {@code minecraft:above_preliminary_surface}, no fields): {@code context.blockY() >= context.getMinSurfaceLevel()},
 * evaluated on every test (the minimum surface level itself is cached per XZ update).
 */
public enum AbovePreliminarySurfaceCondition implements MaterialCondition {
    INSTANCE;

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        return () -> context.blockY() >= context.getMinSurfaceLevel();
    }
}
