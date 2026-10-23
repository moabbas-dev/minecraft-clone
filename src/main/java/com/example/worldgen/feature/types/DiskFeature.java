package com.example.worldgen.feature.types;

import com.example.worldgen.feature.Feature;
import com.example.worldgen.feature.Pos;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.feature.predicate.BlockPredicate;
import com.example.worldgen.feature.stateprovider.BlockStateProvider;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.provider.IntProvider;
import com.example.worldgen.random.RandomSource;

/**
 * Port of {@code net.minecraft.world.level.levelgen.feature.DiskFeature} (26.3, a record of {@code state_provider},
 * {@code target}, {@code radius} ({@code IntProviders.codec(0, 8)}), {@code half_height} (0..4)): {@code r =
 * radius.sample(random)}; columns over {@code BlockPos.betweenClosed(origin - (r, 0, r), origin + (r, 0, r))} (x fastest,
 * then z) with {@code dx^2 + dz^2 <= r^2}; per column {@code y} from {@code origin.y + halfHeight} down to
 * {@code origin.y - halfHeight} (exclusive bottom {@code origin.y - halfHeight - 1}): where {@code target} matches, the
 * provider's optional state (if not null) is set with flags 2, and the first block of each run marks the two blocks above
 * for post-processing ({@code markAboveForPostProcessing}); a non-matching block ends the run.
 */
public record DiskFeature(BlockStateProvider stateProvider, BlockPredicate target, IntProvider radius, int halfHeight) implements Feature {

    @Override
    public String type() {
        return "minecraft:disk";
    }

    @Override
    public boolean place(GenRegion level, RandomSource random, Pos origin) {
        boolean placedAny = false;
        int originY = origin.y();
        int top = originY + halfHeight;
        int bottom = originY - halfHeight - 1;
        int r = radius.sample(random);
        for (int z = origin.z() - r; z <= origin.z() + r; z++) {
            for (int x = origin.x() - r; x <= origin.x() + r; x++) {
                int xd = x - origin.x();
                int zd = z - origin.z();
                if (xd * xd + zd * zd <= r * r) {
                    placedAny |= placeColumn(level, random, top, bottom, x, z);
                }
            }
        }
        return placedAny;
    }

    private boolean placeColumn(GenRegion level, RandomSource random, int top, int bottom, int x, int z) {
        boolean placedAny = false;
        boolean placedAbove = false;
        for (int y = top; y > bottom; --y) {
            Pos pos = new Pos(x, y, z);
            if (target.test(level, pos)) {
                StateRef state = stateProvider.getOptionalState(level, random, pos);
                if (state != null) {
                    level.setBlock(pos, state, 2);
                    if (!placedAbove) Feature.markAboveForPostProcessing(level, pos);
                    placedAny = true;
                    placedAbove = true;
                }
            } else {
                placedAbove = false;
            }
        }
        return placedAny;
    }
}
