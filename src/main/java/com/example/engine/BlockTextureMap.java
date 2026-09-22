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
