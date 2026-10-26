package com.example.worldgen.feature.types;

import com.example.worldgen.feature.DecorationGenerator;
import com.example.worldgen.feature.Direction;
import com.example.worldgen.feature.Feature;
import com.example.worldgen.feature.Pos;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.feature.predicate.BlockPredicate;
import com.example.worldgen.feature.stateprovider.BlockStateProvider;
import com.example.worldgen.provider.IntProvider;
import com.example.worldgen.random.RandomSource;

import java.util.List;

/**
 * Port of {@code net.minecraft.world.level.levelgen.feature.BlockColumnFeature} (26.3 record {@code (layers, direction,
 * allowedPlacement, prioritizeTip)}, {@code place} 265 bytes):
 * <ol>
 *     <li>every layer's height is sampled in layer order ({@code IntProvider.sample}); total 0: false;</li>
 *     <li>a test position starts one step from the origin in {@code direction}; for {@code i < total}: when
 *     {@code allowedPlacement} fails there, {@code truncate(heights, total, i, prioritizeTip)} and stop; else step on;</li>
 *     <li>from the origin itself, every layer writes its (possibly truncated) height of {@code provider.getState(level,
 *     random, pos)} with flags 2, stepping in {@code direction}; true.</li>
 * </ol>
 * {@code truncate(heights, total, placeable, prioritizeTip)}: {@code toRemove = total - placeable}, taken from the first
 * layer onwards when {@code prioritizeTip}, else from the last layer backwards.
 */
public final class BlockColumnFeature implements Feature {

    /** {@code BlockColumnFeature.Layer(height, provider)}. */
    public record Layer(IntProvider height, BlockStateProvider state) {
    }

    private final List<Layer> layers;
    private final Direction direction;
    private final BlockPredicate allowedPlacement;
    private final boolean prioritizeTip;

    public BlockColumnFeature(List<Layer> layers, Direction direction, BlockPredicate allowedPlacement, boolean prioritizeTip) {
        this.layers = layers;
        this.direction = direction;
        this.allowedPlacement = allowedPlacement;
        this.prioritizeTip = prioritizeTip;
    }

    @Override
    public String type() {
        return "minecraft:block_column";
    }

    @Override
    public boolean place(GenRegion level, DecorationGenerator generator, RandomSource random, Pos origin) {
        int layerCount = layers.size();
        int[] heights = new int[layerCount];
        int total = 0;
        for (int i = 0; i < layerCount; ++i) {
            heights[i] = layers.get(i).height().sample(random);
            total += heights[i];
        }
        if (total == 0) return false;
        Pos test = origin.relative(direction);
        for (int i = 0; i < total; ++i) {
            if (!allowedPlacement.test(level, test)) {
                truncate(heights, total, i, prioritizeTip);
                break;
            }
            test = test.relative(direction);
        }
        Pos pos = origin;
        for (int i = 0; i < layerCount; ++i) {
            int height = heights[i];
            if (height == 0) continue;
            Layer layer = layers.get(i);
            for (int j = 0; j < height; ++j) {
                level.setBlock(pos, layer.state().getState(level, random, pos), 2);
                pos = pos.relative(direction);
            }
        }
        return true;
    }

    static void truncate(int[] heights, int totalHeight, int placeable, boolean prioritizeTip) {
        int toRemove = totalHeight - placeable;
        int dir = prioritizeTip ? 1 : -1;
        int start = prioritizeTip ? 0 : heights.length - 1;
        int end = prioritizeTip ? heights.length : -1;
        for (int i = start; i != end && toRemove > 0; i += dir) {
            int height = heights[i];
            int removed = Math.min(height, toRemove);
            toRemove -= removed;
            heights[i] -= removed;
        }
    }
}
