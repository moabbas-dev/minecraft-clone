package com.example.worldgen.feature.level;

import com.example.worldgen.feature.Pos;
import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.terrain.BiomeManager;
import com.example.worldgen.terrain.BlockProperties;
import com.example.worldgen.terrain.BlockTags;

/**
 * Port of {@code net.minecraft.server.level.WorldGenRegion} (26.3) as the FEATURES status uses it (the {@code WorldGenLevel}
 * the decoration loop and the features run against). Read from the bytecode:
 * <ul>
 *     <li>the region is built for {@code ChunkPyramid.GENERATION_PYRAMID}'s FEATURES step: {@code addRequirement(
 *     STRUCTURE_STARTS, 8).addRequirement(TERRAIN, 1).blockStateWriteRadius(1)}, so {@code directDependencies} is
 *     {@code [TERRAIN, TERRAIN, STRUCTURE_STARTS x 7]}: chunks up to chessboard distance 8 can be read, the 3 x 3 around the
 *     centre are at least TERRAIN, farther ones only STRUCTURE_STARTS (their content depends on what the server has generated
 *     there; the caller supplies them through {@link ChunkLookup});</li>
 *     <li>{@code getChunk(x, z)} beyond distance 8, or {@code getChunk(x, z, BIOMES, false)} (biome reads) beyond distance 1,
 *     throws ({@code "Requested chunk unavailable during world generation"});</li>
 *     <li>{@code getBlockState(pos)} = {@code getChunk(pos).getBlockState(pos)} (a read outside the write radius only logs);</li>
 *     <li>{@code ensureCanWrite(pos)}: false (with a log line) outside chessboard radius {@code writeRadius} (1) around the
 *     centre; true otherwise (a new chunk is never {@code isUpgrading()});</li>
 *     <li>{@code setBlock(pos, state, flags, limit)}: {@code ensureCanWrite}, then {@code chunk.setBlockState(pos, state,
 *     flags)}; block entities are not modelled yet (no ported feature writes one); then, unless {@code (flags & 16) != 0},
 *     {@code state.getPostProcessPos(this, pos)} is marked for post-processing in its chunk
 *     ({@link #postProcessPos}); returns true;</li>
 *     <li>{@code getHeight(type, x, z)} = {@code getChunk(x >> 4, z >> 4).getHeight(type, x & 15, z & 15) + 1};</li>
 *     <li>{@code getBiome(pos)} = {@code biomeManager.getBiome(pos)} with {@code new BiomeManager(this,
 *     BiomeManager.obfuscateSeed(seed))} over {@code LevelReader.getNoiseBiome}, which reads
 *     {@code getChunk(quart >> 2, ..., BIOMES, false).getNoiseBiome(quartX, quartY, quartZ)};</li>
 *     <li>{@code scheduleTick(pos, fluid, delay)} goes to {@code getChunk(pos).getFluidTicks()}.</li>
 * </ul>
 */
public final class GenRegion {

    /** {@code GENERATION_PYRAMID} FEATURES {@code directDependencies().size()} (radius 0..8 readable). */
    public static final int READ_RADIUS = 8;
    /** FEATURES {@code blockStateWriteRadius}. */
    public static final int WRITE_RADIUS = 1;
    /** The radius whose chunks are at least BIOMES (the TERRAIN requirement). */
    public static final int BIOME_RADIUS = 1;

    /** The region's chunks: chessboard distance 0..{@link #READ_RADIUS} from the centre. */
    @FunctionalInterface
    public interface ChunkLookup {
        FeatureChunk chunk(int chunkX, int chunkZ);
    }

    /** Optional observer of the region's writes (tests and debugging). */
    public interface Listener {
        default void setBlock(int x, int y, int z, StateRef state) {
        }

        default void sectionWrite(int x, int y, int z, StateRef state) {
        }

        default void postProcessMark(int x, int y, int z) {
        }

        default void fluidTick(int x, int y, int z, String fluid) {
        }

        default void blockTick(int x, int y, int z, String block, int delay) {
        }
    }

    private final long seed;
    private final int centerX;
    private final int centerZ;
    private final ChunkLookup chunks;
    private final BlockTags tags;
    private final int minY;
    private final int height;
    private final int seaLevel;
    private final BiomeManager biomeManager;
    private Listener listener = new Listener() {
    };

