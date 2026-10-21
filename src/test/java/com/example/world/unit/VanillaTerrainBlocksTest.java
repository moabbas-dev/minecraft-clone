package com.example.world.unit;

import com.example.engine.BlockTextureMap;
import com.example.engine.Tile;
import com.example.entity.Explosions;
import com.example.world.BlockDrops;
import com.example.world.BlockSupport;
import com.example.world.BlockType;
import com.example.world.LightProperties;
import com.example.world.ToolEffectiveness;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The G28 terrain blocks against the 26.3 client jar (Blocks static initializer, loot tables, block tags, models). */
class VanillaTerrainBlocksTest {

    @Test
    void idsAreUniqueAndStartAt256() {
        Set<Integer> ids = new HashSet<>();
        for (BlockType b : BlockType.values()) assertTrue(ids.add(b.id), "duplicate id " + b.id + " on " + b);
        assertEquals(256, BlockType.CALCITE.id);
        assertEquals(277, BlockType.LIGHT_GRAY_TERRACOTTA.id);
        assertEquals(BlockType.AIR, BlockType.byId(255));
    }

    @Test
    void hardnessAndBlastResistanceMatchTheJar() {
        Object[][] expected = {
                {BlockType.CALCITE, 0.75f, 0.75f}, {BlockType.CINNABAR, 1.5f, 6f}, {BlockType.COARSE_DIRT, 0.5f, 0.5f},
                {BlockType.DEEPSLATE, 3f, 6f}, {BlockType.COBBLED_DEEPSLATE, 3.5f, 6f}, {BlockType.DEEPSLATE_IRON_ORE, 4.5f, 3f},
                {BlockType.GRANITE, 1.5f, 6f}, {BlockType.MYCELIUM, 0.6f, 0.6f}, {BlockType.PODZOL, 0.5f, 0.5f},
                {BlockType.POWDER_SNOW, 0.25f, 0.25f}, {BlockType.PACKED_ICE, 0.5f, 0.5f}, {BlockType.RAW_COPPER_BLOCK, 5f, 6f},
                {BlockType.RAW_IRON_BLOCK, 5f, 6f}, {BlockType.RED_SANDSTONE, 0.8f, 0.8f}, {BlockType.SULFUR, 1.5f, 6f},
                {BlockType.TUFF, 1.5f, 6f}, {BlockType.WHITE_TERRACOTTA, 1.25f, 4.2f}, {BlockType.ORANGE_TERRACOTTA, 1.25f, 4.2f},
                {BlockType.YELLOW_TERRACOTTA, 1.25f, 4.2f}, {BlockType.BROWN_TERRACOTTA, 1.25f, 4.2f},
                {BlockType.RED_TERRACOTTA, 1.25f, 4.2f}, {BlockType.LIGHT_GRAY_TERRACOTTA, 1.25f, 4.2f}};
        for (Object[] e : expected) {
            BlockType b = (BlockType) e[0];
            assertEquals((float) e[1], b.hardness, 1e-6f, b + " destroyTime");
            assertEquals((float) e[2], Explosions.blastResistance(b), 1e-6f, b + " explosion resistance");
            assertEquals(0, LightProperties.emission(b), b + " light");
        }
    }

