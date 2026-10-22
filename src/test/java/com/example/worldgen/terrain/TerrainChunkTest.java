package com.example.worldgen.terrain;

import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@code ProtoChunk.setBlockState} / {@code Heightmap.update} behaviour of {@link TerrainChunk} on hand-built columns
 * (expected values follow directly from the ported 26.3 code paths; the filled-chunk paths are covered by
 * {@link TerrainOracleTest}).
 */
class TerrainChunkTest {

    private static final Path DATA = Path.of("data");

    private static TerrainChunk chunk() {
        assumeTrue(Files.isDirectory(DATA.resolve("minecraft/tags/block")), "vanilla data/ folder not present");
        return new TerrainChunk(-3, 7, -64, 384, BlockTags.load(DATA));
    }

    @Test
    void airIntoEmptySectionIsDroppedAndReadsAsAir() {
        TerrainChunk c = chunk();
        c.setState(-48, 10, 112, BlockProperties.AIR);
        assertEquals(BlockProperties.AIR, c.getState(-48, 10, 112));
        assertEquals(BlockProperties.VOID_AIR, c.getState(-48, 320, 112));
        assertEquals(BlockProperties.VOID_AIR, c.getState(-48, -65, 112));
        assertEquals(-64, c.getHighestSectionPosition());
    }

    @Test
    void worldgenStatusesKeepOnlyTheWorldgenHeightmaps() {
        TerrainChunk c = chunk();
        c.setPersistedStatus(TerrainChunk.Status.BIOMES);
        c.setState(-48, 70, 112, BlockProperties.STONE);
        assertEquals(EnumSet.of(HeightmapType.OCEAN_FLOOR_WG, HeightmapType.WORLD_SURFACE_WG).stream().allMatch(c::hasPrimedHeightmap), true);
        assertEquals(false, c.hasPrimedHeightmap(HeightmapType.MOTION_BLOCKING));
        assertEquals(70, c.getHeight(HeightmapType.WORLD_SURFACE_WG, -48, 112));
        assertEquals(70, c.getHeight(HeightmapType.OCEAN_FLOOR_WG, -48, 112));
    }

    @Test
    void finalHeightmapsFollowFluidAndRemoval() {
        TerrainChunk c = chunk();
        c.setPersistedStatus(TerrainChunk.Status.TERRAIN);
        int x = -47;
        int z = 113;
        c.setState(x, 60, z, BlockProperties.STONE);
        for (int y = 61; y < 63; y++) c.setState(x, y, z, BlockProperties.WATER);
        assertEquals(62, c.getHeight(HeightmapType.WORLD_SURFACE, x, z));
        assertEquals(62, c.getHeight(HeightmapType.MOTION_BLOCKING, x, z));
        assertEquals(62, c.getHeight(HeightmapType.MOTION_BLOCKING_NO_LEAVES, x, z));
        assertEquals(60, c.getHeight(HeightmapType.OCEAN_FLOOR, x, z));
        c.setState(x, 62, z, BlockProperties.AIR);
        assertEquals(61, c.getHeight(HeightmapType.WORLD_SURFACE, x, z));
        assertEquals(61, c.getHeight(HeightmapType.MOTION_BLOCKING, x, z));
        c.setState(x, 61, z, BlockProperties.AIR);
        c.setState(x, 60, z, BlockProperties.AIR);
        assertEquals(-65, c.getHeight(HeightmapType.WORLD_SURFACE, x, z));
        assertEquals(-65, c.getHeight(HeightmapType.OCEAN_FLOOR, x, z));
    }

    @Test
    void postProcessingMarksArePackedPerSection() {
        TerrainChunk c = chunk();
        c.markPosForPostProcessing(-37, -60, 127);
        c.markPosForPostProcessing(-48, 319, 112);
        c.markPosForPostProcessing(-48, 320, 112);
        assertEquals(2, c.postProcessingPositions().size());
        assertEquals(-37, c.postProcessingPositions().get(0)[0]);
        assertEquals(-60, c.postProcessingPositions().get(0)[1]);
        assertEquals(127, c.postProcessingPositions().get(0)[2]);
        assertEquals(319, c.postProcessingPositions().get(1)[1]);
    }

    @Test
    void unknownBlocksFailLoudly() {
        TerrainChunk c = chunk();
        assertThrows(IllegalArgumentException.class, () -> c.setState(-48, 0, 112, StateRef.of("minecraft:diamond_block")));
        assertThrows(IllegalArgumentException.class, () -> BlockProperties.isAir(StateRef.of("minecraft:diamond_block")));
    }

    @Test
    void blockStateDecodingUsesDefaults() {
        assertEquals(StateRef.of("minecraft:water", "level", "0"), BlockProperties.decodeState("minecraft:water"));
        assertEquals(StateRef.of("minecraft:lava", "level", "3"),
                BlockProperties.decodeState(Map.of("Name", "minecraft:lava", "Properties", Map.of("level", "3"))));
        assertEquals(BlockProperties.STONE, BlockProperties.decodeState(Map.of("Name", "minecraft:stone")));
        assertThrows(IllegalArgumentException.class,
                () -> BlockProperties.decodeState(Map.of("Name", "minecraft:stone", "Properties", Map.of("level", "1"))));
    }
}
