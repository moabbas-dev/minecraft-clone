package com.example.worldgen.material.condition;

import com.example.worldgen.material.MaterialRuleContext;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.condition.WaterCondition} (26.3, type {@code minecraft:water},
 * fields {@code offset}, {@code surface_depth_multiplier} (int in [-20, 20]), {@code add_stone_depth}). The
 * {@code LazyYCondition} ({@code WaterCondition$1.compute}): {@code waterHeight == Integer.MIN_VALUE} or
 * {@code blockY + (addStoneDepth ? stoneDepthAbove : 0) >= waterHeight + offset + surfaceDepth * surfaceDepthMultiplier}.
 */
public record WaterCondition(int offset, int surfaceDepthMultiplier, boolean addStoneDepth) implements MaterialCondition {

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        return new MaterialRuleContext.LazyYCondition(context) {
            @Override
            protected boolean compute() {
                return context.waterHeight() == Integer.MIN_VALUE
                        || context.blockY() + (addStoneDepth ? context.stoneDepthAbove() : 0)
                        >= context.waterHeight() + offset + context.surfaceDepth() * surfaceDepthMultiplier;
            }
        };
    }
}
