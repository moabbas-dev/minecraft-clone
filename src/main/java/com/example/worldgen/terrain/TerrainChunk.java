package com.example.worldgen.terrain;

import com.example.worldgen.Mth;
import com.example.worldgen.level.GenChunk;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.material.FluidPostProcessing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A chunk under generation with the behaviour of vanilla's {@code ProtoChunk} (26.3) that the worldgen stages observe:
 * <ul>
 *     <li>blocks: a palette of {@link StateRef} with one {@code short} index per block (16 x height x 16), plus the
 *     per-section non-air count of {@code LevelChunkSection} ({@code hasOnlyAir()} sections read as
 *     {@code minecraft:air}, outside the build height {@code minecraft:void_air}, as {@code ProtoChunk.getBlockState});</li>
 *     <li>{@link #setState}: {@code ProtoChunk.setBlockState}: a write of {@code minecraft:air} into an all-air
 *     section is dropped; then every heightmap of {@code getPersistedStatus().heightmapsAfter()} is updated, missing
 *     ones are primed ({@code Heightmap.primeHeightmaps}). {@code ChunkStatus}: statuses up to {@code BIOMES} carry
 *     {@code WORLDGEN_HEIGHTMAPS = {OCEAN_FLOOR_WG, WORLD_SURFACE_WG}}, {@code TERRAIN} and later
 *     {@code FINAL_HEIGHTMAPS = {OCEAN_FLOOR, WORLD_SURFACE, MOTION_BLOCKING, MOTION_BLOCKING_NO_LEAVES}}. Lighting
 *     updates of {@code setBlockState} (status at least {@code INITIALIZE_LIGHT}) are not part of worldgen here;</li>
 *     <li>{@link #getHeight}: {@code ChunkAccess.getHeight(type, x, z)} = {@code firstAvailable - 1}, priming the
 *     heightmap first when it does not exist;</li>
 *     <li>post-processing marks: {@code ProtoChunk.markPosForPostProcessing} (inside the build height only), kept per
 *     section as packed {@code (x & 15) | (y & 15) << 4 | (z & 15) << 8} shorts in call order, duplicates kept
 *     (vanilla uses a {@code ShortList} per section);</li>
 *     <li>biomes: one biome id per quart cell ({@code LevelChunkSection.biomes}, 4 x 4 x 4 per section), read with
 *     {@code ChunkAccess.getNoiseBiome} (quart Y clamped into the chunk).</li>
 * </ul>
 */
public final class TerrainChunk implements GenChunk, FluidPostProcessing {

    /** {@code ChunkStatus} of 26.3 in order, with {@code heightmapsAfter()}. */
    public enum Status {
        EMPTY, STRUCTURE_STARTS, STRUCTURE_REFERENCES, BIOMES, TERRAIN, FEATURES, INITIALIZE_LIGHT, LIGHT, SPAWN, FULL;

        public Set<HeightmapType> heightmapsAfter() {
            return ordinal() < TERRAIN.ordinal() ? WORLDGEN_HEIGHTMAPS : FINAL_HEIGHTMAPS;
        }

        public boolean isOrAfter(Status other) {
            return ordinal() >= other.ordinal();
        }
    }

    /** {@code ChunkStatus.WORLDGEN_HEIGHTMAPS}. */
    public static final Set<HeightmapType> WORLDGEN_HEIGHTMAPS =
            Collections.unmodifiableSet(EnumSet.of(HeightmapType.OCEAN_FLOOR_WG, HeightmapType.WORLD_SURFACE_WG));
    /** {@code ChunkStatus.FINAL_HEIGHTMAPS}. */
    public static final Set<HeightmapType> FINAL_HEIGHTMAPS = Collections.unmodifiableSet(EnumSet.of(HeightmapType.OCEAN_FLOOR,
            HeightmapType.WORLD_SURFACE, HeightmapType.MOTION_BLOCKING, HeightmapType.MOTION_BLOCKING_NO_LEAVES));

    private static final int AIR_INDEX = 0;

    private final int chunkX;
    private final int chunkZ;
    private final int minY;
    private final int height;
    private final BlockTags tags;
    private final short[] blocks;
    private final int[] nonEmptyBlockCount;
    private final List<StateRef> palette = new ArrayList<>();
    private final Map<StateRef, Integer> paletteIndex = new HashMap<>();
    /** Per heightmap type, per palette index: 0 unknown, 1 opaque, 2 not opaque. */
    private final byte[][] opaqueCache = new byte[HeightmapType.values().length][];
    private StateRef lastState;
    private int lastIndex;
    private final Map<HeightmapType, Heightmap> heightmaps = new EnumMap<>(HeightmapType.class);
    private final List<List<Short>> postProcessing;
    private final short[] biomes;
    private final List<String> biomePalette = new ArrayList<>();
    private final Map<String, Short> biomeIndex = new HashMap<>();
    private Status status = Status.EMPTY;

    public TerrainChunk(int chunkX, int chunkZ, int minY, int height, BlockTags tags) {
        if ((minY & 15) != 0 || (height & 15) != 0 || height <= 0) throw new IllegalArgumentException("bad height " + minY + "/" + height);
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.minY = minY;
        this.height = height;
        this.tags = tags;
        this.blocks = new short[16 * 16 * height];
        this.nonEmptyBlockCount = new int[height >> 4];
        this.postProcessing = new ArrayList<>(height >> 4);
        for (int i = 0; i < (height >> 4); i++) postProcessing.add(null);
        this.biomes = new short[16 * (height >> 2)];
        for (int i = 0; i < opaqueCache.length; i++) opaqueCache[i] = new byte[16];
        paletteIndexOf(BlockProperties.AIR);
    }

    @Override
    public int chunkX() {
        return chunkX;
    }

    @Override
    public int chunkZ() {
        return chunkZ;
    }

    @Override
    public int minY() {
        return minY;
    }

    @Override
    public int height() {
        return height;
    }

    public BlockTags tags() {
        return tags;
    }

    public Status getPersistedStatus() {
        return status;
    }

    public void setPersistedStatus(Status status) {
        this.status = status;
    }

    public boolean isOutsideBuildHeight(int y) {
        return y < minY || y >= minY + height;
    }

    /** {@code getSectionIndex(y)}. */
    public int getSectionIndex(int y) {
        return (y >> 4) - (minY >> 4);
    }

    /** {@code LevelChunkSection.hasOnlyAir()}. */
    public boolean sectionHasOnlyAir(int sectionIndex) {
        return nonEmptyBlockCount[sectionIndex] == 0;
    }

    /** {@code ChunkAccess.getHighestSectionPosition()}. */
    public int getHighestSectionPosition() {
        for (int i = nonEmptyBlockCount.length - 1; i >= 0; --i) {
            if (nonEmptyBlockCount[i] != 0) return ((minY >> 4) + i) << 4;
        }
        return minY;
    }

    // ------------------------------------------------------------------ palette

    int paletteIndexOf(StateRef state) {
        if (state == lastState) return lastIndex;
        Integer idx = paletteIndex.get(state);
        if (idx == null) {
            BlockProperties.entry(state.id());
            idx = palette.size();
            if (idx > Short.MAX_VALUE) throw new IllegalStateException("palette overflow");
            palette.add(state);
            paletteIndex.put(state, idx);
            for (int t = 0; t < opaqueCache.length; t++) {
                if (opaqueCache[t].length <= idx) opaqueCache[t] = java.util.Arrays.copyOf(opaqueCache[t], opaqueCache[t].length * 2);
            }
        }
        lastState = state;
        lastIndex = idx;
        return idx;
    }

    /** The state of a palette index ({@link #paletteIndexLocal}). */
    public StateRef paletteState(int index) {
        return palette.get(index);
    }

    /** Number of distinct states written so far (palette indices are {@code 0 .. paletteSize() - 1}; 0 is air). */
    public int paletteSize() {
        return palette.size();
    }

    boolean opaque(HeightmapType type, int paletteIdx) {
        byte[] c = opaqueCache[type.ordinal()];
        byte v = c[paletteIdx];
        if (v == 0) {
            v = Heightmap.isOpaque(type, palette.get(paletteIdx), tags) ? (byte) 1 : (byte) 2;
            c[paletteIdx] = v;
        }
        return v == 1;
    }

    private int index(int lx, int y, int lz) {
        return ((y - minY) * 16 + lz) * 16 + lx;
    }

    /** Palette index of what {@code ProtoChunk.getBlockState} returns inside the build height (local x, z). */
    public int paletteIndexLocal(int lx, int y, int lz) {
        if (nonEmptyBlockCount[getSectionIndex(y)] == 0) return AIR_INDEX;
        return blocks[index(lx, y, lz)];
    }

    // ------------------------------------------------------------------ blocks

    @Override
    public StateRef getState(int x, int y, int z) {
        if (isOutsideBuildHeight(y)) return BlockProperties.VOID_AIR;
        return palette.get(paletteIndexLocal(x & 15, y, z & 15));
    }

    /**
     * {@code LevelChunkSection.setBlockState(x, y, z, state, false)}: the raw section write used by {@code doFill}
     * (non-air count kept, no heightmap update). Returns the previous state.
     */
    public StateRef setStateInSection(int x, int y, int z, StateRef state) {
        int p = paletteIndexOf(state);
        int i = index(x & 15, y, z & 15);
        int old = blocks[i];
        blocks[i] = (short) p;
        int section = getSectionIndex(y);
        StateRef previous = palette.get(old);
        if (!BlockProperties.isAir(previous)) --nonEmptyBlockCount[section];
        if (!BlockProperties.isAir(state)) ++nonEmptyBlockCount[section];
        return previous;
    }

    /** {@code ProtoChunk.setBlockState(pos, state, flags)} (see the class comment). */
    @Override
    public void setState(int x, int y, int z, StateRef state) {
        if (isOutsideBuildHeight(y)) return;
        int section = getSectionIndex(y);
        boolean wasEmpty = nonEmptyBlockCount[section] == 0;
        if (wasEmpty && state.is("minecraft:air")) return;
        int lx = x & 15;
        int lz = z & 15;
        setStateInSection(lx, y, lz, state);
        Set<HeightmapType> after = status.heightmapsAfter();
        EnumSet<HeightmapType> toPrime = null;
        for (HeightmapType type : HeightmapType.values()) {
            if (!after.contains(type)) continue;
            Heightmap h = heightmaps.get(type);
            if (h == null) {
                if (toPrime == null) toPrime = EnumSet.noneOf(HeightmapType.class);
                toPrime.add(type);
            } else {
                h.update(lx, y, lz, state);
            }
        }
        if (toPrime != null) Heightmap.primeHeightmaps(this, toPrime);
    }

    // ------------------------------------------------------------------ heightmaps

    /** {@code getOrCreateHeightmapUnprimed(type)}. */
    public Heightmap getOrCreateHeightmapUnprimed(HeightmapType type) {
        return heightmaps.computeIfAbsent(type, t -> new Heightmap(this, t));
    }

    public boolean hasPrimedHeightmap(HeightmapType type) {
        return heightmaps.containsKey(type);
    }

    /** {@code Heightmap.primeHeightmaps(this, types)}. */
    public void primeHeightmaps(Set<HeightmapType> types) {
        Heightmap.primeHeightmaps(this, types);
    }

    @Override
    public int getHeight(HeightmapType type, int x, int z) {
        Heightmap h = heightmaps.get(type);
        if (h == null) {
            Heightmap.primeHeightmaps(this, EnumSet.of(type));
            h = heightmaps.get(type);
        }
        return h.getFirstAvailable(x & 15, z & 15) - 1;
    }

    // ------------------------------------------------------------------ post-processing

    /** {@code ProtoChunk.markPosForPostProcessing(pos)}. */
    @Override
    public void markPosForPostProcessing(int x, int y, int z) {
        if (isOutsideBuildHeight(y)) return;
        int s = getSectionIndex(y);
        List<Short> list = postProcessing.get(s);
        if (list == null) {
            list = new ArrayList<>();
            postProcessing.set(s, list);
        }
        list.add((short) ((x & 15) | (y & 15) << 4 | (z & 15) << 8));
    }

    /** The marked positions (world coordinates), section by section in mark order. */
    public List<int[]> postProcessingPositions() {
        List<int[]> out = new ArrayList<>();
        for (int s = 0; s < postProcessing.size(); s++) {
            List<Short> list = postProcessing.get(s);
            if (list == null) continue;
            int baseY = ((minY >> 4) + s) << 4;
            for (short packed : list) {
                out.add(new int[]{minBlockX() + (packed & 15), baseY + (packed >>> 4 & 15), minBlockZ() + (packed >>> 8 & 15)});
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ biomes

    /** {@code ChunkAccess.fillBiomesFromNoise}: sections bottom-up, then x, y, z in the section ({@code LevelChunkSection}). */
    public void fillBiomesFromNoise(BiomeResolver resolver) {
        setQuartBiomes(quartBiomesFromNoise(resolver, chunkX, chunkZ, minY, height));
    }

    /**
     * The quart biome table {@link #fillBiomesFromNoise} stores, without a chunk: the resolver is queried in the same order
     * (sections bottom-up, then x, y, z), the result is indexed {@code (quartYFromBottom * 4 + quartZ) * 4 + quartX}.
     */
    public static String[] quartBiomesFromNoise(BiomeResolver resolver, int chunkX, int chunkZ, int minY, int height) {
        String[] table = new String[16 * (height >> 2)];
        int quartMinX = (chunkX * 16) >> 2;
        int quartMinZ = (chunkZ * 16) >> 2;
        int minSection = minY >> 4;
        int maxSection = (minY + height - 1) >> 4;
        for (int sectionY = minSection; sectionY <= maxSection; ++sectionY) {
            int quartMinY = sectionY << 2;
            for (int x = 0; x < 4; ++x) {
                for (int y = 0; y < 4; ++y) {
                    for (int z = 0; z < 4; ++z) {
                        String biome = resolver.getNoiseBiome(quartMinX + x, quartMinY + y, quartMinZ + z);
                        int qy = (sectionY - minSection) * 4 + y;
                        table[(qy * 4 + z) * 4 + x] = biome;
                    }
                }
            }
        }
        return table;
    }

    /** Stores a quart biome table laid out as {@link #quartBiomesFromNoise} returns it. */
    public void setQuartBiomes(String[] table) {
        if (table.length != biomes.length) throw new IllegalArgumentException("biome table of " + table.length + " cells, expected " + biomes.length);
        for (int i = 0; i < table.length; i++) {
            biomes[i] = biomeIndexOf(table[i]);
        }
    }

    /** A copy of the quart biome table, laid out as {@link #quartBiomesFromNoise} returns it. */
    public String[] quartBiomes() {
        String[] table = new String[biomes.length];
        for (int i = 0; i < table.length; i++) {
            table[i] = biomePalette.get(biomes[i]);
        }
        return table;
    }

    private short biomeIndexOf(String biome) {
        Short i = biomeIndex.get(biome);
        if (i == null) {
            i = (short) biomePalette.size();
            biomePalette.add(biome);
            biomeIndex.put(biome, i);
        }
        return i;
    }

    /** {@code ChunkAccess.getNoiseBiome(quartX, quartY, quartZ)}: quart Y clamped into the chunk, x and z taken {@code & 3}. */
    public String getNoiseBiome(int quartX, int quartY, int quartZ) {
        int quartMinY = minY >> 2;
        int quartMaxY = quartMinY + (height >> 2) - 1;
        int clamped = Mth.clamp(quartY, quartMinY, quartMaxY);
        return biomePalette.get(biomes[((clamped - quartMinY) * 4 + (quartZ & 3)) * 4 + (quartX & 3)]);
    }
}
