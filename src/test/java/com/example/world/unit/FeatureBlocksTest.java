package com.example.world.unit;

import com.example.audio.SoundMaterial;
import com.example.engine.BlockTextureMap;
import com.example.engine.Tile;
import com.example.entity.Explosions;
import com.example.world.BlockDrops;
import com.example.world.BlockType;
import com.example.world.ChunkMeshBuilder;
import com.example.world.LightProperties;
import com.example.world.ToolEffectiveness;
import com.example.world.VanillaBlocks;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** The C1 feature blocks and items against the 26.3 client jar (Blocks static initializer, InfestedBlock, loot tables, tags, models). */
class FeatureBlocksTest {

    @Test
    void hardnessResistanceAndLightMatchTheJar() {
        Object[][] expected = {
                {BlockType.ANDESITE, 1.5f, 6f, 0}, {BlockType.DIORITE, 1.5f, 6f, 0},
                {BlockType.DEEPSLATE_COAL_ORE, 4.5f, 3f, 0}, {BlockType.DEEPSLATE_COPPER_ORE, 4.5f, 3f, 0},
                {BlockType.DEEPSLATE_DIAMOND_ORE, 4.5f, 3f, 0}, {BlockType.DEEPSLATE_EMERALD_ORE, 4.5f, 3f, 0},
                {BlockType.DEEPSLATE_GOLD_ORE, 4.5f, 3f, 0}, {BlockType.DEEPSLATE_LAPIS_ORE, 4.5f, 3f, 0},
                {BlockType.DEEPSLATE_REDSTONE_ORE, 4.5f, 3f, 0}, {BlockType.EMERALD_ORE, 3f, 3f, 0}, {BlockType.LAPIS_ORE, 3f, 3f, 0},
                {BlockType.REDSTONE_ORE, 3f, 3f, 0}, {BlockType.INFESTED_STONE, 0.75f, 0.75f, 0},
                {BlockType.INFESTED_DEEPSLATE, 1.5f, 0.75f, 0}, {BlockType.MAGMA_BLOCK, 0.5f, 0.5f, 3}};
        for (Object[] e : expected) {
            BlockType b = (BlockType) e[0];
            assertEquals((float) e[1], b.hardness, 1e-6f, b + " destroyTime");
            assertEquals((float) e[2], Explosions.blastResistance(b), 1e-6f, b + " explosion resistance");
            assertEquals((int) e[3], LightProperties.emission(b), b + " light");
            assertEquals(15, LightProperties.dampening(b), b + " is a full opaque cube");
            assertTrue(List.of(BlockType.PLACEABLE).contains(b), b + " has a block item");
        }
    }

