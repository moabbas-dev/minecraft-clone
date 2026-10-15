package com.example.world;

import org.joml.Vector3f;

/**
 * True DDA (Amanatides &amp; Woo) voxel traversal for block targeting (break/place).
 *
 * This replaces an earlier small-step marcher (0.02-unit steps, up to 250 iterations for a
 * 5-block reach, one {@link World#getBlock} HashMap lookup per newly-entered cell). DDA steps
 * directly from grid-cell boundary to grid-cell boundary instead of sampling at a fixed
 * distance interval, so a 5-block cast now takes on the order of 10-20 iterations - it also
 * can't skip past a thin diagonal corner the way a fixed step size theoretically could.
 */
public class Raycast {

    public record Hit(int x, int y, int z, int placeX, int placeY, int placeZ, BlockType type) {
    }

    public static Hit cast(World world, Vector3f origin, Vector3f direction, float maxDistance) {
        return cast(world, origin, direction, maxDistance, false);
    }

    /** As {@link #cast(World, Vector3f, Vector3f, float)}; with {@code fluidSources} a still water or lava source also stops the ray (an empty bucket's aim). */
    public static Hit cast(World world, Vector3f origin, Vector3f direction, float maxDistance, boolean fluidSources) {
        Vector3f dir = new Vector3f(direction).normalize();

        int x = (int) Math.floor(origin.x);
        int y = (int) Math.floor(origin.y);
        int z = (int) Math.floor(origin.z);

        int stepX = Float.compare(dir.x, 0f) > 0 ? 1 : (dir.x < 0f ? -1 : 0);
        int stepY = Float.compare(dir.y, 0f) > 0 ? 1 : (dir.y < 0f ? -1 : 0);
        int stepZ = Float.compare(dir.z, 0f) > 0 ? 1 : (dir.z < 0f ? -1 : 0);

        float tDeltaX = stepX != 0 ? Math.abs(1f / dir.x) : Float.POSITIVE_INFINITY;
        float tDeltaY = stepY != 0 ? Math.abs(1f / dir.y) : Float.POSITIVE_INFINITY;
        float tDeltaZ = stepZ != 0 ? Math.abs(1f / dir.z) : Float.POSITIVE_INFINITY;

        float tMaxX = stepX != 0 ? nextBoundaryT(origin.x, x, stepX, dir.x) : Float.POSITIVE_INFINITY;
        float tMaxY = stepY != 0 ? nextBoundaryT(origin.y, y, stepY, dir.y) : Float.POSITIVE_INFINITY;
        float tMaxZ = stepZ != 0 ? nextBoundaryT(origin.z, z, stepZ, dir.z) : Float.POSITIVE_INFINITY;

        while (true) {
            int axis; // 0=x, 1=y, 2=z - whichever grid boundary is nearest along the ray
            float nextT;
            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                axis = 0;
                nextT = tMaxX;
            } else if (tMaxY <= tMaxZ) {
                axis = 1;
                nextT = tMaxY;
            } else {
                axis = 2;
                nextT = tMaxZ;
            }
            if (nextT > maxDistance) return null;

            int prevX = x, prevY = y, prevZ = z;
            switch (axis) {
                case 0 -> {
                    x += stepX;
                    tMaxX += tDeltaX;
                }
                case 1 -> {
                    y += stepY;
                    tMaxY += tDeltaY;
                }
                default -> {
                    z += stepZ;
                    tMaxZ += tDeltaZ;
                }
            }

            BlockType type = world.getBlock(x, y, z);
            if (!type.isAir() && (!type.isFluid() || (fluidSources && type.isFluidSource()))) {
                return new Hit(x, y, z, prevX, prevY, prevZ, type);
            }
        }
    }

    /** Ray parameter t at which the ray first crosses the boundary of the starting cell along one axis. */
    private static float nextBoundaryT(float originCoord, int cell, int step, float dirComponent) {
        float boundary = step > 0 ? (cell + 1) : cell;
        return (boundary - originCoord) / dirComponent;
    }
}
