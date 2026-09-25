package com.example.world;

import java.util.Random;

/**
 * One tree species per real trunk/leaf block pair this project has textures for, plus one
 * shape-only variant (SWAMP_OAK, reusing the oak blocks - real vanilla also calls swamp trees
 * "oak variants," not a separate species). Each entry owns its own dimension ranges and its own
 * shape-generator method - the "TreeGenerationProfile" the request describes, sized to what a
 * block-based voxel tree actually needs. Branch geometry (this project's first) is opt-in per
 * species rather than a shared mechanism every species must use - see {@link #DARK_OAK} and
 * {@link #placeDarkOakBranches}; every other species still generates trunk+canopy only.
 *
 * Trunk color is always the species' own real log texture - never tinted (see
 * ChunkMeshBuilder, which only ever tints LEAVES faces). Foliage color is the existing
 * per-column biome-blended tint for every species except {@link #SPRUCE}, which vanilla itself
 * gives a fixed color rather than colormap-sampling (see ChunkMeshBuilder's isSpruceLeaves
 * branch) - a real, verifiable vanilla special case, not an invented one.
 */
public enum TreeSpecies {
    /** The reference shape - deliberately unchanged by the canopy rework (a test pins its exact
     *  block set). maxBlockCount 80: measured max 59 across 1000 seeded trials. */
    OAK(BlockType.OAK_LOG, BlockType.OAK_LEAVES, 4, 6, 1, 2, 4, CanopyShape.ROUND_LAYERED, 5, 80),
    /** Tall, slim oval canopy (7 rows, at most 5 wide) over a bare trunk.
     *  maxBlockCount 100: measured max 84 across 1000 seeded trials. */
    BIRCH(BlockType.BIRCH_LOG, BlockType.BIRCH_LEAVES, 7, 10, 1, 3, 7, CanopyShape.TALL_OVAL, 4, 100),
    /** Tiered conifer: pointed tip, shelves widening to canopyRadius at the bottom.
     *  maxBlockCount 420: measured max 369 across 1000 seeded trials. */
    SPRUCE(BlockType.SPRUCE_LOG, BlockType.SPRUCE_LEAVES, 8, 12, 1, 4, 10, CanopyShape.CONICAL, 6, 420),
    /** Tall trunk, broad top crown plus log branches ending in leaf blobs.
     *  maxBlockCount 450: measured max 395 across 1000 seeded trials. */
    JUNGLE(BlockType.JUNGLE_LOG, BlockType.JUNGLE_LEAVES, 9, 14, 1, 5, 9, CanopyShape.BRANCH_CLUSTERS, 10, 450),
    /** Bent/forked trunk carrying broad, shallow, offset leaf platforms.
     *  maxBlockCount 130: measured max 111 across 1000 seeded trials. */
    ACACIA(BlockType.ACACIA_LOG, BlockType.ACACIA_LEAVES, 4, 5, 1, 5, 4, CanopyShape.FORKED_UMBRELLA, 9, 130),
    /** Low, wide, lumpy lobes with one drooping side; a shape variant of oak (same blocks).
     *  maxBlockCount 160: measured max 138 across 1000 seeded trials. */
    SWAMP_OAK(BlockType.OAK_LOG, BlockType.OAK_LEAVES, 4, 6, 1, 4, 4, CanopyShape.IRREGULAR_LOW, 7, 160),
    /** Branch-driven sprawling crown with hanging strands, plus a root skirt toward any adjacent
     *  shoreline water (see {@link #placeMangrove}). Full over-water placement (relaxing the shared
     *  height&lt;=SEA_LEVEL suitability gate every species uses) is still deferred.
     *  maxBlockCount 290 (canopy+roots): measured max 248 across 1000 seeded trials with an
     *  adjacent shoreline present on every side. */
    MANGROVE(BlockType.MANGROVE_LOG, BlockType.MANGROVE_LEAVES, 4, 6, 1, 5, 6, CanopyShape.BRANCH_SPRAWL, 8, 290),
    /** Real vanilla's one 2x2-trunk species: thick trunk, heavy multi-lobed crown centred on the
     *  trunk, and the project's original cardinal branches (see {@link #placeDarkOakBranches}).
     *  canopyRadius 4 covers the crown, lobes and branch puffs from the (x, z) anchor.
     *  maxBlockCount 320: measured max 278 across 1000 seeded trials. */
    DARK_OAK(BlockType.DARK_OAK_LOG, BlockType.DARK_OAK_LEAVES, 5, 7, 2, 4, 6, CanopyShape.THICK_BROAD, 9, 320),
    /** Rounded lobes around short branches with soft gaps and hanging strands; foliage uses a fixed
     *  pink tint rather than the per-column biome blend (see ChunkMeshBuilder's isCherryLeaves).
     *  maxBlockCount 200: measured max 168 across 1000 seeded trials. */
    CHERRY(BlockType.CHERRY_LOG, BlockType.CHERRY_LEAVES, 6, 8, 1, 4, 6, CanopyShape.LOBED_ROUND, 6, 200);

