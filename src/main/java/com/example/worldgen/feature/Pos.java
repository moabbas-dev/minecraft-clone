package com.example.worldgen.feature;

import com.example.worldgen.provider.CodecJson;

import java.util.List;

/**
 * An immutable block position: the value semantics of {@code net.minecraft.core.BlockPos} (26.3) the feature code uses
 * ({@code offset}, {@code above}, {@code below}, {@code relative}, {@code atY}). Vanilla's {@code MutableBlockPos} is only an
 * allocation optimisation; every place that hands a mutable position on (for example {@code EnvironmentScanPlacement})
 * stops mutating it afterwards, so immutable values give the same results.
 */
public record Pos(int x, int y, int z) {

    public Pos offset(int dx, int dy, int dz) {
        return dx == 0 && dy == 0 && dz == 0 ? this : new Pos(x + dx, y + dy, z + dz);
    }

    public Pos relative(Direction d) {
        return new Pos(x + d.stepX(), y + d.stepY(), z + d.stepZ());
    }

    public Pos above() {
        return new Pos(x, y + 1, z);
    }

    public Pos below() {
        return new Pos(x, y - 1, z);
    }

    public Pos atY(int newY) {
        return new Pos(x, newY, z);
    }

    /** {@code BlockPos.CODEC} / {@code Vec3i.CODEC}: a list of three ints. */
    public static Pos parse(Object json) {
        List<Object> l = CodecJson.list(json, "block position");
        if (l.size() != 3) throw new CodecJson.CodecException("Input is not a list of 3 elements: " + json);
        return new Pos(CodecJson.intValue(l.get(0), "x"), CodecJson.intValue(l.get(1), "y"), CodecJson.intValue(l.get(2), "z"));
    }

    /**
     * {@code Vec3i.offsetCodec(maxOffset)}: a {@link #parse three-int list} with every component in
     * {@code [-maxOffset, maxOffset]}.
     */
    public static Pos parseOffset(Object json, int maxOffset) {
        Pos p = parse(json);
        if (Math.abs(p.x) > maxOffset || Math.abs(p.y) > maxOffset || Math.abs(p.z) > maxOffset) {
            throw new CodecJson.CodecException("Position out of range, expected at most " + maxOffset + ": " + p);
        }
        return p;
    }

    @Override
    public String toString() {
        return x + "," + y + "," + z;
    }
}