    public GenRegion(long seed, int centerChunkX, int centerChunkZ, ChunkLookup chunks, BlockTags tags, int minY, int height,
                     int seaLevel) {
        this.seed = seed;
        this.centerX = centerChunkX;
        this.centerZ = centerChunkZ;
        this.chunks = chunks;
        this.tags = tags;
        this.minY = minY;
        this.height = height;
        this.seaLevel = seaLevel;
        this.biomeManager = new BiomeManager(this::getNoiseBiome, BiomeManager.obfuscateSeed(seed));
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public long getSeed() {
        return seed;
    }

    public int centerChunkX() {
        return centerX;
    }

    public int centerChunkZ() {
        return centerZ;
    }

    public BlockTags tags() {
        return tags;
    }

    public int getMinY() {
        return minY;
    }

    public int getHeight() {
        return height;
    }

    public int getSeaLevel() {
        return seaLevel;
    }

    /** {@code LevelHeightAccessor.getMinSectionY()}. */
    public int getMinSectionY() {
        return minY >> 4;
    }

    /** {@code LevelHeightAccessor.isOutsideBuildHeight(y)}. */
    public boolean isOutsideBuildHeight(int y) {
        return y < minY || y >= minY + height;
    }

    /** {@code LevelHeightAccessor.getSectionIndex(y)} = {@code (y >> 4) - getMinSectionY()}. */
    public int getSectionIndex(int y) {
        return (y >> 4) - getMinSectionY();
    }

    /** {@code LevelHeightAccessor.getSectionsCount()} = {@code getMaxSectionY() - getMinSectionY() + 1}. */
    public int getSectionsCount() {
        return ((minY + height - 1) >> 4) - getMinSectionY() + 1;
    }

    private int distance(int chunkX, int chunkZ) {
        return Math.max(Math.abs(chunkX - centerX), Math.abs(chunkZ - centerZ));
    }

    /** {@code getChunk(chunkX, chunkZ)} (status EMPTY: any chunk of the region). */
    public FeatureChunk getChunk(int chunkX, int chunkZ) {
        if (distance(chunkX, chunkZ) > READ_RADIUS) {
            throw new IllegalStateException("Requested chunk unavailable during world generation: " + chunkX + ", " + chunkZ
                    + " (centre " + centerX + ", " + centerZ + ")");
        }
        FeatureChunk chunk = chunks.chunk(chunkX, chunkZ);
        if (chunk == null) {
            throw new IllegalStateException("the region was not given chunk " + chunkX + ", " + chunkZ);
        }
        return chunk;
    }

    public FeatureChunk getChunkAt(int x, int z) {
        return getChunk(x >> 4, z >> 4);
    }

    public StateRef getBlockState(int x, int y, int z) {
        return getChunkAt(x, z).getBlockState(x, y, z);
    }

    public StateRef getBlockState(Pos pos) {
        return getBlockState(pos.x(), pos.y(), pos.z());
    }

    /** {@code LevelReader.isEmptyBlock(pos)} = {@code getBlockState(pos).isAir()}. */
    public boolean isEmptyBlock(Pos pos) {
        return BlockProperties.isAir(getBlockState(pos));
    }

    public boolean isWithinWriteZone(int x, int z) {
        return Math.abs(centerX - (x >> 4)) <= WRITE_RADIUS && Math.abs(centerZ - (z >> 4)) <= WRITE_RADIUS;
    }

    public boolean ensureCanWrite(int x, int y, int z) {
        return isWithinWriteZone(x, z);
    }

    /** {@code setBlock(pos, state, flags)} (update limit 512 is irrelevant here). */
    public boolean setBlock(int x, int y, int z, StateRef state, int flags) {
        if (!ensureCanWrite(x, y, z)) return false;
        BlockProperties.entry(state.id());
        getChunkAt(x, z).setBlockState(x, y, z, state);
        listener.setBlock(x, y, z, state);
        if ((flags & 16) == 0) {
            Pos mark = postProcessPos(state, x, y, z);
            if (mark != null) markPosForPostProcessing(mark.x(), mark.y(), mark.z());
        }
        return true;
    }

    public boolean setBlock(Pos pos, StateRef state, int flags) {
        return setBlock(pos.x(), pos.y(), pos.z(), state, flags);
    }

    /**
     * {@code BlockStateBase.getPostProcessPos(level, pos)} = {@code properties.postProcess}: {@code Properties} defaults to
     * a lambda returning null; {@code Blocks.<clinit>} sets {@code postProcessSelf} (returns {@code pos}) on
     * {@code BROWN_MUSHROOM}, {@code RED_MUSHROOM} and {@code SOUL_SAND}, and {@code postProcessAbove} (returns
     * {@code pos.above()}) on {@code MAGMA_BLOCK}.
     */
    static Pos postProcessPos(StateRef state, int x, int y, int z) {
        return switch (state.id()) {
            case "minecraft:brown_mushroom", "minecraft:red_mushroom", "minecraft:soul_sand" -> new Pos(x, y, z);
            case "minecraft:magma_block" -> new Pos(x, y + 1, z);
            default -> null;
        };
    }

    /** {@code getChunk(pos).markPosForPostProcessing(pos)}. */
    public void markPosForPostProcessing(int x, int y, int z) {
        getChunkAt(x, z).markPosForPostProcessing(x, y, z);
        if (!isOutsideBuildHeight(y)) listener.postProcessMark(x, y, z);
    }

    /** {@code scheduleTick(pos, fluid, 0)} (fluid ticks of the chunk holding {@code pos}). */
    public void scheduleFluidTick(int x, int y, int z, String fluid) {
        getChunkAt(x, z).scheduleFluidTick(x, y, z, fluid);
        listener.fluidTick(x, y, z, fluid);
    }

    /**
     * {@code LevelAccessor.scheduleTick(pos, block, delay)} = {@code getBlockTicks().schedule(createTick(pos, block, delay))}
     * ({@code WorldGenRegion.blockTicks} forwards to the chunk's block ticks). {@code createTick} also stamps the tick with
     * {@code getGameTime() + delay} and {@code nextSubTickCount()}, server state that is not modelled; the block, the
     * position and the delay are recorded.
     */
    public void scheduleBlockTick(int x, int y, int z, String block, int delay) {
        getChunkAt(x, z).scheduleBlockTick(x, y, z, block, delay);
        listener.blockTick(x, y, z, block, delay);
    }

    /**
     * {@code WorldGenRegion.getRandom()}: the region's own random, {@code randomState.getOrCreateRandomFactory(
     * "worldgen_region_random").at(centre)}, which every user in the chunk shares; its state at a given call depends on all
     * earlier users, including unported features, so it is not modelled ({@link UnportedFeature.Reached}).
     */
    public com.example.worldgen.random.RandomSource getRandom() {
        throw new com.example.worldgen.feature.UnportedFeature.Reached("WorldGenRegion.getRandom (region random) is not modelled");
    }

    /**
     * {@code LevelReader.getRawBrightness(pos, amount)} = {@code getLightEngine().getRawBrightness(pos, amount)};
     * {@code WorldGenRegion.getLightEngine()} returns the server level's light engine, whose state for a chunk at the
     * FEATURES status depends on the server (sky light sections of neighbouring, possibly lit chunks), so it is not modelled.
     */
    public int getRawBrightness(Pos pos, int amount) {
        throw new com.example.worldgen.feature.UnportedFeature.Reached("light (getRawBrightness) during FEATURES is not modelled");
    }

    /** {@code getHeight(type, x, z)}: the first free Y above the column's highest {@code type} block. */
    public int getHeight(HeightmapType type, int x, int z) {
        return getChunkAt(x, z).getHeight(type, x, z) + 1;
    }

    /** {@code LevelReader.getNoiseBiome(quartX, quartY, quartZ)} through {@code getChunk(..., BIOMES, false)}. */
    public String getNoiseBiome(int quartX, int quartY, int quartZ) {
        int chunkX = quartX >> 2;
        int chunkZ = quartZ >> 2;
        if (distance(chunkX, chunkZ) > BIOME_RADIUS) {
            throw new IllegalStateException("Requested chunk unavailable during world generation: " + chunkX + ", " + chunkZ
                    + " at status biomes (centre " + centerX + ", " + centerZ + ")");
        }
        return getChunk(chunkX, chunkZ).getNoiseBiome(quartX, quartY, quartZ);
    }

    /** {@code getBiome(pos)}. */
    public String getBiome(int x, int y, int z) {
        return biomeManager.getBiome(x, y, z);
    }

    public String getBiome(Pos pos) {
        return getBiome(pos.x(), pos.y(), pos.z());
    }

    Listener listener() {
        return listener;
    }
}
