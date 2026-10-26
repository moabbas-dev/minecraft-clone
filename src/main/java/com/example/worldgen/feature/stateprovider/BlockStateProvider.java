package com.example.worldgen.feature.stateprovider;

import com.example.worldgen.Mth;
import com.example.worldgen.feature.BlockStateDefinitions;
import com.example.worldgen.feature.Direction;
import com.example.worldgen.feature.Pos;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.feature.predicate.BlockPredicate;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.noise.Noise;
import com.example.worldgen.noise.NormalNoise;
import com.example.worldgen.provider.IntProvider;
import com.example.worldgen.provider.WeightedList;
import com.example.worldgen.random.LegacyRandomSource;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.random.WorldgenRandom;
import com.example.worldgen.terrain.BlockProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of {@code net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider} (26.3):
 * {@code getState(level, random, pos)} and {@code getOptionalState} (default: {@code getState}), with all ten types of
 * {@code BlockStateProviderTypes.bootstrap} ({@code copy_properties, dual_noise, noise, noise_threshold, random_block,
 * randomized_int, rotated, rule_based, simple, weighted}), each {@code getState} transcribed from its class's bytecode.
 */
public interface BlockStateProvider {

    StateRef getState(GenRegion level, RandomSource random, Pos pos);

    /** {@code getOptionalState}: null for "no state". */
    default StateRef getOptionalState(GenRegion level, RandomSource random, Pos pos) {
        return getState(level, random, pos);
    }

