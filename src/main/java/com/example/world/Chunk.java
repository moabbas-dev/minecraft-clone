package com.example.world;

public class Chunk {
    public static final int SIZE_X = 16;
    public static final int SIZE_Z = 16;
    public static final int SIZE_Y = 256;

    public final int chunkX;
    public final int chunkZ;
    private final byte[] blocks = new byte[SIZE_X * SIZE_Y * SIZE_Z];
    /** Highest non-air Y per column, used only as a meshing early-exit hint. */
    private final int[] heightMap = new int[SIZE_X * SIZE_Z];

    /** 0-15 light levels, recomputed by LightEngine whenever the chunk's mesh is rebuilt. */
    final byte[] skyLight = new byte[SIZE_X * SIZE_Y * SIZE_Z];
    final byte[] blockLight = new byte[SIZE_X * SIZE_Y * SIZE_Z];

    // Per-column grass/foliage tint (RGB), computed once by the world generator - either right
    // after generating fresh terrain or right after loading saved blocks from disk, never
    // per-frame or per-vertex. ChunkMeshBuilder reads these when baking grass-top/leaf faces.
    private final float[] grassTint = new float[SIZE_X * SIZE_Z * 3];
    private final float[] foliageTint = new float[SIZE_X * SIZE_Z * 3];

    private volatile boolean generated = false;
    private volatile boolean meshDirty = true;
    private volatile boolean lightComputed = false;

    public Chunk(int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public static int index(int x, int y, int z) {
        return (y * SIZE_Z + z) * SIZE_X + x;
    }

    public BlockType getLocal(int x, int y, int z) {
        if (x < 0 || x >= SIZE_X || z < 0 || z >= SIZE_Z || y < 0 || y >= SIZE_Y) return BlockType.AIR;
        return BlockType.byId(blocks[index(x, y, z)] & 0xFF);
    }

    public void setLocal(int x, int y, int z, BlockType type) {
        blocks[index(x, y, z)] = (byte) type.id;
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

    public int getSkyLight(int x, int y, int z) {
        if (x < 0 || x >= SIZE_X || z < 0 || z >= SIZE_Z || y < 0 || y >= SIZE_Y) return 15;
        return skyLight[index(x, y, z)] & 0xFF;
    }

    public int getBlockLight(int x, int y, int z) {
        if (x < 0 || x >= SIZE_X || z < 0 || z >= SIZE_Z || y < 0 || y >= SIZE_Y) return 0;
        return blockLight[index(x, y, z)] & 0xFF;
    }

    public boolean isGenerated() {
        return generated;
    }

    public void markGenerated() {
        generated = true;
    }

    /** True once {@link LightEngine#compute} has run at least once - see {@link World#getSkyLight}. */
    public boolean isLightComputed() {
        return lightComputed;
    }

    public void markLightComputed() {
        lightComputed = true;
    }

    public boolean isMeshDirty() {
        return meshDirty;
    }

    public void clearMeshDirty() {
        meshDirty = false;
    }

    public void markMeshDirty() {
        meshDirty = true;
    }

    /** Raw block IDs for saving to disk. */
    public byte[] exportBlocks() {
        return blocks.clone();
    }

    /** Restores block IDs loaded from disk and rebuilds the derived height map. */
    public void importBlocks(byte[] data) {
        System.arraycopy(data, 0, blocks, 0, blocks.length);
        java.util.Arrays.fill(heightMap, 0);
        for (int x = 0; x < SIZE_X; x++) {
            for (int z = 0; z < SIZE_Z; z++) {
                for (int y = SIZE_Y - 1; y >= 0; y--) {
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
