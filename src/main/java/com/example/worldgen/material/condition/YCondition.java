package com.example.worldgen.material.condition;

import com.example.worldgen.material.MaterialRuleContext;
import com.example.worldgen.material.VerticalAnchor;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.condition.YCondition} (26.3, type {@code minecraft:y_above},
 * fields {@code anchor}, {@code surface_depth_multiplier} (int in [-20, 20]), {@code add_stone_depth}). The
 * {@code LazyYCondition} ({@code YCondition$1.compute}): {@code blockY + (addStoneDepth ? stoneDepthAbove : 0) >=
 * resolveAnchorY(anchor) + surfaceDepth * surfaceDepthMultiplier} (the anchor is resolved on every computation).
 */
public record YCondition(VerticalAnchor anchor, int surfaceDepthMultiplier, boolean addStoneDepth) implements MaterialCondition {

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        return new MaterialRuleContext.LazyYCondition(context) {
            @Override
            protected boolean compute() {
                return context.blockY() + (addStoneDepth ? context.stoneDepthAbove() : 0)
                        >= context.resolveAnchorY(anchor) + context.surfaceDepth() * surfaceDepthMultiplier;
            }
        };
    }
}
