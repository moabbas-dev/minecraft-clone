package com.example.world;

import lombok.extern.slf4j.Slf4j;

/**
 * Computes Minecraft-style 0-15 sky light and block light per chunk via multi-source BFS,
 * the same model vanilla 1.8 itself uses for "shadows" (there is no real-time shadow mapping
 * in that version - dark caves and lit torches come entirely from this kind of light level).
 *
 * Deliberately chunk-local: light does not propagate across chunk borders. Full cross-chunk
 * propagation is a well-known source of hard-to-track seam bugs in hobby voxel engines, and
 * a 16x256x16 chunk is large enough that the overwhelming majority of rooms/caves/light
 * sources fit inside a single chunk - the tradeoff is that a light source very close to a
 * chunk edge won't spill into the next chunk over.
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

    public static void compute(Chunk chunk) {
        byte[] sky = chunk.skyLight;
        byte[] block = chunk.blockLight;
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

        propagate(chunk, sky, skyQueue);
        propagate(chunk, block, blockQueue);
        chunk.markLightComputed();

        if (log.isTraceEnabled()) {
            log.trace("Lighting recomputed for chunk ({}, {})", chunk.chunkX, chunk.chunkZ);
        }
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
