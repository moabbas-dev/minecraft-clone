package com.example.audio;

import com.example.world.BlockType;
import com.example.world.FoodRegistry;
import com.example.world.ToolEffectiveness;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SoundMaterialTest {

    @Test
    void everyRealBlockTypeExceptAirAndWaterHasAMaterial() {
        // Tools/STICK/food are never a world block at all (never placed, never mined) - the
        // same "never solid-collision-walkable or player-breakable" null case materialFor()'s
        // own docs already carve out for AIR/WATER, just reached a different way.
        for (BlockType type : BlockType.values()) {
            if (type == BlockType.AIR || type == BlockType.WATER || type == BlockType.STICK
                    || ToolEffectiveness.isTool(type) || FoodRegistry.isFood(type)) continue;
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
