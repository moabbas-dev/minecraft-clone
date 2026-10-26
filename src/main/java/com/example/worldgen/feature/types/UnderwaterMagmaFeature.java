package com.example.worldgen.feature.types;

import com.example.worldgen.feature.Direction;
import com.example.worldgen.feature.Feature;
import com.example.worldgen.feature.Pos;
import com.example.worldgen.feature.level.BlockStateFacts;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.terrain.BlockProperties;

/**
 * Port of {@code net.minecraft.world.level.levelgen.feature.UnderwaterMagmaFeature} (26.3; record of
 * {@code floor_search_range} 0..512, {@code placement_radius_around_floor} 0..64,
 * {@code placement_probability_per_valid_position} 0..1) with {@code Column.scan}:
 * <ul>
 *     <li>floor: nothing unless the origin is water; scanning up then down from the origin
 *     ({@code for (i = 1; i < range && inside; i++) move}), a side ends at the first non-water block, and the floor is the
 *     downward edge when that block is not water;</li>
 *     <li>then over {@code BlockPos.betweenClosedStream} of the box {@code floor +- radius} (x fastest, then y, then z), per
 *     position: {@code nextFloat() < probability} (drawn for every position), then {@code isValidPlacement}, then
 *     {@code setBlock(pos, magma_block, 2)};</li>
 *     <li>{@code isValidPlacement}: the block is neither water nor air, the block below does not leave its UP face visible,
 *     and no horizontal neighbour ({@code NORTH, EAST, SOUTH, WEST}) leaves its face towards the position visible
 *     ({@code isVisibleFromOutside}: the face occlusion shape is empty or not a full block).</li>
 * </ul>
 */
public record UnderwaterMagmaFeature(int floorSearchRange, int placementRadiusAroundFloor, float placementProbabilityPerValidPosition)
        implements Feature {

    private static final StateRef MAGMA_BLOCK = StateRef.of("minecraft:magma_block");

    @Override
    public String type() {
        return "minecraft:underwater_magma";
    }

    @Override
    public boolean place(GenRegion level, com.example.worldgen.feature.DecorationGenerator generator, RandomSource random, Pos origin) {
        Integer floorY = getFloorY(level, origin);
        if (floorY == null) return false;
        int r = placementRadiusAroundFloor;
        int placed = 0;
        for (int z = origin.z() - r; z <= origin.z() + r; z++) {
            for (int y = floorY - r; y <= floorY + r; y++) {
                for (int x = origin.x() - r; x <= origin.x() + r; x++) {
                    Pos pos = new Pos(x, y, z);
                    if (random.nextFloat() < placementProbabilityPerValidPosition && isValidPlacement(level, pos)) {
                        level.setBlock(pos, BlockProperties.defaultState(MAGMA_BLOCK.id()), 2);
                        placed++;
                    }
                }
            }
        }
        return placed > 0;
    }

    private Integer getFloorY(GenRegion level, Pos origin) {
        if (!isWater(level.getBlockState(origin))) return null;
        int y = origin.y();
        // ceiling side first (its result is not used, but its reads are part of Column.scan)
        Pos p = origin;
        for (int i = 1; i < floorSearchRange && isWater(level.getBlockState(p)); ++i) p = p.above();
        level.getBlockState(p);
        p = origin.atY(y);
        for (int i = 1; i < floorSearchRange && isWater(level.getBlockState(p)); ++i) p = p.below();
        return !isWater(level.getBlockState(p)) ? p.y() : null;
    }

    private static boolean isWater(StateRef state) {
        return state.is("minecraft:water");
    }

    private boolean isValidPlacement(GenRegion level, Pos pos) {
        StateRef state = level.getBlockState(pos);
        if (isWater(state) || BlockProperties.isAir(state) || isVisibleFromOutside(level, pos.below(), Direction.UP)) return false;
        for (Direction d : Direction.HORIZONTAL) {
            if (isVisibleFromOutside(level, pos.relative(d), d.opposite())) return false;
        }
        return true;
    }

    private static boolean isVisibleFromOutside(GenRegion level, Pos pos, Direction coveredDirection) {
        return !BlockStateFacts.faceOcclusionIsFullBlock(level.getBlockState(pos), coveredDirection);
    }
}
