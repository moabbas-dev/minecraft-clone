package com.example.worldgen.feature;

import com.example.worldgen.provider.CodecJson;
import com.example.worldgen.random.RandomSource;

/**
 * The parts of {@code net.minecraft.core.Direction} (26.3) the feature code uses. Enum order is vanilla's
 * {@code Direction.values()} order ({@code DOWN, UP, NORTH, SOUTH, WEST, EAST}, read from the static initialiser), which
 * {@code AbstractOreFeature.checkNeighbors} iterates; {@link #HORIZONTAL} is {@code Direction$Plane.HORIZONTAL}'s order
 * ({@code NORTH, EAST, SOUTH, WEST}). Steps: {@code DOWN (0,-1,0)}, {@code UP (0,1,0)}, {@code NORTH (0,0,-1)},
 * {@code SOUTH (0,0,1)}, {@code WEST (-1,0,0)}, {@code EAST (1,0,0)}.
 */
public enum Direction {
    DOWN(0, -1, 0, "down"),
    UP(0, 1, 0, "up"),
    NORTH(0, 0, -1, "north"),
    SOUTH(0, 0, 1, "south"),
    WEST(-1, 0, 0, "west"),
    EAST(1, 0, 0, "east");

    /** {@code Direction$Plane.HORIZONTAL} iteration order. */
    public static final Direction[] HORIZONTAL = {NORTH, EAST, SOUTH, WEST};

    private final int stepX;
    private final int stepY;
    private final int stepZ;
    private final String serializedName;

    Direction(int stepX, int stepY, int stepZ, String serializedName) {
        this.stepX = stepX;
        this.stepY = stepY;
        this.stepZ = stepZ;
        this.serializedName = serializedName;
    }

    public int stepX() {
        return stepX;
    }

    public int stepY() {
        return stepY;
    }

    public int stepZ() {
        return stepZ;
    }

    public String serializedName() {
        return serializedName;
    }

    public Direction opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }

    /** {@code getAxis().isHorizontal()}. */
    public boolean isHorizontal() {
        return stepY == 0;
    }

    /** {@code getAxis().getName()}: {@code x}, {@code y} or {@code z}. */
    public String axisName() {
        return stepX != 0 ? "x" : stepY != 0 ? "y" : "z";
    }

    /** {@code Direction.getRandom(random)}: {@code Util.getRandom(VALUES, random)} = {@code VALUES[random.nextInt(6)]}. */
    public static Direction getRandom(RandomSource random) {
        return values()[random.nextInt(values().length)];
    }

    /** {@code Direction.CODEC} ({@code StringRepresentable} by serialized name). */
    public static Direction parse(Object json) {
        if (json instanceof String s) {
            for (Direction d : values()) {
                if (d.serializedName.equals(s)) return d;
            }
        }
        throw new CodecJson.CodecException("Unknown direction: " + json);
    }

    /** {@code Direction.VERTICAL_CODEC}: {@code up} or {@code down} only. */
    public static Direction parseVertical(Object json) {
        Direction d = parse(json);
        if (d.isHorizontal()) throw new CodecJson.CodecException("Expected a vertical direction: " + json);
        return d;
    }
}
