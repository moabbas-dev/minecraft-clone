package com.example.audio;

import com.example.world.BlockType;

import java.util.EnumMap;
import java.util.Map;

/**
 * Groups {@link BlockType}s into the small set of footstep/dig/place sound families this
 * project actually has real assets for (see {@code sounds/dig}, {@code sounds/step}) - not one
 * sound per block ID. Mirrors {@code TextureAtlas.REAL_TEXTURE_PATHS}' pattern of an explicit
 * static mapping table rather than a computed/inferred classification.
 *
 * Real vanilla-adjacent choices (grass-family leaves/saplings, stone-family ores/bricks/
 * sandstone) are noted as such; anything without a genuinely close asset (dirt/mud/clay,
 * pumpkin, TNT, ice) is an explicit project-specific approximation, not a verified vanilla
 * sound mapping - documented per block below rather than silently guessed.
 */
public enum SoundMaterial {
    STONE, GRAVEL, GRASS, SAND, SNOW, WOOD, GLASS;

    private static final Map<BlockType, SoundMaterial> BY_BLOCK = new EnumMap<>(BlockType.class);

    static {
        // --- Stone family: real ore/brick/stone-adjacent blocks, plus a few approximations
        // (ICE, TNT, GLOWSTONE, NETHERRACK, TERRACOTTA) that have no closer available category. ---
        for (BlockType b : new BlockType[]{
                BlockType.STONE, BlockType.COBBLESTONE, BlockType.BEDROCK, BlockType.GOLD_ORE,
                BlockType.IRON_ORE, BlockType.COAL_ORE, BlockType.DIAMOND_ORE, BlockType.SANDSTONE,
                BlockType.GOLD_BLOCK, BlockType.IRON_BLOCK, BlockType.BRICKS, BlockType.MOSSY_COBBLESTONE,
                BlockType.OBSIDIAN, BlockType.DIAMOND_BLOCK, BlockType.FURNACE, BlockType.NETHERRACK,
                BlockType.GLOWSTONE, BlockType.TERRACOTTA, BlockType.ICE, BlockType.TNT,
                BlockType.BLAST_FURNACE, BlockType.SMOKER, BlockType.COPPER_ORE, BlockType.SMOOTH_STONE
        }) {
            BY_BLOCK.put(b, STONE);
        }

        // --- Gravel family: DIRT/MUD/CLAY/GRAVEL - "packed earth" is closer to gravel's texture
        // than to grass or stone in this asset set; an explicit approximation, not verified vanilla. ---
        for (BlockType b : new BlockType[]{BlockType.DIRT, BlockType.MUD, BlockType.CLAY, BlockType.GRAVEL, BlockType.FARMLAND, BlockType.FARMLAND_1, BlockType.FARMLAND_2, BlockType.FARMLAND_3,
                BlockType.FARMLAND_4, BlockType.FARMLAND_5, BlockType.FARMLAND_6, BlockType.FARMLAND_MOIST}) {
            BY_BLOCK.put(b, GRAVEL);
        }

        BY_BLOCK.put(BlockType.GRASS_BLOCK, GRASS);
        // Leaves/saplings use the grass family - real vanilla's own actual convention.
        for (BlockType b : new BlockType[]{
                BlockType.OAK_LEAVES, BlockType.BIRCH_LEAVES, BlockType.SPRUCE_LEAVES, BlockType.JUNGLE_LEAVES,
                BlockType.ACACIA_LEAVES, BlockType.MANGROVE_LEAVES, BlockType.DARK_OAK_LEAVES, BlockType.CHERRY_LEAVES,
                BlockType.OAK_SAPLING_STAGE_0, BlockType.OAK_SAPLING_STAGE_1, BlockType.OAK_SAPLING_STAGE_2, BlockType.OAK_SAPLING_STAGE_3
        }) {
            BY_BLOCK.put(b, GRASS);
        }

        BY_BLOCK.put(BlockType.SAND, SAND);
        BY_BLOCK.put(BlockType.RED_SAND, SAND);
        BY_BLOCK.put(BlockType.SNOW_BLOCK, SNOW);

        // --- Wood family: planks/logs plus wood-furniture blocks and PUMPKIN/MANGROVE_ROOTS
        // (no closer category available for either of those last two - approximations). ---
        for (BlockType b : new BlockType[]{
                BlockType.OAK_PLANKS, BlockType.OAK_LOG, BlockType.BIRCH_LOG, BlockType.SPRUCE_LOG,
                BlockType.JUNGLE_LOG, BlockType.ACACIA_LOG, BlockType.MANGROVE_LOG, BlockType.DARK_OAK_LOG,
                BlockType.CHERRY_LOG, BlockType.CRAFTING_TABLE, BlockType.BOOKSHELF, BlockType.CHEST,
                BlockType.PUMPKIN, BlockType.MANGROVE_ROOTS,
                BlockType.TORCH, BlockType.WALL_TORCH_EAST, BlockType.WALL_TORCH_WEST, BlockType.WALL_TORCH_NORTH, BlockType.WALL_TORCH_SOUTH
        }) {
            BY_BLOCK.put(b, WOOD);
        }

        BY_BLOCK.put(BlockType.GLASS, GLASS);

        // G28 terrain blocks whose 26.3 Properties name a SoundType this enum has assets for (Blocks static initializer;
        // no .sound(...) call means SoundType.STONE). Their own vanilla sound types are exact here, not approximations.
        for (BlockType b : new BlockType[]{BlockType.GRANITE, BlockType.RAW_COPPER_BLOCK, BlockType.RAW_IRON_BLOCK,
                BlockType.RED_SANDSTONE, BlockType.WHITE_TERRACOTTA, BlockType.ORANGE_TERRACOTTA, BlockType.YELLOW_TERRACOTTA,
                BlockType.BROWN_TERRACOTTA, BlockType.RED_TERRACOTTA, BlockType.LIGHT_GRAY_TERRACOTTA}) {
            BY_BLOCK.put(b, STONE);
        }
        BY_BLOCK.put(BlockType.COARSE_DIRT, GRAVEL);
        BY_BLOCK.put(BlockType.PODZOL, GRAVEL);
        BY_BLOCK.put(BlockType.MYCELIUM, GRASS);
        BY_BLOCK.put(BlockType.PACKED_ICE, GLASS);
        // AIR, WATER, and every tool/STICK/food item (see ToolEffectiveness/FoodRegistry): never targeted for
        // break/place, and never solid-collision ground for footsteps either - deliberately
        // left unmapped, materialFor() returns null for all of them.
    }

    /**
     * Blocks whose 26.3 SoundType (CALCITE, CINNABAR, DEEPSLATE, SULFUR, TUFF, POWDER_SNOW) has its own sound files that are
     * not in this project's {@code sounds/} folder. They are left silent (materialFor returns null) rather than given
     * another block's sound; docs/PARITY.md section 28 lists them.
     */
    public static final java.util.Set<BlockType> MISSING_VANILLA_SOUND = java.util.EnumSet.of(
            BlockType.CALCITE, BlockType.CINNABAR, BlockType.DEEPSLATE, BlockType.COBBLED_DEEPSLATE,
            BlockType.DEEPSLATE_IRON_ORE, BlockType.SULFUR, BlockType.TUFF, BlockType.POWDER_SNOW);

    /** Null for AIR/WATER/anything never solid-collision-walkable or player-breakable, and for {@link #MISSING_VANILLA_SOUND}. */
    public static SoundMaterial materialFor(BlockType type) {
        return BY_BLOCK.get(type);
    }
}
