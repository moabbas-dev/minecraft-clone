package com.example.world;

import java.util.Random;

/**
 * Shared voxel primitives for the species canopy shapes in {@link TreeSpecies}: rounded blobs,
 * flat discs, branch logs and hanging leaves. Every write goes through the same two rules the
 * tree code has always followed - leaves only ever replace AIR (so they can never overwrite a log,
 * terrain, water or another tree), and nothing is written outside the chunk. Logs may replace air
 * or leaves (a branch grown after a neighbouring canopy) but never terrain or other solids.
 *
 * All geometry is integer-coordinate; ellipsoid tests use exact double arithmetic on integer
 * offsets from a (possibly half-block) centre, so the result depends only on the inputs and the
 * given Random. Package-private: an implementation detail of {@link TreeSpecies}.
 */
final class CanopyBuilder {
    private final Chunk chunk;
    private final BlockType leaves;
    private final BlockType log;
    private final Random rnd;
    /** Lowest y a leaf may occupy - one above the trunk base, so no foliage ever reaches the ground. */
    private final int floorY;
    /** Every leaf this builder placed, packed - what {@link #prune()} may remove. */
    private final java.util.ArrayList<int[]> placed = new java.util.ArrayList<>();

    CanopyBuilder(Chunk chunk, BlockType log, BlockType leaves, Random rnd, int floorY) {
        this.floorY = floorY;
        this.chunk = chunk;
        this.log = log;
        this.leaves = leaves;
        this.rnd = rnd;
    }

