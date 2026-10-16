package com.example.world;

import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class World {
    public final long seed;
    private final Map<Long, Chunk> chunks = new HashMap<>();
    private final WorldGenerator generator;
    /** Null means this world is never persisted (shouldn't normally happen once a world exists on disk). */
    @Setter
    @Getter
    private File saveDir;

    /** Water and lava: scheduled flow ticks, driven by the game's 20 Hz loop. */
    private final FluidSim fluids = new FluidSim();

    public FluidSim fluids() {
        return fluids;
    }

    public World(long seed, WorldGenerator generator) {
        this.seed = seed;
        this.generator = generator;
    }

    /** Climate at a world column for the debug overlay; null for generators with no climate model. */
    public ClimateSample climateAt(int worldX, int worldZ) {
        return generator.climateAt(worldX, worldZ);
    }

    public static int worldToChunk(int worldCoord) {
        return Math.floorDiv(worldCoord, Chunk.SIZE_X);
    }

    public static int worldToLocal(int worldCoord) {
        return Math.floorMod(worldCoord, Chunk.SIZE_X);
    }

    public Chunk getChunk(int cx, int cz) {
        return chunks.get(Chunk.key(cx, cz));
    }

    public Chunk getOrCreateChunk(int cx, int cz) {
        long key = Chunk.key(cx, cz);
        Chunk c = chunks.get(key);
        if (c == null) {
            c = new Chunk(cx, cz);
            chunks.put(key, c);
        }
        return c;
    }

    public boolean hasChunk(int cx, int cz) {
        return chunks.containsKey(Chunk.key(cx, cz));
    }

    public void removeChunk(int cx, int cz) {
        chunks.remove(Chunk.key(cx, cz));
    }

    /** Generates a fresh chunk, or restores it from disk if it was previously saved. */
    public void generateChunk(Chunk chunk) {
        byte[] saved = saveDir != null ? WorldIO.loadChunk(saveDir, chunk.chunkX, chunk.chunkZ) : null;
        if (saved != null) {
            chunk.importBlocks(saved);
            fluids.chunkRestored(this, chunk);
        } else {
            generator.generate(chunk, this);
            chunk.setFreshlyGenerated(true);
        }
        generator.computeTint(chunk); // not persisted, so this runs on both the load and generate paths
        chunk.markGenerated();
        chunk.markMeshDirty();
        // Neighbors may have already been meshed while this chunk was still unloaded (their
        // border faces were drawn against "air"). Now that real terrain exists here, those
        // neighbor meshes are stale and must be rebuilt or they'll show phantom/z-fighting faces.
        markNeighborChunksDirty(chunk.chunkX, chunk.chunkZ);
    }

    /** Marks the 4 orthogonally-adjacent chunks (if loaded) mesh-dirty so their border faces get rebuilt. */
    public void markNeighborChunksDirty(int cx, int cz) {
        markNeighborDirty(cx - 1, cz);
        markNeighborDirty(cx + 1, cz);
        markNeighborDirty(cx, cz - 1);
        markNeighborDirty(cx, cz + 1);
    }

    /** Returns AIR for unloaded chunks so chunk-edge faces still render while neighbors stream in. */
    public BlockType getBlock(int x, int y, int z) {
        if (y < 0 || y >= Chunk.SIZE_Y) return BlockType.AIR;
        int cx = worldToChunk(x);
        int cz = worldToChunk(z);
        Chunk c = getChunk(cx, cz);
        if (c == null || !c.isGenerated()) return BlockType.AIR;
        return c.getLocal(worldToLocal(x), y, worldToLocal(z));
    }

    /** Highest solid block's Y at this column, or 0 if the chunk isn't loaded (used by rain particles). */
    public int getColumnHeightAt(int worldX, int worldZ) {
        Chunk c = getChunk(worldToChunk(worldX), worldToChunk(worldZ));
        if (c == null || !c.isGenerated()) return 0;
        return c.getColumnHeight(worldToLocal(worldX), worldToLocal(worldZ));
    }

    /**
     * Returns full brightness for unloaded/not-yet-lit chunks so streaming-in terrain doesn't
     * flash dark. Gating on {@link Chunk#isLightComputed} (not just {@link Chunk#isGenerated})
     * matters: a chunk's blocks can exist for one or more frames before {@link LightEngine} has
     * actually run on it (generation and meshing are time-budgeted separately), and during that
     * window its skyLight/blockLight arrays are still all-zero. A neighbor chunk meshed in that
     * window would otherwise sample real zeros across the border - a permanently black seam,
     * since nothing revisits that neighbor once the border chunk's real light is later computed.
     */
    public int getSkyLight(int x, int y, int z) {
        if (y < 0 || y >= Chunk.SIZE_Y) return 15;
        Chunk c = getChunk(worldToChunk(x), worldToChunk(z));
        if (c == null || !c.isGenerated() || !c.isLightComputed()) return 15;
        return c.getSkyLight(worldToLocal(x), y, worldToLocal(z));
    }

    public int getBlockLight(int x, int y, int z) {
        if (y < 0 || y >= Chunk.SIZE_Y) return 0;
        Chunk c = getChunk(worldToChunk(x), worldToChunk(z));
        if (c == null || !c.isGenerated() || !c.isLightComputed()) return 0;
        return c.getBlockLight(worldToLocal(x), y, worldToLocal(z));
    }

    /** How much the sky light is dimmed for gameplay (vanilla's {@code Level.getSkyDarken}: 0 at noon, 11 on a clear night). */
    private int skyDarken;

    public int getSkyDarken() {
        return skyDarken;
    }

    public void setSkyDarken(int skyDarken) {
        this.skyDarken = Math.max(0, Math.min(15, skyDarken));
    }

    private boolean monstersBurn;

    /** The timeline's gameplay/monsters_burn: true by day, from tick 23460 to tick 12542 of the next day. */
    public boolean monstersBurn() {
        return monstersBurn;
    }

    public void setMonstersBurn(boolean burn) {
        this.monstersBurn = burn;
    }

    /** {@code Level.isBrightOutside}: the sky is dimmed by less than 4. */
    public boolean isBrightOutside() {
        return skyDarken < 4;
    }

    /** {@code getRawBrightness(pos, amount)}: the larger of the block light and the sky light dimmed by {@code amount}. */
    public int getRawBrightness(int x, int y, int z, int amount) {
        if (y < 0 || y >= Chunk.SIZE_Y) return 15;
        return Math.max(getBlockLight(x, y, z), getSkyLight(x, y, z) - amount);
    }

    /** {@code getRawBrightness(pos)} with the current sky darkness: the light a mob at this cell is in. */
    public int getRawBrightness(int x, int y, int z) {
        return getRawBrightness(x, y, z, skyDarken);
    }

    /** Extra block light a position gives off on top of its block's own (a burning furnace), keyed by packed position. */
    private final Map<Long, Integer> emissionOverrides = new HashMap<>();

    private static long posKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) | (((long) z & 0x3FFFFFFL) << 26) | (((long) y & 0xFFL) << 52);
    }

    public int lightEmissionOverride(int x, int y, int z) {
        if (emissionOverrides.isEmpty()) return 0;
        Integer v = emissionOverrides.get(posKey(x, y, z));
        return v == null ? 0 : v;
    }

    /** Sets (0 clears) the extra light a position emits and relights around it - a furnace lighting or going out. */
    public void setLightEmissionOverride(int x, int y, int z, int level) {
        int old = lightEmissionOverride(x, y, z);
        if (old == level) return;
        if (level <= 0) emissionOverrides.remove(posKey(x, y, z));
        else emissionOverrides.put(posKey(x, y, z), level);
        LightEngine.onEmissionChanged(this, x, y, z);
    }

    /** Told when a block that needed support (a torch) lost it and was removed, so the game can drop its item. */
    @FunctionalInterface
    public interface DetachListener {
        void detached(int x, int y, int z, BlockType type);
    }

    private DetachListener detachListener;

    public void setDetachListener(DetachListener listener) {
        this.detachListener = listener;
    }

    void notifyDetached(int x, int y, int z, BlockType type) {
        if (detachListener != null) detachListener.detached(x, y, z, type);
    }

    public void setBlock(int x, int y, int z, BlockType type) {
        if (y < 0 || y >= Chunk.SIZE_Y) return;
        int cx = worldToChunk(x);
        int cz = worldToChunk(z);
        Chunk c = getOrCreateChunk(cx, cz);
        int lx = worldToLocal(x);
        int lz = worldToLocal(z);
        BlockType previous = c.getLocal(lx, y, lz);
        c.setLocal(lx, y, lz, type);
        c.markMeshDirty();
        if (previous != type) {
            int oldOverride = lightEmissionOverride(x, y, z);
            if (oldOverride > 0) emissionOverrides.remove(posKey(x, y, z)); // the burning furnace is gone
            if (c.isGenerated() && c.isLightComputed()) LightEngine.onBlockChanged(this, x, y, z, previous, type, oldOverride);
            if (c.isGenerated()) BlockSupport.blockChanged(this, x, y, z);
            if (c.isGenerated()) fluids.blockChanged(this, x, y, z);
        }
        // If the edit is on a chunk border, the neighboring chunk's mesh must be rebuilt too.
        if (lx == 0) markNeighborDirty(cx - 1, cz);
        if (lx == Chunk.SIZE_X - 1) markNeighborDirty(cx + 1, cz);
        if (lz == 0) markNeighborDirty(cx, cz - 1);
        if (lz == Chunk.SIZE_Z - 1) markNeighborDirty(cx, cz + 1);
    }

    private void markNeighborDirty(int cx, int cz) {
        Chunk c = getChunk(cx, cz);
        if (c != null) c.markMeshDirty();
    }

    public boolean isSolidCollision(int x, int y, int z) {
        return getBlock(x, y, z).isSolidCollision();
    }

    public Map<Long, Chunk> chunkMap() {
        return chunks;
    }
}