    @Test
    void lootTablesWithoutSilkTouch() {
        Random r = new Random(1);
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.COBBLED_DEEPSLATE, 1)), BlockDrops.resolve(BlockType.DEEPSLATE, r, BlockType.WOODEN_PICKAXE));
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.RAW_IRON, 1)), BlockDrops.resolve(BlockType.DEEPSLATE_IRON_ORE, r, BlockType.STONE_PICKAXE));
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.DIRT, 1)), BlockDrops.resolve(BlockType.MYCELIUM, r, null));
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.DIRT, 1)), BlockDrops.resolve(BlockType.PODZOL, r, null));
        assertEquals(List.of(), BlockDrops.resolve(BlockType.PACKED_ICE, r, BlockType.IRON_PICKAXE));
        assertEquals(List.of(), BlockDrops.resolve(BlockType.POWDER_SNOW, r, BlockType.IRON_SHOVEL));
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.TUFF, 1)), BlockDrops.resolve(BlockType.TUFF, r, BlockType.WOODEN_PICKAXE));
        assertEquals(List.of(new BlockDrops.Resolved(BlockType.COARSE_DIRT, 1)), BlockDrops.resolve(BlockType.COARSE_DIRT, r, null));
    }

    @Test
    void toolsFollowMineableAndNeedsToolTags() {
        // requiresCorrectToolForDrops + mineable/pickaxe: nothing by hand
        for (BlockType b : new BlockType[]{BlockType.CALCITE, BlockType.TUFF, BlockType.DEEPSLATE, BlockType.RED_SANDSTONE, BlockType.ORANGE_TERRACOTTA}) {
            assertFalse(ToolEffectiveness.canHarvest(null, b), b.name());
            assertTrue(ToolEffectiveness.canHarvest(BlockType.WOODEN_PICKAXE, b), b.name());
        }
        // needs_stone_tool
        for (BlockType b : new BlockType[]{BlockType.DEEPSLATE_IRON_ORE, BlockType.RAW_IRON_BLOCK, BlockType.RAW_COPPER_BLOCK}) {
            assertFalse(ToolEffectiveness.canHarvest(BlockType.WOODEN_PICKAXE, b), b.name());
            assertTrue(ToolEffectiveness.canHarvest(BlockType.STONE_PICKAXE, b), b.name());
        }
        // packed ice: a pickaxe speeds it up, but it has no requiresCorrectToolForDrops (0.5 * 1.5 by hand)
        assertTrue(ToolEffectiveness.canHarvest(null, BlockType.PACKED_ICE));
        assertEquals(ToolEffectiveness.Kind.PICKAXE, ToolEffectiveness.correctKindFor(BlockType.PACKED_ICE));
        assertEquals(0.75f, ToolEffectiveness.breakSeconds(null, BlockType.PACKED_ICE), 1e-6f);
        for (BlockType b : new BlockType[]{BlockType.COARSE_DIRT, BlockType.MYCELIUM, BlockType.PODZOL}) {
            assertEquals(ToolEffectiveness.Kind.SHOVEL, ToolEffectiveness.correctKindFor(b), b.name());
            assertTrue(ToolEffectiveness.canHarvest(null, b), b.name());
        }
        assertNull(ToolEffectiveness.correctKindFor(BlockType.POWDER_SNOW));
    }

    @Test
    void facesFollowTheModels() {
        BlockTextureMap m = new BlockTextureMap();
        assertEquals(new BlockTextureMap.Faces(Tile.DEEPSLATE_TOP, Tile.DEEPSLATE, Tile.DEEPSLATE_TOP), m.facesFor(BlockType.DEEPSLATE));
        assertEquals(new BlockTextureMap.Faces(Tile.MYCELIUM_TOP, Tile.MYCELIUM_SIDE, Tile.DIRT), m.facesFor(BlockType.MYCELIUM));
        assertEquals(new BlockTextureMap.Faces(Tile.PODZOL_TOP, Tile.PODZOL_SIDE, Tile.DIRT), m.facesFor(BlockType.PODZOL));
        assertEquals(new BlockTextureMap.Faces(Tile.RED_SANDSTONE_TOP, Tile.RED_SANDSTONE_SIDE, Tile.RED_SANDSTONE_BOTTOM),
                m.facesFor(BlockType.RED_SANDSTONE));
        assertEquals(Tile.SULFUR, m.facesFor(BlockType.SULFUR).side());
    }

    @Test
    void powderSnowLightAndSupport() {
        assertFalse(BlockType.POWDER_SNOW.isOpaqueCube()); // noOcclusion
        assertEquals(1, LightProperties.dampening(BlockType.POWDER_SNOW));
        assertEquals(1.0f, LightProperties.shadeBrightness(BlockType.POWDER_SNOW));
        assertFalse(BlockSupport.isSturdy(BlockType.POWDER_SNOW));
        assertEquals(15, LightProperties.dampening(BlockType.PACKED_ICE)); // packed ice occludes, unlike ice
        assertFalse(java.util.Arrays.asList(BlockType.PLACEABLE).contains(BlockType.POWDER_SNOW)); // no block item in vanilla
    }
}
