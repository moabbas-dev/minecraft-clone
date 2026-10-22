package com.example.worldgen.material.condition;

import com.example.worldgen.Mth;
import com.example.worldgen.material.MaterialRuleContext;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.condition.StoneDepthCondition} (26.3, type
 * {@code minecraft:stone_depth}, fields {@code offset}, {@code add_surface_depth}, {@code secondary_depth_range},
 * {@code surface_type}). The {@code LazyYCondition} ({@code StoneDepthCondition$1.compute}):
 * <pre>
 * depth = ceiling ? stoneDepthBelow : stoneDepthAbove;
 * surface = addSurfaceDepth ? surfaceDepth : 0;
 * secondary = range == 0 ? 0 : (int) Mth.map(getSurfaceSecondary(), -1.0, 1.0, 0.0, (double) range);
 * return depth &lt;= 1 + offset + surface + secondary;
 * </pre>
 */
public record StoneDepthCondition(int offset, boolean addSurfaceDepth, int secondaryDepthRange, CaveSurface surfaceType)
        implements MaterialCondition {

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        boolean ceiling = surfaceType == CaveSurface.CEILING;
        return new MaterialRuleContext.LazyYCondition(context) {
            @Override
            protected boolean compute() {
                int stoneDepth = ceiling ? context.stoneDepthBelow() : context.stoneDepthAbove();
                int surfaceDepth = addSurfaceDepth ? context.surfaceDepth() : 0;
                int secondarySurfaceDepth = secondaryDepthRange == 0
                        ? 0
                        : (int) Mth.map(context.getSurfaceSecondary(), -1.0, 1.0, 0.0, (double) secondaryDepthRange);
                return stoneDepth <= 1 + offset + surfaceDepth + secondarySurfaceDepth;
            }
        };
    }
}
