package com.example.worldgen.material.rule;

import com.example.worldgen.material.MaterialRuleContext;
import com.example.worldgen.material.condition.ConditionEvaluator;
import com.example.worldgen.material.condition.MaterialCondition;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.rule.ConditionRule} (26.3, type {@code minecraft:condition},
 * fields {@code if_true}, {@code then_run}). {@code compile} compiles the condition, then the rule; the evaluator returns
 * {@code null} when the condition is false, else the rule's result.
 */
public record ConditionRule(MaterialCondition ifTrue, MaterialRule thenRun) implements MaterialRule {

    @Override
    public RuleEvaluator compile(MaterialRuleContext context) {
        ConditionEvaluator condition = ifTrue.compile(context);
        RuleEvaluator rule = thenRun.compile(context);
        return (x, y, z) -> {
            if (!condition.test()) {
                return null;
            }
            return rule.tryApply(x, y, z);
        };
    }
}
