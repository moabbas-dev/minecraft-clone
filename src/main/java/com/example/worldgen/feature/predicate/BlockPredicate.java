package com.example.worldgen.feature.predicate;

import com.example.worldgen.feature.BlockSet;
import com.example.worldgen.feature.Direction;
import com.example.worldgen.feature.Pos;
import com.example.worldgen.feature.level.BlockStateFacts;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.provider.VerticalAnchor;
import com.example.worldgen.provider.WorldGenerationContext;

import java.util.List;

/**
 * Port of {@code net.minecraft.world.level.levelgen.blockpredicates.BlockPredicate} (26.3) and the types of
 * {@code BlockPredicateType} (bytecode of each class). {@code would_survive} is the one registered type that is not ported:
 * it needs {@code BlockState.canSurvive} of the tested block, which no ported feature uses
 * ({@link BlockPredicates#parse} rejects it with a {@link BlockPredicates.UnportedPredicateException}).
 */
public interface BlockPredicate {

    boolean test(GenRegion level, Pos pos);

    /** {@code StateTestingPredicate}: tests {@code level.getBlockState(origin.offset(offset))}. */
    abstract class StateTesting implements BlockPredicate {
        private final Pos offset;

        StateTesting(Pos offset) {
            this.offset = offset;
        }

        @Override
        public final boolean test(GenRegion level, Pos pos) {
            return test(level.getBlockState(pos.offset(offset.x(), offset.y(), offset.z())));
        }

        abstract boolean test(StateRef state);
    }

    /** {@code matching_blocks}: {@code state.is(blocks)}. */
    final class MatchingBlocks extends StateTesting {
        private final BlockSet blocks;

        MatchingBlocks(Pos offset, BlockSet blocks) {
            super(offset);
            this.blocks = blocks;
        }

        @Override
        boolean test(StateRef state) {
            return blocks.contains(state);
        }
    }

    /** {@code matching_block_tag}: {@code state.is(tag)}. */
    final class MatchingBlockTag extends StateTesting {
        private final String tag;
        private final com.example.worldgen.terrain.BlockTags tags;

        MatchingBlockTag(Pos offset, String tag, com.example.worldgen.terrain.BlockTags tags) {
            super(offset);
            this.tag = tag;
            this.tags = tags;
        }

        @Override
        boolean test(StateRef state) {
            return tags.is(state, tag);
        }
    }

    /** {@code matching_fluids}: {@code state.getFluidState().is(fluids)} (a direct set of fluid ids). */
    final class MatchingFluids extends StateTesting {
        private final List<String> fluids;

        MatchingFluids(Pos offset, List<String> fluids) {
            super(offset);
            this.fluids = fluids;
        }

        @Override
        boolean test(StateRef state) {
            return fluids.contains(BlockStateFacts.fluidType(state));
        }
    }

    /** {@code replaceable}: {@code state.canBeReplaced()}. */
    final class Replaceable extends StateTesting {
        Replaceable(Pos offset) {
            super(offset);
        }

        @Override
        boolean test(StateRef state) {
            return BlockStateFacts.canBeReplaced(state);
        }
    }

    /** {@code solid} (deprecated in vanilla, still registered): {@code state.isSolid()}. */
    final class Solid extends StateTesting {
        Solid(Pos offset) {
            super(offset);
        }

        @Override
        boolean test(StateRef state) {
            return BlockStateFacts.isSolid(state);
        }
    }

    /** {@code has_sturdy_face}: {@code getBlockState(p).isFaceSturdy(level, p, direction)} with {@code p = origin + offset}. */
    record HasSturdyFace(Pos offset, Direction direction) implements BlockPredicate {
        @Override
        public boolean test(GenRegion level, Pos pos) {
            Pos p = pos.offset(offset.x(), offset.y(), offset.z());
            return BlockStateFacts.isFaceSturdy(level.getBlockState(p), direction);
        }
    }

