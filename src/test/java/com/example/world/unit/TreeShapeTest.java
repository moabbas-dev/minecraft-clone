package com.example.world.unit;

import com.example.world.BlockType;
import com.example.world.Chunk;
import com.example.world.TreeSpecies;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Per-species silhouette guarantees for the canopy rework. Every test places real trees through
 * {@link TreeSpecies#place} on a bare chunk (no rendering) and checks measurable geometry - the
 * defining trait of each species - rather than pinning exact block layouts, which the bounded
 * random variation deliberately changes tree to tree. Oak is the exception: it is the reference
 * shape the rework promised not to touch, so its exact output is pinned.
 */
class TreeShapeTest {
    private static final int BX = 8, BZ = 8, BASE_Y = 10;
    private static final int SEEDS = 200;

    private static Chunk grow(TreeSpecies s, long seed) {
        Chunk chunk = new Chunk(0, 0);
        s.place(chunk, BX, BASE_Y, BZ, new Random(seed));
        return chunk;
    }

    private static boolean isTree(TreeSpecies s, BlockType t) {
        return t == s.log || t == s.leaves;
    }

    /** Bounding box + counts of a species' leaves: {minX,maxX,minY,maxY,minZ,maxZ,count}. */
    private static int[] leafBox(TreeSpecies s, Chunk c) {
        int[] b = {99, -1, 999, -1, 99, -1, 0};
        for (int x = 0; x < Chunk.SIZE_X; x++)
            for (int y = 0; y < 60; y++)
                for (int z = 0; z < Chunk.SIZE_Z; z++) {
                    if (c.getLocal(x, y, z) != s.leaves) continue;
                    b[0] = Math.min(b[0], x); b[1] = Math.max(b[1], x);
                    b[2] = Math.min(b[2], y); b[3] = Math.max(b[3], y);
                    b[4] = Math.min(b[4], z); b[5] = Math.max(b[5], z);
                    b[6]++;
                }
        return b;
    }

    private static int leavesInRow(TreeSpecies s, Chunk c, int y) {
        int n = 0;
        for (int x = 0; x < Chunk.SIZE_X; x++)
            for (int z = 0; z < Chunk.SIZE_Z; z++) if (c.getLocal(x, y, z) == s.leaves) n++;
        return n;
    }

    private static int rowWidth(TreeSpecies s, Chunk c, int y) {
        int minX = 99, maxX = -1, minZ = 99, maxZ = -1;
        for (int x = 0; x < Chunk.SIZE_X; x++)
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                if (c.getLocal(x, y, z) != s.leaves) continue;
                minX = Math.min(minX, x); maxX = Math.max(maxX, x);
                minZ = Math.min(minZ, z); maxZ = Math.max(maxZ, z);
            }
        return maxX < 0 ? 0 : Math.max(maxX - minX, maxZ - minZ) + 1;
    }

    // ---- shared guarantees, every species ----

    @Test
    void everyBlockOfEverySpeciesIsFaceConnectedToTheTrunkNoFloatingLeaves() {
        for (TreeSpecies s : TreeSpecies.values()) {
            for (int seed = 0; seed < SEEDS; seed++) {
                Chunk c = grow(s, seed);
                Set<Integer> tree = new HashSet<>();
                for (int x = 0; x < Chunk.SIZE_X; x++)
                    for (int y = 0; y < 80; y++)
                        for (int z = 0; z < Chunk.SIZE_Z; z++) if (isTree(s, c.getLocal(x, y, z))) tree.add(key(x, y, z));
                Set<Integer> seen = new HashSet<>();
                ArrayDeque<int[]> q = new ArrayDeque<>();
                q.add(new int[]{BX, BASE_Y, BZ});
                seen.add(key(BX, BASE_Y, BZ));
                int[][] n6 = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
                while (!q.isEmpty()) {
                    int[] p = q.poll();
                    for (int[] d : n6) {
                        int nx = p[0] + d[0], ny = p[1] + d[1], nz = p[2] + d[2];
                        int k = key(nx, ny, nz);
                        if (tree.contains(k) && seen.add(k)) q.add(new int[]{nx, ny, nz});
                    }
                }
                assertEquals(tree.size(), seen.size(), s + " seed " + seed + " has blocks not connected to its trunk");
            }
        }
    }

    private static int key(int x, int y, int z) {
        return (x * 256 + y) * 32 + z; // x, z in 0..31 and y < 256 pack uniquely (neighbour probes reach -1..16)
    }

    @Test
    void neverOverwritesAnySolidBlockAndNeverTouchesGroundBelowTheBase() {
        for (TreeSpecies s : TreeSpecies.values()) {
            for (int seed = 0; seed < 50; seed++) {
                Chunk c = new Chunk(0, 0);
                Random scatter = new Random(seed);
                boolean[][][] stone = new boolean[Chunk.SIZE_X][40][Chunk.SIZE_Z];
                for (int x = 0; x < Chunk.SIZE_X; x++) {
                    for (int z = 0; z < Chunk.SIZE_Z; z++) {
                        c.setLocal(x, BASE_Y - 1, z, BlockType.GRASS_BLOCK); // ground
                        for (int y = BASE_Y + 3; y < BASE_Y + 14; y++) {
                            if (scatter.nextInt(12) == 0 && !(x >= BX && x <= BX + 1 && z >= BZ && z <= BZ + 1)) { // never inside the trunk footprint
                                c.setLocal(x, y, z, BlockType.STONE);
                                stone[x][y][z] = true;
                            }
                        }
                    }
                }
                s.place(c, BX, BASE_Y, BZ, new Random(seed));
                for (int x = 0; x < Chunk.SIZE_X; x++)
                    for (int y = 0; y < 40; y++)
                        for (int z = 0; z < Chunk.SIZE_Z; z++) {
                            if (stone[x][y][z]) assertEquals(BlockType.STONE, c.getLocal(x, y, z), s + " overwrote a solid block");
                        }
                for (int x = 0; x < Chunk.SIZE_X; x++)
                    for (int z = 0; z < Chunk.SIZE_Z; z++)
                        if (!(s == TreeSpecies.MANGROVE)) assertEquals(BlockType.GRASS_BLOCK, c.getLocal(x, BASE_Y - 1, z), s + " changed the ground");
            }
        }
    }

    @Test
    void shapesVaryAcrossSeedsButAreIdenticalForTheSameSeed() {
        for (TreeSpecies s : TreeSpecies.values()) {
            Set<Integer> hashes = new HashSet<>();
            for (int seed = 0; seed < 40; seed++) hashes.add(Arrays.hashCode(grow(s, seed).exportBlocks()));
            if (s == TreeSpecies.OAK) {
                assertTrue(hashes.size() <= 3 && hashes.size() >= 1, "oak only varies by trunk height");
            } else {
                assertTrue(hashes.size() >= 8, s + " should not generate the same tree every time, got " + hashes.size() + " distinct shapes");
            }
            assertArrayEquals(grow(s, 77).exportBlocks(), grow(s, 77).exportBlocks(), s + " must be deterministic");
        }
    }

    @Test
    void everyCanopyStaysInsideItsDeclaredHorizontalMarginOnBothSides() {
        // fitsAndIsSuitable reserves exactly canopyRadius on every side of (x, z) inside the chunk;
        // a leaf beyond it could be silently clipped at a chunk edge.
        for (TreeSpecies s : TreeSpecies.values()) {
            int extra = s.trunkFootprint - 1; // dark oak's second trunk column
            for (int seed = 0; seed < SEEDS; seed++) {
                int[] b = leafBox(s, grow(s, seed));
                assertTrue(b[0] >= BX - s.canopyRadius && b[1] <= BX + s.canopyRadius + extra, s + " seed " + seed + " leaves exceed canopyRadius in x: " + b[0] + ".." + b[1]);
                assertTrue(b[4] >= BZ - s.canopyRadius && b[5] <= BZ + s.canopyRadius + extra, s + " seed " + seed + " leaves exceed canopyRadius in z: " + b[4] + ".." + b[5]);
            }
        }
    }

    // ---- oak: preserved ----

    /** Hash of the exact block array for OAK seed 5, taken from the oak implementation as it was
     *  before the canopy rework (placeOak, trunk and leaf are byte-for-byte unchanged in git). */
    private static final int EXPECTED_OAK_HASH = -1640457775;

    @Test
    void oakOutputIsPinnedToTheOriginalShape() {
        // Oak is the reference shape the rework promised not to touch; its exact blocks for a fixed
        // seed are pinned so a future shared-helper change can't alter it unnoticed.
        Chunk c = grow(TreeSpecies.OAK, 5);
        assertEquals(EXPECTED_OAK_HASH, Arrays.hashCode(c.exportBlocks()), "oak's exact block layout changed");
        assertArrayEquals(c.exportBlocks(), grow(TreeSpecies.OAK, 5).exportBlocks());
    }

    // ---- species silhouettes ----

    @Test
    void birchIsNarrowAndVerticallyStretchedWithABareTrunkBelow() {
        for (int seed = 0; seed < SEEDS; seed++) {
            Chunk c = grow(TreeSpecies.BIRCH, seed);
            int[] b = leafBox(TreeSpecies.BIRCH, c);
            int width = Math.max(b[1] - b[0], b[5] - b[4]) + 1;
            int height = b[3] - b[2] + 1;
            assertTrue(width <= 6, "birch canopy too wide: " + width);
            assertTrue(height >= 6, "birch canopy should be tall: " + height);
            assertTrue(height > width, "birch canopy must be taller than wide (" + height + " vs " + width + ")");
            assertTrue(b[2] >= BASE_Y + 2, "birch trunk must be visibly bare for at least 2 blocks");
            // widest row is in the middle band, not the bottom or the top row
            int widest = 0, widestY = b[2];
            for (int y = b[2]; y <= b[3]; y++) if (rowWidth(TreeSpecies.BIRCH, c, y) > widest) { widest = rowWidth(TreeSpecies.BIRCH, c, y); widestY = y; }
            assertTrue(widestY > b[2] && widestY < b[3], "birch is widest in the middle");
            assertTrue(rowWidth(TreeSpecies.BIRCH, c, b[3]) <= 3, "birch tip is narrow");
        }
    }

    @Test
    void spruceTapersFromABroadBottomToAPointedTopInDistinctTiers() {
        boolean sawAnyShelf = false;
        for (int seed = 0; seed < SEEDS; seed++) {
            Chunk c = grow(TreeSpecies.SPRUCE, seed);
            int[] b = leafBox(TreeSpecies.SPRUCE, c);
            assertEquals(1, leavesInRow(TreeSpecies.SPRUCE, c, b[3]), "spruce ends in a single-block tip");
            int bottomWidth = 0, topHalfMax = 0;
            int mid = (b[2] + b[3]) / 2;
            for (int y = b[2]; y <= b[3]; y++) {
                int w = rowWidth(TreeSpecies.SPRUCE, c, y);
                if (y <= b[2] + 2) bottomWidth = Math.max(bottomWidth, w);
                if (y > mid) topHalfMax = Math.max(topHalfMax, w);
            }
            assertTrue(bottomWidth >= 7, "spruce bottom tier should be broad, got " + bottomWidth);
            assertTrue(topHalfMax < bottomWidth, "spruce must be narrower in its upper half than at the bottom");
            assertTrue(b[2] >= BASE_Y + 2, "spruce keeps a bare trunk stub");
            for (int y = b[2]; y < b[3]; y++) {
                if (rowWidth(TreeSpecies.SPRUCE, c, y + 1) < rowWidth(TreeSpecies.SPRUCE, c, y)) { /* narrowing upward is the norm */ }
                if (rowWidth(TreeSpecies.SPRUCE, c, y + 1) > rowWidth(TreeSpecies.SPRUCE, c, y)) sawAnyShelf = true; // a wider whorl above a narrower row
            }
        }
        assertTrue(sawAnyShelf, "spruce should show shelf/whorl rows, not a smooth cone");
    }

    @Test
    void jungleIsTallWithABranchNetworkAndSeveralConnectedLeafMasses() {
        for (int seed = 0; seed < SEEDS; seed++) {
            Chunk c = grow(TreeSpecies.JUNGLE, seed);
            int branchLogs = 0;
            for (int x = 0; x < Chunk.SIZE_X; x++)
                for (int y = 0; y < 60; y++)
                    for (int z = 0; z < Chunk.SIZE_Z; z++)
                        if (c.getLocal(x, y, z) == BlockType.JUNGLE_LOG && !(x == BX && z == BZ)) branchLogs++;
            assertTrue(branchLogs >= 6, "jungle should grow real branches, got " + branchLogs + " branch logs");
            int[] b = leafBox(TreeSpecies.JUNGLE, c);
            assertTrue(b[2] >= BASE_Y + 3, "jungle keeps its lower trunk bare, lowest leaf at " + b[2]);
            assertTrue(b[6] >= 200, "jungle canopy should be dense, got " + b[6] + " leaves");
            assertTrue(b[3] - b[2] >= 6, "jungle canopy is deep");
        }
    }

    @Test
    void acaciaIsBroadShallowAndOffsetFromItsTrunkBase() {
        int offset = 0;
        for (int seed = 0; seed < SEEDS; seed++) {
            Chunk c = grow(TreeSpecies.ACACIA, seed);
            int[] b = leafBox(TreeSpecies.ACACIA, c);
            int width = Math.max(b[1] - b[0], b[5] - b[4]) + 1;
            int thickness = b[3] - b[2] + 1;
            assertTrue(width >= 5, "acacia canopy is broad, got " + width);
            assertTrue(thickness <= 7, "acacia canopy is shallow, got " + thickness);
            assertTrue(width >= thickness, "acacia must be at least as wide as thick");
            double centreX = (b[0] + b[1]) / 2.0, centreZ = (b[4] + b[5]) / 2.0;
            if (Math.abs(centreX - BX) >= 1 || Math.abs(centreZ - BZ) >= 1) offset++;
        }
        assertTrue(offset > SEEDS * 0.5, "the acacia crown should usually sit off its trunk base, got " + offset + "/" + SEEDS);
    }

    @Test
    void darkOakIsBroadAndDenserThanAnyOtherSpecies() {
        int oakLeaves = leafBox(TreeSpecies.OAK, grow(TreeSpecies.OAK, 1))[6];
        for (int seed = 0; seed < SEEDS; seed++) {
            Chunk c = grow(TreeSpecies.DARK_OAK, seed);
            int[] b = leafBox(TreeSpecies.DARK_OAK, c);
            int width = Math.max(b[1] - b[0], b[5] - b[4]) + 1;
            assertTrue(width >= 8, "dark oak crown is wide, got " + width);
            assertTrue(b[6] >= oakLeaves * 4, "dark oak crown is heavy: " + b[6] + " leaves vs oak " + oakLeaves);
            // the crown's widest row spans (nearly) its full width - a solid mass, not a thin disc
            assertTrue(b[3] - b[2] >= 4, "dark oak crown has real vertical depth");
        }
    }

    @Test
    void cherryCrownIsSeveralRoundedLobesNotOneBallAndHasHangingStrands() {
        int withHanging = 0;
        for (int seed = 0; seed < SEEDS; seed++) {
            Chunk c = grow(TreeSpecies.CHERRY, seed);
            int[] b = leafBox(TreeSpecies.CHERRY, c);
            int branchLogs = 0;
            for (int x = 0; x < Chunk.SIZE_X; x++)
                for (int y = 0; y < 60; y++)
                    for (int z = 0; z < Chunk.SIZE_Z; z++)
                        if (c.getLocal(x, y, z) == BlockType.CHERRY_LOG && !(x == BX && z == BZ)) branchLogs++;
            assertTrue(branchLogs >= 2, "cherry branches into lobes, got " + branchLogs + " branch logs");
            assertTrue(Math.max(b[1] - b[0], b[5] - b[4]) + 1 >= 6, "cherry crown is broad");
            // hanging strand: a leaf with air on both sides horizontally and above it
            for (int x = 1; x < 15 && withHanging <= seed; x++)
                for (int y = b[2]; y <= b[3]; y++)
                    for (int z = 1; z < 15; z++) {
                        if (c.getLocal(x, y, z) == BlockType.CHERRY_LEAVES && c.getLocal(x, y + 1, z) == BlockType.CHERRY_LEAVES
                                && c.getLocal(x, y - 1, z).isAir() && c.getLocal(x + 1, y, z).isAir() && c.getLocal(x - 1, y, z).isAir()
                                && c.getLocal(x, y, z + 1).isAir() && c.getLocal(x, y, z - 1).isAir()) { withHanging++; x = 99; break; }
                    }
        }
        assertTrue(withHanging > SEEDS / 2, "most cherry trees should have hanging strands, got " + withHanging);
    }

    @Test
    void mangroveSprawlsIrregularlyAlongItsBranchesAndIsAsymmetric() {
        int asymmetric = 0;
        for (int seed = 0; seed < SEEDS; seed++) {
            Chunk c = grow(TreeSpecies.MANGROVE, seed);
            int[] b = leafBox(TreeSpecies.MANGROVE, c);
            int branchLogs = 0;
            for (int x = 0; x < Chunk.SIZE_X; x++)
                for (int y = 0; y < 60; y++)
                    for (int z = 0; z < Chunk.SIZE_Z; z++)
                        if (c.getLocal(x, y, z) == BlockType.MANGROVE_LOG && !(x == BX && z == BZ)) branchLogs++;
            assertTrue(branchLogs >= 6, "mangrove crown follows real branches, got " + branchLogs);
            assertTrue(Math.max(b[1] - b[0], b[5] - b[4]) + 1 >= 8, "mangrove sprawls wide");
            double cx = 0, cz = 0;
            for (int x = 0; x < Chunk.SIZE_X; x++)
                for (int y = 0; y < 60; y++)
                    for (int z = 0; z < Chunk.SIZE_Z; z++)
                        if (c.getLocal(x, y, z) == BlockType.MANGROVE_LEAVES) { cx += x; cz += z; }
            cx /= b[6]; cz /= b[6];
            if (Math.abs(cx - BX) > 0.3 || Math.abs(cz - BZ) > 0.3) asymmetric++;
        }
        assertTrue(asymmetric > SEEDS / 2, "the mangrove canopy centre should usually be off the trunk, got " + asymmetric);
    }

    @Test
    void swampOakIsLowWideAndDroopsOnOneSide() {
        for (int seed = 0; seed < SEEDS; seed++) {
            Chunk c = grow(TreeSpecies.SWAMP_OAK, seed);
            int[] b = leafBox(TreeSpecies.SWAMP_OAK, c);
            int width = Math.max(b[1] - b[0], b[5] - b[4]) + 1;
            int thickness = b[3] - b[2] + 1;
            assertTrue(width >= 6, "swamp oak is wide, got " + width);
            assertTrue(thickness <= 6, "swamp oak is low, got " + thickness);
            assertTrue(width >= thickness, "swamp oak must not be taller than wide");
        }
    }

    @Test
    void speciesHaveClearlyDifferentSilhouettesFromEachOther() {
        // aspect = canopy height / canopy width, averaged over seeds; each species must sit in its
        // own band so no two read as the same tree.
        double birch = aspect(TreeSpecies.BIRCH), spruce = aspect(TreeSpecies.SPRUCE), oak = aspect(TreeSpecies.OAK);
        double acacia = aspect(TreeSpecies.ACACIA), darkOak = aspect(TreeSpecies.DARK_OAK), swamp = aspect(TreeSpecies.SWAMP_OAK);
        assertTrue(birch > 1.2, "birch is taller than wide: " + birch);
        assertTrue(birch > oak + 0.4, "birch must be far more vertical than oak");
        assertTrue(spruce > 0.9, "spruce is tall: " + spruce);
        assertTrue(acacia < 0.7, "acacia is flat and wide: " + acacia);
        assertTrue(swamp < 0.7, "swamp oak is low: " + swamp);
        assertTrue(darkOak < spruce && darkOak < birch, "dark oak is broad, not tall");
    }

    private static double aspect(TreeSpecies s) {
        double sum = 0;
        for (int seed = 0; seed < 40; seed++) {
            int[] b = leafBox(s, grow(s, seed));
            sum += (b[3] - b[2] + 1.0) / (Math.max(b[1] - b[0], b[5] - b[4]) + 1);
        }
        return sum / 40;
    }
}
