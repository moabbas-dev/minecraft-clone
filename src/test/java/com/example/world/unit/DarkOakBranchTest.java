package com.example.world.unit;

import com.example.world.BlockType;
import com.example.world.Chunk;
import com.example.world.TreeSpecies;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * This project's first branch geometry - Dark Oak only (see TreeSpecies' class docs for why).
 * Exercises everything only through the public place() method, black-box, matching this
 * project's established testing style; the branch-placement methods themselves are private.
 */
class DarkOakBranchTest {

    private static final int BX = 8, BASE_Y = 10, BZ = 8;

    /** A branch's log, distinguished from the trunk's 4 columns and the (x,z)-anchored canopy:
     *  any DARK_OAK_LOG whose (x,z) is not one of the trunk's own 4 columns must be a branch log. */
    private boolean isTrunkColumn(int x, int z) {
        return (x == BX || x == BX + 1) && (z == BZ || z == BZ + 1);
    }

    @Test
    void everyDarkOakTreeGrowsAtLeastOneRealBranchLog() {
        for (int seed = 0; seed < 50; seed++) {
            Chunk chunk = new Chunk(0, 0);
            TreeSpecies.DARK_OAK.place(chunk, BX, BASE_Y, BZ, new Random(seed));
            boolean anyBranchLog = false;
            for (int x = 0; x < Chunk.SIZE_X && !anyBranchLog; x++) {
                for (int y = 0; y < Chunk.SIZE_Y && !anyBranchLog; y++) {
                    for (int z = 0; z < Chunk.SIZE_Z && !anyBranchLog; z++) {
                        if (chunk.getLocal(x, y, z) == BlockType.DARK_OAK_LOG && !isTrunkColumn(x, z)) {
                            anyBranchLog = true;
                        }
                    }
                }
            }
            assertTrue(anyBranchLog, "seed=" + seed + " produced no branch log outside the trunk columns");
        }
    }

    @Test
    void branchCountIsAlwaysBetweenTwoAndFour() {
        // Each branch contributes exactly one "connection log" directly adjacent to a trunk
        // column, on one of the 4 cardinal sides - counting those distinct connection points
        // counts branches without needing access to the private branch-generation method.
        int[] connectionOffsets = {2, -1}; // x+2/z+2 (east/south) or x-1/z-1 (west/north) from BX/BZ
        for (int seed = 0; seed < 50; seed++) {
            Chunk chunk = new Chunk(0, 0);
            TreeSpecies.DARK_OAK.place(chunk, BX, BASE_Y, BZ, new Random(seed));
            int connections = 0;
            for (int dy = 0; dy < Chunk.SIZE_Y; dy++) {
                if (chunk.getLocal(BX + 2, dy, BZ) == BlockType.DARK_OAK_LOG) connections++;
                if (chunk.getLocal(BX - 1, dy, BZ) == BlockType.DARK_OAK_LOG) connections++;
                if (chunk.getLocal(BX, dy, BZ + 2) == BlockType.DARK_OAK_LOG) connections++;
                if (chunk.getLocal(BX, dy, BZ - 1) == BlockType.DARK_OAK_LOG) connections++;
            }
            assertTrue(connections >= 2 && connections <= 4,
                    "seed=" + seed + " had " + connections + " branch connections, expected 2-4");
        }
    }

    @Test
    void everyBranchConnectsDirectlyToARealTrunkLogNeverFloating() {
        // For each of the 4 possible connection columns, if a branch log is there, the trunk
        // column it's laterally adjacent to must itself be a real log at that same Y - the
        // concrete "connected, never a disconnected floating log" check.
        for (int seed = 0; seed < 50; seed++) {
            Chunk chunk = new Chunk(0, 0);
            TreeSpecies.DARK_OAK.place(chunk, BX, BASE_Y, BZ, new Random(seed));
            for (int y = BASE_Y; y < BASE_Y + 10; y++) {
                if (chunk.getLocal(BX + 2, y, BZ) == BlockType.DARK_OAK_LOG) {
                    assertEquals(BlockType.DARK_OAK_LOG, chunk.getLocal(BX + 1, y, BZ),
                            "seed=" + seed + ": east branch at y=" + y + " must touch a real trunk log");
                }
                if (chunk.getLocal(BX - 1, y, BZ) == BlockType.DARK_OAK_LOG) {
                    assertEquals(BlockType.DARK_OAK_LOG, chunk.getLocal(BX, y, BZ),
                            "seed=" + seed + ": west branch at y=" + y + " must touch a real trunk log");
                }
                if (chunk.getLocal(BX, y, BZ + 2) == BlockType.DARK_OAK_LOG) {
                    assertEquals(BlockType.DARK_OAK_LOG, chunk.getLocal(BX, y, BZ + 1),
                            "seed=" + seed + ": south branch at y=" + y + " must touch a real trunk log");
                }
                if (chunk.getLocal(BX, y, BZ - 1) == BlockType.DARK_OAK_LOG) {
                    assertEquals(BlockType.DARK_OAK_LOG, chunk.getLocal(BX, y, BZ),
                            "seed=" + seed + ": north branch at y=" + y + " must touch a real trunk log");
                }
            }
        }
    }

