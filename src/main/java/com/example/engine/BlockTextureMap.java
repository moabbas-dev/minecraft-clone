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
        // vanilla: the surface shows the still sprite, the sides the flowing one
        for (BlockType w : BlockType.values()) {
            if (w.isWater()) put(w, Tile.WATER, Tile.WATER_FLOW, Tile.WATER_FLOW);
            else if (w.isLava()) put(w, Tile.LAVA, Tile.LAVA_FLOW, Tile.LAVA_FLOW);
        }
        uniform(BlockType.BUCKET, Tile.BUCKET);
        uniform(BlockType.WATER_BUCKET, Tile.WATER_BUCKET);
        uniform(BlockType.LAVA_BUCKET, Tile.LAVA_BUCKET);
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
        put(BlockType.CHEST, Tile.CHEST_TOP, Tile.CHEST_SIDE, Tile.CHEST_TOP);
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
        uniform(BlockType.GOLD_SWORD, Tile.GOLD_SWORD);
        uniform(BlockType.GOLD_AXE, Tile.GOLD_AXE);
        uniform(BlockType.GOLD_PICKAXE, Tile.GOLD_PICKAXE);
        uniform(BlockType.GOLD_SHOVEL, Tile.GOLD_SHOVEL);
        uniform(BlockType.WOODEN_HOE, Tile.WOODEN_HOE);
        uniform(BlockType.STONE_HOE, Tile.STONE_HOE);
        uniform(BlockType.IRON_HOE, Tile.IRON_HOE);
        uniform(BlockType.GOLD_HOE, Tile.GOLD_HOE);
        for (BlockType torch : new BlockType[]{BlockType.TORCH, BlockType.WALL_TORCH_EAST, BlockType.WALL_TORCH_WEST,
                BlockType.WALL_TORCH_NORTH, BlockType.WALL_TORCH_SOUTH}) uniform(torch, Tile.TORCH);
        uniform(BlockType.GOLDEN_APPLE, Tile.GOLDEN_APPLE);
        uniform(BlockType.WHEAT, Tile.WHEAT);
        uniform(BlockType.WHEAT_SEEDS, Tile.WHEAT_SEEDS);
        uniform(BlockType.CARROT, Tile.CARROT);
        uniform(BlockType.BEETROOT, Tile.BEETROOT);
        uniform(BlockType.BEETROOT_SEEDS, Tile.BEETROOT_SEEDS);
        uniform(BlockType.BOW, Tile.BOW);
        uniform(BlockType.BOW_PULLING_0, Tile.BOW_PULLING_0);
        uniform(BlockType.BOW_PULLING_1, Tile.BOW_PULLING_1);
        uniform(BlockType.BOW_PULLING_2, Tile.BOW_PULLING_2);
        // only moisture 7 shows the wet top (minecraft.wiki/w/Farmland, block states)
        for (int m = 0; m <= 6; m++) put(com.example.world.BlockSupport.farmland(m), Tile.FARMLAND, Tile.DIRT, Tile.DIRT);
        put(BlockType.FARMLAND_MOIST, Tile.FARMLAND_MOIST, Tile.DIRT, Tile.DIRT);
        uniform(BlockType.COPPER_SWORD, Tile.COPPER_SWORD);
        uniform(BlockType.COPPER_AXE, Tile.COPPER_AXE);
        uniform(BlockType.COPPER_PICKAXE, Tile.COPPER_PICKAXE);
        uniform(BlockType.COPPER_SHOVEL, Tile.COPPER_SHOVEL);
        uniform(BlockType.COPPER_HOE, Tile.COPPER_HOE);
        uniform(BlockType.WOODEN_SPEAR, Tile.WOODEN_SPEAR);
        uniform(BlockType.STONE_SPEAR, Tile.STONE_SPEAR);
        uniform(BlockType.COPPER_SPEAR, Tile.COPPER_SPEAR);
        uniform(BlockType.IRON_SPEAR, Tile.IRON_SPEAR);
        uniform(BlockType.GOLD_SPEAR, Tile.GOLD_SPEAR);
        uniform(BlockType.DIAMOND_SPEAR, Tile.DIAMOND_SPEAR);
        uniform(BlockType.DIAMOND_HOE, Tile.DIAMOND_HOE);
        uniform(BlockType.FLINT, Tile.FLINT);
        uniform(BlockType.CLAY_BALL, Tile.CLAY_BALL);
        uniform(BlockType.SNOWBALL, Tile.SNOWBALL);
        uniform(BlockType.GLOWSTONE_DUST, Tile.GLOWSTONE_DUST);
        uniform(BlockType.BOOK, Tile.BOOK);
        uniform(BlockType.GUNPOWDER, Tile.GUNPOWDER);
        uniform(BlockType.BONE, Tile.BONE);
        uniform(BlockType.ARROW, Tile.ARROW);
        uniform(BlockType.STRING, Tile.STRING);
        uniform(BlockType.SPIDER_EYE, Tile.SPIDER_EYE);

        // G28 terrain blocks, from their 26.3 models: cube_all, except deepslate (cube_column: end deepslate_top, side
        // deepslate; only its axis=y unmirrored variant - the blockstate's random mirrored/rotated variants are not drawn),
        // mycelium and podzol (cube_bottom_top over dirt; neither has a tintindex) and red sandstone (cube_bottom_top).
        uniform(BlockType.CALCITE, Tile.CALCITE);
        uniform(BlockType.CINNABAR, Tile.CINNABAR);
        uniform(BlockType.COARSE_DIRT, Tile.COARSE_DIRT);
        put(BlockType.DEEPSLATE, Tile.DEEPSLATE_TOP, Tile.DEEPSLATE, Tile.DEEPSLATE_TOP);
        uniform(BlockType.COBBLED_DEEPSLATE, Tile.COBBLED_DEEPSLATE);
        uniform(BlockType.DEEPSLATE_IRON_ORE, Tile.DEEPSLATE_IRON_ORE);
        uniform(BlockType.GRANITE, Tile.GRANITE);
        put(BlockType.MYCELIUM, Tile.MYCELIUM_TOP, Tile.MYCELIUM_SIDE, Tile.DIRT);
        put(BlockType.PODZOL, Tile.PODZOL_TOP, Tile.PODZOL_SIDE, Tile.DIRT);
        uniform(BlockType.POWDER_SNOW, Tile.POWDER_SNOW);
        uniform(BlockType.PACKED_ICE, Tile.PACKED_ICE);
        uniform(BlockType.RAW_COPPER_BLOCK, Tile.RAW_COPPER_BLOCK);
        uniform(BlockType.RAW_IRON_BLOCK, Tile.RAW_IRON_BLOCK);
        put(BlockType.RED_SANDSTONE, Tile.RED_SANDSTONE_TOP, Tile.RED_SANDSTONE_SIDE, Tile.RED_SANDSTONE_BOTTOM);
        uniform(BlockType.SULFUR, Tile.SULFUR);
        uniform(BlockType.TUFF, Tile.TUFF);
        uniform(BlockType.WHITE_TERRACOTTA, Tile.WHITE_TERRACOTTA);
        uniform(BlockType.ORANGE_TERRACOTTA, Tile.ORANGE_TERRACOTTA);
        uniform(BlockType.YELLOW_TERRACOTTA, Tile.YELLOW_TERRACOTTA);
        uniform(BlockType.BROWN_TERRACOTTA, Tile.BROWN_TERRACOTTA);
        uniform(BlockType.RED_TERRACOTTA, Tile.RED_TERRACOTTA);
        uniform(BlockType.LIGHT_GRAY_TERRACOTTA, Tile.LIGHT_GRAY_TERRACOTTA);

        // C1 feature blocks: cube_all models; infested stone and infested deepslate use the stone and deepslate models (their
        // blockstates' random mirrored/rotated variants are not drawn, as for stone and deepslate).
        uniform(BlockType.ANDESITE, Tile.ANDESITE);
        uniform(BlockType.DIORITE, Tile.DIORITE);
        uniform(BlockType.DEEPSLATE_COAL_ORE, Tile.DEEPSLATE_COAL_ORE);
        uniform(BlockType.DEEPSLATE_COPPER_ORE, Tile.DEEPSLATE_COPPER_ORE);
        uniform(BlockType.DEEPSLATE_DIAMOND_ORE, Tile.DEEPSLATE_DIAMOND_ORE);
        uniform(BlockType.DEEPSLATE_EMERALD_ORE, Tile.DEEPSLATE_EMERALD_ORE);
        uniform(BlockType.DEEPSLATE_GOLD_ORE, Tile.DEEPSLATE_GOLD_ORE);
        uniform(BlockType.DEEPSLATE_LAPIS_ORE, Tile.DEEPSLATE_LAPIS_ORE);
        uniform(BlockType.DEEPSLATE_REDSTONE_ORE, Tile.DEEPSLATE_REDSTONE_ORE);
        uniform(BlockType.EMERALD_ORE, Tile.EMERALD_ORE);
        uniform(BlockType.LAPIS_ORE, Tile.LAPIS_ORE);
        uniform(BlockType.REDSTONE_ORE, Tile.REDSTONE_ORE);
        uniform(BlockType.INFESTED_STONE, Tile.STONE);
        put(BlockType.INFESTED_DEEPSLATE, Tile.DEEPSLATE_TOP, Tile.DEEPSLATE, Tile.DEEPSLATE_TOP);
        uniform(BlockType.MAGMA_BLOCK, Tile.MAGMA);
        uniform(BlockType.LAPIS_LAZULI, Tile.LAPIS_LAZULI);
        uniform(BlockType.REDSTONE, Tile.REDSTONE);
        uniform(BlockType.EMERALD, Tile.EMERALD);
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
