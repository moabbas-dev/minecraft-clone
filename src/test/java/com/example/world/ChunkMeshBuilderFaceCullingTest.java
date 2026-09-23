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
}
