package com.example.world;

import lombok.extern.slf4j.Slf4j;

/**
 * Computes Minecraft-style 0-15 sky light and block light per chunk via multi-source BFS,
 * the same model vanilla 1.8 itself uses for "shadows" (there is no real-time shadow mapping
 * in that version - dark caves and lit torches come entirely from this kind of light level).
 *
 * Each chunk still owns and stores its own {@code skyLight}/{@code blockLight} arrays, and each
 * {@link #compute} call still only actively re-derives values for ONE chunk - but that chunk's
 * BFS now also imports light across its 4 side borders from whichever neighboring chunks are
 * already loaded (via {@link #seedFromNeighbors}), rather than treating the chunk edge as a
 * hard wall. A light source placed near a chunk boundary previously stopped dead at that
 * boundary even when well within its own radius of the next chunk over - visible as one face of
 * a placed light block staying dark depending on exactly where it was placed.
 *
 * This makes propagation a settling process across multiple {@link #compute} calls rather than
 * one shot: after importing from its neighbors, a chunk marks THOSE neighbors mesh-dirty again
 * only if its own border values actually changed as a result (see {@link #borderChecksum}) -
 * so a change ripples outward exactly as far as it needs to and then stops, instead of pinging
 * back and forth forever. Since the brightest possible source (15) can only ever cross one
 * chunk boundary before hitting 0, this always settles within a couple of extra remesh passes,
 * spread across frames by the same time-budgeted streaming loop that already handles chunk
 * generation and meshing.
 */
@Slf4j
public class LightEngine {
    private static final int MAX_LIGHT = 15;

    // Reused across every call instead of allocating a fresh queue per chunk recompute - this
    // runs on the single main game-loop thread only (chunk streaming has no worker threads),
    // so static mutable state here is safe and avoids both the queue's own reallocation and
    // the boxed-Integer churn a generic ArrayDeque<Integer> would produce every single call.
    private static final IntQueue skyQueue = new IntQueue(4096);
    private static final IntQueue blockQueue = new IntQueue(256);

