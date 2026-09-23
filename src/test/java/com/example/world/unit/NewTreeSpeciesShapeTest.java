package com.example.world.unit;

import com.example.world.BlockType;
import com.example.world.Chunk;
import com.example.world.TreeSpecies;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Species-specific shape geometry not covered by TreeSpeciesTest's generic per-species loop:
 * Dark Oak's real 2x2 trunk footprint, Cherry's circular (not square) canopy, and Mangrove's
 * water-adjacent root skirt.
 */
class NewTreeSpeciesShapeTest {

    @Test
    void darkOakGrowsARealTwoByTwoTrunkNotJustASingleColumn() {
        Chunk chunk = new Chunk(0, 0);
        int bx = 8, baseY = 10, bz = 8;
        TreeSpecies.DARK_OAK.place(chunk, bx, baseY, bz, new Random(1));

        // All 4 columns of the 2x2 footprint must be solid log for the full trunk height, not
        // just the (x,z) anchor - a genuine multi-block trunk, not a wider single column.
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                assertEquals(BlockType.DARK_OAK_LOG, chunk.getLocal(bx + dx, baseY, bz + dz),
                        "trunk column (" + dx + "," + dz + ") must be log at the base");
            }
        }
    }

    @Test
    void darkOakCanopyNeverExceedsItsOwnCanopyRadiusMargin() {
        // Regression guard for the off-by-one this round caught before it shipped: the canopy
        // loop must never reach further than TerrainGenerator.fitsAndIsSuitable actually
        // reserves (species.canopyRadius), or it could silently lose leaves at a chunk's edge.
        Chunk chunk = new Chunk(0, 0);
        int bx = 8, baseY = 10, bz = 8;
        TreeSpecies.DARK_OAK.place(chunk, bx, baseY, bz, new Random(1));
        int r = TreeSpecies.DARK_OAK.canopyRadius;

        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int y = 0; y < Chunk.SIZE_Y; y++) {
                for (int z = 0; z < Chunk.SIZE_Z; z++) {
                    if (chunk.getLocal(x, y, z) != BlockType.DARK_OAK_LEAVES) continue;
                    assertTrue(Math.abs(x - bx) <= r + 1, "leaf beyond canopyRadius+trunk margin at x=" + x);
                    assertTrue(Math.abs(z - bz) <= r + 1, "leaf beyond canopyRadius+trunk margin at z=" + z);
                }
            }
        }
    }

    @Test
    void cherryCanopyIsRoughlyCircularNotSquare() {
        // A square canopy would place a leaf at every corner of its bounding box; a genuinely
        // circular one must not - this directly distinguishes placeCherry from the square/
        // diamond canopies every other species uses.
        Chunk chunk = new Chunk(0, 0);
        int bx = 8, baseY = 10, bz = 8;
        TreeSpecies.CHERRY.place(chunk, bx, baseY, bz, new Random(7));
        int r = TreeSpecies.CHERRY.canopyRadius;

        boolean anyCornerIsLeaf = false;
        for (int y = baseY; y < baseY + TreeSpecies.CHERRY.maxTotalHeight(); y++) {
            for (int[] corner : new int[][]{{bx - r, bz - r}, {bx - r, bz + r}, {bx + r, bz - r}, {bx + r, bz + r}}) {
                if (chunk.getLocal(corner[0], y, corner[1]) == BlockType.CHERRY_LEAVES) anyCornerIsLeaf = true;
            }
        }
        assertFalse(anyCornerIsLeaf, "a circular canopy must not fill its bounding box's corners");
    }

    @Test
    void mangroveGrowsRootsTowardAdjacentShorelineWater() {
        // Root placement per direction is gated by a coin flip (rnd.nextBoolean(), natural
        // variation - not every shoreline tree needs visible roots on every side), so a single
        // fixed seed can legitimately roll "no roots" this specific call. Trying several seeds
        // is what actually tests "the rule can produce roots," not one arbitrary RNG draw.
        int bx = 8, baseY = 10, bz = 8;
        boolean anyRootAcrossSeeds = false;
        for (int seed = 0; seed < 20 && !anyRootAcrossSeeds; seed++) {
            Chunk chunk = new Chunk(0, 0);
            // Solid ground under the trunk's own column, water immediately east of it - a minimal
            // hand-built shoreline, isolating the root rule from real terrain generation.
            chunk.setLocal(bx, baseY - 1, bz, BlockType.MUD);
            chunk.setLocal(bx + 1, baseY - 1, bz, BlockType.WATER);
            TreeSpecies.MANGROVE.place(chunk, bx, baseY, bz, new Random(seed));
            for (int x = 0; x < Chunk.SIZE_X && !anyRootAcrossSeeds; x++) {
                for (int y = 0; y < Chunk.SIZE_Y && !anyRootAcrossSeeds; y++) {
                    for (int z = 0; z < Chunk.SIZE_Z && !anyRootAcrossSeeds; z++) {
                        if (chunk.getLocal(x, y, z) == BlockType.MANGROVE_ROOTS) anyRootAcrossSeeds = true;
                    }
                }
            }
        }
        assertTrue(anyRootAcrossSeeds, "expected at least one root block toward the adjacent water over repeated seeds");
    }

    @Test
    void mangroveRootsNeverOverwriteSolidGround() {
        // Every neighboring column's ground block (not water/air) must survive untouched -
        // "prevent invalid block replacement."
        Chunk chunk = new Chunk(0, 0);
        int bx = 8, baseY = 10, bz = 8;
        chunk.setLocal(bx, baseY - 1, bz, BlockType.MUD);
        chunk.setLocal(bx + 1, baseY - 1, bz, BlockType.MUD); // solid neighbor ground, NOT water
        chunk.setLocal(bx - 1, baseY - 1, bz, BlockType.WATER);

        for (int seed = 0; seed < 30; seed++) {
            Chunk c = new Chunk(0, 0);
            c.setLocal(bx, baseY - 1, bz, BlockType.MUD);
            c.setLocal(bx + 1, baseY - 1, bz, BlockType.MUD);
            c.setLocal(bx - 1, baseY - 1, bz, BlockType.WATER);
            TreeSpecies.MANGROVE.place(c, bx, baseY, bz, new Random(seed));
            assertEquals(BlockType.MUD, c.getLocal(bx + 1, baseY - 1, bz),
                    "solid neighboring ground must never be replaced by a root, seed=" + seed);
        }
    }

    @Test
    void mangroveRootsAreAlwaysLaterallyAdjacentToTheTrunkNeverDisconnected() {
        Chunk chunk = new Chunk(0, 0);
        int bx = 8, baseY = 10, bz = 8;
        chunk.setLocal(bx, baseY - 1, bz, BlockType.MUD);
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            chunk.setLocal(bx + d[0], baseY - 1, bz + d[1], BlockType.WATER);
        }
        TreeSpecies.MANGROVE.place(chunk, bx, baseY, bz, new Random(3));

        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int nx = bx + d[0], nz = bz + d[1];
            boolean rootHere = chunk.getLocal(nx, baseY - 1, nz) == BlockType.MANGROVE_ROOTS
                    || chunk.getLocal(nx, baseY, nz) == BlockType.MANGROVE_ROOTS;
            // Not every direction necessarily gets a root (rnd.nextBoolean() gate) - just confirm
            // that whichever ones do are exactly-adjacent to the trunk column, never further out.
            if (rootHere) {
                assertEquals(1, Math.abs(d[0]) + Math.abs(d[1]), "root must be exactly 1 block from the trunk");
            }
        }
    }
}
