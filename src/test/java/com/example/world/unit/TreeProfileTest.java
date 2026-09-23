package com.example.world.unit;

import com.example.world.*;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The reusable TreeProfile bundle extracted from TreeSpecies this round: every species exposes
 * its own dimensional/shape data as one queryable record, and the empirically-measured
 * maxBlockCount is a real, tested invariant (not an invented number) - see TreeProfile's class
 * docs for why it's validated rather than live-enforced.
 */
class TreeProfileTest {

    @Test
    void profileFieldsMatchTheSpeciesOwnFieldsExactly() {
        for (TreeSpecies s : TreeSpecies.values()) {
            TreeProfile p = s.profile();
            assertEquals(s.log, p.log());
            assertEquals(s.leaves, p.leaves());
            assertEquals(s.minTrunkHeight, p.minTrunkHeight());
            assertEquals(s.maxTrunkHeight, p.maxTrunkHeight());
            assertEquals(s.trunkFootprint, p.trunkFootprint());
            assertEquals(s.canopyRadius, p.canopyRadius());
            assertEquals(s.canopyHeight, p.canopyHeight());
            assertEquals(s.canopyShape, p.canopyShape());
            assertEquals(s.minSpacing, p.minSpacing());
            assertEquals(s.maxBlockCount, p.maxBlockCount());
        }
    }

    @Test
    void everySpeciesHasAConcreteCanopyShapeTag() {
        for (TreeSpecies s : TreeSpecies.values()) {
            assertNotNull(s.canopyShape, s + " must declare a canopy shape");
        }
    }

    @Test
    void everySpeciesHasASaneCanopyHeight() {
        for (TreeSpecies s : TreeSpecies.values()) {
            assertTrue(s.canopyHeight >= 1, s + " canopyHeight must be positive");
        }
    }

    @Test
    void onlyDarkOakHasATwoBlockTrunkFootprint() {
        for (TreeSpecies s : TreeSpecies.values()) {
            if (s == TreeSpecies.DARK_OAK) {
                assertEquals(2, s.trunkFootprint, "Dark Oak must have the real 2x2 trunk footprint");
            } else {
                assertEquals(1, s.trunkFootprint, s + " must have a normal single-block trunk footprint");
            }
        }
    }

    @Test
    void actualPlacedBlockCountNeverExceedsMaxBlockCountAcrossManySeedsAndPositions() {
        // The validated invariant itself - maxBlockCount was set from real measurements (see
        // each species' own field javadoc in TreeSpecies) with a safety margin; this locks that
        // margin in going forward rather than letting it silently go stale as shapes change.
        for (TreeSpecies s : TreeSpecies.values()) {
            for (int seed = 0; seed < 200; seed++) {
                Chunk chunk = new Chunk(0, 0);
                if (s == TreeSpecies.MANGROVE) {
                    // Give Mangrove a shoreline on every side so root placement is exercised too -
                    // maxBlockCount is meant to cover roots, not just canopy+trunk.
                    chunk.setLocal(8, 9, 8, BlockType.MUD);
                    for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                        chunk.setLocal(8 + d[0], 9, 8 + d[1], BlockType.WATER);
                    }
                }
                s.place(chunk, 8, 10, 8, new Random(seed));
                int count = 0;
                for (int x = 0; x < Chunk.SIZE_X; x++) {
                    for (int y = 0; y < Chunk.SIZE_Y; y++) {
                        for (int z = 0; z < Chunk.SIZE_Z; z++) {
                            BlockType t = chunk.getLocal(x, y, z);
                            if (t == s.log || t == s.leaves || t == BlockType.MANGROVE_ROOTS) count++;
                        }
                    }
                }
                assertTrue(count <= s.maxBlockCount,
                        s + " placed " + count + " blocks, exceeding its own maxBlockCount=" + s.maxBlockCount + " at seed=" + seed);
            }
        }
    }
}
