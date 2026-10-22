package com.example.worldgen.material;

/**
 * Port of {@code net.minecraft.world.level.levelgen.VerticalAnchor} (26.3) for the material rules. The 26.3 codec is
 * {@code xor(Absolute, xor(AboveBottom, xor(BelowTop, RelativeToSeaLevel)))}: an object with exactly one of the keys
 * {@code absolute}, {@code above_bottom}, {@code below_top}, {@code relative_to_sea_level}, each an int in
 * {@code [DimensionType.MIN_Y, DimensionType.MAX_Y]}. {@code resolveY} (bytecode):
 * <ul>
 *     <li>absolute: {@code y};</li>
 *     <li>above_bottom: {@code context.getMinGenY() + offset};</li>
 *     <li>below_top: {@code context.getGenDepth() - 1 + context.getMinGenY() - offset};</li>
 *     <li>relative_to_sea_level: {@code context.seaLevel() + offset}.</li>
 * </ul>
 */
public sealed interface VerticalAnchor {

    int resolveY(WorldGenerationContext context);

    record Absolute(int y) implements VerticalAnchor {
        @Override
        public int resolveY(WorldGenerationContext context) {
            return y;
        }
    }

    record AboveBottom(int offset) implements VerticalAnchor {
        @Override
        public int resolveY(WorldGenerationContext context) {
            return context.minGenY() + offset;
        }
    }

    record BelowTop(int offset) implements VerticalAnchor {
        @Override
        public int resolveY(WorldGenerationContext context) {
            return context.genDepth() - 1 + context.minGenY() - offset;
        }
    }

    record RelativeToSeaLevel(int offset) implements VerticalAnchor {
        @Override
        public int resolveY(WorldGenerationContext context) {
            return context.seaLevel() + offset;
        }
    }
}