    @Test
    void lootTablesWithoutSilkTouchOrFortune() {
        Random r = new Random(3);
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.ANDESITE, 1)), BlockDrops.resolve(BlockType.ANDESITE, r, BlockType.WOODEN_PICKAXE));
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.MAGMA_BLOCK, 1)), BlockDrops.resolve(BlockType.MAGMA_BLOCK, r, BlockType.WOODEN_PICKAXE));
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.COAL, 1)), BlockDrops.resolve(BlockType.DEEPSLATE_COAL_ORE, r, BlockType.WOODEN_PICKAXE));
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.RAW_GOLD, 1)), BlockDrops.resolve(BlockType.DEEPSLATE_GOLD_ORE, r, BlockType.IRON_PICKAXE));
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.DIAMOND, 1)), BlockDrops.resolve(BlockType.DEEPSLATE_DIAMOND_ORE, r, BlockType.IRON_PICKAXE));
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.EMERALD, 1)), BlockDrops.resolve(BlockType.EMERALD_ORE, r, BlockType.IRON_PICKAXE));
        for (int i = 0; i < 50; i++) {
            int lapis = BlockDrops.resolve(BlockType.LAPIS_ORE, r, BlockType.STONE_PICKAXE).get(0).count();
            assertTrue(lapis >= 4 && lapis <= 9, "lapis 4-9: " + lapis);
            int redstone = BlockDrops.resolve(BlockType.DEEPSLATE_REDSTONE_ORE, r, BlockType.IRON_PICKAXE).get(0).count();
            assertTrue(redstone >= 4 && redstone <= 5, "redstone 4-5: " + redstone);
            int copper = BlockDrops.resolve(BlockType.DEEPSLATE_COPPER_ORE, r, BlockType.STONE_PICKAXE).get(0).count();
            assertTrue(copper >= 2 && copper <= 5, "raw copper 2-5: " + copper);
        }
        assertEquals(List.of(), BlockDrops.resolve(BlockType.INFESTED_STONE, r, BlockType.IRON_PICKAXE));
        assertEquals(List.of(), BlockDrops.resolve(BlockType.INFESTED_DEEPSLATE, r, null));
    }

    @Test
    void toolsFollowMineableAndNeedsToolTags() {
        for (BlockType b : new BlockType[]{BlockType.ANDESITE, BlockType.DIORITE, BlockType.MAGMA_BLOCK, BlockType.DEEPSLATE_COAL_ORE}) {
            assertFalse(ToolEffectiveness.canHarvest(null, b), b.name());
            assertTrue(ToolEffectiveness.canHarvest(BlockType.WOODEN_PICKAXE, b), b.name());
        }
        for (BlockType b : new BlockType[]{BlockType.LAPIS_ORE, BlockType.DEEPSLATE_LAPIS_ORE, BlockType.DEEPSLATE_COPPER_ORE}) {
            assertFalse(ToolEffectiveness.canHarvest(BlockType.WOODEN_PICKAXE, b), b.name());
            assertTrue(ToolEffectiveness.canHarvest(BlockType.STONE_PICKAXE, b), b.name());
        }
        for (BlockType b : new BlockType[]{BlockType.REDSTONE_ORE, BlockType.DEEPSLATE_REDSTONE_ORE, BlockType.EMERALD_ORE,
                BlockType.DEEPSLATE_EMERALD_ORE, BlockType.DEEPSLATE_GOLD_ORE, BlockType.DEEPSLATE_DIAMOND_ORE}) {
            assertFalse(ToolEffectiveness.canHarvest(BlockType.STONE_PICKAXE, b), b.name());
            assertTrue(ToolEffectiveness.canHarvest(BlockType.IRON_PICKAXE, b), b.name());
        }
        // infested blocks: mineable/pickaxe but no requiresCorrectToolForDrops
        assertTrue(ToolEffectiveness.canHarvest(null, BlockType.INFESTED_STONE));
        assertEquals(ToolEffectiveness.Kind.PICKAXE, ToolEffectiveness.correctKindFor(BlockType.INFESTED_DEEPSLATE));
        assertEquals(0.75f * 1.5f, ToolEffectiveness.rawBreakSeconds(null, BlockType.INFESTED_STONE), 1e-6f);
    }

    @Test
    void facesItemsAndSounds() {
        BlockTextureMap m = new BlockTextureMap();
        assertEquals(new BlockTextureMap.Faces(Tile.MAGMA, Tile.MAGMA, Tile.MAGMA), m.facesFor(BlockType.MAGMA_BLOCK));
        assertEquals(new BlockTextureMap.Faces(Tile.STONE, Tile.STONE, Tile.STONE), m.facesFor(BlockType.INFESTED_STONE));
        assertEquals(new BlockTextureMap.Faces(Tile.DEEPSLATE_TOP, Tile.DEEPSLATE, Tile.DEEPSLATE_TOP), m.facesFor(BlockType.INFESTED_DEEPSLATE));
        assertEquals(Tile.DEEPSLATE_LAPIS_ORE, m.facesFor(BlockType.DEEPSLATE_LAPIS_ORE).side());
        for (BlockType item : new BlockType[]{BlockType.LAPIS_LAZULI, BlockType.REDSTONE, BlockType.EMERALD}) {
            assertTrue(ChunkMeshBuilder.isFlatIconItem(item), item.name());
            assertFalse(VanillaBlocks.isVanillaBlock(item), item.name());
            assertFalse(List.of(BlockType.PLACEABLE).contains(item), item.name());
        }
        assertEquals(SoundMaterial.STONE, SoundMaterial.materialFor(BlockType.MAGMA_BLOCK));
        assertNull(SoundMaterial.materialFor(BlockType.DEEPSLATE_REDSTONE_ORE), "SoundType.DEEPSLATE files are not in sounds/");
    }

    @Test
    void vanillaStates() {
        assertEquals(BlockType.REDSTONE_ORE, VanillaBlocks.byVanilla("minecraft:redstone_ore", Map.of("lit", "false")));
        assertEquals(BlockType.INFESTED_DEEPSLATE, VanillaBlocks.byVanilla("minecraft:infested_deepslate", Map.of("axis", "y")));
        assertEquals(BlockType.MAGMA_BLOCK, VanillaBlocks.byVanilla("minecraft:magma_block"));
        assertEquals(Map.of("lit", "false"), VanillaBlocks.vanillaProperties(BlockType.DEEPSLATE_REDSTONE_ORE));
    }
}
