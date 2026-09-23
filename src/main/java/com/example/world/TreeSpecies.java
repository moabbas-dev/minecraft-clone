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
    /** Same low, wide, irregular canopy as SWAMP_OAK, with real mangrove blocks - now also grows
     *  a small root skirt toward any adjacent shoreline water (see {@link #placeMangrove}), this
     *  round's initial version of the request's "roots"/"water-adjacent placement" requirement.
     *  Full over-water placement (relaxing the shared height&lt;=SEA_LEVEL suitability gate every
     *  species uses) is explicitly deferred - documented in the round's final report. */
    MANGROVE(BlockType.MANGROVE_LOG, BlockType.MANGROVE_LEAVES, 3, 5, 3, 7),
    /** Real vanilla's one 2x2-trunk species - the actual structural differentiator that makes a
     *  "thick trunk, broad canopy" reading distinct from just a wider single-block oak. The 2x2
     *  footprint is safe within the shared TerrainGenerator suitability check unchanged: canopyRadius
     *  4 already reserves far more chunk-interior margin than the 1-block trunk extension needs. */
    DARK_OAK(BlockType.DARK_OAK_LOG, BlockType.DARK_OAK_LEAVES, 5, 7, 4, 9),
    /** Rounded (circular-distance, not square) canopy, real cherry blocks - foliage uses a fixed
     *  pink tint rather than the per-column biome blend (see ChunkMeshBuilder's isCherryLeaves
     *  branch), the same real vanilla special case Spruce already gets here, so cherry pink can
     *  never be replaced by a generic biome green the way the request explicitly warns against. */
    CHERRY(BlockType.CHERRY_LOG, BlockType.CHERRY_LEAVES, 4, 6, 3, 6);

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
            case SWAMP_OAK -> placeSwampOak(chunk, x, baseY, z, trunkHeight, rnd);
            case MANGROVE -> placeMangrove(chunk, x, baseY, z, trunkHeight, rnd);
            case DARK_OAK -> placeDarkOak(chunk, x, baseY, z, trunkHeight, rnd);
            case CHERRY -> placeCherry(chunk, x, baseY, z, trunkHeight);
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

    /** SWAMP_OAK's own canopy shape plus a root skirt reaching toward adjacent shoreline water -
     *  see the class docs on MANGROVE. Only ever replaces WATER or AIR in a neighboring column
     *  (never the trunk's own solid ground, never another tree's already-placed blocks), and
     *  every root is laterally adjacent to the trunk it grew from - never a disconnected floater. */
    private void placeMangrove(Chunk chunk, int x, int baseY, int z, int trunkHeight, Random rnd) {
        placeSwampOak(chunk, x, baseY, z, trunkHeight, rnd);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : dirs) {
            int nx = x + d[0], nz = z + d[1];
            if (nx < 0 || nx >= Chunk.SIZE_X || nz < 0 || nz >= Chunk.SIZE_Z) continue;
            boolean shoreline = chunk.getLocal(nx, baseY - 1, nz) == BlockType.WATER;
            if (!shoreline || !rnd.nextBoolean()) continue;
            rootIfReplaceable(chunk, nx, baseY - 1, nz);
            rootIfReplaceable(chunk, nx, baseY, nz);
        }
    }

    private void rootIfReplaceable(Chunk chunk, int x, int y, int z) {
        BlockType existing = chunk.getLocal(x, y, z);
        if (existing.isAir() || existing == BlockType.WATER) {
            chunk.setLocal(x, y, z, BlockType.MANGROVE_ROOTS);
        }
    }

    /** Real vanilla's 2x2 trunk (this project's only multi-block-footprint trunk) topped with a
     *  wide, layered canopy - see the class docs on DARK_OAK. */
    private void placeDarkOak(Chunk chunk, int x, int baseY, int z, int trunkHeight, Random rnd) {
        for (int dy = 0; dy < trunkHeight; dy++) {
            setIfInBounds(chunk, x, baseY + dy, z, log);
            setIfInBounds(chunk, x + 1, baseY + dy, z, log);
            setIfInBounds(chunk, x, baseY + dy, z + 1, log);
            setIfInBounds(chunk, x + 1, baseY + dy, z + 1, log);
        }
        int top = baseY + trunkHeight - 1;
        // Two broad layers spanning the full 2x2 trunk footprint plus canopyRadius, then a
        // smaller cap - wide and thick, not just tall, the request's "large and wide canopies."
        // Bounded to exactly canopyRadius on every side (same convention as every other species,
        // and what TerrainGenerator.fitsAndIsSuitable actually reserves chunk-interior margin
        // for) - the second trunk column (x+1) sits inside this same canopy, not centered under
        // it, which still reads as "thick trunk under a wide canopy" without risking a canopy
        // that reaches past the margin the suitability check promised.
        for (int dy = -1; dy <= 0; dy++) {
            int ly = top + dy + 1;
            int r = canopyRadius - (dy == -1 ? 0 : 1);
            for (int lx = -r; lx <= r; lx++) {
                for (int lz = -r; lz <= r; lz++) {
                    if (Math.abs(lx) == r && Math.abs(lz) == r && rnd.nextBoolean()) continue;
                    leaf(chunk, x + lx, ly, z + lz);
                }
            }
        }
        for (int lx = 0; lx <= 1; lx++) {
            for (int lz = 0; lz <= 1; lz++) {
                leaf(chunk, x + lx, top + 1, z + lz);
            }
        }
    }

    /** Rounded (circular, not square) canopy using real cherry blocks - see the class docs on
     *  CHERRY. Genuinely spherical rather than square-trimmed, matching "rounded canopy." */
    private void placeCherry(Chunk chunk, int x, int baseY, int z, int trunkHeight) {
        trunk(chunk, x, baseY, z, trunkHeight);
        int top = baseY + trunkHeight - 1;
        int layers = 3;
        for (int i = 0; i < layers; i++) {
            int ly = top - 1 + i + 1;
            double radius = canopyRadius - Math.abs(i - (layers - 1) / 2.0) * 0.5;
            int r = (int) Math.round(radius);
            for (int lx = -r; lx <= r; lx++) {
                for (int lz = -r; lz <= r; lz++) {
                    if (Math.sqrt(lx * lx + lz * lz) > radius + 0.5) continue; // circular, not square
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
