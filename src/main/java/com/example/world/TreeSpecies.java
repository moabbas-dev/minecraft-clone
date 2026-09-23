package com.example.world;

import java.util.Random;

/**
 * One tree species per real trunk/leaf block pair this project has textures for, plus one
 * shape-only variant (SWAMP_OAK, reusing the oak blocks - real vanilla also calls swamp trees
 * "oak variants," not a separate species). Each entry owns its own dimension ranges and its own
 * shape-generator method - the "TreeGenerationProfile" the request describes, sized to what a
 * block-based voxel tree actually needs: no branch geometry beyond simple trunk+canopy shapes,
 * since this engine has no non-cube geometry to build a branch out of.
 *
 * Trunk color is always the species' own real log texture - never tinted (see
 * ChunkMeshBuilder, which only ever tints LEAVES faces). Foliage color is the existing
 * per-column biome-blended tint for every species except {@link #SPRUCE}, which vanilla itself
 * gives a fixed color rather than colormap-sampling (see ChunkMeshBuilder's isSpruceLeaves
 * branch) - a real, verifiable vanilla special case, not an invented one.
 */
public enum TreeSpecies {
    /** Unchanged from before this round - the original shape, now one of several. */
    OAK(BlockType.OAK_LOG, BlockType.OAK_LEAVES, 4, 6, 2, 5),
    /** Taller and slimmer than oak - a narrower canopy, no wide base layer. */
    BIRCH(BlockType.BIRCH_LOG, BlockType.BIRCH_LEAVES, 5, 8, 1, 4),
    /** Conical: tapers from a wide base ring to a single point at the top - never a broad
     *  oak-like canopy, matching the request's explicit "avoid broad canopies for spruce." */
    SPRUCE(BlockType.SPRUCE_LOG, BlockType.SPRUCE_LEAVES, 6, 10, 2, 6),
    /** Tall trunk, large canopy - needs the widest chunk-interior margin of any species. */
    JUNGLE(BlockType.JUNGLE_LOG, BlockType.JUNGLE_LEAVES, 8, 13, 3, 10),
    /** Short trunk, wide flat umbrella canopy sitting only at the very top. */
    ACACIA(BlockType.ACACIA_LOG, BlockType.ACACIA_LEAVES, 3, 5, 3, 9),
    /** Short and wide with an irregular, asymmetric canopy - a shape variant of oak, not a new
     *  species/block pair (see class docs). */
    SWAMP_OAK(BlockType.OAK_LOG, BlockType.OAK_LEAVES, 3, 5, 3, 7),
    /** Same low, wide, irregular shape as SWAMP_OAK (real mangrove roots/multi-trunk geometry
     *  is out of scope this round - see the plan's explicit deferral) but with real mangrove
     *  blocks, not oak - this project's asset dump has real mangrove_log/mangrove_leaves. */
    MANGROVE(BlockType.MANGROVE_LOG, BlockType.MANGROVE_LEAVES, 3, 5, 3, 7);

    public final BlockType log;
    public final BlockType leaves;
    public final int minTrunkHeight;
    public final int maxTrunkHeight;
    /** Horizontal reach of the widest canopy layer - used both for the shape itself and for the
     *  chunk-interior-margin / minimum-spacing checks in TerrainGenerator. */
    public final int canopyRadius;
    /** Minimum distance (in blocks, trunk to trunk) required from any other already-placed tree
     *  in the same chunk. No "maximum spacing" constant - this architecture drives spacing from
     *  density + rejection sampling, not a grid, so an upper bound isn't a meaningful knob here
     *  (documented per the plan rather than added as an unused field). */
    public final int minSpacing;

    TreeSpecies(BlockType log, BlockType leaves, int minTrunkHeight, int maxTrunkHeight, int canopyRadius, int minSpacing) {
        this.log = log;
        this.leaves = leaves;
        this.minTrunkHeight = minTrunkHeight;
        this.maxTrunkHeight = maxTrunkHeight;
        this.canopyRadius = canopyRadius;
        this.minSpacing = minSpacing;
    }

