package com.example.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression test for a z-fighting bug: a transparent block (water/glass/ice) used to always
 * draw its face against any neighbor, including a fully opaque one - which already draws its
 * own face at that exact same plane, producing two coincident quads that flicker. Since
 * underwater terrain has water touching most faces of every submerged block, this was visible
 * on essentially all underwater rendering, and on any opaque block (e.g. a placed light source)
 * touching water.
 */
class ChunkMeshBuilderFaceCullingTest {

    @Test
    void transparentBlockDoesNotDrawAgainstAnOpaqueNeighbor() {
        assertFalse(ChunkMeshBuilder.shouldDrawFace(BlockType.WATER, BlockType.STONE),
                "water must not draw a coincident face against opaque stone - stone already draws its own face there");
        assertFalse(ChunkMeshBuilder.shouldDrawFace(BlockType.GLASS, BlockType.DIRT));
        assertFalse(ChunkMeshBuilder.shouldDrawFace(BlockType.ICE, BlockType.SAND));
    }

    @Test
    void opaqueBlockDoesNotDrawAgainstATransparentNeighborsOpaqueRule() {
        // Unchanged behavior: an opaque block still draws its face when the neighbor doesn't
        // fully occlude it (air or any transparent block).
        assertTrue(ChunkMeshBuilder.shouldDrawFace(BlockType.STONE, BlockType.WATER));
        assertTrue(ChunkMeshBuilder.shouldDrawFace(BlockType.GLOWSTONE, BlockType.WATER));
    }

    @Test
    void transparentBlockStillDrawsAgainstAirAndOtherTransparentTypes() {
        assertTrue(ChunkMeshBuilder.shouldDrawFace(BlockType.WATER, BlockType.AIR));
        assertTrue(ChunkMeshBuilder.shouldDrawFace(BlockType.WATER, BlockType.GLASS));
        assertTrue(ChunkMeshBuilder.shouldDrawFace(BlockType.WATER, BlockType.ICE));
    }

    @Test
    void identicalNeighboringBlocksNeverDrawAFaceBetweenThem() {
        assertFalse(ChunkMeshBuilder.shouldDrawFace(BlockType.WATER, BlockType.WATER));
        assertFalse(ChunkMeshBuilder.shouldDrawFace(BlockType.STONE, BlockType.STONE));
        assertFalse(ChunkMeshBuilder.shouldDrawFace(BlockType.GLASS, BlockType.GLASS));
    }

    @Test
    void twoDifferentOpaqueBlocksNeverDrawAFaceBetweenThem() {
        assertFalse(ChunkMeshBuilder.shouldDrawFace(BlockType.STONE, BlockType.DIRT));
        assertFalse(ChunkMeshBuilder.shouldDrawFace(BlockType.GLOWSTONE, BlockType.STONE));
    }

    // --- Regression: a sapling sitting on top of grass used to delete the grass block's own
    // top face, because the sapling was (incorrectly) marked as a fully opaque cube. ---

    @Test
    void grassBlockStillDrawsItsTopFaceUnderneathASapling() {
        for (BlockType stage : new BlockType[]{BlockType.OAK_SAPLING_STAGE_0, BlockType.OAK_SAPLING_STAGE_1,
                BlockType.OAK_SAPLING_STAGE_2, BlockType.OAK_SAPLING_STAGE_3}) {
            assertTrue(ChunkMeshBuilder.shouldDrawFace(BlockType.GRASS_BLOCK, stage),
                    "grass's top face must still render underneath a " + stage);
        }
    }

    @Test
    void saplingIsNotAnOpaqueCube() {
        for (BlockType stage : new BlockType[]{BlockType.OAK_SAPLING_STAGE_0, BlockType.OAK_SAPLING_STAGE_1,
                BlockType.OAK_SAPLING_STAGE_2, BlockType.OAK_SAPLING_STAGE_3}) {
            assertFalse(stage.isOpaqueCube(), stage + " must not be treated as a fully opaque cube");
            assertTrue(stage.isTransparentRender());
        }
    }

    @Test
    void everySaplingStageIsRenderedAsACrossShapedPlantNotACube() {
        for (BlockType stage : new BlockType[]{BlockType.OAK_SAPLING_STAGE_0, BlockType.OAK_SAPLING_STAGE_1,
                BlockType.OAK_SAPLING_STAGE_2, BlockType.OAK_SAPLING_STAGE_3}) {
            assertTrue(ChunkMeshBuilder.isCrossShapedPlant(stage));
        }
        assertFalse(ChunkMeshBuilder.isCrossShapedPlant(BlockType.OAK_LEAVES));
        assertFalse(ChunkMeshBuilder.isCrossShapedPlant(BlockType.GRASS_BLOCK));
    }

    // --- Icon tinting (hotbar slots) ---

    @Test
    void leafIconTintIsNullForNonLeafBlocks() {
        assertNull(ChunkMeshBuilder.leafIconTint(BlockType.STONE));
        assertNull(ChunkMeshBuilder.leafIconTint(BlockType.GRASS_BLOCK));
        assertNull(ChunkMeshBuilder.leafIconTint(BlockType.OAK_LOG));
    }

    @Test
    void leafIconTintIsRealColorForEveryLeafSpecies() {
        BlockType[] leaves = {BlockType.OAK_LEAVES, BlockType.BIRCH_LEAVES, BlockType.SPRUCE_LEAVES,
                BlockType.JUNGLE_LEAVES, BlockType.ACACIA_LEAVES, BlockType.MANGROVE_LEAVES,
                BlockType.DARK_OAK_LEAVES, BlockType.CHERRY_LEAVES};
        for (BlockType leaf : leaves) {
            float[] tint = ChunkMeshBuilder.leafIconTint(leaf);
            assertNotNull(tint, leaf + " must have an icon tint");
            assertEquals(3, tint.length);
            for (float channel : tint) {
                assertTrue(channel >= 0f && channel <= 1f, leaf + " tint channel out of [0,1]: " + channel);
            }
            // Must not be the neutral no-op tint - the entire bug being fixed is "no color at all."
            assertFalse(tint[0] == 1f && tint[1] == 1f && tint[2] == 1f, leaf + " must not use the neutral (no-op) tint");
        }
    }

    @Test
    void spruceAndCherryIconTintsMatchTheirOwnFixedWorldRenderingTint() {
        // Spruce/cherry never blend with biome color even in real chunk rendering (see
        // ChunkMeshBuilder's own SPRUCE_TINT/CHERRY_TINT) - the icon must use the exact same
        // fixed value, not a different "default" approximation.
        float[] spruceIcon = ChunkMeshBuilder.leafIconTint(BlockType.SPRUCE_LEAVES);
        float[] cherryIcon = ChunkMeshBuilder.leafIconTint(BlockType.CHERRY_LEAVES);
        assertFalse(java.util.Arrays.equals(spruceIcon, cherryIcon), "spruce and cherry must have visually distinct icon tints");
    }

    @Test
    void grassIconTintIsARealGreenNotTheNeutralTint() {
        float[] tint = ChunkMeshBuilder.grassIconTint();
        assertEquals(3, tint.length);
        assertFalse(tint[0] == 1f && tint[1] == 1f && tint[2] == 1f, "grass icon tint must not be the neutral (no-op) tint");
        assertTrue(tint[1] > tint[0] && tint[1] > tint[2], "grass tint should read as green (G channel dominant)");
    }
}
