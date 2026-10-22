package com.example.worldgen.material.condition;

import com.example.worldgen.material.MaterialRuleContext;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.condition.TemperatureCondition} (26.3, type
 * {@code minecraft:temperature}, no fields): {@code context.getBiome().value().coldEnoughToSnow(context.blockPos(),
 * context.getSeaLevel())}, evaluated on every test (not lazy).
 */
public enum TemperatureCondition implements MaterialCondition {
    INSTANCE;

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        return () -> context.coldEnoughToSnow(context.getBiome(), context.blockX(), context.blockY(), context.blockZ(),
                context.getSeaLevel());
    }
}