    public static void compute(Chunk chunk, World world) {
        byte[] sky = chunk.skyLight;
        byte[] block = chunk.blockLight;

        // Checksums of the OLD border values, taken before this recompute clears anything -
        // compared against the new border after propagation to decide whether neighbors need
        // to re-import from this chunk. No extra array allocation - see borderChecksum.
        long oldBorderSky = borderChecksum(sky);
        long oldBorderBlock = borderChecksum(block);

        java.util.Arrays.fill(sky, (byte) 0);
        java.util.Arrays.fill(block, (byte) 0);
        skyQueue.clear();
        blockQueue.clear();

        // Seed sky light: every air/transparent cell with a clear vertical path to the top of
        // the world gets full light directly (vertical sky light does not attenuate with depth -
        // only spreading sideways into a cave does), then BFS carries it in under overhangs.
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                for (int y = Chunk.SIZE_Y - 1; y >= 0; y--) {
                    if (chunk.getLocal(x, y, z).isOpaqueCube()) break;
                    int idx = Chunk.index(x, y, z);
                    sky[idx] = MAX_LIGHT;
                    skyQueue.add(idx);
                }
            }
        }

        // Seed block light from light-emitting blocks (glowstone, etc.).
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int z = 0; z < Chunk.SIZE_Z; z++) {
                for (int y = 0; y < Chunk.SIZE_Y; y++) {
                    BlockType type = chunk.getLocal(x, y, z);
                    if (type.lightEmission > 0) {
                        int idx = Chunk.index(x, y, z);
                        block[idx] = (byte) type.lightEmission;
                        blockQueue.add(idx);
                    }
                }
            }
        }

        // Import light across borders from already-loaded, already-lit neighbors, so a source
        // near the edge of one chunk still reaches into the next one instead of stopping dead
        // at the boundary.
        seedFromNeighbors(chunk, world, sky, skyQueue, true);
        seedFromNeighbors(chunk, world, block, blockQueue, false);

        propagate(chunk, sky, skyQueue);
        propagate(chunk, block, blockQueue);
        chunk.markLightComputed();

        if (borderChecksum(sky) != oldBorderSky || borderChecksum(block) != oldBorderBlock) {
            // This chunk's own edge values changed - neighbors that already imported from here
            // (or never got the chance to) need to pick up the new values.
            world.markNeighborChunksDirty(chunk.chunkX, chunk.chunkZ);
        }

        if (log.isTraceEnabled()) {
            log.trace("Lighting recomputed for chunk ({}, {})", chunk.chunkX, chunk.chunkZ);
        }
    }

    /**
     * Seeds this chunk's queue with values imported from the 4 orthogonally-adjacent chunks'
     * own border cells (one step attenuated, same as any other BFS hop) - a chunk that isn't
     * loaded simply contributes nothing, same as open space with no light source. This is a
     * one-directional peek (read the neighbor's current array, never write to it); the neighbor
     * imports back from this chunk symmetrically the next time *it* gets recomputed, which the
     * border-checksum check above triggers whenever this chunk's own edge actually changes.
     */
    private static void seedFromNeighbors(Chunk chunk, World world, byte[] levels, IntQueue queue, boolean sky) {
        Chunk west = world.getChunk(chunk.chunkX - 1, chunk.chunkZ);
        Chunk east = world.getChunk(chunk.chunkX + 1, chunk.chunkZ);
        Chunk north = world.getChunk(chunk.chunkX, chunk.chunkZ - 1);
        Chunk south = world.getChunk(chunk.chunkX, chunk.chunkZ + 1);
        if (west == null && east == null && north == null && south == null) return;

        for (int y = 0; y < Chunk.SIZE_Y; y++) {
            for (int i = 0; i < Chunk.SIZE_X; i++) {
                if (west != null) importBorderCell(chunk, levels, queue, 0, y, i, neighborLight(west, sky, Chunk.SIZE_X - 1, y, i));
                if (east != null) importBorderCell(chunk, levels, queue, Chunk.SIZE_X - 1, y, i, neighborLight(east, sky, 0, y, i));
                if (north != null) importBorderCell(chunk, levels, queue, i, y, 0, neighborLight(north, sky, i, y, Chunk.SIZE_Z - 1));
                if (south != null) importBorderCell(chunk, levels, queue, i, y, Chunk.SIZE_Z - 1, neighborLight(south, sky, i, y, 0));
            }
        }
    }

    private static int neighborLight(Chunk neighbor, boolean sky, int x, int y, int z) {
        return sky ? neighbor.getSkyLight(x, y, z) : neighbor.getBlockLight(x, y, z);
    }

    private static void importBorderCell(Chunk chunk, byte[] levels, IntQueue queue, int x, int y, int z, int neighborLevel) {
        int candidate = neighborLevel - 1;
        if (candidate <= 0) return;
        if (chunk.getLocal(x, y, z).isOpaqueCube()) return;
        int idx = Chunk.index(x, y, z);
        if ((levels[idx] & 0xFF) < candidate) {
            levels[idx] = (byte) candidate;
            queue.add(idx);
        }
    }

    /**
     * A cheap, allocation-free polynomial hash of just this chunk's 4 border faces (not the
     * whole chunk) - used only to detect "did this chunk's edge change" between two computes,
     * not for anything correctness-critical, so the vanishingly small chance of a hash
     * collision just means an unrelated edit will trigger the missed re-import later instead.
     */
    private static long borderChecksum(byte[] levels) {
        long h = 1125899906842597L;
        for (int y = 0; y < Chunk.SIZE_Y; y++) {
            for (int i = 0; i < Chunk.SIZE_X; i++) {
                h = h * 131 + (levels[Chunk.index(0, y, i)] & 0xFF);
                h = h * 131 + (levels[Chunk.index(Chunk.SIZE_X - 1, y, i)] & 0xFF);
                h = h * 131 + (levels[Chunk.index(i, y, 0)] & 0xFF);
                h = h * 131 + (levels[Chunk.index(i, y, Chunk.SIZE_Z - 1)] & 0xFF);
            }
        }
        return h;
    }

    private static void propagate(Chunk chunk, byte[] levels, IntQueue queue) {
        while (!queue.isEmpty()) {
            int idx = queue.poll();
            int level = levels[idx] & 0xFF;
            if (level <= 1) continue;

            int x = idx % Chunk.SIZE_X;
            int y = idx / (Chunk.SIZE_X * Chunk.SIZE_Z);
            int z = (idx / Chunk.SIZE_X) % Chunk.SIZE_Z;

            spread(chunk, levels, queue, x + 1, y, z, level);
            spread(chunk, levels, queue, x - 1, y, z, level);
            spread(chunk, levels, queue, x, y + 1, z, level);
            spread(chunk, levels, queue, x, y - 1, z, level);
            spread(chunk, levels, queue, x, y, z + 1, level);
            spread(chunk, levels, queue, x, y, z - 1, level);
        }
    }

    private static void spread(Chunk chunk, byte[] levels, IntQueue queue, int x, int y, int z, int level) {
        if (x < 0 || x >= Chunk.SIZE_X || z < 0 || z >= Chunk.SIZE_Z || y < 0 || y >= Chunk.SIZE_Y) return;
        if (chunk.getLocal(x, y, z).isOpaqueCube()) return;
        int idx = Chunk.index(x, y, z);
        int next = level - 1;
        if ((levels[idx] & 0xFF) < next) {
            levels[idx] = (byte) next;
            queue.add(idx);
        }
    }

    /** Minimal growable circular-buffer queue of primitive ints - no boxing, unlike ArrayDeque<Integer>. */
    private static final class IntQueue {
        private int[] data;
        private int head = 0;
        private int size = 0;

        IntQueue(int initialCapacity) {
            data = new int[Math.max(16, initialCapacity)];
        }

        void add(int value) {
            if (size == data.length) grow();
            data[(head + size) % data.length] = value;
            size++;
        }

        int poll() {
            int v = data[head];
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

        private void grow() {
            int[] bigger = new int[data.length * 2];
            for (int i = 0; i < size; i++) {
                bigger[i] = data[(head + i) % data.length];
            }
            data = bigger;
            head = 0;
        }
    }
}