    private static boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < Chunk.SIZE_X && z >= 0 && z < Chunk.SIZE_Z && y >= 0 && y < Chunk.SIZE_Y;
    }

    /** Places one leaf if the cell is in the chunk, at or above the floor, and currently AIR. */
    boolean leaf(int x, int y, int z) {
        if (y < floorY || !inBounds(x, y, z) || !chunk.getLocal(x, y, z).isAir()) return false;
        chunk.setLocal(x, y, z, leaves);
        placed.add(new int[]{x, y, z});
        return true;
    }

    /** Places one log if the cell is in the chunk and currently AIR or this tree's own/any leaves. */
    boolean log(int x, int y, int z) {
        if (!inBounds(x, y, z)) return false;
        BlockType existing = chunk.getLocal(x, y, z);
        if (!existing.isAir() && existing != leaves) return false;
        chunk.setLocal(x, y, z, log);
        return true;
    }

    /** True if the block at the given cell is one of this tree's logs. */
    boolean isLog(int x, int y, int z) {
        return inBounds(x, y, z) && chunk.getLocal(x, y, z) == log;
    }

    /**
     * A filled ellipsoid of leaves centred on (cx, cy, cz) (half-block centres allowed, e.g. the
     * middle of a 2x2 trunk) with semi-axes rx/ry/rz. A cell is inside when its offset satisfies
     * (dx/rx)^2 + (dy/ry)^2 + (dz/rz)^2 &lt;= 1. Cells on the outer shell (value above 0.62) are
     * skipped with probability {@code holeChance}, which is what leaves small natural gaps at the
     * edge without ever hollowing the interior.
     */
    void blob(double cx, double cy, double cz, double rx, double ry, double rz, double holeChance) {
        int x0 = (int) Math.floor(cx - rx), x1 = (int) Math.ceil(cx + rx);
        int y0 = (int) Math.floor(cy - ry), y1 = (int) Math.ceil(cy + ry);
        int z0 = (int) Math.floor(cz - rz), z1 = (int) Math.ceil(cz + rz);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                for (int z = z0; z <= z1; z++) {
                    double dx = (x - cx) / rx, dy = (y - cy) / ry, dz = (z - cz) / rz;
                    double v = dx * dx + dy * dy + dz * dz;
                    if (v > 1.0) continue;
                    if (v > 0.62 && holeChance > 0 && rnd.nextDouble() < holeChance) continue;
                    leaf(x, y, z);
                }
            }
        }
    }

    /**
     * One horizontal layer: every cell within Euclidean distance {@code radius} of the centre
     * (radius 1.4 gives a plus shape, 1.5 a 3x3 without corners removed, etc.). Cells at the very
     * edge (distance above radius - 0.6) are skipped with probability {@code edgeHole}.
     */
    void disc(int cx, int y, int cz, double radius, double edgeHole) {
        int r = (int) Math.ceil(radius);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d > radius) continue;
                if (edgeHole > 0 && d > radius - 0.6 && rnd.nextDouble() < edgeHole) continue;
                leaf(cx + dx, y, cz + dz);
            }
        }
    }

    /**
     * A staircase of logs from (x, y) toward (x + dx*steps, ...): each step goes one block sideways
     * in the (dx, dz) direction and one block up, placed as side-then-up so consecutive logs always
     * share a face (never a diagonal-only connection). The start cell is not written; returns the
     * final log position as {x, y, z}.
     */
    int[] branch(int x, int y, int z, int dx, int dz, int steps) {
        int cx = x, cy = y, cz = z;
        for (int i = 0; i < steps; i++) {
            if (dx != 0) {
                cx += dx;
                log(cx, cy, cz);
            }
            if (dz != 0) {
                cz += dz;
                log(cx, cy, cz);
            }
            cy++;
            log(cx, cy, cz);
        }
        return new int[]{cx, cy, cz};
    }

    /** A vertical run of hanging leaves under (x, y, z) - stops at the first non-air cell. */
    void hang(int x, int y, int z, int length) {
        for (int i = 1; i <= length; i++) {
            if (!leaf(x, y - i, z)) break;
        }
    }

    /**
     * Hangs {@code length} leaves under the lowest leaf found in column (x, z) between yHigh and
     * yLow (scanned top-down, so this is the first leaf with air beneath it). Returns whether a
     * strand was placed. Used for cherry/mangrove/swamp drooping edges.
     */
    boolean dangle(int x, int z, int yHigh, int yLow, int length) {
        for (int y = yHigh; y >= yLow; y--) {
            if (inBounds(x, y, z) && chunk.getLocal(x, y, z) == leaves && inBounds(x, y - 1, z) && chunk.getLocal(x, y - 1, z).isAir()) {
                hang(x, y, z, length);
                return true;
            }
        }
        return false;
    }

    /**
     * Removes any leaf this builder placed that is not face-connected (through other placed leaves)
     * to a log. Random shell holes can, rarely, leave a single leaf attached only diagonally; this
     * guarantees no floating leaves without changing anything that is already connected.
     */
    void prune() {
        java.util.HashSet<Integer> set = new java.util.HashSet<>();
        for (int[] p : placed) set.add(pack(p[0], p[1], p[2]));
        java.util.HashSet<Integer> seen = new java.util.HashSet<>();
        java.util.ArrayDeque<int[]> queue = new java.util.ArrayDeque<>();
        for (int[] p : placed) {
            if (touchesLog(p[0], p[1], p[2]) && seen.add(pack(p[0], p[1], p[2]))) queue.add(p);
        }
        int[][] n6 = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            for (int[] d : n6) {
                int nx = p[0] + d[0], ny = p[1] + d[1], nz = p[2] + d[2];
                if (set.contains(pack(nx, ny, nz)) && seen.add(pack(nx, ny, nz))) queue.add(new int[]{nx, ny, nz});
            }
        }
        for (int[] p : placed) {
            if (!seen.contains(pack(p[0], p[1], p[2]))) chunk.setLocal(p[0], p[1], p[2], BlockType.AIR);
        }
    }

    private static int pack(int x, int y, int z) {
        return ((x + 64) * 512 + y) * 256 + (z + 64);
    }

    private boolean touchesLog(int x, int y, int z) {
        return isLog(x + 1, y, z) || isLog(x - 1, y, z) || isLog(x, y + 1, z) || isLog(x, y - 1, z)
                || isLog(x, y, z + 1) || isLog(x, y, z - 1);
    }
}
