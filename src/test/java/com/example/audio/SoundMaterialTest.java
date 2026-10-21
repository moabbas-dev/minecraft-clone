package com.example.audio;

import com.example.world.BlockType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SoundMaterialTest {

    @Test
    void everyPlaceableBlockHasAMaterialAndWaterDoesToo() {
        // Every non-placeable item (tools/stick/food/ingots/ores-as-items/nuggets/raw materials
        // - see ToolEffectiveness/FoodRegistry/BlockType) is never a world block at all: never
        // placed, never mined, never solid-collision-walkable - the same null case
        // materialFor()'s own docs already carve out for AIR, just reached a different way.
        // WATER is the one real block that's still deliberately unmapped (see materialFor's docs).
        List<BlockType> placeable = Arrays.asList(BlockType.PLACEABLE);
        for (BlockType type : BlockType.values()) {
            if (type == BlockType.AIR || type == BlockType.WATER || !placeable.contains(type)) continue;
            if (SoundMaterial.MISSING_VANILLA_SOUND.contains(type)) continue;
            assertNotNull(SoundMaterial.materialFor(type), type + " must have a sound material");
        }
    }

    @Test
    void blocksWithoutTheirVanillaSoundAssetStaySilent() {
        for (BlockType type : SoundMaterial.MISSING_VANILLA_SOUND) {
            assertNull(SoundMaterial.materialFor(type), type + " must not borrow another block's sound");
        }
        assertEquals(SoundMaterial.GLASS, SoundMaterial.materialFor(BlockType.PACKED_ICE)); // SoundType.GLASS
        assertEquals(SoundMaterial.GRASS, SoundMaterial.materialFor(BlockType.MYCELIUM)); // SoundType.GRASS
        assertEquals(SoundMaterial.GRAVEL, SoundMaterial.materialFor(BlockType.PODZOL)); // SoundType.GRAVEL
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
