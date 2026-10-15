package com.example.world;

import java.util.HashSet;
import java.util.Set;

/**
 * Block light and sky light on the 0-15 scale, propagated across the whole loaded world the way vanilla's LightEngine does
 * (rules read from the 26.3 client: BlockLightEngine, SkyLightEngine, LightEngine, BlockBehaviour.getLightDampening):
 * <ul>
 *   <li>light spreads to the six neighbours losing {@code max(1, dampening)} per step, and does not enter a block that
 *       dampens by 15 (leaves, water and ice dampen by 1, glass and plants by 0);</li>
 *   <li>a block that emits (glowstone) is a source at its own level;</li>
 *   <li>sky light: every cell above the highest light-dampening block of its column is a source at 15 (so light does not weaken
 *       straight down an open column, and weakens by the dampening of each leaf or water block it crosses), and spreads from
 *       there like any other light;</li>
 *   <li>placing or breaking a block (and a furnace starting or stopping to burn) updates the light with the usual two queues:
 *       first the light that depended on the changed cells is removed, then what remains spreads back in. It crosses chunk
 *       borders freely, so an edit near a border relights both sides, and no stale light is left behind when a source goes.</li>
 * </ul>
 * Every chunk whose light changed (and the neighbours that read its border cells for smooth lighting) is marked mesh-dirty.
 * Runs on the main thread only, so the working state is static.
 */
public final class LightEngine {
    private LightEngine() {
    }

    private static final int MAX = 15;
    private static final int SIZE_Y = Chunk.SIZE_Y;

    private static World world;
    private static Chunk cachedChunk;
    private static int cachedCx = Integer.MIN_VALUE, cachedCz = Integer.MIN_VALUE;
    private static final Set<Long> touched = new HashSet<>();

    private static final LongQueue increase = new LongQueue(4096);
    private static final LongQueue decrease = new LongQueue(1024);

    // ---- entry points ----

    /** Compatibility name for {@link #initChunk}. */
    public static void compute(Chunk chunk, World w) {
        initChunk(chunk, w);
    }