    @Test
    void branchesNeverStartBelowTheTrunksLowerHalf() {
        // "Minimum trunk height before branching" as an observable invariant: no branch
        // connection log should appear below baseY + trunkHeight/2. Since trunkHeight isn't
        // directly observable from outside, this instead checks the documented absolute floor:
        // DARK_OAK's own minTrunkHeight is 5, so lowestBranchY is never below BASE_Y + 5/2 = BASE_Y+2.
        for (int seed = 0; seed < 50; seed++) {
            Chunk chunk = new Chunk(0, 0);
            TreeSpecies.DARK_OAK.place(chunk, BX, BASE_Y, BZ, new Random(seed));
            for (int y = BASE_Y; y < BASE_Y + 2; y++) {
                assertNotEquals(BlockType.DARK_OAK_LOG, chunk.getLocal(BX + 2, y, BZ),
                        "seed=" + seed + " placed a branch too low, at y=" + y);
                assertNotEquals(BlockType.DARK_OAK_LOG, chunk.getLocal(BX - 1, y, BZ),
                        "seed=" + seed + " placed a branch too low, at y=" + y);
            }
        }
    }

    @Test
    void branchReachNeverExceedsTheExistingCanopyRadiusMargin() {
        // The exact chunk-boundary safety property from the design: no branch-related block
        // (log or its leaf puff) may exceed canopyRadius (4) from the tree's own (x,z) anchor -
        // the same margin TerrainGenerator.fitsAndIsSuitable already reserves, unchanged.
        int r = TreeSpecies.DARK_OAK.canopyRadius;
        for (int seed = 0; seed < 50; seed++) {
            Chunk chunk = new Chunk(0, 0);
            TreeSpecies.DARK_OAK.place(chunk, BX, BASE_Y, BZ, new Random(seed));
            for (int x = 0; x < Chunk.SIZE_X; x++) {
                for (int y = 0; y < Chunk.SIZE_Y; y++) {
                    for (int z = 0; z < Chunk.SIZE_Z; z++) {
                        BlockType t = chunk.getLocal(x, y, z);
                        if (t != BlockType.DARK_OAK_LOG && t != BlockType.DARK_OAK_LEAVES) continue;
                        assertTrue(Math.abs(x - BX) <= r, "seed=" + seed + " block at x=" + x + " exceeds canopyRadius margin");
                        assertTrue(Math.abs(z - BZ) <= r, "seed=" + seed + " block at z=" + z + " exceeds canopyRadius margin");
                    }
                }
            }
        }
    }

    @Test
    void branchDirectionVariesAcrossManySeeds() {
        // Not every seed needs every direction, but across enough seeds all 4 cardinal
        // directions must actually appear at least once - confirms direction is real
        // per-tree variation, not a hardcoded single direction.
        boolean[] seen = new boolean[4]; // east, west, south, north
        for (int seed = 0; seed < 100; seed++) {
            Chunk chunk = new Chunk(0, 0);
            TreeSpecies.DARK_OAK.place(chunk, BX, BASE_Y, BZ, new Random(seed));
            for (int y = 0; y < Chunk.SIZE_Y; y++) {
                if (chunk.getLocal(BX + 2, y, BZ) == BlockType.DARK_OAK_LOG) seen[0] = true;
                if (chunk.getLocal(BX - 1, y, BZ) == BlockType.DARK_OAK_LOG) seen[1] = true;
                if (chunk.getLocal(BX, y, BZ + 2) == BlockType.DARK_OAK_LOG) seen[2] = true;
                if (chunk.getLocal(BX, y, BZ - 1) == BlockType.DARK_OAK_LOG) seen[3] = true;
            }
        }
        assertTrue(seen[0] && seen[1] && seen[2] && seen[3], "expected all 4 cardinal directions across 100 seeds");
    }

    @Test
    void branchGenerationIsDeterministicForTheSameSeed() {
        for (int seed = 0; seed < 20; seed++) {
            Chunk chunkA = new Chunk(0, 0);
            Chunk chunkB = new Chunk(0, 0);
            TreeSpecies.DARK_OAK.place(chunkA, BX, BASE_Y, BZ, new Random(seed));
            TreeSpecies.DARK_OAK.place(chunkB, BX, BASE_Y, BZ, new Random(seed));
            assertArrayEquals(chunkA.exportBlocks(), chunkB.exportBlocks(), "seed=" + seed + " must produce identical branches");
        }
    }

