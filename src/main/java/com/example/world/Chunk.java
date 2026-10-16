package com.example.world;

import lombok.Getter;
import lombok.Setter;

public class Chunk {
    public static final int SIZE_X = 16;
    public static final int SIZE_Z = 16;
    /**
     * The overworld's vertical range from the 26.3 noise settings (worldgen/noise_settings/overworld.json: min_y -64, height 384):
     * world Y runs from {@link #MIN_Y} to {@link #MAX_Y}. Callers always use real world Y (negative included); only the storage
     * index below subtracts {@link #MIN_Y}.
     */
    public static final int MIN_Y = -64;
    public static final int SIZE_Y = 384;
    public static final int MAX_Y = MIN_Y + SIZE_Y - 1;

    /** True when {@code y} is a Y the world stores. */
    public static boolean inWorld(int y) {
        return y >= MIN_Y && y <= MAX_Y;
    }

    public final int chunkX;
    public final int chunkZ;
    /** Block ids, 16 bits each (the 8-bit ids ran out of room for the vanilla blocks). */
    private final short[] blocks = new short[SIZE_X * SIZE_Y * SIZE_Z];
    /** Highest non-air Y per column ({@code MIN_Y - 1} while the column is empty), used only as a meshing early-exit hint. */
    private final int[] heightMap = new int[SIZE_X * SIZE_Z];

    /** 0-15 light levels, recomputed by LightEngine whenever the chunk's mesh is rebuilt. */
    final byte[] skyLight = new byte[SIZE_X * SIZE_Y * SIZE_Z];
    final byte[] blockLight = new byte[SIZE_X * SIZE_Z * SIZE_Y];
    /** Per column, the y of the highest block that dampens light ({@code MIN_Y - 1} for none): every cell above it is a sky light source. */
    private final int[] skyBlocker = new int[SIZE_X * SIZE_Z];

    // Per-column grass/foliage tint (RGB), computed once by the world generator - either right
    // after generating fresh terrain or right after loading saved blocks from disk, never
    // per-frame or per-vertex. ChunkMeshBuilder reads these when baking grass-top/leaf faces.
    private final float[] grassTint = new float[SIZE_X * SIZE_Z * 3];
    private final float[] foliageTint = new float[SIZE_X * SIZE_Z * 3];
    /** Same idea as grassTint/foliageTint, but for water faces - see ChunkMeshBuilder. */
    private final float[] waterTint = new float[SIZE_X * SIZE_Z * 3];

    @Getter
    private volatile boolean generated = false;
    /** True when terrain was freshly generated (not restored from a save): passive animals pack-spawn only then. */
    @Getter
    @Setter
    private volatile boolean freshlyGenerated = false;
    @Getter
    private volatile boolean meshDirty = true;
    /** True once {@link LightEngine#compute} has run at least once - see {@link World#getSkyLight}. */
    @Getter
    private volatile boolean lightComputed = false;

    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        java.util.Arrays.fill(heightMap, MIN_Y - 1);
        java.util.Arrays.fill(skyBlocker, MIN_Y - 1);
    }

    /** Storage index of a cell: {@code y} is the real world Y. */
    public static int index(int x, int y, int z) {
        return ((y - MIN_Y) * SIZE_Z + z) * SIZE_X + x;
    }

    public BlockType getLocal(int x, int y, int z) {
        if (x < 0 || x >= SIZE_X || z < 0 || z >= SIZE_Z || !inWorld(y)) return BlockType.AIR;
        return BlockType.byId(blocks[index(x, y, z)] & 0xFFFF);
    }

    public void setLocal(int x, int y, int z, BlockType type) {
        blocks[index(x, y, z)] = (short) type.id;
        int hIdx = z * SIZE_X + x;
        if (!type.isAir() && y > heightMap[hIdx]) {
            heightMap[hIdx] = y;
        }
        meshDirty = true;
    }

    public int getColumnHeight(int x, int z) {
        return heightMap[z * SIZE_X + x];
    }

    public void setGrassTint(int x, int z, float r, float g, float b) {
        int i = (z * SIZE_X + x) * 3;
        grassTint[i] = r;
        grassTint[i + 1] = g;
        grassTint[i + 2] = b;
    }

    public void setFoliageTint(int x, int z, float r, float g, float b) {
        int i = (z * SIZE_X + x) * 3;
        foliageTint[i] = r;
        foliageTint[i + 1] = g;
        foliageTint[i + 2] = b;
    }

    public float[] getGrassTint(int x, int z) {
        int i = (z * SIZE_X + x) * 3;
        return new float[]{grassTint[i], grassTint[i + 1], grassTint[i + 2]};
    }

    public float[] getFoliageTint(int x, int z) {
        int i = (z * SIZE_X + x) * 3;
        return new float[]{foliageTint[i], foliageTint[i + 1], foliageTint[i + 2]};
    }

    public void setWaterTint(int x, int z, float r, float g, float b) {
        int i = (z * SIZE_X + x) * 3;
        waterTint[i] = r;
        waterTint[i + 1] = g;
        waterTint[i + 2] = b;
    }

    public float[] getWaterTint(int x, int z) {
        int i = (z * SIZE_X + x) * 3;
        return new float[]{waterTint[i], waterTint[i + 1], waterTint[i + 2]};
    }

    int skyBlocker(int x, int z) {
        return skyBlocker[z * SIZE_X + x];
    }

    void setSkyBlocker(int x, int z, int y) {
        skyBlocker[z * SIZE_X + x] = y;
    }

    public int getSkyLight(int x, int y, int z) {
        if (x < 0 || x >= SIZE_X || z < 0 || z >= SIZE_Z || !inWorld(y)) return 15;
        return skyLight[index(x, y, z)] & 0xFF;
    }

    public int getBlockLight(int x, int y, int z) {
        if (x < 0 || x >= SIZE_X || z < 0 || z >= SIZE_Z || !inWorld(y)) return 0;
        return blockLight[index(x, y, z)] & 0xFF;
    }

    public void markGenerated() {
        generated = true;
    }

    public void markLightComputed() {
        lightComputed = true;
    }

    public void clearMeshDirty() {
        meshDirty = false;
    }

    public void markMeshDirty() {
        meshDirty = true;
    }

    /** Raw block IDs for saving to disk. */
    public short[] exportBlocks() {
        return blocks.clone();
    }

    /** Restores block IDs loaded from disk and rebuilds the derived height map. */
    public void importBlocks(short[] data) {
        System.arraycopy(data, 0, blocks, 0, blocks.length);
        java.util.Arrays.fill(heightMap, MIN_Y - 1);
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                for (int y = MAX_Y; y >= MIN_Y; y--) {
                    if (!getLocal(x, y, z).isAir()) {
                        heightMap[z * SIZE_X + x] = y;
                        break;
                    }
                }
            }
        }
        meshDirty = true;
    }

    public int worldOriginX() {
        return chunkX * SIZE_X;
    }

    public int worldOriginZ() {
        return chunkZ * SIZE_Z;
    }

    public static long key(int cx, int cz) {
        return ((long) cx & 0xFFFFFFFFL) | (((long) cz & 0xFFFFFFFFL) << 32);
    }
}
