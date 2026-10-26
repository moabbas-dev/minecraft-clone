package com.example.worldgen.feature.types;

import com.example.worldgen.feature.BlockStateDefinitions;
import com.example.worldgen.feature.DecorationGenerator;
import com.example.worldgen.feature.Feature;
import com.example.worldgen.feature.Pos;
import com.example.worldgen.feature.level.BlockStateFacts;
import com.example.worldgen.feature.level.BlockSurvival;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.feature.stateprovider.BlockStateProvider;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.terrain.BlockProperties;

/**
 * Port of {@code net.minecraft.world.level.levelgen.feature.SimpleBlockFeature} (26.3 record {@code (toPlace, scheduleTick)},
 * {@code place} 183 bytes):
 * <ol>
 *     <li>{@code state = toPlace.getOptionalState(level, random, pos)}; null: false;</li>
 *     <li>{@code !state.canSurvive(level, pos)}: false;</li>
 *     <li>a {@code DoublePlantBlock}: {@code above = level.getBlockState(pos.above())}; if {@code above.isAir()} or
 *     ({@code Objects.equals(state.getFluidState(), above.getFluidState())} and {@code above.canBeReplaced()})
 *     {@code DoublePlantBlock.placeAt(level, state, pos, 2)}, else false;</li>
 *     <li>a {@code MossyCarpetBlock}: {@code MossyCarpetBlock.placeAt(level, pos, level.getRandom(), 2)} (the region random:
 *     not modelled, see {@link GenRegion#getRandom});</li>
 *     <li>otherwise {@code level.setBlock(pos, state, 2)};</li>
 *     <li>{@code scheduleTick}: {@code level.scheduleTick(pos, level.getBlockState(pos).getBlock(), 1)}; true.</li>
 * </ol>
 * {@code DoublePlantBlock.placeAt(level, state, pos, flags)}: {@code setBlock(pos, copyWaterloggedFrom(level, pos,
 * state.setValue(HALF, LOWER)))}, then {@code setBlock(pos.above(), copyWaterloggedFrom(level, pos.above(),
 * state.setValue(HALF, UPPER)))}; {@code copyWaterloggedFrom} sets {@code WATERLOGGED = level.isWaterAt(pos)} when the state
 * has that property.
 */
public final class SimpleBlockFeature implements Feature {

    private final BlockStateProvider toPlace;
    private final boolean scheduleTick;
    private final BlockSurvival survival;

    public SimpleBlockFeature(BlockStateProvider toPlace, boolean scheduleTick, BlockSurvival survival) {
        this.toPlace = toPlace;
        this.scheduleTick = scheduleTick;
        this.survival = survival;
    }

    @Override
    public String type() {
        return "minecraft:simple_block";
    }

    @Override
    public boolean place(GenRegion level, DecorationGenerator generator, RandomSource random, Pos pos) {
        StateRef state = toPlace.getOptionalState(level, random, pos);
        if (state == null) return false;
        if (!survival.canSurvive(state, level, pos)) return false;
        if (BlockSurvival.isDoublePlant(state.id())) {
            StateRef above = level.getBlockState(pos.above());
            if (BlockProperties.isAir(above) || BlockStateFacts.fluidStateKey(state).equals(BlockStateFacts.fluidStateKey(above))
                    && BlockStateFacts.canBeReplaced(above)) {
                placeDoublePlant(level, survival, state, pos, 2);
            } else {
                return false;
            }
        } else if (BlockSurvival.isMossyCarpet(state.id())) {
            level.getRandom();
            throw new IllegalStateException("unreachable: GenRegion.getRandom throws");
        } else {
            level.setBlock(pos, state, 2);
        }
        if (scheduleTick) {
            level.scheduleBlockTick(pos.x(), pos.y(), pos.z(), level.getBlockState(pos).id(), 1);
        }
        return true;
    }

    /** {@code DoublePlantBlock.placeAt(level, state, pos, flags)}. */
    public static void placeDoublePlant(GenRegion level, BlockSurvival survival, StateRef state, Pos pos, int flags) {
        Pos above = pos.above();
        level.setBlock(pos, copyWaterloggedFrom(level, survival, pos,
                BlockStateDefinitions.setValue(state, BlockStateDefinitions.HALF, "lower")), flags);
        level.setBlock(above, copyWaterloggedFrom(level, survival, above,
                BlockStateDefinitions.setValue(state, BlockStateDefinitions.HALF, "upper")), flags);
    }

    /** {@code DoublePlantBlock.copyWaterloggedFrom(level, pos, state)}. */
    static StateRef copyWaterloggedFrom(GenRegion level, BlockSurvival survival, Pos pos, StateRef state) {
        if (BlockStateDefinitions.hasProperty(state, BlockStateDefinitions.WATERLOGGED)) {
            return BlockStateDefinitions.setValue(state, BlockStateDefinitions.WATERLOGGED, Boolean.toString(survival.isWaterAt(level, pos)));
        }
        return state;
    }
}