    @Test
    void branchesNeverPlaceABlockAtOrAboveWorldHeightLimit() {
        // Places the tree deliberately close to Chunk.SIZE_Y so a branch tip (baseY + trunkHeight
        // range + the branch's own upward step) would overflow if unguarded - setIfInBounds/
        // leaf's existing y<SIZE_Y check must still hold for branch blocks specifically, not just
        // trunk/canopy blocks.
        int highBaseY = Chunk.SIZE_Y - 9; // DARK_OAK's maxTrunkHeight(7) + branch's +1 + canopy margin
        for (int seed = 0; seed < 30; seed++) {
            Chunk chunk = new Chunk(0, 0);
            int s = seed;
            assertDoesNotThrow(() -> TreeSpecies.DARK_OAK.place(chunk, BX, highBaseY, BZ, new Random(s)),
                    "seed=" + seed + " must not throw when placed near the world height ceiling");
        }
    }

    @Test
    void speciesWithoutBranchesNeverPlaceALogOutsideTheirSingleTrunkColumn() {
        // Branch geometry is per-species opt-in: dark oak and the branch-driven canopies (jungle,
        // acacia, cherry, mangrove) have it; oak, birch, spruce and swamp oak must still be exactly
        // one trunk column - not one extra log block anywhere else.
        java.util.Set<TreeSpecies> branched = java.util.EnumSet.of(TreeSpecies.DARK_OAK, TreeSpecies.JUNGLE,
                TreeSpecies.ACACIA, TreeSpecies.CHERRY, TreeSpecies.MANGROVE);
        for (TreeSpecies s : TreeSpecies.values()) {
            if (branched.contains(s)) continue;
            Chunk chunk = new Chunk(0, 0);
            s.place(chunk, BX, BASE_Y, BZ, new Random(1));
            for (int x = 0; x < Chunk.SIZE_X; x++) {
                for (int y = 0; y < Chunk.SIZE_Y; y++) {
                    for (int z = 0; z < Chunk.SIZE_Z; z++) {
                        if (chunk.getLocal(x, y, z) == s.log) {
                            assertTrue(x == BX && z == BZ, s + " placed a log outside its single trunk column at (" + x + "," + z + ")");
                        }
                    }
                }
            }
        }
    }

    @Test
    void branchLeavesNeverOverwriteAnExistingSolidBlock() {
        // Two-pass per seed: first discover where this seed's branch tip logs actually land on
        // a clean chunk, then re-generate on a fresh chunk with stone pre-placed one block
        // perpendicular to each tip - a position only the leaf puff ever reaches (logs only
        // ever travel along the branch's own single cardinal axis, never sideways), so if it
        // survives, leaf()'s existing isAir()-only rule held for branch foliage specifically.
        for (int seed = 0; seed < 30; seed++) {
            Chunk discovery = new Chunk(0, 0);
            TreeSpecies.DARK_OAK.place(discovery, BX, BASE_Y, BZ, new Random(seed));

            java.util.List<int[]> perpendicularSpots = new java.util.ArrayList<>();
            for (int x = 0; x < Chunk.SIZE_X; x++) {
                for (int y = 0; y < Chunk.SIZE_Y; y++) {
                    for (int z = 0; z < Chunk.SIZE_Z; z++) {
                        if (discovery.getLocal(x, y, z) != BlockType.DARK_OAK_LOG || isTrunkColumn(x, z)) continue;
                        boolean isConnect = (x == BX + 2 && z == BZ) || (x == BX - 1 && z == BZ)
                                || (x == BX && z == BZ + 2) || (x == BX && z == BZ - 1);
                        if (isConnect) continue; // only the farther tip log, not the near connect log
                        boolean eastWest = (z == BZ);
                        int px = eastWest ? x : x + 1;
                        int pz = eastWest ? z + 1 : z;
                        perpendicularSpots.add(new int[]{px, y, pz});
                    }
                }
            }
            assertFalse(perpendicularSpots.isEmpty(), "seed=" + seed + " discovered no branch tip to test against");

            Chunk protectedChunk = new Chunk(0, 0);
            for (int[] spot : perpendicularSpots) {
                protectedChunk.setLocal(spot[0], spot[1], spot[2], BlockType.STONE);
            }
            TreeSpecies.DARK_OAK.place(protectedChunk, BX, BASE_Y, BZ, new Random(seed));
            for (int[] spot : perpendicularSpots) {
                assertEquals(BlockType.STONE, protectedChunk.getLocal(spot[0], spot[1], spot[2]),
                        "seed=" + seed + ": pre-existing stone at " + java.util.Arrays.toString(spot) + " must survive branch leaf placement");
            }
        }
    }
}