    /** {@code matching_biomes}: {@code biomes.contains(level.getBiome(pos))}. */
    record MatchingBiomes(List<String> biomes) implements BlockPredicate {
        @Override
        public boolean test(GenRegion level, Pos pos) {
            return biomes.contains(level.getBiome(pos));
        }
    }

    /** {@code inside_world_bounds}: {@code level.isInsideBuildHeight(pos.offset(offset))} (only y matters). */
    record InsideWorldBounds(Pos offset) implements BlockPredicate {
        @Override
        public boolean test(GenRegion level, Pos pos) {
            return !level.isOutsideBuildHeight(pos.y() + offset.y());
        }
    }

    /** {@code any_of}: the first passing predicate wins (in list order). */
    record AnyOf(List<BlockPredicate> predicates) implements BlockPredicate {
        @Override
        public boolean test(GenRegion level, Pos pos) {
            for (BlockPredicate p : predicates) {
                if (p.test(level, pos)) return true;
            }
            return false;
        }
    }

    /** {@code all_of}: the first failing predicate stops (in list order). */
    record AllOf(List<BlockPredicate> predicates) implements BlockPredicate {
        @Override
        public boolean test(GenRegion level, Pos pos) {
            for (BlockPredicate p : predicates) {
                if (!p.test(level, pos)) return false;
            }
            return true;
        }
    }

    /** {@code not}. */
    record Not(BlockPredicate predicate) implements BlockPredicate {
        @Override
        public boolean test(GenRegion level, Pos pos) {
            return !predicate.test(level, pos);
        }
    }

    /** {@code true} ({@code TrueBlockPredicate.INSTANCE}). */
    final class True implements BlockPredicate {
        static final True INSTANCE = new True();

        private True() {
        }

        @Override
        public boolean test(GenRegion level, Pos pos) {
            return true;
        }
    }

    /**
     * {@code unobstructed}: {@code level.isUnobstructed(null, Shapes.block().move(pos))} = {@code EntityGetter.isUnobstructed}:
     * true unless a non-removed, building-blocking entity intersects the shape; {@code WorldGenRegion.getEntities} returns an
     * empty list, so it is always true during generation.
     */
    record Unobstructed(Pos offset) implements BlockPredicate {
        @Override
        public boolean test(GenRegion level, Pos pos) {
            return true;
        }
    }

    /**
     * {@code height_range}: {@code WorldGenerationContext.of(level)} resolves both anchors, then {@code min <= y <= max}. For a
     * {@code WorldGenLevel} (bytecode) that context is {@code new WorldGenerationContext(level.getLevel().getChunkSource()
     * .getGenerator(), level)}: {@code max(level min Y, generator min Y)}, {@code min(level height, generator gen depth)},
     * the generator's sea level; the region's own values are used here, which is the same for the overworld (generator and
     * level both -64 / 384 / 63).
     */
    record HeightRange(VerticalAnchor minInclusive, VerticalAnchor maxInclusive) implements BlockPredicate {
        @Override
        public boolean test(GenRegion level, Pos pos) {
            WorldGenerationContext context = new WorldGenerationContext(level.getMinY(), level.getHeight(), level.getSeaLevel());
            int min = minInclusive.resolveY(context);
            int max = maxInclusive.resolveY(context);
            return pos.y() >= min && pos.y() <= max;
        }
    }

    /** {@code volume_match}: x outer, then z, then y inner, from {@code min} to {@code max} inclusive; all must match. */
    record VolumeMatch(Pos min, Pos max, BlockPredicate match) implements BlockPredicate {
        @Override
        public boolean test(GenRegion level, Pos pos) {
            for (int ox = min.x(); ox <= max.x(); ++ox) {
                for (int oz = min.z(); oz <= max.z(); ++oz) {
                    for (int oy = min.y(); oy <= max.y(); ++oy) {
                        if (!match.test(level, pos.offset(ox, oy, oz))) return false;
                    }
                }
            }
            return true;
        }
    }
}
