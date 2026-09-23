package com.example.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises TerrainGenerator's package-private tree-placement helpers directly (pure logic, no
 * noise/colormap dependency - doesn't need a full TerrainGenerator instance) plus an end-to-end
 * placeTrees run against a synthetic flat chunk for spacing/determinism.
 */
class TerrainGeneratorTreeTest {

    /** Biomes with treeChance=0 in Biome.java - none of these should ever grow a tree. */
    private static final java.util.Set<Biome> TREELESS_BIOMES = java.util.Set.of(
            Biome.DESERT, Biome.BADLANDS, Biome.OCEAN, Biome.FROZEN_OCEAN);

    @Test
    void treelessBiomesNeverGrowTrees() {
        Random rnd = new Random(1);
        for (int i = 0; i < 200; i++) {
            for (Biome b : TREELESS_BIOMES) {
                assertNull(TerrainGenerator.speciesFor(b, rnd), b + " must never resolve to a species");
            }
        }
    }

    @Test
    void everyOtherBiomeAlwaysProducesASpecies() {
        Random rnd = new Random(2);
        for (Biome b : Biome.values()) {
            if (TREELESS_BIOMES.contains(b)) continue;
            for (int i = 0; i < 100; i++) {
                assertNotNull(TerrainGenerator.speciesFor(b, rnd), b + " should always resolve to a species");
            }
        }
    }

    @Test
    void plainsAndForestOnlyEverPickOakOrBirch() {
        Random rnd = new Random(3);
        for (int i = 0; i < 200; i++) {
            TreeSpecies plains = TerrainGenerator.speciesFor(Biome.PLAINS, rnd);
            TreeSpecies forest = TerrainGenerator.speciesFor(Biome.FOREST, rnd);
            assertTrue(plains == TreeSpecies.OAK || plains == TreeSpecies.BIRCH);
            assertTrue(forest == TreeSpecies.OAK || forest == TreeSpecies.BIRCH);
        }
    }

    @Test
    void coldAndHighAltitudeBiomesAlwaysUseSpruce() {
        Random rnd = new Random(4);
        for (int i = 0; i < 20; i++) {
            assertEquals(TreeSpecies.SPRUCE, TerrainGenerator.speciesFor(Biome.TAIGA, rnd));
            assertEquals(TreeSpecies.SPRUCE, TerrainGenerator.speciesFor(Biome.SNOWY_TUNDRA, rnd));
            assertEquals(TreeSpecies.SPRUCE, TerrainGenerator.speciesFor(Biome.MOUNTAINS, rnd));
        }
    }

    @Test
    void jungleSavannaSwampAndMangroveUseTheirOwnDedicatedSpecies() {
        Random rnd = new Random(5);
        assertEquals(TreeSpecies.JUNGLE, TerrainGenerator.speciesFor(Biome.JUNGLE, rnd));
        assertEquals(TreeSpecies.ACACIA, TerrainGenerator.speciesFor(Biome.SAVANNA, rnd));
        assertEquals(TreeSpecies.SWAMP_OAK, TerrainGenerator.speciesFor(Biome.SWAMP, rnd));
        assertEquals(TreeSpecies.MANGROVE, TerrainGenerator.speciesFor(Biome.MANGROVE, rnd));
    }

    @Test
    void clusterChanceIsAValidProbabilityAndZeroForTreelessBiomes() {
        for (Biome b : Biome.values()) {
            float c = TerrainGenerator.clusterChanceFor(b);
            assertTrue(c >= 0f && c <= 1f, b + " cluster chance out of [0,1]: " + c);
        }
        for (Biome b : TREELESS_BIOMES) {
            assertEquals(0f, TerrainGenerator.clusterChanceFor(b), b + " treeless biome must have zero cluster chance");
        }
    }

    @Test
    void slopeCheckRejectsASteepCandidateButAllowsFlatTerrain() {
        int[][] flat = new int[Chunk.SIZE_X][Chunk.SIZE_Z];
        for (int[] row : flat) java.util.Arrays.fill(row, 70);
        assertTrue(TerrainGenerator.slopeOk(flat, 8, 8));

        int[][] cliff = new int[Chunk.SIZE_X][Chunk.SIZE_Z];
        for (int[] row : cliff) java.util.Arrays.fill(row, 70);
        cliff[9][8] = 90; // a sharp 20-block step right next to the candidate
        assertFalse(TerrainGenerator.slopeOk(cliff, 8, 8));
    }

    @Test
    void canopyMustFitInsideTheChunkToBeAccepted() {
        int[][] heights = new int[Chunk.SIZE_X][Chunk.SIZE_Z];
        for (int[] row : heights) java.util.Arrays.fill(row, 70);
        Chunk chunk = new Chunk(0, 0);
        List<int[]> placed = new ArrayList<>();

        // JUNGLE has canopyRadius=3; at x=1 the canopy would need x=-2, out of bounds.
        assertFalse(TerrainGenerator.fitsAndIsSuitable(chunk, 1, 8, heights, TreeSpecies.JUNGLE, placed));
        // Comfortably inside with nothing else placed yet.
        assertTrue(TerrainGenerator.fitsAndIsSuitable(chunk, 8, 8, heights, TreeSpecies.JUNGLE, placed));
    }