    /** {@code Util.getRandom(list, random)}: {@code list.get(random.nextInt(list.size()))}. */
    static <T> T getRandom(List<T> list, RandomSource random) {
        return list.get(random.nextInt(list.size()));
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

    /** {@code WeightedStateProvider}: {@code weightedList.getRandomOrThrow(random)} (one {@code nextInt(totalWeight)}). */
    record Weighted(WeightedList<StateRef> weightedList) implements BlockStateProvider {
        public Weighted {
            if (weightedList.isEmpty()) throw new IllegalArgumentException("Weighted list must have at least one entry");
        }

        @Override
        public StateRef getState(GenRegion level, RandomSource random, Pos pos) {
            return weightedList.getRandomOrThrow(random);
        }
    }

    /**
     * {@code RandomBlockProvider}: {@code blocks.getRandomElement(random)} ({@code Util.getRandomSafe}: empty for an empty
     * set, else {@code get(nextInt(size))}) mapped to the block's default state; {@code getState} falls back to the block at
     * {@code pos}, {@code getOptionalState} to null. {@code blocks} is in holder-set order (tag file order with nested tags
     * expanded in place, as {@code TagLoader} builds it).
     */
    record RandomBlock(List<String> blocks) implements BlockStateProvider {
        private StateRef pick(RandomSource random) {
            if (blocks.isEmpty()) return null;
            return BlockProperties.defaultState(blocks.get(random.nextInt(blocks.size())));
        }

        @Override
        public StateRef getState(GenRegion level, RandomSource random, Pos pos) {
            StateRef s = pick(random);
            return s != null ? s : level.getBlockState(pos);
        }

        @Override
        public StateRef getOptionalState(GenRegion level, RandomSource random, Pos pos) {
            return pick(random);
        }
    }

    /**
     * {@code RandomizedIntStateProvider}: the source's {@code getState}, then (when the state has an integer property of
     * that name, {@code findProperty}) {@code setValue(property, values.sample(random))}; otherwise the source state as is
     * (no draw).
     */
    record RandomizedInt(BlockStateProvider source, String propertyName, IntProvider values) implements BlockStateProvider {
        @Override
        public StateRef getState(GenRegion level, RandomSource random, Pos pos) {
            StateRef state = source.getState(level, random, pos);
            BlockStateDefinitions.Property property = BlockStateDefinitions.findIntegerProperty(state, propertyName);
            if (property == null) return state;
            return BlockStateDefinitions.setValue(state, property, Integer.toString(values.sample(random)));
        }
    }

    /**
     * {@code RotatedBlockProvider}: {@code direction} or {@code Direction.getRandom(random)} (drawn before the source),
     * then the source state with {@code trySetValue(AXIS, dir.getAxis())}, {@code trySetValue(FACING, dir)} and, for a
     * horizontal direction, {@code trySetValue(HORIZONTAL_FACING, dir)}.
     */
    record Rotated(BlockStateProvider state, Direction direction) implements BlockStateProvider {
        @Override
        public StateRef getState(GenRegion level, RandomSource random, Pos pos) {
            Direction dir = direction != null ? direction : Direction.getRandom(random);
            StateRef s = state.getState(level, random, pos);
            s = BlockStateDefinitions.trySetValue(s, BlockStateDefinitions.AXIS, dir.axisName());
            s = BlockStateDefinitions.trySetValue(s, BlockStateDefinitions.FACING, dir.serializedName());
            if (dir.isHorizontal()) s = BlockStateDefinitions.trySetValue(s, BlockStateDefinitions.HORIZONTAL_FACING, dir.serializedName());
            return s;
        }
    }

    /** {@code CopyPropertiesProvider}: the source's state {@code withPropertiesOf(level.getBlockState(pos))}. */
    record CopyProperties(BlockStateProvider source) implements BlockStateProvider {
        @Override
        public StateRef getState(GenRegion level, RandomSource random, Pos pos) {
            StateRef s = source.getState(level, random, pos);
            return BlockStateDefinitions.withPropertiesOf(s, level.getBlockState(pos));
        }
    }

    /**
     * {@code NoiseBasedStateProvider}: {@code noise = parameters.create(new WorldgenRandom(new LegacyRandomSource(seed)))};
     * {@code getNoiseValue(pos, scale)} = {@code noise.get(x * scale, y * scale, z * scale)} (doubles in, float out).
     */
    abstract class NoiseBased implements BlockStateProvider {
        protected final long seed;
        protected final NormalNoise parameters;
        protected final float scale;
        protected final Noise noise;

        protected NoiseBased(long seed, NormalNoise parameters, float scale) {
            this.seed = seed;
            this.parameters = parameters;
            this.scale = scale;
            this.noise = parameters.create(new WorldgenRandom(new LegacyRandomSource(seed)));
        }

        protected float getNoiseValue(Pos pos, double scale) {
            return noise.get((double) pos.x() * scale, (double) pos.y() * scale, (double) pos.z() * scale);
        }
    }

    /**
     * {@code NoiseThresholdProvider}: {@code n = (double) getNoiseValue(pos, scale)}; {@code n < threshold}: a random low
     * state; else {@code nextFloat() < highChance}: a random high state; else the default state.
     */
    final class NoiseThreshold extends NoiseBased {
        private final float threshold;
        private final float highChance;
        private final StateRef defaultState;
        private final List<StateRef> lowStates;
        private final List<StateRef> highStates;

        public NoiseThreshold(long seed, NormalNoise parameters, float scale, float threshold, float highChance, StateRef defaultState,
                              List<StateRef> lowStates, List<StateRef> highStates) {
            super(seed, parameters, scale);
            this.threshold = threshold;
            this.highChance = highChance;
            this.defaultState = defaultState;
            this.lowStates = lowStates;
            this.highStates = highStates;
        }

        @Override
        public StateRef getState(GenRegion level, RandomSource random, Pos pos) {
            double noiseValue = (double) getNoiseValue(pos, (double) scale);
            if (noiseValue < (double) threshold) return BlockStateProvider.getRandom(lowStates, random);
            if (random.nextFloat() < highChance) return BlockStateProvider.getRandom(highStates, random);
            return defaultState;
        }
    }

    /**
     * {@code NoiseProvider}: {@code getRandomState(states, pos, scale)} = {@code getRandomState(states, getNoiseValue(pos,
     * scale))}; {@code getRandomState(list, n)} = {@code list.get((int) (clamp((1 + n) / 2, 0, 0.9999f) * list.size()))}
     * (float arithmetic).
     */
    class NoiseStates extends NoiseBased {
        protected final List<StateRef> states;

        public NoiseStates(long seed, NormalNoise parameters, float scale, List<StateRef> states) {
            super(seed, parameters, scale);
            this.states = states;
        }

        @Override
        public StateRef getState(GenRegion level, RandomSource random, Pos pos) {
            return getRandomState(states, pos, (double) scale);
        }

        protected StateRef getRandomState(List<StateRef> list, Pos pos, double scale) {
            float value = getNoiseValue(pos, scale);
            return getRandomState(list, value);
        }

        protected StateRef getRandomState(List<StateRef> list, float value) {
            float clamped = Mth.clamp((1.0F + value) / 2.0F, 0.0F, 0.9999F);
            return list.get((int) (clamped * (float) list.size()));
        }
    }

    /**
     * {@code DualNoiseProvider}: {@code slowNoise = slowNoiseParameters.create(new WorldgenRandom(new LegacyRandomSource(seed)))};
     * {@code count = (int) Mth.clampedMap((double) slowNoise(pos), -1, 1, variety.min, variety.max + 1)}; a list of
     * {@code count} states, element {@code i} = {@code getRandomState(states, slowNoise(pos.offset(i * 54545, 0, i * 34234)))};
     * the result is {@code getRandomState(thatList, pos, scale)}. {@code slowNoise(p)} =
     * {@code slowNoise.get((double) ((float) x * slowScale), ...)} (float products widened to double).
     */
    final class DualNoise extends NoiseStates {
        private final int varietyMin;
        private final int varietyMax;
        private final NormalNoise slowNoiseParameters;
        private final float slowScale;
        private final Noise slowNoise;

        public DualNoise(int varietyMin, int varietyMax, NormalNoise slowNoiseParameters, float slowScale, long seed, NormalNoise parameters,
                         float scale, List<StateRef> states) {
            super(seed, parameters, scale, states);
            this.varietyMin = varietyMin;
            this.varietyMax = varietyMax;
            this.slowNoiseParameters = slowNoiseParameters;
            this.slowScale = slowScale;
            this.slowNoise = slowNoiseParameters.create(new WorldgenRandom(new LegacyRandomSource(seed)));
        }

        @Override
        public StateRef getState(GenRegion level, RandomSource random, Pos pos) {
            double noiseValue = (double) getSlowNoiseValue(pos);
            int count = (int) Mth.clampedMap(noiseValue, -1.0, 1.0, (double) varietyMin, (double) (varietyMax + 1));
            List<StateRef> list = new ArrayList<>(Math.max(count, 0));
            for (int i = 0; i < count; ++i) {
                list.add(getRandomState(states, getSlowNoiseValue(pos.offset(i * 54545, 0, i * 34234))));
            }
            return getRandomState(list, pos, (double) scale);
        }

        float getSlowNoiseValue(Pos pos) {
            return slowNoise.get((double) ((float) pos.x() * slowScale), (double) ((float) pos.y() * slowScale),
                    (double) ((float) pos.z() * slowScale));
        }
    }
}
