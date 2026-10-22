package com.example.worldgen.material.rule;

import com.example.worldgen.level.StateRef;
import com.example.worldgen.material.MaterialRuleContext;

import java.util.List;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.rule.SequenceRule} (26.3, type {@code minecraft:sequence},
 * field {@code sequence}). {@code compile}: a one-element sequence compiles to its element; otherwise every element is
 * compiled in order and the evaluator returns the first non-null result ({@code null} when none applies, also for an
 * empty list, which the codec accepts).
 */
public record SequenceRule(List<MaterialRule> sequence) implements MaterialRule {

    public SequenceRule {
        sequence = List.copyOf(sequence);
    }

    @Override
    public RuleEvaluator compile(MaterialRuleContext context) {
        if (sequence.size() == 1) {
            return sequence.get(0).compile(context);
        }
        RuleEvaluator[] evaluators = new RuleEvaluator[sequence.size()];
        for (int i = 0; i < evaluators.length; i++) {
            evaluators[i] = sequence.get(i).compile(context);
        }
        return (x, y, z) -> {
            for (RuleEvaluator evaluator : evaluators) {
                StateRef state = evaluator.tryApply(x, y, z);
                if (state != null) {
                    return state;
                }
            }
            return null;
        };
    }
}
