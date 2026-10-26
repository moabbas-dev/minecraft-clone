package com.example.worldgen.feature.types;

import com.example.worldgen.feature.BlockSet;
import com.example.worldgen.feature.Feature;
import com.example.worldgen.feature.Pos;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.terrain.BlockProperties;

/**
 * Port of {@code net.minecraft.world.level.levelgen.feature.SpringFeature} (26.3; record of {@code state} (a
 * {@code FluidState}), {@code requires_block_below} (default true), {@code rock_count} (default 4), {@code hole_count}
 * (default 1), {@code valid_blocks}): the block above (and, if required, below) must be valid, the origin air or valid;
 * rocks = valid among west, east, north, south, below; holes = air ({@code isEmptyBlock}) among the same five; when both
 * counts match exactly the fluid's legacy block is set (flags 2) and a fluid tick of the fluid type is scheduled (delay 0).
 * {@code FlowingFluid.getLegacyLevel}: a source gives {@code level=0}; the data's springs are sources
 * ({@code minecraft:water} / {@code minecraft:lava} with {@code falling=true}), so the block is {@code water[level=0]} or
 * {@code lava[level=0]} and the tick type is the source fluid id.
 */
public record SpringFeature(String fluid, StateRef legacyBlock, boolean requiresBlockBelow, int rockCount, int holeCount,
                            BlockSet validBlocks) implements Feature {

    @Override
    public String type() {
        return "minecraft:spring_feature";
    }

    @Override
    public boolean place(GenRegion level, com.example.worldgen.feature.DecorationGenerator generator, RandomSource random, Pos origin) {
        if (!validBlocks.contains(level.getBlockState(origin.above()))) return false;
        if (requiresBlockBelow && !validBlocks.contains(level.getBlockState(origin.below()))) return false;
        StateRef currentState = level.getBlockState(origin);
        if (!BlockProperties.isAir(currentState) && !validBlocks.contains(currentState)) return false;
        int rocks = 0;
        if (validBlocks.contains(level.getBlockState(origin.offset(-1, 0, 0)))) ++rocks;
        if (validBlocks.contains(level.getBlockState(origin.offset(1, 0, 0)))) ++rocks;
        if (validBlocks.contains(level.getBlockState(origin.offset(0, 0, -1)))) ++rocks;
        if (validBlocks.contains(level.getBlockState(origin.offset(0, 0, 1)))) ++rocks;
        if (validBlocks.contains(level.getBlockState(origin.below()))) ++rocks;
        int holes = 0;
        if (level.isEmptyBlock(origin.offset(-1, 0, 0))) ++holes;
        if (level.isEmptyBlock(origin.offset(1, 0, 0))) ++holes;
        if (level.isEmptyBlock(origin.offset(0, 0, -1))) ++holes;
        if (level.isEmptyBlock(origin.offset(0, 0, 1))) ++holes;
        if (level.isEmptyBlock(origin.below())) ++holes;
        if (rocks == rockCount && holes == holeCount) {
            level.setBlock(origin, legacyBlock, 2);
            level.scheduleFluidTick(origin.x(), origin.y(), origin.z(), fluid);
            return true;
        }
        return false;
    }
}
