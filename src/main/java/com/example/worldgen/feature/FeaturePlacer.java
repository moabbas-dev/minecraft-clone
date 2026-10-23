package com.example.worldgen.feature;

import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.feature.placement.PlacementContext;
import com.example.worldgen.feature.placement.PlacementModifier;
import com.example.worldgen.random.RandomSource;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of {@code net.minecraft.world.level.levelgen.placement.FeaturePlacer} (26.3, new in this version; private
 * {@code place(feature, random, origin, biomeCheck)}). An explicit stack replaces the old lazy streams: pop a position and
 * its modifier index, run that modifier (all its outputs are produced eagerly, in order); if more modifiers follow, push the
 * outputs in reverse order (so the first output is processed next, depth first); otherwise place the feature at every
 * output in order and OR the results. With no modifiers the feature is placed once at the origin. One placer is used per
 * chunk; its lists are reused.
 */
public final class FeaturePlacer {

    private final GenRegion level;
    private final DecorationGenerator generator;
    private final List<Pos> positions = new ArrayList<>();
    private final List<Integer> modifierIndices = new ArrayList<>();
    private final List<Pos> modifiedPositions = new ArrayList<>();

    public FeaturePlacer(GenRegion level, DecorationGenerator generator) {
        this.level = level;
        this.generator = generator;
    }

    public boolean place(PlacedFeature placedFeature, RandomSource random, Pos origin) {
        return place(placedFeature, random, origin, false);
    }

    public boolean placeWithBiomeCheck(PlacedFeature placedFeature, RandomSource random, Pos origin) {
        return place(placedFeature, random, origin, true);
    }

    private boolean place(PlacedFeature placedFeature, RandomSource random, Pos origin, boolean biomeCheck) {
        PlacementContext context = new PlacementContext(level, generator, biomeCheck ? placedFeature : null);
        Feature feature = placedFeature.feature();
        List<PlacementModifier> placement = placedFeature.placement();
        if (placement.isEmpty()) {
            return feature.place(level, random, origin);
        }
        boolean placedAny = false;
        positions.add(origin);
        modifierIndices.add(0);
        while (!positions.isEmpty()) {
            Pos pos = positions.remove(positions.size() - 1);
            int modifierIndex = modifierIndices.remove(modifierIndices.size() - 1);
            placement.get(modifierIndex).modify(context, random, pos, modifiedPositions::add);
            int nextModifierIndex = modifierIndex + 1;
            if (nextModifierIndex < placement.size()) {
                for (int i = modifiedPositions.size() - 1; i >= 0; --i) {
                    positions.add(modifiedPositions.get(i));
                    modifierIndices.add(nextModifierIndex);
                }
            } else {
                for (Pos nextPos : modifiedPositions) {
                    placedAny |= feature.place(level, random, nextPos);
                }
            }
            modifiedPositions.clear();
        }
        return placedAny;
    }
}