    /** Tallest this species can ever grow - used for the headroom/world-height check before generation starts. */
    public int maxTotalHeight() {
        return maxTrunkHeight + 3;
    }

    /**
     * Builds this species' shape rooted at (x, baseY, z) - baseY is the first air block above
     * the ground. Trunk height and minor shape variation are drawn from the given per-chunk
     * Random, so two calls with the same Random state produce the same tree (determinism is the
     * caller's responsibility via a consistently-seeded Random, not this method's).
     */
    public void place(Chunk chunk, int x, int baseY, int z, Random rnd) {
        int trunkHeight = minTrunkHeight + rnd.nextInt(maxTrunkHeight - minTrunkHeight + 1);
        switch (this) {
            case SPRUCE -> placeSpruce(chunk, x, baseY, z, trunkHeight);
            case BIRCH -> placeBirch(chunk, x, baseY, z, trunkHeight);
            case JUNGLE -> placeJungle(chunk, x, baseY, z, trunkHeight);
            case ACACIA -> placeAcacia(chunk, x, baseY, z, trunkHeight);
            case SWAMP_OAK, MANGROVE -> placeSwampOak(chunk, x, baseY, z, trunkHeight, rnd);
            default -> placeOak(chunk, x, baseY, z, trunkHeight);
        }
    }

    private void trunk(Chunk chunk, int x, int baseY, int z, int height) {
        for (int dy = 0; dy < height; dy++) {
            setIfInBounds(chunk, x, baseY + dy, z, log);
        }
    }

    private void placeOak(Chunk chunk, int x, int baseY, int z, int trunkHeight) {
        trunk(chunk, x, baseY, z, trunkHeight);
        int top = baseY + trunkHeight - 1;
        // Two wide leaf layers (radius 2, corners trimmed), then a narrow cap (radius 1, plus-shape).
        for (int dy = -2; dy <= -1; dy++) {
            int ly = top + dy + 1;
            for (int lx = -2; lx <= 2; lx++) {
                for (int lz = -2; lz <= 2; lz++) {
                    if (Math.abs(lx) == 2 && Math.abs(lz) == 2) continue;
                    if (lx == 0 && lz == 0 && dy != -2) continue;
                    leaf(chunk, x + lx, ly, z + lz);
                }
            }
        }
        for (int dy = 0; dy <= 1; dy++) {
            int ly = top + dy + 1;
            for (int lx = -1; lx <= 1; lx++) {
                for (int lz = -1; lz <= 1; lz++) {
                    if (dy == 1 && lx != 0 && lz != 0) continue;
                    if (lx == 0 && lz == 0 && dy == 0) continue;
                    leaf(chunk, x + lx, ly, z + lz);
                }
            }
        }
    }

    private void placeBirch(Chunk chunk, int x, int baseY, int z, int trunkHeight) {
        trunk(chunk, x, baseY, z, trunkHeight);
        int top = baseY + trunkHeight - 1;
        // Narrow throughout - radius 1 for two layers, a single-block cap. No radius-2 layer at
        // all, which is what actually reads as "slimmer than oak" rather than just shorter.
        for (int dy = -1; dy <= 0; dy++) {
            int ly = top + dy + 1;
            for (int lx = -1; lx <= 1; lx++) {
                for (int lz = -1; lz <= 1; lz++) {
                    if (lx == 0 && lz == 0 && dy == 0) continue;
                    leaf(chunk, x + lx, ly, z + lz);
                }
            }
        }
        leaf(chunk, x, top + 2, z);
    }

