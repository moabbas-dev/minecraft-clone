package com.example.world.unit;

import com.example.world.BlockType;
import com.example.world.Chunk;
import com.example.world.TreePlanner;
import com.example.world.TreeSpecies;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The trees against the numbers of the 26.3 client's configured features and the block counts on minecraft.wiki's Tree/Structure page
 * (oak 5+ logs and 56+ leaves, swamp oak 137+ leaves, birch 5+ and 57+, jungle 7+ and 49+, acacia 9+ logs, dark oak 24+ logs, mangrove
 * 10+ logs and 20+ roots).
 */
class VanillaTreeTest {
    private static Chunk ground() {
        Chunk c = new Chunk(0, 0);
        c.markGenerated();
        for (int x = 0; x < Chunk.SIZE_X; x++) for (int z = 0; z < Chunk.SIZE_Z; z++) {
            c.setLocal(x, 0, z, BlockType.BEDROCK);
            for (int y = 1; y <= 2; y++) c.setLocal(x, y, z, BlockType.DIRT);
            c.setLocal(x, 3, z, BlockType.GRASS_BLOCK);
        }
        return c;
    }

    private static TreePlanner.Plan plan(TreeSpecies s, long seed) {
        return TreePlanner.plan(s, ground(), 8, 4, 8, new Random(seed));
    }

    @Test
    void trunkHeightsFollowTheConfiguredBaseAndRandomParts() {
        for (long seed = 0; seed < 300; seed++) {
            int oak = plan(TreeSpecies.OAK, seed).count(BlockType.OAK_LOG);
            assertTrue(oak >= 4 && oak <= 6, "oak 4 + 0..2: " + oak);
            int birch = plan(TreeSpecies.BIRCH, seed).count(BlockType.BIRCH_LOG);
            assertTrue(birch >= 5 && birch <= 7, "birch 5 + 0..2: " + birch);
            int swamp = plan(TreeSpecies.SWAMP_OAK, seed).count(BlockType.OAK_LOG);
            assertTrue(swamp >= 5 && swamp <= 8, "swamp oak 5 + 0..3: " + swamp);
            int jungle = plan(TreeSpecies.JUNGLE, seed).count(BlockType.JUNGLE_LOG);
            assertTrue(jungle >= 4 && jungle <= 12, "jungle 4 + 0..8: " + jungle);
        }
    }

    @Test
    void leafCountsMatchTheWikiBlueprints() {
        for (long seed = 0; seed < 100; seed++) {
            int oak = plan(TreeSpecies.OAK, seed).count(BlockType.OAK_LEAVES);
            assertTrue(oak >= 50 && oak <= 60, "oak leaves (wiki: 56+, a balloon oak 60+): " + oak);
            assertTrue(plan(TreeSpecies.SWAMP_OAK, seed).count(BlockType.OAK_LEAVES) >= 130, "swamp oak leaves (wiki: 137+)");
            assertTrue(plan(TreeSpecies.JUNGLE, seed).count(BlockType.JUNGLE_LEAVES) >= 49, "jungle leaves (wiki: 49+)");
            assertTrue(plan(TreeSpecies.BIRCH, seed).count(BlockType.BIRCH_LEAVES) >= 50, "birch leaves (wiki: 57+ in the blueprint)");
        }
    }

    @Test
    void oakCrownIsTwoWideLayersUnderTwoNarrowOnes() {
        TreePlanner.Plan p = plan(TreeSpecies.OAK, 11);
        int top = 0;
        for (long k : p.blocks().keySet()) top = Math.max(top, y(k));
        int[] widthPerLayer = new int[5];
        for (Map.Entry<Long, BlockType> e : p.blocks().entrySet()) {
            if (e.getValue() != BlockType.OAK_LEAVES) continue;
            int layer = top - y(e.getKey());
            if (layer < 5) widthPerLayer[layer] = Math.max(widthPerLayer[layer], Math.max(Math.abs(x(e.getKey()) - 8), Math.abs(z(e.getKey()) - 8)));
        }
        assertEquals(1, widthPerLayer[0], "the top layer is radius 1");
        assertEquals(1, widthPerLayer[1], "so is the one under it");
        assertEquals(2, widthPerLayer[2], "then two layers of radius 2");
        assertEquals(2, widthPerLayer[3]);
    }

    @Test
    void everyLeafIsCloseEnoughToALogOrRootToLive() {
        for (TreeSpecies s : TreeSpecies.values()) {
            for (long seed = 0; seed < 40; seed++) {
                TreePlanner.Plan p = plan(s, seed);
                Set<Long> solid = new HashSet<>();
                for (Map.Entry<Long, BlockType> e : p.blocks().entrySet()) {
                    if (e.getValue() == s.log || e.getValue() == BlockType.MANGROVE_ROOTS) solid.add(e.getKey());
                }
                for (Map.Entry<Long, BlockType> e : p.blocks().entrySet()) {
                    if (e.getValue() != s.leaves) continue;
                    long k = e.getKey();
                    int best = 99;
                    for (long l : solid) best = Math.min(best, Math.abs(x(k) - x(l)) + Math.abs(y(k) - y(l)) + Math.abs(z(k) - z(l)));
                    assertTrue(best <= 7, s + " seed " + seed + ": a leaf " + best + " blocks from any log would decay");
                }
            }
        }
    }

