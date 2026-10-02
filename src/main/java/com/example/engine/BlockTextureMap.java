package com.example.engine;

import com.example.world.BlockType;

import java.util.EnumMap;
import java.util.Map;

/** Maps each block type to its top/side/bottom atlas tiles. */
public class BlockTextureMap {
    /** {@code sideOverlay} is null for almost every block; grass has a tintable overlay drawn on top of its side. */
    public record Faces(Tile top, Tile side, Tile bottom, Tile sideOverlay) {
        public Faces(Tile top, Tile side, Tile bottom) {
            this(top, side, bottom, null);
        }
    }

    private final Map<BlockType, Faces> map = new EnumMap<>(BlockType.class);

    public BlockTextureMap() {
        uniform(BlockType.STONE, Tile.STONE);
        uniform(BlockType.DIRT, Tile.DIRT);
        map.put(BlockType.GRASS_BLOCK, new Faces(Tile.GRASS_TOP, Tile.GRASS_SIDE, Tile.DIRT, Tile.GRASS_SIDE_OVERLAY));
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
        // All 4 growth stages share one texture - see BlockType's own doc comment on why.
        uniform(BlockType.OAK_SAPLING_STAGE_0, Tile.OAK_SAPLING);
        uniform(BlockType.OAK_SAPLING_STAGE_1, Tile.OAK_SAPLING);
        uniform(BlockType.OAK_SAPLING_STAGE_2, Tile.OAK_SAPLING);
        uniform(BlockType.OAK_SAPLING_STAGE_3, Tile.OAK_SAPLING);
        uniform(BlockType.STICK, Tile.STICK);
        uniform(BlockType.WOODEN_PICKAXE, Tile.WOODEN_PICKAXE);
        uniform(BlockType.STONE_PICKAXE, Tile.STONE_PICKAXE);
        uniform(BlockType.IRON_PICKAXE, Tile.IRON_PICKAXE);
        uniform(BlockType.WOODEN_AXE, Tile.WOODEN_AXE);
        uniform(BlockType.STONE_AXE, Tile.STONE_AXE);
        uniform(BlockType.IRON_AXE, Tile.IRON_AXE);
        uniform(BlockType.WOODEN_SHOVEL, Tile.WOODEN_SHOVEL);
        uniform(BlockType.STONE_SHOVEL, Tile.STONE_SHOVEL);
        uniform(BlockType.IRON_SHOVEL, Tile.IRON_SHOVEL);
        uniform(BlockType.APPLE, Tile.APPLE);
        uniform(BlockType.BREAD, Tile.BREAD);
        uniform(BlockType.COOKED_BEEF, Tile.COOKED_BEEF);

        // Cooking/smelting milestone.
        put(BlockType.BLAST_FURNACE, Tile.BLAST_FURNACE_TOP, Tile.BLAST_FURNACE_SIDE, Tile.BLAST_FURNACE_TOP);
        put(BlockType.SMOKER, Tile.SMOKER_TOP, Tile.SMOKER_SIDE, Tile.SMOKER_TOP);
        uniform(BlockType.COPPER_ORE, Tile.COPPER_ORE);
        uniform(BlockType.SMOOTH_STONE, Tile.SMOOTH_STONE);
        uniform(BlockType.COAL, Tile.COAL);
        uniform(BlockType.CHARCOAL, Tile.CHARCOAL);
        uniform(BlockType.IRON_INGOT, Tile.IRON_INGOT);
        uniform(BlockType.GOLD_INGOT, Tile.GOLD_INGOT);
        uniform(BlockType.COPPER_INGOT, Tile.COPPER_INGOT);
        uniform(BlockType.IRON_NUGGET, Tile.IRON_NUGGET);
        uniform(BlockType.BRICK, Tile.BRICK);
        uniform(BlockType.RAW_IRON, Tile.RAW_IRON);
        uniform(BlockType.RAW_GOLD, Tile.RAW_GOLD);
        uniform(BlockType.RAW_COPPER, Tile.RAW_COPPER);
        uniform(BlockType.RAW_BEEF, Tile.RAW_BEEF);
        uniform(BlockType.RAW_PORKCHOP, Tile.RAW_PORKCHOP);
        uniform(BlockType.COOKED_PORKCHOP, Tile.COOKED_PORKCHOP);
        uniform(BlockType.RAW_CHICKEN, Tile.RAW_CHICKEN);
        uniform(BlockType.COOKED_CHICKEN, Tile.COOKED_CHICKEN);
        uniform(BlockType.RAW_MUTTON, Tile.RAW_MUTTON);
        uniform(BlockType.COOKED_MUTTON, Tile.COOKED_MUTTON);
        uniform(BlockType.RAW_RABBIT, Tile.RAW_RABBIT);
        uniform(BlockType.COOKED_RABBIT, Tile.COOKED_RABBIT);
        uniform(BlockType.COD, Tile.COD);
        uniform(BlockType.COOKED_COD, Tile.COOKED_COD);
        uniform(BlockType.SALMON, Tile.SALMON);
        uniform(BlockType.COOKED_SALMON, Tile.COOKED_SALMON);
        uniform(BlockType.POTATO, Tile.POTATO);
        uniform(BlockType.BAKED_POTATO, Tile.BAKED_POTATO);
        uniform(BlockType.KELP, Tile.KELP);
        uniform(BlockType.DRIED_KELP, Tile.DRIED_KELP);

        // Mob-system milestone.
        uniform(BlockType.ROTTEN_FLESH, Tile.ROTTEN_FLESH);
        uniform(BlockType.WOODEN_SWORD, Tile.WOODEN_SWORD);
        uniform(BlockType.STONE_SWORD, Tile.STONE_SWORD);
        uniform(BlockType.IRON_SWORD, Tile.IRON_SWORD);
        uniform(BlockType.LEATHER, Tile.LEATHER);
        uniform(BlockType.FEATHER, Tile.FEATHER);
        uniform(BlockType.EGG, Tile.EGG);
        uniform(BlockType.LEATHER_HELMET, Tile.LEATHER_HELMET);
        uniform(BlockType.LEATHER_CHESTPLATE, Tile.LEATHER_CHESTPLATE);
        uniform(BlockType.LEATHER_LEGGINGS, Tile.LEATHER_LEGGINGS);
        uniform(BlockType.LEATHER_BOOTS, Tile.LEATHER_BOOTS);
        uniform(BlockType.IRON_HELMET, Tile.IRON_HELMET);
        uniform(BlockType.IRON_CHESTPLATE, Tile.IRON_CHESTPLATE);
        uniform(BlockType.IRON_LEGGINGS, Tile.IRON_LEGGINGS);
        uniform(BlockType.IRON_BOOTS, Tile.IRON_BOOTS);
        uniform(BlockType.GOLD_HELMET, Tile.GOLD_HELMET);
        uniform(BlockType.GOLD_CHESTPLATE, Tile.GOLD_CHESTPLATE);
        uniform(BlockType.GOLD_LEGGINGS, Tile.GOLD_LEGGINGS);
        uniform(BlockType.GOLD_BOOTS, Tile.GOLD_BOOTS);
        uniform(BlockType.DIAMOND, Tile.DIAMOND);
        uniform(BlockType.DIAMOND_SWORD, Tile.DIAMOND_SWORD);
        uniform(BlockType.DIAMOND_AXE, Tile.DIAMOND_AXE);
        uniform(BlockType.DIAMOND_PICKAXE, Tile.DIAMOND_PICKAXE);
        uniform(BlockType.DIAMOND_SHOVEL, Tile.DIAMOND_SHOVEL);
        uniform(BlockType.DIAMOND_HELMET, Tile.DIAMOND_HELMET);
        uniform(BlockType.DIAMOND_CHESTPLATE, Tile.DIAMOND_CHESTPLATE);
        uniform(BlockType.DIAMOND_LEGGINGS, Tile.DIAMOND_LEGGINGS);
        uniform(BlockType.DIAMOND_BOOTS, Tile.DIAMOND_BOOTS);
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