    public final BlockType log;
    public final BlockType leaves;
    public final int minTrunkHeight;
    public final int maxTrunkHeight;
    /** 1 for every species except DARK_OAK's real 2x2 footprint (see {@link #placeDarkOak}). */
    public final int trunkFootprint;
    /** Horizontal reach of the widest canopy layer - used both for the shape itself and for the
     *  chunk-interior-margin / minimum-spacing checks in TerrainGenerator. */
    public final int canopyRadius;
    /** Distinct vertical rows the canopy spans - read directly off each species' own shape
     *  method (see each entry's javadoc derivation), not separately enforced at placement time. */
    public final int canopyHeight;
    public final CanopyShape canopyShape;
    /** Minimum distance (in blocks, trunk to trunk) required from any other already-placed tree
     *  in the same chunk. No "maximum spacing" constant - this architecture drives spacing from
     *  density + rejection sampling, not a grid, so an upper bound isn't a meaningful knob here
     *  (documented per the plan rather than added as an unused field). */
    public final int minSpacing;
    /** Empirically-measured (200 seeded trials), margin-padded upper bound on how many log+leaf
     *  (+root, for MANGROVE) blocks a single placement can ever produce - see {@link TreeProfile}
     *  for why this is a validated invariant rather than a live-enforced cap. */
    public final int maxBlockCount;

    TreeSpecies(BlockType log, BlockType leaves, int minTrunkHeight, int maxTrunkHeight, int trunkFootprint,
                int canopyRadius, int canopyHeight, CanopyShape canopyShape, int minSpacing, int maxBlockCount) {
        this.log = log;
        this.leaves = leaves;
        this.minTrunkHeight = minTrunkHeight;
        this.maxTrunkHeight = maxTrunkHeight;
        this.trunkFootprint = trunkFootprint;
        this.canopyRadius = canopyRadius;
        this.canopyHeight = canopyHeight;
        this.canopyShape = canopyShape;
        this.minSpacing = minSpacing;
        this.maxBlockCount = maxBlockCount;
    }

