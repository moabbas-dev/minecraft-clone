package com.example.world;

import lombok.Getter;
import lombok.Setter;

import com.example.worldgen.biome.BiomeDefinition;
import com.example.worldgen.terrain.BiomeManager;

import java.io.File;
import java.util.HashMap;
import java.util.List;
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
        this.biomeManager = new BiomeManager(this::noiseBiome, generator.biomeZoomSeed());
    }

    public WorldGenerator generator() {
        return generator;
    }

    /** The level's {@code BiomeManager}: the fiddled zoom over {@link #noiseBiome}, seeded with {@code obfuscateSeed(seed)}. */
    private final BiomeManager biomeManager;

    /**
     * {@code LevelReader.getNoiseBiome}: the quart biome stored in the chunk when it is loaded, otherwise what the generator's BIOMES
     * status stores for that chunk; null for a generator without biomes.
     */
    public String noiseBiome(int quartX, int quartY, int quartZ) {
        Chunk c = getChunk(quartX >> 2, quartZ >> 2);
        if (c != null && c.hasBiomes()) return c.getNoiseBiome(quartX, quartY, quartZ);
        return generator.noiseBiome(quartX, quartY, quartZ);
    }

    /** {@code Level.getBiome(pos)} ({@code BiomeManager.getBiome}): the vanilla biome id at a block, or null without a biome model (flat). */
    public String getBiome(int x, int y, int z) {
        return generator.hasBiomes() ? biomeManager.getBiome(x, y, z) : null;
    }

    /** The biome file of the biome at a block ({@code Level.getBiome(pos).value()}); null without a biome model. */
    public BiomeDefinition getBiomeDefinition(int x, int y, int z) {
        String id = getBiome(x, y, z);
        return id == null ? null : generator.biomeDefinition(id);
    }

    /**
     * {@code ClientLevel.getPrecipitationAt(pos)}: NONE where the column's chunk is not loaded, else the biome's
     * {@code getPrecipitationAt(pos, seaLevel)} (no precipitation without {@code has_precipitation}; snow below the temperature
     * 0.15 after the frozen modifier and the height adjustment above sea level + 17, rain otherwise).
     */
    public BiomeDefinition.Precipitation getPrecipitationAt(int x, int y, int z) {
        if (!hasChunk(worldToChunk(x), worldToChunk(z))) return BiomeDefinition.Precipitation.NONE;
        BiomeDefinition biome = getBiomeDefinition(x, y, z);
        if (biome == null) return BiomeDefinition.Precipitation.NONE;
        return biome.getPrecipitationAt(x, y, z, generator.seaLevel(), generator.biomeNoises());
    }

    /** The biome atmosphere attributes the camera probe reads at a position ({@link BiomeAttributes#sample}). */
    public BiomeAttributes.Values atmosphereAt(double x, double y, double z) {
        if (!generator.hasBiomes()) return BiomeAttributes.Values.overworld();
        return BiomeAttributes.sample(x, y, z, this::clientNoiseBiome, generator::biomeDefinition);
    }

    /**
     * The client level's {@code getNoiseBiome}: a loaded chunk's stored quart biome, otherwise
     * {@code ClientLevel.getUncachedNoiseBiome}, which is always plains.
     */
    private String clientNoiseBiome(int quartX, int quartY, int quartZ) {
        Chunk c = getChunk(quartX >> 2, quartZ >> 2);
        return c != null && c.hasBiomes() ? c.getNoiseBiome(quartX, quartY, quartZ) : com.example.worldgen.biome.Biomes.PLAINS;
    }

    /** The video option "Biome Blend" (vanilla {@code Options.biomeBlendRadius}, 0..7): the tint box radius in blocks. */
    private int biomeBlendRadius = 2;

    public int getBiomeBlendRadius() {
        return biomeBlendRadius;
    }

    public void setBiomeBlendRadius(int radius) {
        this.biomeBlendRadius = Math.max(0, Math.min(7, radius));
    }

    /** Post-processing marks of generated chunks waiting for their 3 x 3 neighbourhood (vanilla runs them when a chunk starts ticking). */
    private final Map<Long, List<int[]>> pendingPostProcessing = new HashMap<>();

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
        forgetPostProcessing(cx, cz);
    }

    /**
     * Drops the post-processing marks of a chunk that is unloaded before its neighbourhood was complete: they are not saved, and a
     * chunk restored from disk gets fluid ticks for every unsettled fluid anyway ({@link FluidSim#chunkRestored}).
     */
    public void forgetPostProcessing(int cx, int cz) {
        pendingPostProcessing.remove(Chunk.key(cx, cz));
    }

    /** Blocks of trees that grew in a neighbouring chunk and fall into a chunk that is not generated yet, keyed by that chunk. */
    private final Map<Long, Map<Long, BlockType>> pendingSpill = new HashMap<>();

    private static long localKey(int lx, int y, int lz) {
        return ((long) (y - Chunk.MIN_Y) << 8) | ((long) lz << 4) | lx;
    }

    /**
     * One block of a tree rooted in another chunk that lands in chunk (cx, cz) at local (lx, y, lz). A generated chunk takes it at once
     * (where the cell is free for a tree: air, leaves or water); one that is not generated yet keeps it - on disk too, so a world closed
     * in between does not lose it - until it is generated or loaded. So a tree is never cut off at a chunk border, whichever side is
     * generated first.
     */
    public void spill(int cx, int cz, int lx, int y, int lz, BlockType type) {
        if (!Chunk.inWorld(y)) return;
        Chunk c = getChunk(cx, cz);
        if (c != null && c.isGenerated()) {
            if (!TreePlanner.replaceableByTree(c.getLocal(lx, y, lz))) return;
            if (ChunkMeshBuilder.isAnyLeaves(type) && ChunkMeshBuilder.isAnyLeaves(c.getLocal(lx, y, lz))) return;
            setBlock(c.worldOriginX() + lx, y, c.worldOriginZ() + lz, type);
            return;
        }
        pendingSpill.computeIfAbsent(Chunk.key(cx, cz), k -> new java.util.LinkedHashMap<>()).put(localKey(lx, y, lz), type);
        if (saveDir != null) WorldIO.appendSpill(saveDir, cx, cz, lx, y, lz, type);
    }

    /** Writes the tree blocks that other chunks' trees left for this one (in memory and from disk) into a chunk that is being generated or loaded. */
    private void applyPendingSpill(Chunk chunk) {
        Map<Long, BlockType> blocks = new java.util.LinkedHashMap<>();
        if (saveDir != null) {
            for (int[] r : WorldIO.takeSpill(saveDir, chunk.chunkX, chunk.chunkZ)) blocks.put(localKey(r[0], r[1], r[2]), BlockType.byId(r[3]));
        }
        Map<Long, BlockType> memory = pendingSpill.remove(Chunk.key(chunk.chunkX, chunk.chunkZ));
        if (memory != null) blocks.putAll(memory);
        for (Map.Entry<Long, BlockType> e : blocks.entrySet()) {
            long k = e.getKey();
            int lx = (int) (k & 15), lz = (int) ((k >> 4) & 15), y = (int) (k >> 8) + Chunk.MIN_Y;
            BlockType current = chunk.getLocal(lx, y, lz);
            if (!TreePlanner.replaceableByTree(current)) continue;
            if (ChunkMeshBuilder.isAnyLeaves(e.getValue()) && ChunkMeshBuilder.isAnyLeaves(current)) continue;
            chunk.setLocal(lx, y, lz, e.getValue());
        }
    }

    /** Chunks whose save file exists but cannot be read (legacy formats): they are generated again, never retried as saved. */
    private final java.util.Set<Long> unreadableSaves = new java.util.HashSet<>();
    /** Chunks that became FULL (generated or loaded) since the last {@link #drainNewlyReady}. */
    private final List<Chunk> newlyReady = new java.util.ArrayList<>();
    /** Wall time spent in {@link #generateStep} and how many chunks it made FULL (the amortised cost per FULL chunk). */
    private long generationNanos;
    private long generationFullChunks;

    /** The chunks that became FULL since the last call, in that order (the game registers saplings and spawns animals for them). */
    public List<Chunk> drainNewlyReady() {
        List<Chunk> out = new java.util.ArrayList<>(newlyReady);
        newlyReady.clear();
        return out;
    }

    public long generationNanos() {
        return generationNanos;
    }

    public long generationFullChunks() {
        return generationFullChunks;
    }

    /** The saved blocks of a chunk, or null when it has no readable save. */
    private short[] readSave(int cx, int cz) {
        if (saveDir == null || unreadableSaves.contains(Chunk.key(cx, cz)) || !WorldIO.chunkExists(saveDir, cx, cz)) return null;
        short[] saved = WorldIO.loadChunk(saveDir, cx, cz);
        if (saved == null) unreadableSaves.add(Chunk.key(cx, cz));
        return saved;
    }

    /**
     * Loads chunk (cx, cz) from its save into the world when it is not loaded and has a readable save (it is FULL then): a neighbour a
     * decoration reads or writes. Returns the loaded chunk, or null when there is no save.
     */
    Chunk loadSavedChunk(int cx, int cz) {
        Chunk c = getChunk(cx, cz);
        if (c != null && c.isGenerated()) return c;
        if (generator.hasTerrain(cx, cz)) return null; // a proto exists only for a chunk without save
        short[] saved = readSave(cx, cz);
        if (saved == null) return null;
        c = getOrCreateChunk(cx, cz);
        restore(c, saved);
        return c;
    }

    /**
     * One unit of work towards making chunk (cx, cz) FULL: load it from its save, or load one saved neighbour or generate one TERRAIN
     * chunk of its 5 x 5 neighbourhood ({@link WorldGenerator#dependencyRadius}), or - when they are all there - decorate it. Returns
     * the chunk once it is FULL, null when more steps are needed. Used by the streaming to spread the work over frames.
     */
    public Chunk generateStep(int cx, int cz) {
        Chunk c = getChunk(cx, cz);
        if (c != null && c.isGenerated()) return c;
        long start = System.nanoTime();
        try {
            if (!generator.hasTerrain(cx, cz)) {
                Chunk loaded = loadSavedChunk(cx, cz);
                if (loaded != null) return loaded;
            }
            if (prepareOneDependency(cx, cz)) return null;
            c = getOrCreateChunk(cx, cz);
            generateChunk(c);
            return c;
        } finally {
            generationNanos += System.nanoTime() - start;
            c = getChunk(cx, cz);
            if (c != null && c.isGenerated()) generationFullChunks++;
        }
    }

    /** Offsets of the dependency square, nearest first (the centre first). */
    private int[][] dependencyOffsets;

    private int[][] dependencyOffsets(int r) {
        if (dependencyOffsets == null || dependencyOffsets.length != (2 * r + 1) * (2 * r + 1)) {
            List<int[]> list = new java.util.ArrayList<>();
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) list.add(new int[]{dx, dz});
            }
            list.sort(java.util.Comparator.comparingInt(o -> o[0] * o[0] + o[1] * o[1]));
            dependencyOffsets = list.toArray(new int[0][]);
        }
        return dependencyOffsets;
    }

    /** Loads one saved neighbour or generates one TERRAIN chunk of chunk (cx, cz)'s dependency square; false when none is missing. */
    private boolean prepareOneDependency(int cx, int cz) {
        int r = generator.dependencyRadius();
        if (r <= 0) return false;
        for (int[] o : dependencyOffsets(r)) {
            int nx = cx + o[0], nz = cz + o[1];
            Chunk n = getChunk(nx, nz);
            if (n != null && n.isGenerated()) continue;
            if (generator.hasTerrain(nx, nz)) continue;
            if ((o[0] != 0 || o[1] != 0) && loadSavedChunk(nx, nz) != null) return true;
            if (generator.ensureTerrain(nx, nz, saveDir)) return true;
        }
        return false;
    }

    /** Lets the generator drop the TERRAIN chunks it holds farther than {@code radius} chunks (chessboard) from the given chunk. */
    public void evictTerrain(int centerX, int centerZ, int radius) {
        generator.evictTerrain(centerX, centerZ, radius, saveDir);
    }

    /** Saves the generator's state that must survive a save (decoration journals of TERRAIN chunks). */
    public void saveGenerationState() {
        generator.saveGenerationState(saveDir);
    }

    private void restore(Chunk chunk, short[] saved) {
        chunk.importBlocks(saved);
        fluids.chunkRestored(this, chunk);
        finishChunk(chunk, List.of());
    }

    /**
     * Makes a chunk FULL synchronously: restored from disk if it was saved, otherwise its saved neighbours are loaded and its 5 x 5
     * TERRAIN neighbourhood generated as needed, then it is decorated ({@link WorldGenerator#generate}). Does nothing for a chunk that
     * is FULL already.
     */
    public void generateChunk(Chunk chunk) {
        if (chunk.isGenerated()) return;
        short[] saved = generator.hasTerrain(chunk.chunkX, chunk.chunkZ) ? null : readSave(chunk.chunkX, chunk.chunkZ);
        if (saved != null) {
            restore(chunk, saved);
            return;
        }
        while (prepareOneDependency(chunk.chunkX, chunk.chunkZ)) {
            // load saved neighbours and generate the TERRAIN chunks the decoration needs
        }
        generator.generate(chunk, this);
        chunk.setFreshlyGenerated(true);
        List<int[]> marks = generator.takePostProcessing(chunk);
        if (!marks.isEmpty()) pendingPostProcessing.put(Chunk.key(chunk.chunkX, chunk.chunkZ), marks);
        finishChunk(chunk, generator.takeFluidTicks(chunk));
    }

    private void finishChunk(Chunk chunk, List<int[]> fluidTicks) {
        applyPendingSpill(chunk);
        generator.computeBiomes(chunk); // biomes and tint are not persisted, so these run on both the load and generate paths
        generator.computeTint(chunk, this);
        chunk.markGenerated();
        chunk.markMeshDirty();
        // Neighbors may have already been meshed while this chunk was still unloaded (their
        // border faces were drawn against "air"). Now that real terrain exists here, those
        // neighbor meshes are stale and must be rebuilt or they'll show phantom/z-fighting faces.
        markNeighborChunksDirty(chunk.chunkX, chunk.chunkZ);
        // ProtoChunkTicks: the fluid ticks the decoration scheduled run with the level's ticks (delay 0: on the next tick).
        for (int[] t : fluidTicks) fluids.schedule(t[0], t[1], t[2], 0);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) runPostProcessingIfReady(chunk.chunkX + dx, chunk.chunkZ + dz);
        }
        newlyReady.add(chunk);
    }

    /**
     * A post-processing mark a decoration made in a chunk that is FULL already (see {@code EngineFeatureChunk}): it waits with the
     * chunk's other marks for its 3 x 3 neighbourhood and then gives the fluid there one tick, as the chunk's own marks do.
     */
    void addPostProcessingMark(Chunk chunk, int x, int y, int z) {
        pendingPostProcessing.computeIfAbsent(Chunk.key(chunk.chunkX, chunk.chunkZ), k -> new java.util.ArrayList<>()).add(new int[]{x, y, z});
    }

    /**
     * A block a decoration writes into a chunk that is FULL already ({@code WorldGenRegion.setBlock}, which makes no neighbour
     * updates): the block is set, the light updated and the meshes (the chunk's and, on a border, the neighbour's) marked dirty; no
     * {@link BlockSupport} or fluid update follows.
     */
    void setBlockFromFeature(int x, int y, int z, BlockType type) {
        int cx = worldToChunk(x), cz = worldToChunk(z);
        Chunk c = getChunk(cx, cz);
        if (c == null || !c.isGenerated()) throw new IllegalStateException("chunk " + cx + "," + cz + " is not FULL");
        int lx = worldToLocal(x), lz = worldToLocal(z);
        BlockType previous = c.getLocal(lx, y, lz);
        c.setLocal(lx, y, lz, type);
        c.markMeshDirty();
        if (previous != type) {
            int oldOverride = lightEmissionOverride(x, y, z);
            if (oldOverride > 0) emissionOverrides.remove(posKey(x, y, z));
            if (c.isLightComputed()) LightEngine.onBlockChanged(this, x, y, z, previous, type, oldOverride);
        }
        if (lx == 0) markNeighborDirty(cx - 1, cz);
        if (lx == Chunk.SIZE_X - 1) markNeighborDirty(cx + 1, cz);
        if (lz == 0) markNeighborDirty(cx, cz - 1);
        if (lz == Chunk.SIZE_Z - 1) markNeighborDirty(cx, cz + 1);
    }

    /**
     * {@code LevelChunk.postProcessGeneration}, run once the chunk and its eight neighbours are generated (vanilla runs it when the
     * chunk becomes a ticking chunk, which needs its neighbours loaded): for every marked position, in mark order, a fluid there gets
     * one fluid tick at once ({@code FluidState.tick}). The other branches (the liquid block's bubble-column tick,
     * {@code Block.updateFromNeighbourShapes} for non-liquids) change nothing for the blocks the terrain stages mark: they only mark
     * fluids, and this engine has no bubble columns.
     */
    private void runPostProcessingIfReady(int cx, int cz) {
        List<int[]> marks = pendingPostProcessing.get(Chunk.key(cx, cz));
        if (marks == null) return;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Chunk n = getChunk(cx + dx, cz + dz);
                if (n == null || !n.isGenerated()) return;
            }
        }
        pendingPostProcessing.remove(Chunk.key(cx, cz));
        for (int[] p : marks) {
            if (getBlock(p[0], p[1], p[2]).isFluid()) fluids.tickNow(this, p[0], p[1], p[2]);
        }
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
        if (!Chunk.inWorld(y)) return BlockType.AIR;
        int cx = worldToChunk(x);
        int cz = worldToChunk(z);
        Chunk c = getChunk(cx, cz);
        if (c == null || !c.isGenerated()) return BlockType.AIR;
        return c.getLocal(worldToLocal(x), y, worldToLocal(z));
    }

    /** Highest solid block's Y at this column, or {@code Chunk.MIN_Y - 1} if the chunk isn't loaded or the column is empty (used by rain particles). */
    public int getColumnHeightAt(int worldX, int worldZ) {
        Chunk c = getChunk(worldToChunk(worldX), worldToChunk(worldZ));
        if (c == null || !c.isGenerated()) return Chunk.MIN_Y - 1;
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
        if (!Chunk.inWorld(y)) return 15;
        Chunk c = getChunk(worldToChunk(x), worldToChunk(z));
        if (c == null || !c.isGenerated() || !c.isLightComputed()) return 15;
        return c.getSkyLight(worldToLocal(x), y, worldToLocal(z));
    }

    public int getBlockLight(int x, int y, int z) {
        if (!Chunk.inWorld(y)) return 0;
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
        if (!Chunk.inWorld(y)) return 15;
        return Math.max(getBlockLight(x, y, z), getSkyLight(x, y, z) - amount);
    }

    /** {@code getRawBrightness(pos)} with the current sky darkness: the light a mob at this cell is in. */
    public int getRawBrightness(int x, int y, int z) {
        return getRawBrightness(x, y, z, skyDarken);
    }

    /** Extra block light a position gives off on top of its block's own (a burning furnace), keyed by packed position. */
    private final Map<Long, Integer> emissionOverrides = new HashMap<>();

    private static long posKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) | (((long) z & 0x3FFFFFFL) << 26) | (((long) (y - Chunk.MIN_Y) & 0x1FFL) << 52);
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
        if (!Chunk.inWorld(y)) return;
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
