package com.example.world.unit;

import com.example.world.BlockType;
import com.example.world.Chunk;
import com.example.world.TreeSpecies;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Species profile validity, shape-generation sanity, and determinism - no biome/placement logic. */
class TreeSpeciesTest {

    @Test
    void everySpeciesHasSaneDimensionBounds() {
        for (TreeSpecies s : TreeSpecies.values()) {
            assertTrue(s.minTrunkHeight >= 1, s + " minTrunkHeight must be positive");
            assertTrue(s.maxTrunkHeight >= s.minTrunkHeight, s + " maxTrunkHeight must be >= min");
            assertTrue(s.canopyRadius >= 1, s + " canopyRadius must be positive");
            assertTrue(s.minSpacing >= 1, s + " minSpacing must be positive");
            assertTrue(s.maxTotalHeight() >= s.maxTrunkHeight, s + " maxTotalHeight must cover the trunk plus canopy");
        }
    }

    @Test
    void everySpeciesUsesARealNonAirLogAndLeafBlock() {
        for (TreeSpecies s : TreeSpecies.values()) {
            assertFalse(s.log.isAir(), s + " log must not be AIR");
            assertFalse(s.leaves.isAir(), s + " leaves must not be AIR");
            assertTrue(s.log.isSolidCollision(), s + " log should be solid");
        }
    }

    @Test
    void everySpeciesProducesAtLeastOneLogAndOneLeafBlockWhenPlaced() {
        for (TreeSpecies s : TreeSpecies.values()) {
            Chunk chunk = new Chunk(0, 0);
            s.place(chunk, 8, 8, 8, new Random(1));
            int logs = 0, leaves = 0;
            for (int x = 0; x < Chunk.SIZE_X; x++) {
                for (int y = 0; y < 30; y++) {
                    for (int z = 0; z < Chunk.SIZE_Z; z++) {
                        BlockType t = chunk.getLocal(x, y, z);
                        if (t == s.log) logs++;
                        if (t == s.leaves) leaves++;
                    }
                }
            }
            assertTrue(logs >= s.minTrunkHeight, s + " should place at least its minimum trunk height in logs, got " + logs);
            assertTrue(leaves > 0, s + " should place at least one leaf block");
        }
    }

    @Test
    void trunkIsAContiguousColumnFromTheBaseUpward() {
        // The shared trunk() helper always writes dy=0..height-1 with no gaps - this locks that
        // invariant ("no impossible gaps in the trunk") for every species going forward.
        for (TreeSpecies s : TreeSpecies.values()) {
            Chunk chunk = new Chunk(0, 0);
            int baseY = 10;
            s.place(chunk, 8, baseY, 8, new Random(2));
            for (int y = baseY; y < baseY + s.minTrunkHeight; y++) {
                assertEquals(s.log, chunk.getLocal(8, y, 8), s + " trunk must be unbroken at y=" + y);
            }
        }
    }

    @Test
    void placementIsDeterministicForTheSameRandomSeed() {
        for (TreeSpecies s : TreeSpecies.values()) {
            Chunk chunkA = new Chunk(0, 0);
            Chunk chunkB = new Chunk(0, 0);
            s.place(chunkA, 8, 8, 8, new Random(42));
            s.place(chunkB, 8, 8, 8, new Random(42));
            assertArrayEquals(chunkA.exportBlocks(), chunkB.exportBlocks(), s + " must produce identical blocks for the same seed");
        }
    }

    @Test
    void noBlockIsPlacedOutsideTheSpeciesOwnBoundingVolume() {
        // "No floating leaf clusters accidentally" as a bounding-box sanity check: everything
        // placed stays within canopyRadius+1 horizontally and maxTotalHeight vertically of the
        // trunk base, for every species.
        for (TreeSpecies s : TreeSpecies.values()) {
            Chunk chunk = new Chunk(0, 0);
            int bx = 8, baseY = 8, bz = 8;
            s.place(chunk, bx, baseY, bz, new Random(3));
            int maxR = s.canopyRadius + 1;
            for (int x = 0; x < Chunk.SIZE_X; x++) {
                for (int y = 0; y < Chunk.SIZE_Y; y++) {
                    for (int z = 0; z < Chunk.SIZE_Z; z++) {
                        BlockType t = chunk.getLocal(x, y, z);
                        if (t != s.log && t != s.leaves) continue;
                        assertTrue(Math.abs(x - bx) <= maxR, s + " block outside expected horizontal bound at x=" + x);
                        assertTrue(Math.abs(z - bz) <= maxR, s + " block outside expected horizontal bound at z=" + z);
                        assertTrue(y >= baseY && y <= baseY + s.maxTotalHeight(), s + " block outside expected vertical bound at y=" + y);
                    }
                }
            }
        }
    }
}
