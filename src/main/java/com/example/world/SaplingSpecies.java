package com.example.world;

import java.util.Set;

/**
 * The reusable mapping between a plantable sapling and the tree it grows into - one entry per
 * species today (OAK only, per this round's explicit scope), extensible later by adding another
 * entry with its own 4 stage blocks, {@link TreeSpecies}, and valid-ground set, the same way
 * {@link TreeSpecies} already grows by adding enum entries in this codebase.
 */
public enum SaplingSpecies {
    OAK(BlockType.OAK_SAPLING_STAGE_0, BlockType.OAK_SAPLING_STAGE_1,
            BlockType.OAK_SAPLING_STAGE_2, BlockType.OAK_SAPLING_STAGE_3,
            TreeSpecies.OAK, Set.of(BlockType.GRASS_BLOCK, BlockType.DIRT));

    public final BlockType stage0, stage1, stage2, stage3;
    public final TreeSpecies treeSpecies;
    /** Ground blocks this species can be planted on - this project's actual block set only,
     *  never an invented grass/podzol/mud variant beyond what {@link BlockType} already has. */
    public final Set<BlockType> validGround;

    SaplingSpecies(BlockType stage0, BlockType stage1, BlockType stage2, BlockType stage3,
                   TreeSpecies treeSpecies, Set<BlockType> validGround) {
        this.stage0 = stage0;
        this.stage1 = stage1;
        this.stage2 = stage2;
        this.stage3 = stage3;
        this.treeSpecies = treeSpecies;
        this.validGround = validGround;
    }

    /** -1 if {@code type} isn't one of this species' 4 stage blocks. */
    public int stageIndex(BlockType type) {
        if (type == stage0) return 0;
        if (type == stage1) return 1;
        if (type == stage2) return 2;
        if (type == stage3) return 3;
        return -1;
    }

    public BlockType stageBlock(int index) {
        return switch (index) {
            case 0 -> stage0;
            case 1 -> stage1;
            case 2 -> stage2;
            case 3 -> stage3;
            default -> throw new IllegalArgumentException("invalid sapling stage index: " + index);
        };
    }

    /** Which species (if any) {@code type} is a sapling block of, at any stage. */
    public static SaplingSpecies bySaplingBlock(BlockType type) {
        for (SaplingSpecies s : values()) {
            if (s.stageIndex(type) >= 0) return s;
        }
        return null;
    }
}
