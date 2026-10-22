package com.example.worldgen.material.rule;

import com.example.worldgen.level.StateRef;
import com.example.worldgen.material.MaterialRuleContext;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.rule.BlockRule} (26.3, type {@code minecraft:block}, field
 * {@code result_state}): the record is its own evaluator and always returns its state.
 */
public record BlockRule(StateRef resultState) implements MaterialRule, RuleEvaluator {

    @Override
    public RuleEvaluator compile(MaterialRuleContext context) {
        return this;
    }

    @Override
    public StateRef tryApply(int x, int y, int z) {
        return resultState;
    }
}
