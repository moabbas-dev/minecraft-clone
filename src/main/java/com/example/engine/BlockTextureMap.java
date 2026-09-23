package com.example.engine;

import com.example.world.BlockType;

import java.util.EnumMap;
import java.util.Map;

/** Maps each block type to its top/side/bottom atlas tiles. */
public class BlockTextureMap {
    public record Faces(Tile top, Tile side, Tile bottom) {
    }

    private final Map<BlockType, Faces> map = new EnumMap<>(BlockType.class);

    public BlockTextureMap() {
        uniform(BlockType.STONE, Tile.STONE);
        uniform(BlockType.DIRT, Tile.DIRT);
        put(BlockType.GRASS_BLOCK, Tile.GRASS_TOP, Tile.GRASS_SIDE, Tile.DIRT);
        uniform(BlockType.COBBLESTONE, Tile.COBBLESTONE);
        uniform(BlockType.OAK_PLANKS, Tile.PLANKS);
        uniform(BlockType.BEDROCK, Tile.BEDROCK);
        uniform(BlockType.WATER, Tile.WATER);
        uniform(BlockType.SAND, Tile.SAND);
        uniform(BlockType.GRAVEL, Tile.GRAVEL);
        uniform(BlockType.GOLD_ORE, Tile.GOLD_ORE);
        uniform(BlockType.IRON_ORE, Tile.IRON_ORE);
        uniform(BlockType.COAL_ORE, Tile.COAL_ORE);
        put(BlockType.OAK_LOG, Tile.LOG_TOP, Tile.LOG_SIDE, Tile.LOG_TOP);
        uniform(BlockType.OAK_LEAVES, Tile.LEAVES);
        uniform(BlockType.GLASS, Tile.GLASS);
        put(BlockType.SANDSTONE, Tile.SANDSTONE_TOP, Tile.SANDSTONE_SIDE, Tile.SANDSTONE_BOTTOM);
        uniform(BlockType.GOLD_BLOCK, Tile.GOLD_BLOCK);
        uniform(BlockType.IRON_BLOCK, Tile.IRON_BLOCK);
        uniform(BlockType.BRICKS, Tile.BRICKS);
        put(BlockType.TNT, Tile.TNT_TOP, Tile.TNT_SIDE, Tile.TNT_BOTTOM);
        uniform(BlockType.BOOKSHELF, Tile.BOOKSHELF);
        uniform(BlockType.MOSSY_COBBLESTONE, Tile.MOSSY_COBBLESTONE);
        uniform(BlockType.OBSIDIAN, Tile.OBSIDIAN);
        uniform(BlockType.CHEST, Tile.CHEST);
        uniform(BlockType.DIAMOND_ORE, Tile.DIAMOND_ORE);
        uniform(BlockType.DIAMOND_BLOCK, Tile.DIAMOND_BLOCK);
        put(BlockType.CRAFTING_TABLE, Tile.CRAFTING_TABLE_TOP, Tile.CRAFTING_TABLE_SIDE, Tile.PLANKS);
        put(BlockType.FURNACE, Tile.FURNACE_TOP, Tile.FURNACE_SIDE, Tile.FURNACE_TOP);
        uniform(BlockType.ICE, Tile.ICE);
        uniform(BlockType.SNOW_BLOCK, Tile.SNOW_BLOCK);
        uniform(BlockType.CLAY, Tile.CLAY);
        put(BlockType.PUMPKIN, Tile.PUMPKIN_TOP, Tile.PUMPKIN_SIDE, Tile.PUMPKIN_TOP);
        uniform(BlockType.NETHERRACK, Tile.NETHERRACK);
        uniform(BlockType.GLOWSTONE, Tile.GLOWSTONE);
        uniform(BlockType.TERRACOTTA, Tile.TERRACOTTA);
        uniform(BlockType.RED_SAND, Tile.RED_SAND);
        put(BlockType.BIRCH_LOG, Tile.BIRCH_LOG_TOP, Tile.BIRCH_LOG_SIDE, Tile.BIRCH_LOG_TOP);
        uniform(BlockType.BIRCH_LEAVES, Tile.BIRCH_LEAVES);
        put(BlockType.SPRUCE_LOG, Tile.SPRUCE_LOG_TOP, Tile.SPRUCE_LOG_SIDE, Tile.SPRUCE_LOG_TOP);
        uniform(BlockType.SPRUCE_LEAVES, Tile.SPRUCE_LEAVES);
        put(BlockType.JUNGLE_LOG, Tile.JUNGLE_LOG_TOP, Tile.JUNGLE_LOG_SIDE, Tile.JUNGLE_LOG_TOP);
        uniform(BlockType.JUNGLE_LEAVES, Tile.JUNGLE_LEAVES);
        put(BlockType.ACACIA_LOG, Tile.ACACIA_LOG_TOP, Tile.ACACIA_LOG_SIDE, Tile.ACACIA_LOG_TOP);
        uniform(BlockType.ACACIA_LEAVES, Tile.ACACIA_LEAVES);
        put(BlockType.MANGROVE_LOG, Tile.MANGROVE_LOG_TOP, Tile.MANGROVE_LOG_SIDE, Tile.MANGROVE_LOG_TOP);
        uniform(BlockType.MANGROVE_LEAVES, Tile.MANGROVE_LEAVES);
        put(BlockType.DARK_OAK_LOG, Tile.DARK_OAK_LOG_TOP, Tile.DARK_OAK_LOG_SIDE, Tile.DARK_OAK_LOG_TOP);
        uniform(BlockType.DARK_OAK_LEAVES, Tile.DARK_OAK_LEAVES);
        put(BlockType.CHERRY_LOG, Tile.CHERRY_LOG_TOP, Tile.CHERRY_LOG_SIDE, Tile.CHERRY_LOG_TOP);
        uniform(BlockType.CHERRY_LEAVES, Tile.CHERRY_LEAVES);
        uniform(BlockType.MUD, Tile.MUD);
        put(BlockType.MANGROVE_ROOTS, Tile.MANGROVE_ROOTS_TOP, Tile.MANGROVE_ROOTS_SIDE, Tile.MANGROVE_ROOTS_TOP);
    }

    private void uniform(BlockType type, Tile tile) {
        map.put(type, new Faces(tile, tile, tile));
    }

    private void put(BlockType type, Tile top, Tile side, Tile bottom) {
        map.put(type, new Faces(top, side, bottom));
    }

    public Faces facesFor(BlockType type) {
        Faces f = map.get(type);
        return f == null ? new Faces(Tile.STONE, Tile.STONE, Tile.STONE) : f;
    }
}
