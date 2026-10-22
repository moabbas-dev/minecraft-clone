package com.example.worldgen.material.rule;

import com.example.worldgen.density.Holder;
import com.example.worldgen.material.MaterialRuleContext;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.rule.MaterialRule} (26.3): {@code compile(context)} binds a
 * rule to one chunk's {@link MaterialRuleContext}. The five registered types ({@code MaterialRules.bootstrapRules}):
 * {@code block} ({@link BlockRule}), {@code bandlands} ({@link BandlandsRule}), {@code sequence} ({@link SequenceRule}),
 * {@code condition} ({@link ConditionRule}), {@code ore_vein} ({@link OreVeinRule}). A registry reference decodes to a
 * {@link HolderHolder} ({@code MaterialRule.CODEC}: {@code Holder.Reference} becomes {@code HolderHolder}, a direct holder its
 * value).
 */
public interface MaterialRule {

    RuleEvaluator compile(MaterialRuleContext context);

    /** {@code MaterialRule.HolderHolder}: compiles the referenced rule (again for every reference). */
    record HolderHolder(Holder<MaterialRule> holder) implements MaterialRule {
        @Override
        public RuleEvaluator compile(MaterialRuleContext context) {
            return holder.value().compile(context);
        }
    }
}
