package com.example.worldgen.density;

import com.example.worldgen.BlockPos;
import com.example.worldgen.Mth;

import java.util.Arrays;
import java.util.Objects;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.SamplerContext} (26.3): per-evaluation state
 * passed to every {@link DensitySampler}: user fields (blender, beardifier, ...), the buffer arena, and, when caches
 * are enabled, one cache cell per {@link CachingDensitySampler} id (grown in steps of 16). A cell remembers the last
 * sampled volume buffer and the last single value keyed by {@link BlockPos#asLong}.
 */
public class SamplerContext {

    public static final SamplerContext EMPTY_UNCACHED = new SamplerContext(ContextMap.EMPTY, DensityBufferArena.GLOBAL, false);
    private static final int CACHE_SIZE_STEP = 16;

    private final ContextMap userFields;
    private final DensityBufferArena bufferArena;
    private CacheCell[] cacheCells;

    private SamplerContext(ContextMap userFields, DensityBufferArena bufferArena, boolean enableCaches) {
        this.userFields = userFields;
        this.bufferArena = bufferArena;
        this.cacheCells = enableCaches ? new CacheCell[0] : null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public <T> T getField(ContextKey<T> key) {
        return userFields.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T getFieldOrDefault(ContextKey<? extends T> key, T defaultValue) {
        return Objects.requireNonNullElse((T) getField(key), defaultValue);
    }

    public ScopedDensityBuffer acquireBuffer(DensityVolume volume) {
        return bufferArena.acquire(volume.size());
    }

    private CacheCell getCacheCell(int cacheId) {
        if (cacheCells == null) {
            return null;
        }
        int oldSize = cacheCells.length;
        if (cacheId >= oldSize) {
            int newSize = Mth.roundToward(cacheId + 1, CACHE_SIZE_STEP);
            cacheCells = Arrays.copyOf(cacheCells, newSize);
            for (int i = oldSize; i < newSize; ++i) {
                cacheCells[i] = new CacheCell();
            }
        }
        return cacheCells[cacheId];
    }

    void sampleVolumeCached(int cacheId, DensitySampler input, DensityBuffer outputBuffer, DensityVolume volume) {
        CacheCell cell = getCacheCell(cacheId);
        if (cell == null) {
            input.sampleVolume(this, outputBuffer, volume);
            return;
        }
        if (cell.buffer == null || !volume.equals(cell.volume)) {
            if (cell.buffer != null) {
                cell.buffer.close();
            }
            cell.volume = volume;
            cell.buffer = acquireBuffer(volume);
            input.sampleVolume(this, cell.buffer, volume);
        }
        outputBuffer.copyFrom(cell.buffer);
    }

    float sampleValueCached(int cacheId, DensitySampler input, int blockX, int blockY, int blockZ) {
        CacheCell cell = getCacheCell(cacheId);
        if (cell == null) {
            return input.sampleValue(this, blockX, blockY, blockZ);
        }
        long cacheKey = BlockPos.asLong(blockX, blockY, blockZ);
        if (cell.valueKey == cacheKey && !Float.isNaN(cell.value)) {
            return cell.value;
        }
        if (cell.buffer != null && cell.volume != null) {
            int index = cell.volume.indexOfBlock(blockX, blockY, blockZ);
            if (index != DensityVolume.NO_BLOCK) {
                return cell.buffer.get(index);
            }
        }
        float value = input.sampleValue(this, blockX, blockY, blockZ);
        cell.valueKey = cacheKey;
        cell.value = value;
        return value;
    }

    private static final class CacheCell {
        private DensityVolume volume;
        private ScopedDensityBuffer buffer;
        private long valueKey;
        private float value = Float.NaN;
    }

    public static final class Builder {

        private ContextMap userFields = ContextMap.EMPTY;
        private DensityBufferArena bufferArena = DensityBufferArena.GLOBAL;
        private boolean enableCaches;

        private Builder() {
        }

        public Builder setUserFields(ContextMap userFields) {
            this.userFields = userFields;
            return this;
        }

        public Builder useBufferArena(DensityBufferArena bufferArena) {
            this.bufferArena = bufferArena;
            return this;
        }

        public Builder enableCaches() {
            this.enableCaches = true;
            return this;
        }

        public SamplerContext build() {
            return new SamplerContext(userFields, bufferArena, enableCaches);
        }
    }
}
