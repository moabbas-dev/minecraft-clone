package com.example.worldgen.material.condition;

import com.example.worldgen.density.Holder;
import com.example.worldgen.material.MaterialRuleContext;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.condition.MaterialCondition} (26.3). The eleven registered
 * types ({@code MaterialRules.bootstrapConditions}, in registration order): {@code biome}, {@code noise_threshold},
 * {@code vertical_gradient}, {@code y_above}, {@code water}, {@code temperature}, {@code steep}, {@code not}, {@code hole},
 * {@code above_preliminary_surface}, {@code stone_depth}. A registry reference ({@code material_condition/**}) decodes to a
 * {@link HolderHolder}.
 */
public interface MaterialCondition {

    ConditionEvaluator compile(MaterialRuleContext context);

    /** {@code MaterialCondition.HolderHolder}: compiles the referenced condition (again for every reference). */
    record HolderHolder(Holder<MaterialCondition> holder) implements MaterialCondition {
        @Override
        public ConditionEvaluator compile(MaterialRuleContext context) {
            return holder.value().compile(context);
        }
    }
}
