package com.example.worldgen.feature.stateprovider;

import com.example.worldgen.feature.Pos;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.feature.predicate.BlockPredicate;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.random.RandomSource;

import java.util.List;

/**
 * Port of {@code net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider} (26.3):
 * {@code getState(level, random, pos)} and {@code getOptionalState} (default: {@code getState}). Ported types: {@code simple}
 * ({@code SimpleStateProvider}) and {@code rule_based} ({@code RuleBasedStateProvider}); the other registered types
 * ({@code copy_properties, dual_noise, noise, noise_threshold, random_block, randomized_int, rotated, weighted}) are not
 * ported yet and are rejected by {@link BlockStateProviders#parse}.
 */
public interface BlockStateProvider {

    StateRef getState(GenRegion level, RandomSource random, Pos pos);

    /** {@code getOptionalState}: null for "no state". */
    default StateRef getOptionalState(GenRegion level, RandomSource random, Pos pos) {
        return getState(level, random, pos);
    }

    /** {@code SimpleStateProvider}: always {@code state}. */
    record Simple(StateRef state) implements BlockStateProvider {
        @Override
        public StateRef getState(GenRegion level, RandomSource random, Pos pos) {
            return state;
        }
    }

    /** {@code RuleBasedStateProvider.Rule}. */
    record Rule(BlockPredicate ifTrue, BlockStateProvider then) {
    }

    /**
     * {@code RuleBasedStateProvider}: {@code getOptionalState} returns the first non-null optional state of a rule whose
     * predicate passes (rules in order), else the fallback's optional state, else null; {@code getState} falls back to the
     * block already at {@code pos}.
     */
    record RuleBased(BlockStateProvider fallback, List<Rule> rules) implements BlockStateProvider {
        @Override
        public StateRef getState(GenRegion level, RandomSource random, Pos pos) {
            StateRef result = getOptionalState(level, random, pos);
            return result != null ? result : level.getBlockState(pos);
        }

        @Override
        public StateRef getOptionalState(GenRegion level, RandomSource random, Pos pos) {
            for (Rule rule : rules) {
                if (rule.ifTrue().test(level, pos)) {
                    StateRef optionalState = rule.then().getOptionalState(level, random, pos);
                    if (optionalState != null) return optionalState;
                }
            }
            return fallback == null ? null : fallback.getOptionalState(level, random, pos);
        }
    }
}
