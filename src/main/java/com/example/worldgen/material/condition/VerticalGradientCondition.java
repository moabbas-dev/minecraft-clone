package com.example.worldgen.material.condition;

import com.example.worldgen.Mth;
import com.example.worldgen.material.MaterialRuleContext;
import com.example.worldgen.provider.VerticalAnchor;
import com.example.worldgen.random.PositionalRandomFactory;
import com.example.worldgen.random.RandomSource;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.condition.VerticalGradientCondition} (26.3, type
 * {@code minecraft:vertical_gradient}, fields {@code random_name} (identifier), {@code true_at_and_below},
 * {@code false_at_and_above} (anchors)). {@code compile} resolves both anchors and takes
 * {@code getOrCreateRandomFactory(random_name)}; the {@code LazyYCondition} ({@code VerticalGradientCondition$1.compute}):
 * {@code y <= trueAt} true; {@code y >= falseAt} false; else
 * {@code random.at(blockX, y, blockZ).nextFloat() < Mth.map(y, trueAt, falseAt, 1.0, 0.0)} (float widened to double).
 */
public record VerticalGradientCondition(String randomName, VerticalAnchor trueAtAndBelow, VerticalAnchor falseAtAndAbove)
        implements MaterialCondition {

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        int trueAt = context.resolveAnchorY(trueAtAndBelow);
        int falseAt = context.resolveAnchorY(falseAtAndAbove);
        PositionalRandomFactory randomFactory = context.getOrCreateRandomFactory(randomName);
        return new MaterialRuleContext.LazyYCondition(context) {
            @Override
            protected boolean compute() {
                int y = context.blockY();
                if (y <= trueAt) {
                    return true;
                }
                if (y >= falseAt) {
                    return false;
                }
                double probability = Mth.map((double) y, (double) trueAt, (double) falseAt, 1.0, 0.0);
                RandomSource random = randomFactory.at(context.blockX(), y, context.blockZ());
                return (double) random.nextFloat() < probability;
            }
        };
    }
}
