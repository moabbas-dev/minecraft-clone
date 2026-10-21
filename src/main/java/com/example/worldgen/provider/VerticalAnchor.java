package com.example.worldgen.provider;

import com.example.worldgen.DimensionType;

import java.util.Map;

/**
 * Port of {@code net.minecraft.world.level.levelgen.VerticalAnchor} (26.3) and its four records. JSON:
 * {@code CODEC = xor(Absolute, xor(AboveBottom, xor(BelowTop, RelativeToSeaLevel)))}, each a single
 * {@code intRange(DimensionType.MIN_Y, DimensionType.MAX_Y).fieldOf(name)}: {@code absolute}, {@code above_bottom},
 * {@code below_top}, {@code relative_to_sea_level}.
 */
public sealed interface VerticalAnchor {

    VerticalAnchor BOTTOM = aboveBottom(0);
    VerticalAnchor TOP = belowTop(0);

    int resolveY(WorldGenerationContext context);

    static VerticalAnchor absolute(int y) {
        return new Absolute(y);
    }

    static VerticalAnchor aboveBottom(int offset) {
        return new AboveBottom(offset);
    }

    static VerticalAnchor belowTop(int offset) {
        return new BelowTop(offset);
    }

    static VerticalAnchor relativeToSeaLevel(int offset) {
        return new RelativeToSeaLevel(offset);
    }

    /** {@code y}. */
    record Absolute(int y) implements VerticalAnchor {
        @Override
        public int resolveY(WorldGenerationContext context) {
            return y;
        }
    }

    /** {@code getMinGenY() + offset}. */
    record AboveBottom(int offset) implements VerticalAnchor {
        @Override
        public int resolveY(WorldGenerationContext context) {
            return context.getMinGenY() + offset;
        }
    }

    /** {@code getGenDepth() - 1 + getMinGenY() - offset}. */
    record BelowTop(int offset) implements VerticalAnchor {
        @Override
        public int resolveY(WorldGenerationContext context) {
            return context.getGenDepth() - 1 + context.getMinGenY() - offset;
        }
    }

    /** {@code seaLevel() + offset}. */
    record RelativeToSeaLevel(int offset) implements VerticalAnchor {
        @Override
        public int resolveY(WorldGenerationContext context) {
            return context.seaLevel() + offset;
        }
    }

    /** {@code VerticalAnchor.CODEC.parse}; throws {@link CodecJson.CodecException} when it does not decode. */
    static VerticalAnchor parse(Object json) {
        VerticalAnchor a = tryParse(json);
        if (a == null) {
            throw new CodecJson.CodecException("Not a vertical anchor: " + json);
        }
        return a;
    }

    /** The nested xor: an alternative is chosen only when exactly one side of each xor decodes; else null. */
    static VerticalAnchor tryParse(Object json) {
        if (!(json instanceof Map<?, ?>)) {
            return null;
        }
        Map<String, Object> m = CodecJson.object(json, "vertical_anchor");
        VerticalAnchor d = single(m, "relative_to_sea_level");
        VerticalAnchor c = single(m, "below_top");
        VerticalAnchor cd = xor(c, d);
        VerticalAnchor b = single(m, "above_bottom");
        VerticalAnchor bcd = xor(b, cd);
        VerticalAnchor a = single(m, "absolute");
        return xor(a, bcd);
    }

    private static VerticalAnchor xor(VerticalAnchor first, VerticalAnchor second) {
        if (first != null && second != null) {
            return null;
        }
        return first != null ? first : second;
    }

    private static VerticalAnchor single(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (!(v instanceof Number n)) {
            return null;
        }
        int i = n.intValue();
        if (i < DimensionType.MIN_Y || i > DimensionType.MAX_Y) {
            return null;
        }
        return switch (key) {
            case "absolute" -> new Absolute(i);
            case "above_bottom" -> new AboveBottom(i);
            case "below_top" -> new BelowTop(i);
            default -> new RelativeToSeaLevel(i);
        };
    }
}