    /**
     * Lights a freshly generated (or loaded) chunk from scratch: its own sources and sky columns, plus the light of every
     * already lit neighbour that reaches over its borders; its own light flows into those neighbours in the same pass.
     */
    public static void initChunk(Chunk chunk, World w) {
        begin(w);
        java.util.Arrays.fill(chunk.skyLight, (byte) 0);
        java.util.Arrays.fill(chunk.blockLight, (byte) 0);
        chunk.markLightComputed();
        int ox = chunk.worldOriginX(), oz = chunk.worldOriginZ();

        // sky: the highest light-dampening block of each column; everything above it is a source
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                int by = -1;
                for (int y = SIZE_Y - 1; y >= 0; y--) {
                    if (LightProperties.dampening(chunk.getLocal(x, y, z)) > 0) {
                        by = y;
                        break;
                    }
                }
                chunk.setSkyBlocker(x, z, by);
                for (int y = by + 1; y < SIZE_Y; y++) chunk.skyLight[Chunk.index(x, y, z)] = MAX;
            }
        }
        increase.clear();
        // the cells of each column that touch a lower neighbouring column (and the one just above the blocker, which feeds the blocker)
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                int by = chunk.skyBlocker(x, z);
                if (by + 1 < SIZE_Y) enqueue(ox + x, by + 1, oz + z, MAX);
                for (int d = 0; d < 4; d++) {
                    int nx = ox + x + DX[d], nz = oz + z + DZ[d];
                    int nb = skyBlockerAt(nx, nz);
                    if (nb == Integer.MIN_VALUE) continue;
                    for (int y = by + 1; y <= Math.min(SIZE_Y - 1, nb); y++) enqueue(ox + x, y, oz + z, MAX);
                }
            }
        }
        // light imported from the lit neighbours (every border cell with light is a source for this chunk)
        importBorders(chunk, true);
        runIncrease(true);

        // block light: emitters, then the neighbours' borders
        increase.clear();
        for (int y = 0; y < SIZE_Y; y++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                for (int x = 0; x < Chunk.SIZE_X; x++) {
                    int e = emissionAt(ox + x, y, oz + z, chunk.getLocal(x, y, z));
                    if (e > 0) {
                        chunk.blockLight[Chunk.index(x, y, z)] = (byte) e;
                        enqueue(ox + x, y, oz + z, e);
                    }
                }
            }
        }
        importBorders(chunk, false);
        runIncrease(false);

        touchChunkAndNeighbors(chunk.chunkX, chunk.chunkZ);
        flush();
    }

    /** Updates the light for the block at (x, y, z) changing from {@code oldType} to {@code newType}. */
    public static void onBlockChanged(World w, int x, int y, int z, BlockType oldType, BlockType newType) {
        onBlockChanged(w, x, y, z, oldType, newType, 0);
    }

    /** Same, for a position whose old block was giving off {@code oldOverride} extra light (a burning furnace). */
    public static void onBlockChanged(World w, int x, int y, int z, BlockType oldType, BlockType newType, int oldOverride) {
        Chunk c = w.getChunk(World.worldToChunk(x), World.worldToChunk(z));
        if (c == null || !c.isGenerated() || !c.isLightComputed() || y < 0 || y >= SIZE_Y) return;
        begin(w);
        if (LightProperties.dampening(oldType) == LightProperties.dampening(newType)
                && Math.max(LightProperties.emission(oldType), oldOverride) == emissionAt(x, y, z, newType)) {
            // nothing light-relevant changed (e.g. one plant for another)
            touchCell(x, y, z);
            flush();
            return;
        }
        // ---- sky ----
        decrease.clear();
        increase.clear();
        int lx = World.worldToLocal(x), lz = World.worldToLocal(z);
        int oldB = c.skyBlocker(lx, lz);
        int dampNew = LightProperties.dampening(newType);
        int newB = oldB;
        if (dampNew > 0 && y > oldB) {
            newB = y;
        } else if (dampNew == 0 && y == oldB) {
            newB = -1;
            for (int yy = y - 1; yy >= 0; yy--) {
                if (LightProperties.dampening(c.getLocal(lx, yy, lz)) > 0) {
                    newB = yy;
                    break;
                }
            }
        }
        if (newB > oldB) { // cells from the old blocker up to the new one stop being sources
            for (int yy = newB; yy > oldB; yy--) {
                int lvl = c.getSkyLight(lx, yy, lz);
                if (lvl > 0) {
                    setLevel(true, x, yy, z, 0);
                    decrease.add(pack(x, yy, z, lvl));
                }
            }
        } else if (newB < oldB) { // cells from the removed blocker down to the next one become sources
            for (int yy = oldB; yy > newB; yy--) {
                if (c.getSkyLight(lx, yy, lz) < MAX) {
                    setLevel(true, x, yy, z, MAX);
                    enqueue(x, yy, z, MAX);
                }
            }
        }
        c.setSkyBlocker(lx, lz, newB);
        recheckAround(true, x, y, z);
        runDecrease(true);
        runIncrease(true);
        // ---- block light ----
        decrease.clear();
        increase.clear();
        recheckAround(false, x, y, z);
        runDecrease(false);
        runIncrease(false);
        touchCell(x, y, z);
        flush();
    }

    /** A cell's emission changed without its block changing (a furnace lighting or going out): relight around it. */
    public static void onEmissionChanged(World w, int x, int y, int z) {
        Chunk c = w.getChunk(World.worldToChunk(x), World.worldToChunk(z));
        if (c == null || !c.isGenerated() || !c.isLightComputed() || y < 0 || y >= SIZE_Y) return;
        begin(w);
        decrease.clear();
        increase.clear();
        recheckAround(false, x, y, z);
        runDecrease(false);
        runIncrease(false);
        touchCell(x, y, z);
        flush();
    }

    // ---- working state ----

    private static void begin(World w) {
        world = w;
        cachedChunk = null;
        cachedCx = cachedCz = Integer.MIN_VALUE;
        touched.clear();
    }

    private static final int[] DX = {1, -1, 0, 0, 0, 0};
    private static final int[] DY = {0, 0, 0, 0, 1, -1};
    private static final int[] DZ = {0, 0, 1, -1, 0, 0};

    /** The lit chunk holding column (x, z), or null when it is not loaded and lit. */
    private static Chunk chunkAt(int x, int z) {
        int cx = World.worldToChunk(x), cz = World.worldToChunk(z);
        if (cx == cachedCx && cz == cachedCz) return cachedChunk;
        Chunk c = world.getChunk(cx, cz);
        if (c != null && !(c.isGenerated() && c.isLightComputed())) c = null;
        cachedChunk = c;
        cachedCx = cx;
        cachedCz = cz;
        return c;
    }

    /** The stored level (0-15) or -1 when the cell is outside the world or in a chunk that is not lit. */
    private static int level(boolean sky, int x, int y, int z) {
        if (y < 0 || y >= SIZE_Y) return -1;
        Chunk c = chunkAt(x, z);
        if (c == null) return -1;
        int i = Chunk.index(World.worldToLocal(x), y, World.worldToLocal(z));
        return (sky ? c.skyLight[i] : c.blockLight[i]) & 0xFF;
    }

    private static void setLevel(boolean sky, int x, int y, int z, int value) {
        Chunk c = chunkAt(x, z);
        int i = Chunk.index(World.worldToLocal(x), y, World.worldToLocal(z));
        if (sky) c.skyLight[i] = (byte) value;
        else c.blockLight[i] = (byte) value;
        touchCell(x, y, z);
    }

    private static BlockType blockAt(int x, int y, int z) {
        Chunk c = chunkAt(x, z);
        return c == null ? BlockType.AIR : c.getLocal(World.worldToLocal(x), y, World.worldToLocal(z));
    }

    private static int opacity(int x, int y, int z) {
        return LightProperties.dampening(blockAt(x, y, z));
    }

    private static int emissionAt(int x, int y, int z, BlockType t) {
        return Math.max(LightProperties.emission(t), world.lightEmissionOverride(x, y, z));
    }

    /** What the cell is on its own: a block's emission, or 15 above the highest dampening block of a sky column. */
    private static int source(boolean sky, int x, int y, int z) {
        if (sky) {
            Chunk c = chunkAt(x, z);
            return c != null && y > c.skyBlocker(World.worldToLocal(x), World.worldToLocal(z)) ? MAX : 0;
        }
        return emissionAt(x, y, z, blockAt(x, y, z));
    }

    /** The blocker height of a column in a lit chunk, or MIN_VALUE when that chunk is not available. */
    private static int skyBlockerAt(int x, int z) {
        Chunk c = chunkAt(x, z);
        return c == null ? Integer.MIN_VALUE : c.skyBlocker(World.worldToLocal(x), World.worldToLocal(z));
    }

    private static void enqueue(int x, int y, int z, int level) {
        increase.add(pack(x, y, z, level));
    }

    /** Enqueues every lit border cell of the neighbouring chunks that touches {@code chunk}, so their light flows in. */
    private static void importBorders(Chunk chunk, boolean sky) {
        int ox = chunk.worldOriginX(), oz = chunk.worldOriginZ();
        for (int y = 0; y < SIZE_Y; y++) {
            for (int i = 0; i < Chunk.SIZE_X; i++) {
                importCell(sky, ox - 1, y, oz + i);
                importCell(sky, ox + Chunk.SIZE_X, y, oz + i);
                importCell(sky, ox + i, y, oz - 1);
                importCell(sky, ox + i, y, oz + Chunk.SIZE_Z);
            }
        }
    }

    private static void importCell(boolean sky, int x, int y, int z) {
        int l = level(sky, x, y, z);
        if (l > 1) enqueue(x, y, z, l);
    }

    // ---- the two queues ----

    /** Re-evaluates a cell and its neighbours after a change: what each should hold given its own source and its neighbours. */
    private static void recheckAround(boolean sky, int x, int y, int z) {
        for (int d = 0; d < 6; d++) check(sky, x + DX[d], y + DY[d], z + DZ[d]);
        check(sky, x, y, z);
    }

    private static void check(boolean sky, int x, int y, int z) {
        int cur = level(sky, x, y, z);
        if (cur < 0) return;
        int op = opacity(x, y, z);
        int expected = source(sky, x, y, z);
        if (op < MAX) {
            int step = Math.max(1, op);
            for (int d = 0; d < 6; d++) {
                int nl = level(sky, x + DX[d], y + DY[d], z + DZ[d]);
                if (nl - step > expected) expected = nl - step;
            }
        }
        if (cur > expected) {
            int own = source(sky, x, y, z);
            setLevel(sky, x, y, z, own);
            decrease.add(pack(x, y, z, cur));
            if (own > 0) enqueue(x, y, z, own);
        } else if (cur < expected) {
            setLevel(sky, x, y, z, expected);
            enqueue(x, y, z, expected);
        }
    }

    private static void runDecrease(boolean sky) {
        while (!decrease.isEmpty()) {
            long e = decrease.poll();
            int x = ux(e), y = uy(e), z = uz(e), lvl = ul(e);
            for (int d = 0; d < 6; d++) {
                int nx = x + DX[d], ny = y + DY[d], nz = z + DZ[d];
                int nl = level(sky, nx, ny, nz);
                if (nl <= 0) continue;
                if (nl < lvl) {
                    int own = source(sky, nx, ny, nz);
                    if (own >= nl) { // a source of its own: it keeps its level and spreads again
                        increase.add(pack(nx, ny, nz, nl));
                    } else {
                        setLevel(sky, nx, ny, nz, own);
                        decrease.add(pack(nx, ny, nz, nl));
                        if (own > 0) increase.add(pack(nx, ny, nz, own));
                    }
                } else {
                    increase.add(pack(nx, ny, nz, nl)); // lit by something else: it re-spreads into the cleared cells
                }
            }
        }
    }

    private static void runIncrease(boolean sky) {
        while (!increase.isEmpty()) {
            long e = increase.poll();
            int x = ux(e), y = uy(e), z = uz(e);
            int lvl = level(sky, x, y, z);
            if (lvl <= 1) continue;
            for (int d = 0; d < 6; d++) {
                int nx = x + DX[d], ny = y + DY[d], nz = z + DZ[d];
                int cur = level(sky, nx, ny, nz);
                if (cur < 0) continue;
                int op = opacity(nx, ny, nz);
                if (op >= MAX) continue;
                int next = lvl - Math.max(1, op);
                if (next > cur) {
                    setLevel(sky, nx, ny, nz, next);
                    increase.add(pack(nx, ny, nz, next));
                }
            }
        }
    }

    // ---- which meshes need rebuilding ----

    private static void touchCell(int x, int y, int z) {
        int cx = World.worldToChunk(x), cz = World.worldToChunk(z);
        int lx = World.worldToLocal(x), lz = World.worldToLocal(z);
        touched.add(Chunk.key(cx, cz));
        boolean w = lx == 0, e = lx == Chunk.SIZE_X - 1, n = lz == 0, s = lz == Chunk.SIZE_Z - 1;
        if (w) touched.add(Chunk.key(cx - 1, cz));
        if (e) touched.add(Chunk.key(cx + 1, cz));
        if (n) touched.add(Chunk.key(cx, cz - 1));
        if (s) touched.add(Chunk.key(cx, cz + 1));
        if (w && n) touched.add(Chunk.key(cx - 1, cz - 1));
        if (w && s) touched.add(Chunk.key(cx - 1, cz + 1));
        if (e && n) touched.add(Chunk.key(cx + 1, cz - 1));
        if (e && s) touched.add(Chunk.key(cx + 1, cz + 1));
    }

    private static void touchChunkAndNeighbors(int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) touched.add(Chunk.key(cx + dx, cz + dz));
    }

    private static void flush() {
        for (long key : touched) {
            Chunk c = world.getChunk((int) key, (int) (key >> 32));
            if (c != null) c.markMeshDirty();
        }
        touched.clear();
    }

    // ---- packing: x (26 bits), z (26 bits), y (8 bits), level (4 bits) in one long ----

    private static long pack(int x, int y, int z, int level) {
        return ((long) (x + (1 << 25)) & 0x3FFFFFFL) | (((long) (z + (1 << 25)) & 0x3FFFFFFL) << 26)
                | (((long) y & 0xFFL) << 52) | (((long) level & 0xFL) << 60);
    }

    private static int ux(long e) {
        return (int) (e & 0x3FFFFFFL) - (1 << 25);
    }

    private static int uz(long e) {
        return (int) ((e >> 26) & 0x3FFFFFFL) - (1 << 25);
    }

    private static int uy(long e) {
        return (int) ((e >> 52) & 0xFFL);
    }

    private static int ul(long e) {
        return (int) ((e >>> 60) & 0xFL);
    }

    /** A growable circular queue of longs. */
    private static final class LongQueue {
        private long[] data;
        private int head, size;

        LongQueue(int capacity) {
            data = new long[Math.max(16, capacity)];
        }

        void add(long v) {
            if (size == data.length) {
                long[] bigger = new long[data.length * 2];
                for (int i = 0; i < size; i++) bigger[i] = data[(head + i) % data.length];
                data = bigger;
                head = 0;
            }
            data[(head + size) % data.length] = v;
            size++;
        }

        long poll() {
            long v = data[head];
            head = (head + 1) % data.length;
            size--;
            return v;
        }

        boolean isEmpty() {
            return size == 0;
        }

        void clear() {
            head = 0;
            size = 0;
        }
    }
}