    /** Bundles this species' own dimensional/shape data into one queryable {@link TreeProfile} -
     *  the reusable profile system future species (and tests/tooling) can operate on generically
     *  instead of reading loose enum fields one at a time. See TreeProfile's class docs for why
     *  the actual shape-generator methods below stay imperative rather than being folded into it. */
    public TreeProfile profile() {
        return new TreeProfile(log, leaves, minTrunkHeight, maxTrunkHeight, trunkFootprint,
                canopyRadius, canopyHeight, canopyShape, minSpacing, maxBlockCount);
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
            case SPRUCE -> placeSpruce(chunk, x, baseY, z, trunkHeight, rnd);
            case BIRCH -> placeBirch(chunk, x, baseY, z, trunkHeight, rnd);
            case JUNGLE -> placeJungle(chunk, x, baseY, z, trunkHeight, rnd);
            case ACACIA -> placeAcacia(chunk, x, baseY, z, trunkHeight, rnd);
            case SWAMP_OAK -> placeSwampOak(chunk, x, baseY, z, trunkHeight, rnd);
            case MANGROVE -> placeMangrove(chunk, x, baseY, z, trunkHeight, rnd);
            case DARK_OAK -> placeDarkOak(chunk, x, baseY, z, trunkHeight, rnd);
            case CHERRY -> placeCherry(chunk, x, baseY, z, trunkHeight, rnd);
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

    private CanopyBuilder builder(Chunk chunk, Random rnd, int baseY) {
        return new CanopyBuilder(chunk, log, leaves, rnd, baseY + 1);
    }

    /**
     * Tall and slim: a bare trunk (at least two blocks clear) under a vertically stretched oval
     * canopy, 7 rows tall and at most 5 wide, widest in the middle and tapering to a rounded top.
     * Variation is bounded: the whole oval may sit one block off the trunk on one axis, one small
     * bulge is added on a random side at a random height, and shell cells drop out at a low rate.
     */
    private void placeBirch(Chunk chunk, int x, int baseY, int z, int trunkHeight, Random rnd) {
        trunk(chunk, x, baseY, z, trunkHeight);
        CanopyBuilder cb = builder(chunk, rnd, baseY);
        int top = baseY + trunkHeight - 1;
        int cy = top - 1;
        int ox = 0, oz = 0;
        if (rnd.nextBoolean()) {
            if (rnd.nextBoolean()) ox = rnd.nextBoolean() ? 1 : -1;
            else oz = rnd.nextBoolean() ? 1 : -1;
        }
        cb.blob(x + ox, cy, z + oz, 2.3, 3.7, 2.3, 0.15);
        int bx = rnd.nextBoolean() ? 1 : -1;
        int bz = rnd.nextBoolean() ? 1 : -1;
        cb.blob(x + ox + bx, cy + rnd.nextInt(3) - 1, z + oz + bz, 1.6, 1.6, 1.6, 0.1);
        cb.prune();
    }

    /**
     * Tiered conifer: a single-block tip at the top, then rows that widen steadily toward the
     * bottom up to canopyRadius, arranged as shelves - every second row is a "whorl" one block
     * wider than the row above and below it, with a few of its outermost blocks missing so the
     * tiers read as separate boughs instead of a smooth cone. The lowest two trunk blocks stay
     * bare. Growth per row is jittered (bounded) so tier spacing differs tree to tree, and one
     * outermost block may poke out a tier's edge. Radius never exceeds canopyRadius.
     */
    private void placeSpruce(Chunk chunk, int x, int baseY, int z, int trunkHeight, Random rnd) {
        trunk(chunk, x, baseY, z, trunkHeight);
        CanopyBuilder cb = builder(chunk, rnd, baseY);
        int top = baseY + trunkHeight - 1;
        int rows = trunkHeight - 2; // rows below the tip
        double step = (canopyRadius + 0.6) / Math.max(1, rows);
        double acc = 0;
        for (int i = 0; i <= rows; i++) {
            int ly = top + 1 - i;
            int base = Math.min(canopyRadius, (int) acc);
            acc += step * (0.7 + rnd.nextDouble() * 0.6);
            if (i == 0) {
                cb.leaf(x, ly, z);
                continue;
            }
            boolean whorl = i % 2 == 1;
            int r = Math.min(canopyRadius, base + (whorl ? 1 : 0));
            r = Math.max(1, r);
            cb.disc(x, ly, z, r + 0.4, whorl && r >= 3 ? 0.18 : 0);
        }
        cb.prune();
    }

    private static final int[][] EIGHT_DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /**
     * Tall and lush: a bare lower trunk, a broad dense crown on top (up to 9 wide, 5 tall, its
     * centre jittered by a block and a random 0-1 higher), a smaller leaf mass hugging the trunk
     * a few blocks lower, and 3-5 log branches leaving the upper trunk in random directions
     * (cardinal or diagonal), each rising as it goes and ending in its own leaf blob. The blobs
     * overlap the crown or the trunk, so every mass is connected; branch tips sit at different
     * heights and distances, so no two jungle trees share a silhouette.
     */
    private void placeJungle(Chunk chunk, int x, int baseY, int z, int trunkHeight, Random rnd) {
        trunk(chunk, x, baseY, z, trunkHeight);
        CanopyBuilder cb = builder(chunk, rnd, baseY);
        int top = baseY + trunkHeight - 1;
        java.util.List<int[]> dirs = new java.util.ArrayList<>(java.util.Arrays.asList(EIGHT_DIRS));
        java.util.Collections.shuffle(dirs, rnd);
        int branches = 3 + rnd.nextInt(3);
        // Logs first, leaves after, so leaf blobs never block a branch and never overwrite one.
        int[][] tips = new int[branches][];
        for (int i = 0; i < branches; i++) {
            int[] d = dirs.get(i);
            boolean diagonal = d[0] != 0 && d[1] != 0;
            int startY = Math.max(baseY + 4, top - 3 - rnd.nextInt(4));
            tips[i] = cb.branch(x, startY, z, d[0], d[1], diagonal ? 2 : 3);
        }
        int ox = rnd.nextInt(3) - 1, oz = rnd.nextInt(3) - 1;
        cb.blob(x + ox, top + rnd.nextInt(2), z + oz, 3.8, 2.4, 3.8, 0.12);
        cb.blob(x, top - 4, z, 2.8, 1.7, 2.8, 0.15);
        for (int[] tip : tips) {
            cb.blob(tip[0], tip[1], tip[2], 2.5, 1.8, 2.5, 0.12);
        }
        cb.prune();
    }

    private static final int[][] CARDINALS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** One flat, uneven acacia leaf mass around a branch tip: a wide top disc, a smaller disc at the
     *  tip's own level, a sparse ragged underside and (sometimes) a small bump on top. */
    private void acaciaMass(CanopyBuilder cb, int[] tip, double topRadius, boolean bump) {
        cb.disc(tip[0], tip[1] + 1, tip[2], topRadius, 0.2);
        cb.disc(tip[0], tip[1], tip[2], topRadius - 0.9, 0.1);
        cb.disc(tip[0], tip[1] - 1, tip[2], 1.7, 0.5);
        if (bump) cb.disc(tip[0], tip[1] + 2, tip[2], 1.5, 0.2);
    }

    /**
     * Savanna umbrella: a straight trunk that bends sideways (one or two step-ups in a random
     * cardinal direction), so the canopy sits off the trunk's base, plus - usually - a second,
     * lower branch leaning another way. Each branch tip carries a broad, shallow leaf mass (a wide
     * top disc, a smaller disc at the tip, a ragged underside), the main one wider than the
     * secondary, so the crown is an uneven pair of platforms rather than one symmetric hat.
     */
    private void placeAcacia(Chunk chunk, int x, int baseY, int z, int trunkHeight, Random rnd) {
        trunk(chunk, x, baseY, z, trunkHeight);
        CanopyBuilder cb = builder(chunk, rnd, baseY);
        int topLog = baseY + trunkHeight - 1;
        int[] d1 = CARDINALS[rnd.nextInt(4)];
        int[] main = cb.branch(x, topLog, z, d1[0], d1[1], 1 + rnd.nextInt(2));
        int[] second = null;
        if (rnd.nextInt(10) < 7) {
            int[] d2 = CARDINALS[rnd.nextInt(4)];
            if (d2 == d1) d2 = CARDINALS[(java.util.Arrays.asList(CARDINALS).indexOf(d1) + 2) % 4];
            second = cb.branch(x, topLog - 1, z, d2[0], d2[1], 1 + rnd.nextInt(2));
        }
        acaciaMass(cb, main, 3.3, rnd.nextBoolean());
        if (second != null) acaciaMass(cb, second, 2.6, false);
        cb.prune();
    }

    /**
     * Low, spreading and untidy: a wide flat-ish main mass whose centre may sit one block off the
     * trunk, two or three side lobes at different heights around it, and one "droop" side - a
     * lower lobe plus a few hanging strands - so the underside is uneven and one edge hangs lower
     * than the rest. The canopy is centred right at the trunk top, so the trunk is bare and visible
     * below it, and the whole thing is much lower and wider than a birch or oak.
     */
    private void placeSwampOak(Chunk chunk, int x, int baseY, int z, int trunkHeight, Random rnd) {
        trunk(chunk, x, baseY, z, trunkHeight);
        CanopyBuilder cb = builder(chunk, rnd, baseY);
        int top = baseY + trunkHeight - 1;
        cb.blob(x + rnd.nextInt(3) - 1, top, z + rnd.nextInt(3) - 1, 3.5, 1.7, 3.5, 0.18);
        java.util.List<int[]> dirs = new java.util.ArrayList<>(java.util.Arrays.asList(EIGHT_DIRS));
        java.util.Collections.shuffle(dirs, rnd);
        int lobes = 2 + rnd.nextInt(2);
        for (int i = 0; i < lobes; i++) {
            int[] d = dirs.get(i);
            double reach = d[0] != 0 && d[1] != 0 ? 1.5 : 2.0;
            cb.blob(x + d[0] * reach, top - rnd.nextInt(2), z + d[1] * reach, 2.0, 1.4, 2.0, 0.15);
        }
        int[] droop = dirs.get(lobes);
        int lowY = Math.max(baseY + 2, top - 2);
        cb.blob(x + droop[0] * 2, lowY, z + droop[1] * 2, 1.9, 1.4, 1.9, 0.2);
        for (int i = 0; i < 6; i++) {
            int hx = x + droop[0] * 2 + rnd.nextInt(5) - 2;
            int hz = z + droop[1] * 2 + rnd.nextInt(5) - 2;
            cb.dangle(hx, hz, lowY + 1, lowY - 2, 1 + rnd.nextInt(2));
        }
        cb.prune();
    }

    /** Branch-driven sprawling crown (3-4 log branches of different lengths and directions, each
     *  ending in its own leaf lobe, plus an off-centre top lobe and sparse hanging strands) and a
     *  root skirt reaching toward adjacent shoreline water - see the class docs on MANGROVE. Only ever replaces WATER or AIR in a neighboring column
     *  (never the trunk's own solid ground, never another tree's already-placed blocks), and
     *  every root is laterally adjacent to the trunk it grew from - never a disconnected floater. */
    private void placeMangrove(Chunk chunk, int x, int baseY, int z, int trunkHeight, Random rnd) {
        trunk(chunk, x, baseY, z, trunkHeight);
        CanopyBuilder cb = builder(chunk, rnd, baseY);
        int top = baseY + trunkHeight - 1;
        java.util.List<int[]> dirs = new java.util.ArrayList<>(java.util.Arrays.asList(EIGHT_DIRS));
        java.util.Collections.shuffle(dirs, rnd);
        int branches = 3 + rnd.nextInt(2);
        int[][] tips = new int[branches][];
        for (int i = 0; i < branches; i++) {
            int[] d = dirs.get(i);
            int startY = Math.max(baseY + 2, top - 2 - rnd.nextInt(3));
            tips[i] = cb.branch(x, startY, z, d[0], d[1], d[0] != 0 && d[1] != 0 ? 2 : 3);
        }
        int[] crown = {x + rnd.nextInt(3) - 1, top + 1, z + rnd.nextInt(3) - 1};
        cb.blob(crown[0], crown[1], crown[2], 3.0, 1.7, 3.0, 0.12);
        for (int[] tip : tips) {
            cb.blob(tip[0], tip[1], tip[2], 2.6, 1.6, 2.6, 0.15);
        }
        // Sparse hanging strands under lobe edges - a few short, uneven ones, not a curtain.
        int[][] lobes = new int[branches + 1][];
        System.arraycopy(tips, 0, lobes, 0, branches);
        lobes[branches] = crown;
        for (int[] lobe : lobes) {
            for (int k = 0; k < 3; k++) {
                cb.dangle(lobe[0] + rnd.nextInt(5) - 2, lobe[2] + rnd.nextInt(5) - 2, lobe[1], lobe[1] - 3, 1 + rnd.nextInt(2));
            }
        }
        cb.prune();
        for (int[] d : CARDINALS) {
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

    /** This project's first branch geometry (see the class docs on DARK_OAK for why it's the
     *  only species that gets it this round). Cardinal directions only, one of {@link #BRANCH_DIRECTIONS}
     *  each. Every branch's first log is placed directly adjacent to a real trunk log of the 2x2
     *  footprint - always connected, never a floating log - and its farthest reach (connection
     *  point + BRANCH_LENGTH-1 steps + the 1-block leaf puff) is exactly canopyRadius (4) from the
     *  tree's own (x,z) anchor, the same margin TerrainGenerator.fitsAndIsSuitable already
     *  reserves for this species' canopy - so this needed no change to that shared check. */
    private static final int[][] BRANCH_DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    private static final int BRANCH_MIN_COUNT = 2;
    private static final int BRANCH_MAX_COUNT = 4;
    /** Log blocks per branch, connection point inclusive. */
    private static final int BRANCH_LENGTH = 2;

    /** Real vanilla's 2x2 trunk (this project's only multi-block-footprint trunk) topped with a
     *  wide, layered canopy - see the class docs on DARK_OAK. */
    private void placeDarkOak(Chunk chunk, int x, int baseY, int z, int trunkHeight, Random rnd) {
        for (int dy = 0; dy < trunkHeight; dy++) {
            setIfInBounds(chunk, x, baseY + dy, z, log);
            setIfInBounds(chunk, x + 1, baseY + dy, z, log);
            setIfInBounds(chunk, x, baseY + dy, z + 1, log);
            setIfInBounds(chunk, x + 1, baseY + dy, z + 1, log);
        }
        placeDarkOakBranches(chunk, x, baseY, trunkHeight, z, rnd);
        int top = baseY + trunkHeight - 1;
        // Heavy, dense, multi-lobed crown centred on the middle of the 2x2 trunk: one wide central
        // mass, a broader-but-flatter mass under it (the "heavy lower canopy"), and 3-4 overlapping
        // side lobes at the quadrant diagonals, each a different height. Everything stays within
        // canopyRadius of the (x, z) anchor - the margin TerrainGenerator reserves. The branch
        // puffs placed above then sit inside or against these lobes.
        CanopyBuilder cb = builder(chunk, rnd, baseY);
        double cx = x + 0.5, cz = z + 0.5;
        cb.blob(cx, top, cz, 3.9, 2.4, 3.9, 0.08);
        cb.blob(cx, top - 2, cz, 3.6, 1.7, 3.6, 0.08);
        int[][] quadrants = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        int skipped = rnd.nextInt(5) == 0 ? rnd.nextInt(4) : -1; // occasionally one corner has no lobe
        for (int i = 0; i < quadrants.length; i++) {
            if (i == skipped) continue;
            cb.blob(cx + quadrants[i][0] * 2.2, top - 1 + rnd.nextInt(2), cz + quadrants[i][1] * 2.2, 2.0, 1.7, 2.0, 0.1);
        }
        cb.prune();
    }

    /** 2-4 branches (deterministic, drawn from the same {@code rnd} already threaded through
     *  this whole placement - no second random source), each starting in the trunk's upper half
     *  so there's always bare trunk beneath them, one distinct cardinal direction per branch
     *  (shuffled, not sampled with replacement, so up to 4 branches never repeat a direction). */
    private void placeDarkOakBranches(Chunk chunk, int x, int baseY, int trunkHeight, int z, Random rnd) {
        if (trunkHeight < 4) return; // defensive: DARK_OAK's own minTrunkHeight (5) never hits this
        int lowestBranchY = baseY + trunkHeight / 2;
        int highestBranchY = Math.max(lowestBranchY, baseY + trunkHeight - 2); // stay below the canopy's own bottom layer

        java.util.List<int[]> dirs = new java.util.ArrayList<>(java.util.Arrays.asList(BRANCH_DIRECTIONS));
        java.util.Collections.shuffle(dirs, rnd);
        int branchCount = BRANCH_MIN_COUNT + rnd.nextInt(BRANCH_MAX_COUNT - BRANCH_MIN_COUNT + 1);
        for (int i = 0; i < branchCount && i < dirs.size(); i++) {
            int branchY = lowestBranchY + (highestBranchY > lowestBranchY ? rnd.nextInt(highestBranchY - lowestBranchY + 1) : 0);
            placeSingleDarkOakBranch(chunk, x, z, branchY, dirs.get(i));
        }
    }

    /** One branch: connects directly to the real trunk log on this side of the 2x2 footprint,
     *  extends BRANCH_LENGTH-1 further steps outward with a slight upward angle, then a small
     *  leaf puff at the tip using the existing leaf() helper (so branch foliage gets the exact
     *  same "never overwrite anything solid" protection every other species' canopy already has). */
    private void placeSingleDarkOakBranch(Chunk chunk, int x, int z, int y, int[] dir) {
        int connectX = dir[0] > 0 ? x + 2 : dir[0] < 0 ? x - 1 : x;
        int connectZ = dir[1] > 0 ? z + 2 : dir[1] < 0 ? z - 1 : z;
        int tipX = connectX + dir[0] * (BRANCH_LENGTH - 1);
        int tipZ = connectZ + dir[1] * (BRANCH_LENGTH - 1);
        int tipY = y + 1; // a slight upward angle over the branch's short length

        logIfReplaceable(chunk, connectX, y, connectZ);
        logIfReplaceable(chunk, tipX, tipY, tipZ);

        for (int lx = -1; lx <= 1; lx++) {
            for (int lz = -1; lz <= 1; lz++) {
                if (Math.abs(lx) + Math.abs(lz) > 1) continue; // a small diamond, not a cube
                leaf(chunk, tipX + lx, tipY, tipZ + lz);
                leaf(chunk, tipX + lx, tipY + 1, tipZ + lz);
            }
        }
    }

    /**
     * Ornamental cherry: a rounded top lobe plus 2-3 short diagonal/cardinal log branches from the
     * upper trunk, each ending in its own rounded lobe, so the crown is several overlapping
     * clouds around the branches rather than one ball. The lobes are ellipsoids (never square),
     * shell cells drop out at a low rate for soft gaps, and a few short strands hang from the
     * lobes' undersides.
     */
    private void placeCherry(Chunk chunk, int x, int baseY, int z, int trunkHeight, Random rnd) {
        trunk(chunk, x, baseY, z, trunkHeight);
        CanopyBuilder cb = builder(chunk, rnd, baseY);
        int top = baseY + trunkHeight - 1;
        java.util.List<int[]> dirs = new java.util.ArrayList<>(java.util.Arrays.asList(EIGHT_DIRS));
        java.util.Collections.shuffle(dirs, rnd);
        int branches = 2 + rnd.nextInt(2);
        int[][] tips = new int[branches][];
        for (int i = 0; i < branches; i++) {
            int[] d = dirs.get(i);
            int startY = Math.max(baseY + 2, top - 2 - rnd.nextInt(2));
            tips[i] = cb.branch(x, startY, z, d[0], d[1], d[0] != 0 && d[1] != 0 ? 1 : 2);
        }
        int[] crown = {x + rnd.nextInt(3) - 1, top + 1, z + rnd.nextInt(3) - 1};
        cb.blob(crown[0], crown[1], crown[2], 2.7, 2.0, 2.7, 0.12);
        for (int[] tip : tips) {
            cb.blob(tip[0], tip[1], tip[2], 2.3, 1.7, 2.3, 0.12);
        }
        int[][] lobes = new int[branches + 1][];
        System.arraycopy(tips, 0, lobes, 0, branches);
        lobes[branches] = crown;
        for (int[] lobe : lobes) {
            for (int k = 0; k < 2; k++) {
                cb.dangle(lobe[0] + rnd.nextInt(5) - 2, lobe[2] + rnd.nextInt(5) - 2, lobe[1], lobe[1] - 2, rnd.nextInt(3) == 0 ? 2 : 1);
            }
        }
        cb.prune();
    }

    /** A branch log only replaces air or leaves - never terrain or another solid block. */
    private void logIfReplaceable(Chunk chunk, int x, int y, int z) {
        if (x < 0 || x >= Chunk.SIZE_X || z < 0 || z >= Chunk.SIZE_Z || y < 0 || y >= Chunk.SIZE_Y) return;
        BlockType existing = chunk.getLocal(x, y, z);
        if (existing.isAir() || existing == leaves) chunk.setLocal(x, y, z, log);
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
