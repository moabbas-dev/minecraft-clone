package com.example.world;

import java.util.Random;

/**
 * The tree kinds this world grows, one per real trunk/leaf block pair this project has textures for, plus SWAMP_OAK (oak blocks, the
 * swamp's own tree). The shapes are not made up here: {@link TreePlanner} runs the 26.3 client's own tree algorithm (trunk placers and
 * foliage placers) for each kind, with the numbers of its configured features.
 *
 * <p>{@code canopyRadius} is only the spacing measure between trees (about how far a typical tree reaches sideways, trunk to leaf tip),
 * not a limit on the shape: the real extent comes from the plan, and a tree whose plan does not fit inside its chunk is simply not
 * placed - so a tree is never cut off at a chunk border. {@code maxTotalHeight} is how tall the tallest tree
 * of the kind can get, used for the world-height check.
 *
 * <p>Trunk colour is the kind's own log texture. Foliage takes the per-column biome tint, except SPRUCE and CHERRY, which have fixed
 * colours (see ChunkMeshBuilder).
 */
public enum TreeSpecies {
    OAK(BlockType.OAK_LOG, BlockType.OAK_LEAVES, 2, 8, 5),
    BIRCH(BlockType.BIRCH_LOG, BlockType.BIRCH_LEAVES, 2, 9, 4),
    SPRUCE(BlockType.SPRUCE_LOG, BlockType.SPRUCE_LEAVES, 3, 12, 6),
    JUNGLE(BlockType.JUNGLE_LOG, BlockType.JUNGLE_LEAVES, 2, 14, 10),
    ACACIA(BlockType.ACACIA_LOG, BlockType.ACACIA_LEAVES, 4, 12, 9),
    SWAMP_OAK(BlockType.OAK_LOG, BlockType.OAK_LEAVES, 3, 10, 7),
    MANGROVE(BlockType.MANGROVE_LOG, BlockType.MANGROVE_LEAVES, 5, 28, 8),
    DARK_OAK(BlockType.DARK_OAK_LOG, BlockType.DARK_OAK_LEAVES, 4, 13, 9),
    CHERRY(BlockType.CHERRY_LOG, BlockType.CHERRY_LEAVES, 5, 12, 6);

    public final BlockType log;
    public final BlockType leaves;
    /** About how far a typical tree of this kind reaches sideways from its trunk: the spacing measure, not a limit. */
    public final int canopyRadius;
    /** The tallest a tree of this kind can be, trunk and foliage, counted from the first free block above the ground. */
    public final int maxTotalHeight;
    /** Minimum distance (blocks, trunk to trunk) from any other tree already placed in the same chunk. */
    public final int minSpacing;

    TreeSpecies(BlockType log, BlockType leaves, int canopyRadius, int maxTotalHeight, int minSpacing) {
        this.log = log;
        this.leaves = leaves;
        this.canopyRadius = canopyRadius;
        this.maxTotalHeight = maxTotalHeight;
        this.minSpacing = minSpacing;
    }

    /** Tallest this kind can ever grow - used for the headroom/world-height check before generation starts. */
    public int maxTotalHeight() {
        return maxTotalHeight;
    }

    /**
     * Plans this kind's tree rooted at (x, baseY, z) - baseY is the first air block above the ground, chunk-local coordinates - and
     * writes it, dropping whatever falls outside the chunk. The same Random state gives the same tree.
     */
    public void place(Chunk chunk, int x, int baseY, int z, Random rnd) {
        TreePlanner.plan(this, chunk, x, baseY, z, rnd).commit(chunk);
    }
}
