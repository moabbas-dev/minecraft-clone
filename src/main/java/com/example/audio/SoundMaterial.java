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
                BlockType.GLOWSTONE, BlockType.TERRACOTTA, BlockType.ICE, BlockType.TNT
        }) {
            BY_BLOCK.put(b, STONE);
        }

        // --- Gravel family: DIRT/MUD/CLAY/GRAVEL - "packed earth" is closer to gravel's texture
        // than to grass or stone in this asset set; an explicit approximation, not verified vanilla. ---
        for (BlockType b : new BlockType[]{BlockType.DIRT, BlockType.MUD, BlockType.CLAY, BlockType.GRAVEL}) {
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
                BlockType.PUMPKIN, BlockType.MANGROVE_ROOTS
        }) {
            BY_BLOCK.put(b, WOOD);
        }

        BY_BLOCK.put(BlockType.GLASS, GLASS);
        // AIR, WATER, and every tool/STICK (see ToolEffectiveness): never targeted for
        // break/place, and never solid-collision ground for footsteps either - deliberately
        // left unmapped, materialFor() returns null for all of them.
    }

    /** Null for AIR/WATER/anything never solid-collision-walkable or player-breakable. */
    public static SoundMaterial materialFor(BlockType type) {
        return BY_BLOCK.get(type);
    }
}
