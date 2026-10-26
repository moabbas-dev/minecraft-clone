package com.example.world;

import com.example.worldgen.feature.level.FeatureChunk;
import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.terrain.BlockProperties;
import com.example.worldgen.terrain.BlockTags;
import com.example.worldgen.terrain.Heightmap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * {@link FeatureChunk} over an engine {@link Chunk} that is already FULL (generated or loaded from disk), for the decoration of a
 * neighbouring chunk (see {@code VanillaChunkGenerator}). All coordinates are world coordinates.
 * <ul>
 *     <li>blocks: an engine block reads as the vanilla state it stands for ({@link VanillaBlocks#vanillaId} and
 *     {@link VanillaBlocks#vanillaProperties}); a written state becomes the engine block {@link VanillaBlocks#byVanilla} gives, and
 *     a state the engine has no block for throws {@link VanillaBlocks.UnsupportedBlockStateException}. The only lossy mapping is
 *     leaves' {@code distance}/{@code persistent}, which read back as their defaults (the engine has no leaf decay);</li>
 *     <li>writes go through {@link World#setBlockFromFeature}: the engine light is updated and the meshes are marked dirty, but there
 *     are no neighbour updates (no {@link BlockSupport}, no fluid scheduling), as {@code WorldGenRegion.setBlock} makes none;</li>
 *     <li>heightmaps: the four final ones ({@code WORLD_SURFACE, OCEAN_FLOOR, MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES},
 *     {@link Heightmap#isOpaque} predicates). The engine keeps no per-type heightmap, so a column is scanned from the blocks the first
 *     time it is asked for, and from then on it is maintained by {@code Heightmap.update}'s rules on {@link #setBlockState} (the raw
 *     section write leaves it alone, as {@code LevelChunkSection.setBlockState} does). A heightmap that was maintained by those rules
 *     since the chunk was empty always equals the scan except after a raw section write that changed a cell's predicate at or above
 *     the column's height; the only raw writer, {@code OreFeature}, replaces a target that blocks motion with an ore that does too,
 *     and {@code VanillaChunkGenerator} checks every decorated chunk's heightmaps against the scan when it becomes FULL;</li>
 *     <li>post-processing marks and fluid ticks go to the {@link World} ({@link World#addPostProcessingMark},
 *     {@link FluidSim#schedule}); a scheduled block tick throws (the engine has no scheduled block ticks).</li>
 * </ul>
 */
final class EngineFeatureChunk implements FeatureChunk {

    /**
     * The four final heightmaps (maintained on {@link #setBlockState}), then the two worldgen ones: {@code ChunkAccess.getHeight}
     * primes a heightmap the chunk does not keep from its blocks ({@code Heightmap.primeHeightmaps}), and nothing updates it after.
     */
    private static final HeightmapType[] FINAL = {HeightmapType.WORLD_SURFACE, HeightmapType.OCEAN_FLOOR,
            HeightmapType.MOTION_BLOCKING, HeightmapType.MOTION_BLOCKING_NO_LEAVES, HeightmapType.WORLD_SURFACE_WG,
            HeightmapType.OCEAN_FLOOR_WG};
    private static final int MAINTAINED = 4;
    private static final int UNKNOWN = Integer.MIN_VALUE;
    private static final StateRef[] STATES = new StateRef[BlockType.values().length];

    private final World world;
    private final Chunk chunk;
    private final BlockTags tags;
    private final Map<StateRef, BlockType> types;
    /** Per final heightmap type: the column's {@code firstAvailable} (highest matching y + 1), or {@link #UNKNOWN}. */
    private final int[][] heights = new int[FINAL.length][256];
    /** Per final heightmap type and engine block: 0 unknown, 1 opaque, 2 not. */
    private final byte[][] opaque = new byte[FINAL.length][BlockType.values().length];

    EngineFeatureChunk(World world, Chunk chunk, BlockTags tags, Map<StateRef, BlockType> typeCache) {
        this.world = world;
        this.chunk = chunk;
        this.tags = tags;
        this.types = typeCache;
        for (int[] h : heights) Arrays.fill(h, UNKNOWN);
    }

    /** The vanilla state an engine block stands for. */
    static StateRef stateOf(BlockType t) {
        StateRef s = STATES[t.ordinal()];
        if (s == null) {
            if (!VanillaBlocks.isVanillaBlock(t)) {
                throw new VanillaBlocks.UnsupportedBlockStateException(t.name(), Map.of(),
                        "engine block " + t + " has no vanilla state (an item or an engine-internal state) and was read by world generation");
            }
            s = new StateRef(VanillaBlocks.vanillaId(t), VanillaBlocks.vanillaProperties(t));
            STATES[t.ordinal()] = s;
        }
        return s;
    }

    BlockType typeOf(StateRef state, int x, int y, int z) {
        BlockType t = types.get(state);
        if (t != null) return t;
        try {
            t = VanillaBlocks.byVanilla(state.id(), state.properties());
        } catch (VanillaBlocks.UnsupportedBlockStateException e) {
            throw new VanillaBlocks.UnsupportedBlockStateException(state.id(), state.properties(),
                    "written by a feature at " + x + "," + y + "," + z + " and the engine has no block for it (" + e.getMessage() + ")");
        }
        types.put(state, t);
        return t;
    }

    private static int typeIndex(HeightmapType type) {
        for (int i = 0; i < FINAL.length; i++) if (FINAL[i] == type) return i;
        throw new IllegalArgumentException("unknown heightmap " + type);
    }

    private boolean isOpaque(int t, BlockType b) {
        byte v = opaque[t][b.ordinal()];
        if (v == 0) {
            v = Heightmap.isOpaque(FINAL[t], stateOf(b), tags) ? (byte) 1 : (byte) 2;
            opaque[t][b.ordinal()] = v;
        }
        return v == 1;
    }

    /** The highest y of the column whose block counts for heightmap {@code t} (from the blocks), or {@code MIN_Y - 1}. */
    int scanHighest(int t, int lx, int lz) {
        for (int y = Math.min(Chunk.MAX_Y, chunk.getColumnHeight(lx, lz)); y >= Chunk.MIN_Y; y--) {
            if (isOpaque(t, chunk.getLocal(lx, y, lz))) return y;
        }
        return Chunk.MIN_Y - 1;
    }

    /** {@link #scanHighest} for a heightmap type. */
    int scanHighest(HeightmapType type, int lx, int lz) {
        return scanHighest(typeIndex(type), lx, lz);
    }

    @Override
    public int chunkX() {
        return chunk.chunkX;
    }

    @Override
    public int chunkZ() {
        return chunk.chunkZ;
    }

    @Override
    public StateRef getBlockState(int x, int y, int z) {
        if (!Chunk.inWorld(y)) return BlockProperties.VOID_AIR;
        return stateOf(chunk.getLocal(x & 15, y, z & 15));
    }

    @Override
    public void setBlockState(int x, int y, int z, StateRef state) {
        if (!Chunk.inWorld(y)) return;
        BlockType t = typeOf(state, x, y, z);
        world.setBlockFromFeature(x, y, z, t);
        int lx = x & 15, lz = z & 15, col = lz * 16 + lx;
        for (int i = 0; i < MAINTAINED; i++) {
            int firstAvailable = heights[i][col];
            if (firstAvailable == UNKNOWN || y <= firstAvailable - 2) continue;
            if (isOpaque(i, t)) {
                if (y >= firstAvailable) heights[i][col] = y + 1;
            } else if (firstAvailable - 1 == y) {
                int h = Chunk.MIN_Y;
                for (int yy = y - 1; yy >= Chunk.MIN_Y; --yy) {
                    if (isOpaque(i, chunk.getLocal(lx, yy, lz))) {
                        h = yy + 1;
                        break;
                    }
                }
                heights[i][col] = h;
            }
        }
    }

    @Override
    public StateRef getSectionBlockState(int x, int y, int z) {
        if (!Chunk.inWorld(y)) throw new IllegalArgumentException("y outside the chunk sections: " + y);
        return stateOf(chunk.getLocal(x & 15, y, z & 15));
    }

    @Override
    public void setSectionBlockState(int x, int y, int z, StateRef state) {
        if (!Chunk.inWorld(y)) throw new IllegalArgumentException("y outside the chunk sections: " + y);
        BlockProperties.entry(state.id());
        world.setBlockFromFeature(x, y, z, typeOf(state, x, y, z));
    }

    @Override
    public int getHeight(HeightmapType type, int x, int z) {
        int t = typeIndex(type);
        int col = (z & 15) * 16 + (x & 15);
        if (heights[t][col] == UNKNOWN) heights[t][col] = scanHighest(t, x & 15, z & 15) + 1;
        return heights[t][col] - 1;
    }

    @Override
    public void markPosForPostProcessing(int x, int y, int z) {
        if (!Chunk.inWorld(y)) return;
        world.addPostProcessingMark(chunk, x, y, z);
    }

    @Override
    public void scheduleFluidTick(int x, int y, int z, String fluid) {
        world.fluids().schedule(x, y, z, 0);
    }

    @Override
    public void scheduleBlockTick(int x, int y, int z, String block, int delay) {
        throw new IllegalStateException("decoration scheduled a block tick for " + block + " at " + x + "," + y + "," + z
                + ": the engine has no scheduled block ticks for it");
    }

    @Override
    public String getNoiseBiome(int quartX, int quartY, int quartZ) {
        return chunk.getNoiseBiome(quartX, quartY, quartZ);
    }

    @Override
    public void forEachStoredBiome(Consumer<String> consumer) {
        chunk.forEachQuartBiome(consumer);
    }

    /** Shares the state-to-block cache between the adapters of one generator. */
    static Map<StateRef, BlockType> newTypeCache() {
        return new HashMap<>();
    }
}