    private void placeSpruce(Chunk chunk, int x, int baseY, int z, int trunkHeight) {
        trunk(chunk, x, baseY, z, trunkHeight);
        int top = baseY + trunkHeight - 1;
        // Conical: radius steps down from canopyRadius to 0 as it rises - a genuinely different
        // silhouette from oak/birch's roughly-spherical canopies, not just a smaller version.
        int layers = 5;
        for (int i = 0; i < layers; i++) {
            int ly = top - (layers - 1) + i + 1;
            int radius = Math.max(0, canopyRadius - (i / 2));
            if (i == layers - 1) radius = 0; // single-point tip
            for (int lx = -radius; lx <= radius; lx++) {
                for (int lz = -radius; lz <= radius; lz++) {
                    if (radius > 1 && Math.abs(lx) == radius && Math.abs(lz) == radius) continue; // trim corners
                    leaf(chunk, x + lx, ly, z + lz);
                }
            }
        }
    }

    private void placeJungle(Chunk chunk, int x, int baseY, int z, int trunkHeight) {
        trunk(chunk, x, baseY, z, trunkHeight);
        int top = baseY + trunkHeight - 1;
        // Large, mostly-flat canopy sitting on a tall trunk - two broad layers plus a smaller cap.
        for (int dy = -2; dy <= -1; dy++) {
            int ly = top + dy + 1;
            int radius = canopyRadius - (dy == -2 ? 0 : 1);
            for (int lx = -radius; lx <= radius; lx++) {
                for (int lz = -radius; lz <= radius; lz++) {
                    if (Math.abs(lx) == radius && Math.abs(lz) == radius) continue;
                    leaf(chunk, x + lx, ly, z + lz);
                }
            }
        }
        for (int lx = -1; lx <= 1; lx++) {
            for (int lz = -1; lz <= 1; lz++) {
                if (lx == 0 && lz == 0) continue;
                leaf(chunk, x + lx, top + 1, z + lz);
            }
        }
    }

    private void placeAcacia(Chunk chunk, int x, int baseY, int z, int trunkHeight) {
        trunk(chunk, x, baseY, z, trunkHeight);
        int top = baseY + trunkHeight - 1;
        // Wide, flat, thin umbrella at the very top only - short trunk beneath, matching the
        // request's "wide flat canopy, greater distance between trees" savanna description.
        int ly = top + 1;
        for (int lx = -canopyRadius; lx <= canopyRadius; lx++) {
            for (int lz = -canopyRadius; lz <= canopyRadius; lz++) {
                int dist = Math.abs(lx) + Math.abs(lz);
                if (dist > canopyRadius + 1) continue; // roughly circular, not a full square
                leaf(chunk, x + lx, ly, z + lz);
            }
        }
    }

    private void placeSwampOak(Chunk chunk, int x, int baseY, int z, int trunkHeight, Random rnd) {
        trunk(chunk, x, baseY, z, trunkHeight);
        int top = baseY + trunkHeight - 1;
        // Wide and low with an irregular (randomly notched) outline, sitting close to the
        // ground - "irregular and wide canopies," per the request's swamp description.
        for (int dy = -1; dy <= 0; dy++) {
            int ly = top + dy + 1;
            for (int lx = -canopyRadius; lx <= canopyRadius; lx++) {
                for (int lz = -canopyRadius; lz <= canopyRadius; lz++) {
                    if (Math.abs(lx) == canopyRadius && Math.abs(lz) == canopyRadius && rnd.nextBoolean()) continue;
                    leaf(chunk, x + lx, ly, z + lz);
                }
            }
        }
    }

    private void leaf(Chunk chunk, int x, int y, int z) {
        if (x < 0 || x >= Chunk.SIZE_X || z < 0 || z >= Chunk.SIZE_Z || y < 0 || y >= Chunk.SIZE_Y) return;
        if (chunk.getLocal(x, y, z).isAir()) {
            chunk.setLocal(x, y, z, leaves);
        }
    }

    private void setIfInBounds(Chunk chunk, int x, int y, int z, BlockType type) {
        if (x < 0 || x >= Chunk.SIZE_X || z < 0 || z >= Chunk.SIZE_Z || y < 0 || y >= Chunk.SIZE_Y) return;
        chunk.setLocal(x, y, z, type);
    }
}
