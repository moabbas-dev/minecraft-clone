package com.example.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * The trees of the 26.3 client: {@code TreeFeature} with its trunk placers and foliage placers, ported from their bytecode (and the
 * configured features in {@code worldgen/feature/*.json}), not shaped by hand. A tree is first <em>planned</em> in a staging area
 * (which reads the chunk but does not write it), so the caller can ask whether it fits, and is only then written.
 *
 * <ul>
 *   <li>Oak: straight trunk 4 + 0-2, blob foliage radius 2, height 3. Birch: 5 + 0-2, same blob. Swamp oak: 5 + 0-3, blob radius 3.</li>
 *   <li>Spruce: straight 5 + 0-2 + 0-1, spruce foliage (radius 2-3, a skirt that widens by one every ring after a start of 0 or 1);
 *       one in three is the pine variant: straight 6 + 0-4, pine foliage radius 1, height 3-4.</li>
 *   <li>Jungle: straight 4 + 0-8, blob radius 2. Acacia: forking trunk 5 + 0-2 + 0-2 (it leans one way for up to 3 blocks near the top
 *       and a second, shorter branch leans another), acacia foliage radius 2 as two flat layers per fork.</li>
 *   <li>Dark oak: a 2 x 2 trunk 6 + 0-2 + 0-1 that leans up to 2, with 2-4 block branches off its corners, dark oak foliage (a wide
 *       3 layer crown over the trunk and a round cap on every branch).</li>
 *   <li>Cherry: trunk 7 + 0-1 with 1-3 branches that climb sideways 2-4 blocks, cherry foliage radius 4 with holes and hanging leaves.</li>
 *   <li>Mangrove (and tall mangrove, 85 % of them): a trunk lifted 1-3 (3-7) blocks on a skirt of roots, an upwards-branching trunk and
 *       random-spread foliage radius 3 with 70 placement attempts per attachment.</li>
 * </ul>
 *
 * Not ported (this game has no such block): vines, cocoa pods, bee nests, propagules, moss carpet, muddy mangrove roots, leaf litter;
 * logs carry no axis, so a branch log is drawn upright like a trunk log.
 */
public final class TreePlanner {
    private TreePlanner() {
    }

    /** Direction.Plane.HORIZONTAL: north (-z), east (+x), south (+z), west (-x). */
    private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /** One planned tree: every block it places, in chunk-local coordinates (possibly outside the chunk), and whether all of them are inside. */
    public static final class Plan {
        final Map<Long, BlockType> blocks;
        public final boolean fits;
        public final int minX, maxX, minZ, maxZ, minY, maxY;

        Plan(Map<Long, BlockType> blocks) {
            this.blocks = blocks;
            int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE, y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE;
            for (long k : blocks.keySet()) {
                x0 = Math.min(x0, keyX(k));
                x1 = Math.max(x1, keyX(k));
                z0 = Math.min(z0, keyZ(k));
                z1 = Math.max(z1, keyZ(k));
                y0 = Math.min(y0, keyY(k));
                y1 = Math.max(y1, keyY(k));
            }
            minX = x0;
            maxX = x1;
            minZ = z0;
            maxZ = z1;
            minY = y0;
            maxY = y1;
            fits = blocks.isEmpty() || (x0 >= 0 && x1 < Chunk.SIZE_X && z0 >= 0 && z1 < Chunk.SIZE_Z && y0 > Chunk.MIN_Y && y1 <= Chunk.MAX_Y);
        }

        public boolean isEmpty() {
            return blocks.isEmpty();
        }

        public int count(BlockType type) {
            int n = 0;
            for (BlockType t : blocks.values()) if (t == type) n++;
            return n;
        }

        public BlockType at(int x, int y, int z) {
            return blocks.get(key(x, y, z));
        }

        public Map<Long, BlockType> blocks() {
            return java.util.Collections.unmodifiableMap(blocks);
        }

        /** Writes the tree; blocks outside the chunk are dropped (a tree planted by hand may spill over a border). */
        public void commit(Chunk chunk) {
            commit(chunk, null);
        }

        /** Writes the tree into the chunk and hands the blocks that lie beyond its border to the neighbouring chunks (see {@link World#spill}). */
        public void commit(Chunk chunk, World world) {
            for (Map.Entry<Long, BlockType> e : blocks.entrySet()) {
                int x = keyX(e.getKey()), y = keyY(e.getKey()), z = keyZ(e.getKey());
                if (!Chunk.inWorld(y)) continue;
                if (x < 0 || x >= Chunk.SIZE_X || z < 0 || z >= Chunk.SIZE_Z) {
                    if (world != null) world.spill(chunk.chunkX + Math.floorDiv(x, Chunk.SIZE_X), chunk.chunkZ + Math.floorDiv(z, Chunk.SIZE_Z),
                            Math.floorMod(x, Chunk.SIZE_X), y, Math.floorMod(z, Chunk.SIZE_Z), e.getValue());
                    continue;
                }
                chunk.setLocal(x, y, z, e.getValue());
            }
        }
    }

    private static long key(int x, int y, int z) {
        return ((long) (x + 4096) << 40) | ((long) (z + 4096) << 20) | (long) (y + 4096);
    }

    private static int keyX(long k) {
        return (int) (k >> 40) - 4096;
    }

    private static int keyZ(long k) {
        return (int) ((k >> 20) & 0xFFFFF) - 4096;
    }

    private static int keyY(long k) {
        return (int) (k & 0xFFFFF) - 4096;
    }

    /** TreeFeature.validTreePos / the replaceable_by_trees tag: air, leaves, saplings and water (no other plants exist here). */
    public static boolean replaceableByTree(BlockType b) {
        return b.isAir() || ChunkMeshBuilder.isAnyLeaves(b) || b.isWater() || ChunkMeshBuilder.isCrossShapedPlant(b);
    }

    /** What a tree being planned sees and changes: the chunk underneath, plus what the tree itself has placed so far. */
    private static final class Stage {
        final Chunk chunk;
        final World world;
        final int baseY;
        final BlockType log, leaves;
        final Map<Long, BlockType> staged = new HashMap<>();
        final Set<Long> leafSet = new HashSet<>();

        Stage(Chunk chunk, World world, int baseY, BlockType log, BlockType leaves) {
            this.chunk = chunk;
            this.world = world;
            this.baseY = baseY;
            this.log = log;
            this.leaves = leaves;
        }

        /**
         * The block at a chunk-local cell: what the tree placed, else the chunk, else (beyond the chunk's border) the neighbouring chunk when
         * it is generated, else a guess - air above the tree's base and rock below it, which is what roots need to stop on.
         */
        BlockType get(int x, int y, int z) {
            BlockType s = staged.get(key(x, y, z));
            if (s != null) return s;
            if (!Chunk.inWorld(y)) return BlockType.AIR;
            if (x >= 0 && x < Chunk.SIZE_X && z >= 0 && z < Chunk.SIZE_Z) return chunk.getLocal(x, y, z);
            if (world != null) {
                Chunk other = world.getChunk(chunk.chunkX + Math.floorDiv(x, Chunk.SIZE_X), chunk.chunkZ + Math.floorDiv(z, Chunk.SIZE_Z));
                if (other != null && other.isGenerated()) return other.getLocal(Math.floorMod(x, Chunk.SIZE_X), y, Math.floorMod(z, Chunk.SIZE_Z));
            }
            return y < baseY ? BlockType.STONE : BlockType.AIR;
        }

        boolean validTreePos(int x, int y, int z) {
            return replaceableByTree(get(x, y, z));
        }

        boolean placeLog(int x, int y, int z) {
            if (!validTreePos(x, y, z)) return false;
            staged.put(key(x, y, z), log);
            leafSet.remove(key(x, y, z));
            return true;
        }

        boolean tryPlaceLeaf(int x, int y, int z) {
            if (!validTreePos(x, y, z)) return false;
            staged.put(key(x, y, z), leaves);
            leafSet.add(key(x, y, z));
            return true;
        }

        boolean placeRoot(int x, int y, int z) {
            if (!validTreePos(x, y, z)) return false;
            staged.put(key(x, y, z), BlockType.MANGROVE_ROOTS);
            return true;
        }
    }

    /** FoliagePlacer$FoliageAttachment: where the foliage goes, with the offsets the trunk placer asked for. */
    private record Attachment(int x, int y, int z, int radiusOffset, int heightOffset, boolean large) {
        Attachment(int x, int y, int z, int radiusOffset, boolean large) {
            this(x, y, z, radiusOffset, 0, large);
        }
    }

    private enum Foliage { BLOB, SPRUCE, PINE, ACACIA, DARK_OAK, CHERRY, SPREAD }

    private static int uniform(Random r, int min, int max) {
        return min + r.nextInt(max - min + 1);
    }

    private static int[] randomHorizontal(Random r) {
        return HORIZONTAL[r.nextInt(4)];
    }

    // ---- entry point ----

    /**
     * Plans one tree of {@code species} standing at (x, baseY, z) in chunk-local coordinates, baseY being the first free block above the
     * ground (TreeFeature's origin). The same Random state gives the same tree.
     */
    public static Plan plan(TreeSpecies species, Chunk chunk, int x, int baseY, int z, Random rnd) {
        return plan(species, chunk, null, x, baseY, z, rnd);
    }

    /** As {@link #plan(TreeSpecies, Chunk, int, int, int, Random)}, reading generated neighbouring chunks beyond the border (null: not). */
    public static Plan plan(TreeSpecies species, Chunk chunk, World world, int x, int baseY, int z, Random rnd) {
        Stage s = new Stage(chunk, world, baseY, species.log, species.leaves);
        switch (species) {
            case OAK -> blobTree(s, rnd, x, baseY, z, 4, 2, 0, 2, 3);
            case BIRCH -> blobTree(s, rnd, x, baseY, z, 5, 2, 0, 2, 3);
            case SWAMP_OAK -> blobTree(s, rnd, x, baseY, z, 5, 3, 0, 3, 3);
            case JUNGLE -> blobTree(s, rnd, x, baseY, z, 4, 8, 0, 2, 3);
            case SPRUCE -> {
                if (rnd.nextFloat() < 0.33333334f) pineTree(s, rnd, x, baseY, z);
                else spruceTree(s, rnd, x, baseY, z);
            }
            case ACACIA -> acaciaTree(s, rnd, x, baseY, z);
            case DARK_OAK -> darkOakTree(s, rnd, x, baseY, z);
            case CHERRY -> cherryTree(s, rnd, x, baseY, z);
            case MANGROVE -> mangroveTree(s, rnd, x, baseY, z, rnd.nextFloat() < 0.85f);
        }
        return new Plan(s.staged);
    }

    /** The block the trunk stands on becomes dirt (the soil_beneath_tree provider), when it is grass. */
    private static void placeBelowTrunk(Stage s, int x, int y, int z) {
        if (y < Chunk.MIN_Y) return;
        BlockType below = s.get(x, y, z);
        if (below == BlockType.GRASS_BLOCK || below.isAir() || below.isWater()) s.staged.put(key(x, y, z), BlockType.DIRT);
    }

    private static int treeHeight(Random r, int base, int randA, int randB) {
        return base + r.nextInt(randA + 1) + r.nextInt(randB + 1);
    }

    // ---- oak, birch, swamp oak and jungle: straight trunk, blob foliage ----

    private static void blobTree(Stage s, Random r, int x, int y, int z, int base, int randA, int randB, int radius, int foliageHeight) {
        int height = treeHeight(r, base, randA, randB);
        placeBelowTrunk(s, x, y - 1, z);
        for (int i = 0; i < height; i++) s.placeLog(x, y + i, z);
        createFoliage(s, r, Foliage.BLOB, new Attachment(x, y + height, z, 0, false), foliageHeight, radius, 0, height);
    }

    // ---- spruce and pine ----

    private static void spruceTree(Stage s, Random r, int x, int y, int z) {
        int height = treeHeight(r, 5, 2, 1);
        int trunkHeight = uniform(r, 1, 2);
        int foliageHeight = Math.max(4, height - trunkHeight);
        int radius = uniform(r, 2, 3);
        placeBelowTrunk(s, x, y - 1, z);
        for (int i = 0; i < height; i++) s.placeLog(x, y + i, z);
        createFoliage(s, r, Foliage.SPRUCE, new Attachment(x, y + height, z, 0, false), foliageHeight, radius, uniform(r, 0, 2), height);
    }

    private static void pineTree(Stage s, Random r, int x, int y, int z) {
        int height = treeHeight(r, 6, 4, 0);
        int foliageHeight = uniform(r, 3, 4);
        placeBelowTrunk(s, x, y - 1, z);
        for (int i = 0; i < height; i++) s.placeLog(x, y + i, z);
        createFoliage(s, r, Foliage.PINE, new Attachment(x, y + height, z, 0, false), foliageHeight, 1, 1, height);
    }

    // ---- acacia: ForkingTrunkPlacer ----

    private static void acaciaTree(Stage s, Random r, int ox, int oy, int oz) {
        int height = treeHeight(r, 5, 2, 2);
        placeBelowTrunk(s, ox, oy - 1, oz);
        List<Attachment> attachments = new ArrayList<>();
        int[] dir = randomHorizontal(r);
        int leanStart = height - r.nextInt(4) - 1;
        int leanLength = 3 - r.nextInt(3);
        int x = ox, z = oz, top = Integer.MIN_VALUE;
        for (int i = 0; i < height; i++) {
            int yy = oy + i;
            if (i >= leanStart && leanLength > 0) {
                x += dir[0];
                z += dir[1];
                leanLength--;
            }
            if (s.placeLog(x, yy, z)) top = yy + 1;
        }
        if (top != Integer.MIN_VALUE) attachments.add(new Attachment(x, top, z, 1, false));
        x = ox;
        z = oz;
        int[] dir2 = randomHorizontal(r);
        if (dir2 != dir) {
            int start = leanStart - r.nextInt(2) - 1;
            int length = 1 + r.nextInt(3);
            top = Integer.MIN_VALUE;
            for (int i = start; i < height && length > 0; i++, length--) {
                if (i >= 1) {
                    int yy = oy + i;
                    x += dir2[0];
                    z += dir2[1];
                    if (s.placeLog(x, yy, z)) top = yy + 1;
                }
            }
            if (top != Integer.MIN_VALUE) attachments.add(new Attachment(x, top, z, 0, false));
        }
        for (Attachment a : attachments) createFoliage(s, r, Foliage.ACACIA, a, 0, 2, 0, height);
    }

    // ---- dark oak: DarkOakTrunkPlacer ----

    private static void darkOakTree(Stage s, Random r, int ox, int oy, int oz) {
        int height = treeHeight(r, 6, 2, 1);
        for (int[] d : new int[][]{{0, 0}, {1, 0}, {0, 1}, {1, 1}}) placeBelowTrunk(s, ox + d[0], oy - 1, oz + d[1]);
        List<Attachment> attachments = new ArrayList<>();
        int[] dir = randomHorizontal(r);
        int leanStart = height - r.nextInt(4);
        int leanLength = 2 - r.nextInt(3);
        int x = ox, z = oz, topY = oy + height - 1;
        for (int i = 0; i < height; i++) {
            if (i >= leanStart && leanLength > 0) {
                x += dir[0];
                z += dir[1];
                leanLength--;
            }
            int yy = oy + i;
            if (s.get(x, yy, z).isAir() || ChunkMeshBuilder.isAnyLeaves(s.get(x, yy, z))) {
                s.placeLog(x, yy, z);
                s.placeLog(x + 1, yy, z);
                s.placeLog(x, yy, z + 1);
                s.placeLog(x + 1, yy, z + 1);
            }
        }
        attachments.add(new Attachment(x, topY, z, 0, true));
        for (int i = -1; i <= 2; i++) {
            for (int j = -1; j <= 2; j++) {
                if (i >= 0 && i <= 1 && j >= 0 && j <= 1) continue;
                if (r.nextInt(3) > 0) continue;
                int length = r.nextInt(3) + 2;
                for (int k = 0; k < length; k++) s.placeLog(ox + i, topY - k - 1, oz + j);
                attachments.add(new Attachment(ox + i, topY, oz + j, 0, false));
            }
        }
        for (Attachment a : attachments) createFoliage(s, r, Foliage.DARK_OAK, a, 4, 0, 0, height);
    }

    // ---- cherry: CherryTrunkPlacer ----

    private static void cherryTree(Stage s, Random r, int ox, int oy, int oz) {
        int height = treeHeight(r, 7, 1, 0);
        placeBelowTrunk(s, ox, oy - 1, oz);
        int first = Math.max(0, height - 1 + uniform(r, -4, -3));
        int second = Math.max(0, height - 1 + uniform(r, -4, -4));
        if (second >= first) second++;
        int branches = 1 + r.nextInt(3); // weighted_list 1, 2, 3 with equal weights
        boolean three = branches == 3, twoOrMore = branches >= 2;
        int trunkTop = three ? height : twoOrMore ? Math.max(first, second) + 1 : first + 1;
        for (int i = 0; i < trunkTop; i++) s.placeLog(ox, oy + i, oz);
        List<Attachment> attachments = new ArrayList<>();
        if (three) attachments.add(new Attachment(ox, oy + trunkTop, oz, 0, false));
        int[] dir = randomHorizontal(r);
        attachments.add(cherryBranch(s, r, height, ox, oy, oz, dir, first, first < trunkTop - 1));
        if (twoOrMore) {
            int[] opposite = {-dir[0], -dir[1]};
            attachments.add(cherryBranch(s, r, height, ox, oy, oz, opposite, second, second < trunkTop - 1));
        }
        for (Attachment a : attachments) createFoliage(s, r, Foliage.CHERRY, a, 5, 4, 0, height);
    }

    private static Attachment cherryBranch(Stage s, Random r, int treeHeight, int ox, int oy, int oz, int[] dir, int startY, boolean longer) {
        int x = ox, y = oy + startY, z = oz;
        int endOffset = treeHeight - 1 + uniform(r, -1, 0);
        boolean extended = longer || endOffset < startY;
        int horizontal = uniform(r, 2, 4) + (extended ? 1 : 0);
        int endX = ox + dir[0] * horizontal, endY = oy + endOffset, endZ = oz + dir[1] * horizontal;
        int firstSteps = extended ? 2 : 1;
        for (int i = 0; i < firstSteps; i++) {
            x += dir[0];
            z += dir[1];
            s.placeLog(x, y, z);
        }
        int vertical = endY > y ? 1 : -1;
        while (true) {
            int dist = Math.abs(endX - x) + Math.abs(endY - y) + Math.abs(endZ - z);
            if (dist == 0) break;
            float chance = Math.abs(endY - y) / (float) dist;
            boolean up = r.nextFloat() < chance;
            if (up) y += vertical;
            else {
                x += dir[0];
                z += dir[1];
            }
            s.placeLog(x, y, z);
        }
        return new Attachment(endX, endY + 1, endZ, 0, false);
    }

    // ---- mangrove: MangroveRootPlacer, UpwardsBranchingTrunkPlacer, RandomSpreadFoliagePlacer ----

    private static void mangroveTree(Stage s, Random r, int ox, int oy, int oz, boolean tall) {
        int height = tall ? treeHeight(r, 4, 1, 9) : treeHeight(r, 2, 1, 4);
        int offsetY = tall ? uniform(r, 3, 7) : uniform(r, 1, 3);
        int ty = oy + offsetY; // the trunk origin sits above the roots
        // the column between the ground and the trunk must be free (RootPlacer.placeRoots), or there is no tree
        for (int yy = oy; yy < ty; yy++) if (!s.validTreePos(ox, yy, oz)) return;
        List<int[]> roots = new ArrayList<>();
        roots.add(new int[]{ox, ty - 1, oz});
        for (int[] d : HORIZONTAL) {
            List<int[]> branch = new ArrayList<>();
            if (!simulateRoots(s, r, ox + d[0], ty, oz + d[1], d, ox, ty, oz, branch, 0)) return;
            roots.addAll(branch);
            roots.add(new int[]{ox + d[0], ty, oz + d[1]});
        }
        for (int[] p : roots) s.placeRoot(p[0], p[1], p[2]);

        List<Attachment> attachments = new ArrayList<>();
        int extraStepsMax = tall ? 6 : 4;
        for (int i = 0; i < height; i++) {
            int yy = ty + i;
            boolean placed = s.placeLog(ox, yy, oz);
            if (placed && i < height - 1 && r.nextFloat() < 0.5f) {
                int[] dir = randomHorizontal(r);
                int branchLength = uniform(r, 0, 1);
                int branchStart = Math.max(0, branchLength - uniform(r, 0, 1) - 1);
                int steps = uniform(r, 1, extraStepsMax);
                mangroveBranch(s, height, attachments, ox, yy, oz, dir, branchStart, steps);
            }
            if (i == height - 1) attachments.add(new Attachment(ox, yy + 1, oz, 0, false));
        }
        for (Attachment a : attachments) createFoliage(s, r, Foliage.SPREAD, a, 2, 3, 0, height);
    }

    private static void mangroveBranch(Stage s, int treeHeight, List<Attachment> out, int ox, int y, int oz, int[] dir, int start, int steps) {
        int lastY = y + start;
        int x = ox, z = oz;
        for (int i = start; i < treeHeight && steps > 0; i++, steps--) {
            if (i < 1) continue;
            int yy = y + i;
            x += dir[0];
            z += dir[1];
            lastY = yy;
            if (s.placeLog(x, yy, z)) lastY = yy + 1;
            out.add(new Attachment(x, yy, z, 0, false));
        }
        if (lastY - y > 1) {
            out.add(new Attachment(x, lastY, z, 0, false));
            out.add(new Attachment(x, lastY - 2, z, 0, false));
        }
    }

    private static final int MAX_ROOT_LENGTH = 15, MAX_ROOT_WIDTH = 8;
    private static final float ROOT_SKEW = 0.2f;

    private static boolean canPlaceRoot(Stage s, int x, int y, int z) {
        return s.validTreePos(x, y, z) || s.get(x, y, z) == BlockType.MANGROVE_ROOTS;
    }

    private static boolean simulateRoots(Stage s, Random r, int x, int y, int z, int[] dir, int tx, int ty, int tz, List<int[]> roots, int depth) {
        if (depth == MAX_ROOT_LENGTH || roots.size() > MAX_ROOT_LENGTH) return false;
        for (int[] p : potentialRootPositions(r, x, y, z, dir, tx, ty, tz)) {
            if (canPlaceRoot(s, p[0], p[1], p[2])) {
                roots.add(p);
                if (!simulateRoots(s, r, p[0], p[1], p[2], dir, tx, ty, tz, roots, depth + 1)) return false;
            }
        }
        return true;
    }

    private static List<int[]> potentialRootPositions(Random r, int x, int y, int z, int[] dir, int tx, int ty, int tz) {
        int[] below = {x, y - 1, z};
        int[] side = {x + dir[0], y, z + dir[1]};
        int dist = Math.abs(x - tx) + Math.abs(y - ty) + Math.abs(z - tz);
        if (dist > MAX_ROOT_WIDTH - 3 && dist <= MAX_ROOT_WIDTH) {
            return r.nextFloat() < ROOT_SKEW ? List.of(below, new int[]{side[0], side[1] - 1, side[2]}) : List.of(below);
        }
        if (dist > MAX_ROOT_WIDTH) return List.of(below);
        if (r.nextFloat() < ROOT_SKEW) return List.of(below);
        return r.nextBoolean() ? List.of(side) : List.of(below);
    }

    // ---- foliage placers ----

    /** FoliagePlacer.createFoliage with the placer's own rules; {@code offset} is the value its offset provider gave. */
    private static void createFoliage(Stage s, Random r, Foliage kind, Attachment a, int foliageHeight, int radius, int offset, int treeHeight) {
        int h = foliageHeight + a.heightOffset();
        switch (kind) {
            case BLOB -> {
                for (int y = offset; y >= offset - h; y--) {
                    int range = Math.max(radius + a.radiusOffset() - 1 - y / 2, 0);
                    placeLeavesRow(s, r, kind, a, range, y);
                }
            }
            case SPRUCE -> {
                int ring = r.nextInt(2), max = 1, restart = 0;
                for (int y = offset; y >= -h; y--) {
                    placeLeavesRow(s, r, kind, a, ring, y);
                    if (ring >= max) {
                        ring = restart;
                        restart = 1;
                        max = Math.min(max + 1, radius + a.radiusOffset());
                    } else {
                        ring++;
                    }
                }
            }
            case PINE -> {
                int ring = 0;
                for (int y = offset; y >= offset - h; y--) {
                    placeLeavesRow(s, r, kind, a, ring, y);
                    if (ring >= 1 && y == offset - h + 1) ring--;
                    else if (ring < radius + a.radiusOffset()) ring++;
                }
            }
            case ACACIA -> {
                Attachment up = new Attachment(a.x(), a.y() + offset, a.z(), a.radiusOffset(), a.heightOffset(), a.large());
                placeLeavesRow(s, r, kind, up, radius + a.radiusOffset(), -1 - h);
                placeLeavesRow(s, r, kind, up, radius - 1, -h);
                placeLeavesRow(s, r, kind, up, radius + a.radiusOffset() - 1, 0);
            }
            case DARK_OAK -> {
                Attachment up = new Attachment(a.x(), a.y() + offset, a.z(), a.radiusOffset(), a.heightOffset(), a.large());
                if (a.large()) {
                    placeLeavesRow(s, r, kind, up, radius + 2, -1);
                    placeLeavesRow(s, r, kind, up, radius + 3, 0);
                    placeLeavesRow(s, r, kind, up, radius + 2, 1);
                    if (r.nextBoolean()) placeLeavesRow(s, r, kind, up, radius, 2);
                } else {
                    placeLeavesRow(s, r, kind, up, radius + 2, -1);
                    placeLeavesRow(s, r, kind, up, radius + 1, 0);
                }
            }
            case CHERRY -> {
                Attachment up = new Attachment(a.x(), a.y() + offset, a.z(), a.radiusOffset(), a.heightOffset(), a.large());
                int rr = radius + a.radiusOffset() - 1;
                placeLeavesRow(s, r, kind, up, rr - 2, h - 3);
                placeLeavesRow(s, r, kind, up, rr - 1, h - 4);
                for (int y = h - 5; y >= 0; y--) placeLeavesRow(s, r, kind, up, rr, y);
                placeLeavesRowWithHangingLeavesBelow(s, r, kind, up, rr, -1);
                placeLeavesRowWithHangingLeavesBelow(s, r, kind, up, rr - 1, -2);
            }
            case SPREAD -> {
                for (int i = 0; i < 70; i++) {
                    int dx = r.nextInt(radius) - r.nextInt(radius);
                    int dy = r.nextInt(foliageHeight) - r.nextInt(foliageHeight);
                    int dz = r.nextInt(radius) - r.nextInt(radius);
                    s.tryPlaceLeaf(a.x() + dx, a.y() + dy, a.z() + dz);
                }
            }
        }
    }

    /** FoliagePlacer.placeLeavesRow: a square of side 2 range + 1 (one more for a 2 x 2 trunk) at the attachment's level + yOffset. */
    private static void placeLeavesRow(Stage s, Random r, Foliage kind, Attachment a, int range, int yOffset) {
        int extra = a.large() ? 1 : 0;
        for (int dx = -range; dx <= range + extra; dx++) {
            for (int dz = -range; dz <= range + extra; dz++) {
                if (shouldSkipLocationSigned(r, kind, dx, yOffset, dz, range, a.large())) continue;
                s.tryPlaceLeaf(a.x() + dx, a.y() + yOffset, a.z() + dz);
            }
        }
    }

    private static boolean shouldSkipLocationSigned(Random r, Foliage kind, int x, int y, int z, int range, boolean large) {
        int ax, az;
        if (large) {
            ax = Math.min(Math.abs(x), Math.abs(x - 1));
            az = Math.min(Math.abs(z), Math.abs(z - 1));
        } else {
            ax = Math.abs(x);
            az = Math.abs(z);
        }
        return shouldSkipLocation(r, kind, ax, y, az, range, large);
    }

    private static final float CHERRY_CORNER_HOLE = 0.25f, CHERRY_WIDE_BOTTOM_HOLE = 0.25f;
    private static final float CHERRY_HANGING = 0.16666667f, CHERRY_HANGING_EXTENSION = 0.33333334f;

    private static boolean shouldSkipLocation(Random r, Foliage kind, int x, int y, int z, int range, boolean large) {
        switch (kind) {
            case BLOB:
                return x == range && z == range && (r.nextInt(2) == 0 || y == 0);
            case SPRUCE:
            case PINE:
                return x == range && z == range && range > 0;
            case ACACIA:
                if (y == 0) return (x > 1 || z > 1) && x != 0 && z != 0;
                return x == range && z == range && range > 0;
            case DARK_OAK:
                if (y == -1 && !large) return x == range && z == range;
                if (y == 1) return x + z > range * 2 - 2;
                return false;
            case CHERRY: {
                if (y == -1 && (x == range || z == range) && r.nextFloat() < CHERRY_WIDE_BOTTOM_HOLE) return true;
                boolean corner = x == range && z == range;
                boolean wide = range > 2;
                if (wide) return corner || (x + z > range * 2 - 2 && r.nextFloat() < CHERRY_CORNER_HOLE);
                return corner && r.nextFloat() < CHERRY_CORNER_HOLE;
            }
            default:
                return false;
        }
    }

    /** FoliagePlacer.placeLeavesRowWithHangingLeavesBelow: after a row, a leaf may hang under each edge leaf, and one more under that. */
    private static void placeLeavesRowWithHangingLeavesBelow(Stage s, Random r, Foliage kind, Attachment a, int range, int yOffset) {
        placeLeavesRow(s, r, kind, a, range, yOffset);
        int extra = a.large() ? 1 : 0;
        int[] belowOrigin = {a.x(), a.y() - 1, a.z()};
        for (int[] dir : HORIZONTAL) {
            int[] cw = {-dir[1], dir[0]}; // Direction.getClockWise on the (x, z) plane: north -> east -> south -> west
            boolean positive = cw[0] + cw[1] > 0;
            int j = positive ? range + extra : range;
            int px = a.x() + cw[0] * j - dir[0] * range, pz = a.z() + cw[1] * j - dir[1] * range, py = a.y() + yOffset - 1;
            for (int k = -range; k < range + extra; k++) {
                boolean leafAbove = s.leafSet.contains(key(px, py + 1, pz));
                if (leafAbove && tryPlaceExtension(s, r, CHERRY_HANGING, belowOrigin, px, py, pz)) {
                    tryPlaceExtension(s, r, CHERRY_HANGING_EXTENSION, belowOrigin, px, py - 1, pz);
                }
                px += dir[0];
                pz += dir[1];
            }
        }
    }

    private static boolean tryPlaceExtension(Stage s, Random r, float chance, int[] belowOrigin, int x, int y, int z) {
        if (Math.abs(x - belowOrigin[0]) + Math.abs(y - belowOrigin[1]) + Math.abs(z - belowOrigin[2]) >= 7) return false;
        if (r.nextFloat() > chance) return false;
        return s.tryPlaceLeaf(x, y, z);
    }
}