    @Test
    void aCandidateWithinAnotherTreesMinimumSpacingIsRejected() {
        int[][] heights = new int[Chunk.SIZE_X][Chunk.SIZE_Z];
        for (int[] row : heights) java.util.Arrays.fill(row, 70);
        Chunk chunk = new Chunk(0, 0);
        List<int[]> placed = new ArrayList<>();
        placed.add(new int[]{4, 8, TreeSpecies.OAK.canopyRadius});

        assertFalse(TerrainGenerator.fitsAndIsSuitable(chunk, 5, 8, heights, TreeSpecies.OAK, placed),
                "1 block away is well inside OAK's minimum spacing of " + TreeSpecies.OAK.minSpacing);
        assertTrue(TerrainGenerator.fitsAndIsSuitable(chunk, 4 + TreeSpecies.OAK.minSpacing + 1, 8, heights, TreeSpecies.OAK, placed),
                "well beyond minimum spacing should be accepted");
    }

    /** A fully flat, all-suitable, single-biome synthetic chunk - isolates placeTrees itself
     *  from noise/climate, for deterministic end-to-end spacing and repeatability checks. */
    private Chunk buildFlatGrassChunk(int surfaceY) {
        Chunk chunk = new Chunk(0, 0);
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                for (int y = 0; y <= surfaceY; y++) {
                    chunk.setLocal(x, y, z, y == surfaceY ? BlockType.GRASS_BLOCK : BlockType.DIRT);
                }
            }
        }
        return chunk;
    }

    @Test
    void placeTreesIsDeterministicForTheSameSeed() {
        int surfaceY = 70;
        int[][] heights = new int[Chunk.SIZE_X][Chunk.SIZE_Z];
        Biome[][] dominant = new Biome[Chunk.SIZE_X][Chunk.SIZE_Z];
        float[][] prob = new float[Chunk.SIZE_X][Chunk.SIZE_Z];
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                heights[x][z] = surfaceY;
                dominant[x][z] = Biome.FOREST;
                prob[x][z] = 1f; // every suitable column is a candidate
            }
        }

        Chunk chunkA = buildFlatGrassChunk(surfaceY);
        Chunk chunkB = buildFlatGrassChunk(surfaceY);
        TerrainGenerator.placeTrees(chunkA, heights, dominant, prob, new Random(777));
        TerrainGenerator.placeTrees(chunkB, heights, dominant, prob, new Random(777));

        assertArrayEquals(chunkA.exportBlocks(), chunkB.exportBlocks(), "same seed must produce identical tree placement");
    }

    @Test
    void placeTreesActuallyPlacesTreesAtFullDensityAndRespectsMinimumSpacing() {
        int surfaceY = 70;
        int[][] heights = new int[Chunk.SIZE_X][Chunk.SIZE_Z];
        Biome[][] dominant = new Biome[Chunk.SIZE_X][Chunk.SIZE_Z];
        float[][] prob = new float[Chunk.SIZE_X][Chunk.SIZE_Z];
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                heights[x][z] = surfaceY;
                dominant[x][z] = Biome.FOREST;
                prob[x][z] = 1f;
            }
        }
        Chunk chunk = buildFlatGrassChunk(surfaceY);
        TerrainGenerator.placeTrees(chunk, heights, dominant, prob, new Random(555));

        List<int[]> roots = new ArrayList<>();
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                BlockType atBase = chunk.getLocal(x, surfaceY + 1, z);
                if (atBase == BlockType.OAK_LOG || atBase == BlockType.BIRCH_LOG) {
                    roots.add(new int[]{x, z});
                }
            }
        }
        assertFalse(roots.isEmpty(), "full density on suitable ground should place at least one tree");

        for (int i = 0; i < roots.size(); i++) {
            for (int j = i + 1; j < roots.size(); j++) {
                double dx = roots.get(i)[0] - roots.get(j)[0];
                double dz = roots.get(i)[1] - roots.get(j)[1];
                double dist = Math.sqrt(dx * dx + dz * dz);
                assertTrue(dist >= 2.0, "two tree roots landed almost on top of each other: " +
                        java.util.Arrays.toString(roots.get(i)) + " and " + java.util.Arrays.toString(roots.get(j)) + " dist=" + dist);
            }
        }
    }

    @Test
    void placeTreesNeverPlacesInDesertEvenAtFullBlendedDensity() {
        // Simulates a column right at a Desert/Forest border: dominant biome is Desert, but
        // treeProbability is nonzero (bled in from the neighbor) - Desert must still stay
        // treeless per the explicit "don't place trees merely because a neighbor is close" rule.
        int surfaceY = 70;
        int[][] heights = new int[Chunk.SIZE_X][Chunk.SIZE_Z];
        Biome[][] dominant = new Biome[Chunk.SIZE_X][Chunk.SIZE_Z];
        float[][] prob = new float[Chunk.SIZE_X][Chunk.SIZE_Z];
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                heights[x][z] = surfaceY;
                dominant[x][z] = Biome.DESERT;
                prob[x][z] = 1f; // pretend density bled all the way in from a neighbor
            }
        }
        Chunk chunk = buildFlatGrassChunk(surfaceY);
        TerrainGenerator.placeTrees(chunk, heights, dominant, prob, new Random(999));

        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int y = surfaceY; y < Chunk.SIZE_Y; y++) {
                for (int z = 0; z < Chunk.SIZE_Z; z++) {
                    BlockType t = chunk.getLocal(x, y, z);
                    assertNotEquals(BlockType.OAK_LOG, t);
                    assertNotEquals(BlockType.OAK_LEAVES, t);
                }
            }
        }
    }
}
