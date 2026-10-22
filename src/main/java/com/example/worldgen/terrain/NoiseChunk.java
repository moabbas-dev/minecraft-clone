package com.example.worldgen.terrain;

import com.example.worldgen.density.ContextMap;
import com.example.worldgen.density.DensityBufferPool;
import com.example.worldgen.density.DensitySamplerSet;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.SamplerContext;
import com.example.worldgen.levelgen.NoiseGeneratorSettings;
import com.example.worldgen.levelgen.RandomState;

/**
 * Port of {@code net.minecraft.world.level.levelgen.NoiseChunk} (26.3): the per-chunk sampling state shared by the
 * terrain fill, the surface (material) system and the carving-mask application of one {@code buildTerrain} call.
 * <ul>
 *     <li>takes a buffer pool from {@link RandomState#acquireDensityBufferPool()} and gives it back on {@link #close()};</li>
 *     <li>{@code cachingSamplers = randomState.samplersWithContext(SamplerContext.builder().setUserFields(fields)
 *     .useBufferArena(pool).enableCaches().build())};</li>
 *     <li>{@code aquifer = settings.aquifers().isPresent() ? config.create(cachingSamplers,
 *     randomState.getOrCreateRandomFactory("minecraft:aquifer"), volume, globalFluidPicker)
 *     : Aquifer.createDisabled(globalFluidPicker)}.</li>
 * </ul>
 * User fields: vanilla puts {@code Beardifier.CONTEXT_KEY -> Beardifier.forStructuresInChunk(...)}, and the blender
 * keys only for a non-empty {@code Blender}. With no structure starts in range {@code forStructuresInChunk} returns
 * {@code Beardifier.EMPTY} ({@code affectedBox == null}: {@code sampleVolume} fills 0, {@code sampleValue} returns 0),
 * which samples exactly like the {@code minecraft:beardifier} fallback constant 0 used when the key is absent; a fresh
 * world has an empty blender. So until structures and old-chunk blending exist, the user-field map is empty.
 */
public final class NoiseChunk implements AutoCloseable {

    private final RandomState randomState;
    private final DensityVolume volume;
    private final Aquifer aquifer;
    private final DensityBufferPool bufferPool;
    private final DensitySamplerSet cachingSamplers;

    public NoiseChunk(RandomState randomState, NoiseGeneratorSettings settings, Aquifer.FluidPicker globalFluidPicker, DensityVolume volume) {
        this.randomState = randomState;
        this.volume = volume;
        this.bufferPool = randomState.acquireDensityBufferPool();
        this.cachingSamplers = randomState.samplersWithContext(SamplerContext.builder().setUserFields(ContextMap.EMPTY)
                .useBufferArena(bufferPool).enableCaches().build());
        if (settings.aquifers().isPresent()) {
            this.aquifer = Aquifer.create(settings.aquifers().get(), cachingSamplers,
                    randomState.getOrCreateRandomFactory("minecraft:aquifer"), volume, globalFluidPicker);
        } else {
            this.aquifer = Aquifer.createDisabled(globalFluidPicker);
        }
    }

    public DensityVolume volume() {
        return volume;
    }

    public Aquifer aquifer() {
        return aquifer;
    }

    public DensitySamplerSet cachingSamplers() {
        return cachingSamplers;
    }

    public RandomState randomState() {
        return randomState;
    }

    @Override
    public void close() {
        randomState.releaseDensityBufferPool(bufferPool);
    }
}
