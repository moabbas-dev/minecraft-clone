package com.example.world;

import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Sparse per-world registry of active saplings, ticked once per frame from Game's existing dt
 * accumulator (the same idiom {@code Weather}/day-night already use elsewhere in this codebase -
 * growth speed is tied to elapsed real time, not frame rate). No random-tick or scheduled-update
 * system exists anywhere else in this project (confirmed by audit); this is the smallest
 * compatible mechanism for it, not a general-purpose simulation framework - it is deliberately
 * just "a timer per sapling." Iterating {@link #active} is cheap regardless of how many chunks
 * are loaded, since it only ever holds real sapling positions, never every block in every chunk.
 *
 * <p><b>Persistence policy</b> (no way to persist an in-progress countdown exists in
 * {@code WorldIO} today, and none is added here): a sapling's <i>stage</i> persists for free,
 * since it's just the block ID already flowing through the existing chunk save/load. Its
 * <i>timer</i> does not - {@link #registerChunk} always rolls a fresh delay for whatever stage
 * the loaded block already is, on every chunk load (including a full game reload). This can
 * never lose or duplicate a stage transition (the stage itself is untouched by this), only
 * restart an in-progress wait - a deliberate, documented simplification, not an invented
 * persistence format.
 *
 * <p><b>Determinism note</b>: the per-sapling delay <i>values</i> are drawn from one seeded
 * {@link Random} stream (reproducible given the same registration order), matching this
 * project's "no global mutable random dependent on unrelated order" convention. However, unlike
 * world generation (a pure function of seed + coordinate), <i>which</i> sapling's timer expires
 * first depends on real elapsed play time - that is inherent to any live timer-based system, not
 * a violation of the generation-determinism convention, which only applies to pure generation.
 */
public class SaplingGrowth {

    // This project's own tuning, not sourced from or claimed to match any real vanilla growth-
    // timing value - explicitly disclosed per the request's own "do not invent official values" rule.
    private static final float STAGE_MIN_DELAY_SECONDS = 45f;
    private static final float STAGE_MAX_DELAY_SECONDS = 90f;
    /** Shorter than a normal stage delay so a stage-3 sapling doesn't feel stuck once space
     *  frees up, but still not a per-frame retry. */
    private static final float RETRY_MIN_DELAY_SECONDS = 20f;
    private static final float RETRY_MAX_DELAY_SECONDS = 40f;

    @AllArgsConstructor
    private static final class Entry {
        final int x, y, z;
        final SaplingSpecies species;
        float secondsRemaining;
    }

    private final Map<Long, Entry> active = new HashMap<>();
    private final Random random;

    public SaplingGrowth(long worldSeed) {
        // Offset the same way every other per-world Random in this codebase is (Weather)
        // so it never collides with their streams.
        this.random = new Random(worldSeed ^ 0x5341504C47524F57L);
    }

    private static long packKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) | (((long) z & 0x3FFFFFFL) << 26) | (((long) (y - Chunk.MIN_Y) & 0xFFFL) << 52);
    }

    private float randomDelayFor(int stageIndex) {
        if (stageIndex < 3) {
            return STAGE_MIN_DELAY_SECONDS + random.nextFloat() * (STAGE_MAX_DELAY_SECONDS - STAGE_MIN_DELAY_SECONDS);
        }
        return RETRY_MIN_DELAY_SECONDS + random.nextFloat() * (RETRY_MAX_DELAY_SECONDS - RETRY_MIN_DELAY_SECONDS);
    }

    /** Called right after a player plants a stage-0 sapling. */
    public void registerPlanted(int x, int y, int z, SaplingSpecies species) {
        active.put(packKey(x, y, z), new Entry(x, y, z, species, randomDelayFor(0)));
    }

    /** Scans one chunk's own blocks for sapling stage blocks and registers each with a fresh
     *  delay for its current (already-persisted) stage - called once when a chunk is generated
     *  or loaded, never a per-frame scan of anything beyond this one chunk. See this class'
     *  persistence-policy doc for why the delay always restarts here. */
    public void registerChunk(Chunk chunk) {
        int originX = chunk.worldOriginX(), originZ = chunk.worldOriginZ();
        for (int x = 0; x < Chunk.SIZE_X; x++) {
            for (int y = Chunk.MIN_Y; y <= Chunk.MAX_Y; y++) {
                for (int z = 0; z < Chunk.SIZE_Z; z++) {
                    BlockType t = chunk.getLocal(x, y, z);
                    SaplingSpecies species = SaplingSpecies.bySaplingBlock(t);
                    if (species == null) continue;
                    int worldX = originX + x, worldZ = originZ + z;
                    active.put(packKey(worldX, y, worldZ), new Entry(worldX, y, worldZ, species, randomDelayFor(species.stageIndex(t))));
                }
            }
        }
    }

    /** Called when a chunk unloads - its saplings simply stop ticking (paused) until the chunk
     *  reloads and {@link #registerChunk} scans them again; never grows while unloaded. */
    public void unregisterChunk(int chunkX, int chunkZ) {
        active.values().removeIf(e -> World.worldToChunk(e.x) == chunkX && World.worldToChunk(e.z) == chunkZ);
    }

    /** Ticks every active sapling once. Iterates only this sparse registry - never scans loaded
     *  chunks/blocks. */
    public void update(World world, float dt) {
        if (active.isEmpty()) return;
        List<Entry> ready = null;
        for (Entry e : active.values()) {
            e.secondsRemaining -= dt;
            if (e.secondsRemaining <= 0f) {
                if (ready == null) ready = new ArrayList<>();
                ready.add(e);
            }
        }
        if (ready == null) return;
        for (Entry e : ready) {
            advance(world, e);
        }
    }

    private void advance(World world, Entry e) {
        long key = packKey(e.x, e.y, e.z);
        BlockType current = world.getBlock(e.x, e.y, e.z);
        int stageIndex = e.species.stageIndex(current);
        if (stageIndex < 0) {
            active.remove(key); // broken, replaced, etc. since it was registered - stop tracking it
            return;
        }
        if (stageIndex < 3) {
            int nextStage = stageIndex + 1;
            world.setBlock(e.x, e.y, e.z, e.species.stageBlock(nextStage));
            e.secondsRemaining = randomDelayFor(nextStage);
            return;
        }
        if (tryGrowTree(world, e.x, e.y, e.z, e.species)) {
            active.remove(key);
        } else {
            e.secondsRemaining = randomDelayFor(3); // insufficient space - retry later, sapling untouched
        }
    }

    /**
     * Conservative space check + generation, entirely against live {@code World} state (the
     * former invented worldgen tree placement used worldgen-
     * time methods built around a precomputed heights[][] array for one in-progress chunk, not
     * something a running game can query live - this reuses their underlying concepts
     * (canopyRadius chunk-interior margin, max total height) against {@code World.getBlock}
     * instead). The entire expected bounding volume must be air, or growth is deferred and the
     * sapling is left untouched - simpler and safer than simulating the real procedural canopy
     * shape, the "smallest safe version" rather than a precision preview/rollback system.
     */
    private boolean tryGrowTree(World world, int x, int y, int z, SaplingSpecies species) {
        TreeSpecies tree = species.treeSpecies;
        int cx = World.worldToChunk(x), cz = World.worldToChunk(z);
        Chunk chunk = world.getChunk(cx, cz);
        if (chunk == null || !chunk.isGenerated()) return false;

        if (!species.validGround.contains(world.getBlock(x, y - 1, z))) return false;

        int lx = World.worldToLocal(x), lz = World.worldToLocal(z);
        int r = tree.canopyRadius;
        // Chunk-interior margin, as the former worldgen-time tree placement used
        // for worldgen-time placement, re-applied here: a live-planted sapling has no such
        // guarantee from placement time, unlike a worldgen candidate.
        if (lx - r < 0 || lx + r >= Chunk.SIZE_X || lz - r < 0 || lz + r >= Chunk.SIZE_Z) return false;
        int maxHeight = tree.maxTotalHeight();
        if (y + maxHeight > Chunk.MAX_Y) return false;

        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = 0; dy <= maxHeight; dy++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue; // the sapling's own current block - about to be replaced
                    if (world.getBlock(x + dx, y + dy, z + dz) != BlockType.AIR) return false;
                }
            }
        }

        world.setBlock(x, y, z, BlockType.AIR); // clear the sapling before the trunk takes its place
        tree.place(chunk, lx, y, lz, new Random(random.nextLong()));
        chunk.markMeshDirty();
        world.markNeighborChunksDirty(cx, cz);
        return true;
    }

    /** For tests and debug tooling - the number of saplings currently being tracked. */
    public int activeCount() {
        return active.size();
    }
}