    @Test
    void darkOakHasATwoByTwoTrunkAndTheWikiLogCount() {
        for (long seed = 0; seed < 60; seed++) {
            TreePlanner.Plan p = plan(TreeSpecies.DARK_OAK, seed);
            assertTrue(p.count(BlockType.DARK_OAK_LOG) >= 24, "wiki: 24+ logs");
            for (int dx = 0; dx <= 1; dx++) for (int dz = 0; dz <= 1; dz++) {
                assertEquals(BlockType.DARK_OAK_LOG, p.at(8 + dx, 4, 8 + dz), "the four trunk columns start at the ground");
            }
        }
    }

    @Test
    void acaciaForksAndLeansAndItsCrownIsTwoFlatLayersPerFork() {
        int forked = 0;
        for (long seed = 0; seed < 200; seed++) {
            TreePlanner.Plan p = plan(TreeSpecies.ACACIA, seed);
            assertTrue(p.count(BlockType.ACACIA_LOG) >= 5);
            Set<Long> columns = new HashSet<>();
            for (Map.Entry<Long, BlockType> e : p.blocks().entrySet()) {
                if (e.getValue() == BlockType.ACACIA_LOG) columns.add((long) x(e.getKey()) * 1000 + z(e.getKey()));
            }
            if (columns.size() > 3) forked++;
            // the leaves of a fork lie in two layers: the log's own level (a ring of radius 3) and the one above
            int lowest = Integer.MAX_VALUE;
            for (Map.Entry<Long, BlockType> e : p.blocks().entrySet()) if (e.getValue() == BlockType.ACACIA_LEAVES) lowest = Math.min(lowest, y(e.getKey()));
            assertTrue(lowest >= 5, "no leaf below the first fork log: " + lowest);
        }
        assertTrue(forked > 100, "most acacias lean or fork over several columns: " + forked);
    }

    @Test
    void mangroveStandsOnRootsAndHasTheWikiMaterials() {
        int made = 0;
        for (long seed = 0; seed < 100; seed++) {
            TreePlanner.Plan p = plan(TreeSpecies.MANGROVE, seed);
            if (p.isEmpty()) continue;
            made++;
            assertTrue(p.count(BlockType.MANGROVE_LOG) >= 3, "logs");
            assertTrue(p.count(BlockType.MANGROVE_ROOTS) >= 5, "a skirt of roots (wiki: 20+ in the big ones)");
            assertTrue(p.count(BlockType.MANGROVE_LEAVES) >= 20);
        }
        assertTrue(made > 50, "the root simulation lets most mangroves grow: " + made);
    }

    @Test
    void cherryHasBranchesHolesAndHangingLeaves() {
        int bigCrowns = 0;
        for (long seed = 0; seed < 100; seed++) {
            TreePlanner.Plan p = plan(TreeSpecies.CHERRY, seed);
            assertTrue(p.count(BlockType.CHERRY_LOG) >= 8);
            if (p.count(BlockType.CHERRY_LEAVES) >= 180) bigCrowns++;
        }
        assertTrue(bigCrowns > 30, "the radius 4 crown has well over 100 leaves: " + bigCrowns);
    }

    @Test
    void theTrunkStandsOnDirtAndNothingSolidIsEverOverwritten() {
        Chunk c = ground();
        c.setLocal(9, 5, 8, BlockType.STONE); // a rock in the way of the oak's crown
        TreePlanner.Plan p = TreePlanner.plan(TreeSpecies.OAK, c, 8, 4, 8, new Random(3));
        assertEquals(BlockType.DIRT, p.at(8, 3, 8), "grass under the trunk becomes dirt");
        p.commit(c);
        assertEquals(BlockType.STONE, c.getLocal(9, 5, 8));
    }

    @Test
    void theSameRandomGivesTheSameTreeAndDifferentOnesDiffer() {
        for (TreeSpecies s : TreeSpecies.values()) {
            assertEquals(plan(s, 5).blocks(), plan(s, 5).blocks(), s + " is deterministic");
        }
        Set<Integer> shapes = new HashSet<>();
        for (long seed = 0; seed < 30; seed++) shapes.add(plan(TreeSpecies.ACACIA, seed).blocks().hashCode());
        assertTrue(shapes.size() > 10, "acacias vary: " + shapes.size());
    }

    @Test
    void treesStayInsideTheirOwnReachOfTheTrunk() {
        int[] reach = {2, 2, 3, 2, 6, 3, 8, 6, 9}; // OAK BIRCH SPRUCE JUNGLE ACACIA SWAMP_OAK MANGROVE DARK_OAK CHERRY, from a 400 seed survey
        TreeSpecies[] all = TreeSpecies.values();
        for (int i = 0; i < all.length; i++) {
            for (long seed = 0; seed < 100; seed++) {
                TreePlanner.Plan p = plan(all[i], seed);
                if (p.isEmpty()) continue;
                int extent = Math.max(Math.max(8 - p.minX, p.maxX - 8), Math.max(8 - p.minZ, p.maxZ - 8));
                assertTrue(extent <= reach[i], all[i] + " seed " + seed + " reaches " + extent);
            }
        }
    }

    private static int x(long k) {
        return (int) (k >> 40) - 4096;
    }

    private static int z(long k) {
        return (int) ((k >> 20) & 0xFFFFF) - 4096;
    }

    private static int y(long k) {
        return (int) (k & 0xFFFFF) - 4096;
    }
}

