package com.example.audio;

import com.example.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoundMaterialTest {

    @Test
    void everyBlockTypeExceptAirAndWaterHasAMaterial() {
        for (BlockType type : BlockType.values()) {
            if (type == BlockType.AIR || type == BlockType.WATER) continue;
            assertNotNull(SoundMaterial.materialFor(type), type + " must have a sound material");
        }
    }

    @Test
    void airAndWaterHaveNoMaterial() {
        assertNull(SoundMaterial.materialFor(BlockType.AIR));
        assertNull(SoundMaterial.materialFor(BlockType.WATER));
    }

    @Test
    void stoneFamilyIncludesOresAndBricks() {
        assertEquals(SoundMaterial.STONE, SoundMaterial.materialFor(BlockType.STONE));
        assertEquals(SoundMaterial.STONE, SoundMaterial.materialFor(BlockType.COBBLESTONE));
        assertEquals(SoundMaterial.STONE, SoundMaterial.materialFor(BlockType.DIAMOND_ORE));
        assertEquals(SoundMaterial.STONE, SoundMaterial.materialFor(BlockType.BRICKS));
        assertEquals(SoundMaterial.STONE, SoundMaterial.materialFor(BlockType.OBSIDIAN));
    }

    @Test
    void leavesAndSaplingsUseGrassFamily() {
        assertEquals(SoundMaterial.GRASS, SoundMaterial.materialFor(BlockType.OAK_LEAVES));
        assertEquals(SoundMaterial.GRASS, SoundMaterial.materialFor(BlockType.CHERRY_LEAVES));
        assertEquals(SoundMaterial.GRASS, SoundMaterial.materialFor(BlockType.OAK_SAPLING_STAGE_0));
        assertEquals(SoundMaterial.GRASS, SoundMaterial.materialFor(BlockType.GRASS_BLOCK));
    }

    @Test
    void woodFamilyIncludesLogsAndFurniture() {
        assertEquals(SoundMaterial.WOOD, SoundMaterial.materialFor(BlockType.OAK_LOG));
        assertEquals(SoundMaterial.WOOD, SoundMaterial.materialFor(BlockType.OAK_PLANKS));
        assertEquals(SoundMaterial.WOOD, SoundMaterial.materialFor(BlockType.CRAFTING_TABLE));
        assertEquals(SoundMaterial.WOOD, SoundMaterial.materialFor(BlockType.CHEST));
    }

    @Test
    void glassHasItsOwnDedicatedMaterial() {
        assertEquals(SoundMaterial.GLASS, SoundMaterial.materialFor(BlockType.GLASS));
    }

    @Test
    void dirtMudAndClayUseGravelFamily() {
        assertEquals(SoundMaterial.GRAVEL, SoundMaterial.materialFor(BlockType.DIRT));
        assertEquals(SoundMaterial.GRAVEL, SoundMaterial.materialFor(BlockType.MUD));
        assertEquals(SoundMaterial.GRAVEL, SoundMaterial.materialFor(BlockType.CLAY));
        assertEquals(SoundMaterial.GRAVEL, SoundMaterial.materialFor(BlockType.GRAVEL));
    }
}
