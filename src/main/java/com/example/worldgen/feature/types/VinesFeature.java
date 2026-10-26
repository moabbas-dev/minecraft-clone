package com.example.worldgen.feature.types;

import com.example.worldgen.feature.DecorationGenerator;
import com.example.worldgen.feature.Direction;
import com.example.worldgen.feature.Feature;
import com.example.worldgen.feature.Pos;
import com.example.worldgen.feature.level.BlockStateFacts;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.terrain.BlockProperties;

import java.util.Map;
import java.util.TreeMap;

/**
 * Port of {@code net.minecraft.world.level.levelgen.feature.VinesFeature} (26.3, unit codec, {@code place} 107 bytes): not
 * empty at the origin: false; else for each direction of {@code Direction.values()} except {@code DOWN}, the first one with
 * {@code VineBlock.isAcceptableNeighbour(level, origin.relative(dir), dir)} gets {@code VINE.defaultBlockState()
 * .setValue(VineBlock.getPropertyForFace(dir), true)} written with flags 2 and the feature returns true; none: false.
 * {@code isAcceptableNeighbour(level, pos, dir)} = {@code MultifaceBlock.canAttachTo(level, dir, pos, level.getBlockState(pos))}
 * = {@code Block.isFaceFull(state.getBlockSupportShape(level, pos), dir.getOpposite())} or the same for the collision shape.
 * {@code getPropertyForFace}: the {@code PipeBlock} property of the direction's name ({@code up}, {@code north}, ...).
 */
public final class VinesFeature implements Feature {

    @Override
    public String type() {
        return "minecraft:vines";
    }

    @Override
    public boolean place(GenRegion level, DecorationGenerator generator, RandomSource random, Pos origin) {
        if (!level.isEmptyBlock(origin)) return false;
        for (Direction direction : Direction.values()) {
            if (direction == Direction.DOWN) continue;
            if (isAcceptableNeighbour(level, origin.relative(direction), direction)) {
                Map<String, String> p = new TreeMap<>(BlockProperties.VINE.properties());
                p.put(direction.serializedName(), "true");
                level.setBlock(origin, new StateRef(BlockProperties.VINE.id(), p), 2);
                return true;
            }
        }
        return false;
    }

    /** {@code VineBlock.isAcceptableNeighbour(level, pos, direction)}. */
    static boolean isAcceptableNeighbour(GenRegion level, Pos pos, Direction direction) {
        StateRef state = level.getBlockState(pos);
        Direction face = direction.opposite();
        return BlockStateFacts.supportFaceFull(state, face) || BlockStateFacts.collisionFaceFull(state, face);
    }
}
