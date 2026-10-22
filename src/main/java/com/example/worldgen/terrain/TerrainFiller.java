package com.example.worldgen.terrain;

import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.ScopedDensityBuffer;
import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.levelgen.NoiseGeneratorSettings;
import com.example.worldgen.levelgen.NoiseSettings;
import com.example.worldgen.levelgen.RandomState;

/**
 * The noise fill of {@code net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator} (26.3): {@code createFluidPicker},
 * {@code chunkVolume}, {@code createNoiseChunk} and {@code doFill}, the first step of {@code buildTerrain}'s supplier.
 *
 * <p>One instance per {@link NoiseGeneratorSettings} (vanilla memoises the fluid picker in the generator).
 */
public final class TerrainFiller {

    private final NoiseGeneratorSettings settings;
    private final StateRef defaultBlock;
    private final StateRef defaultFluid;
    private final Aquifer.FluidPicker globalFluidPicker;

    public TerrainFiller(NoiseGeneratorSettings settings) {
        this.settings = settings;
        this.defaultBlock = BlockProperties.decodeState(settings.defaultBlock());
        this.defaultFluid = BlockProperties.decodeState(settings.defaultFluid());
        this.globalFluidPicker = createFluidPicker(settings.seaLevel(), defaultFluid);
    }

    public NoiseGeneratorSettings settings() {
        return settings;
    }

    /** {@code settings.defaultBlock()} decoded. */
    public StateRef defaultBlock() {
        return defaultBlock;
    }

    public StateRef defaultFluid() {
        return defaultFluid;
    }

    public Aquifer.FluidPicker globalFluidPicker() {
        return globalFluidPicker;
    }

    /**
     * {@code createFluidPicker}: {@code lava = FluidStatus(-54, LAVA)}, {@code sea = FluidStatus(seaLevel, defaultFluid)};
     * {@code y < min(-54, seaLevel) ? lava : sea}. (The {@code FluidStatus(DimensionType.MIN_Y * 2, AIR)} branch is
     * only taken under {@code SharedConstants.DEBUG_DISABLE_FLUID_GENERATION}, false in release builds.)
     */
    public static Aquifer.FluidPicker createFluidPicker(int seaLevel, StateRef defaultFluid) {
        Aquifer.FluidStatus lavaStatus = new Aquifer.FluidStatus(-54, BlockProperties.LAVA);
        Aquifer.FluidStatus seaStatus = new Aquifer.FluidStatus(seaLevel, defaultFluid);
        int lavaBelow = Math.min(-54, seaLevel);
        return (x, y, z) -> y < lavaBelow ? lavaStatus : seaStatus;
    }

    /** {@code chunkVolume}: {@code DensityVolume(16, height, 16, minBlockX, minY, minBlockZ)}. */
    public static DensityVolume chunkVolume(int chunkX, int chunkZ, NoiseSettings noiseSettings) {
        return new DensityVolume(16, noiseSettings.height(), 16, chunkX * 16, noiseSettings.minY(), chunkZ * 16);
    }

    /**
     * {@code createNoiseChunk} (no structures, empty blender; see {@link NoiseChunk}). The caller closes it after the
     * last stage of the TERRAIN status that uses it (surface, carvers).
     */
    public NoiseChunk createNoiseChunk(RandomState randomState, TerrainChunk chunk) {
        return new NoiseChunk(randomState, settings, globalFluidPicker, chunkVolume(chunk.chunkX(), chunk.chunkZ(), settings.noiseSettings()));
    }

    /**
     * {@code doFill(noiseChunk, chunk)}: samples {@code final_density} over the whole chunk volume through the
     * caching samplers, then for z, x, y (top down): {@code state = aquifer.computeSubstance(x, y, z, (double) density)},
     * {@code null -> defaultBlock}; non-air ({@code state != AIR}, the {@code minecraft:air} default state) is written
     * into the section (no {@code setBlockState} heightmap logic), then {@code OCEAN_FLOOR_WG} and
     * {@code WORLD_SURFACE_WG} are updated, and the position is marked for post-processing when
     * {@code aquifer.shouldScheduleFluidUpdate()} and the state has a fluid.
     */
    public void doFill(NoiseChunk noiseChunk, TerrainChunk chunk) {
        Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(HeightmapType.OCEAN_FLOOR_WG);
        Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(HeightmapType.WORLD_SURFACE_WG);
        Aquifer aquifer = noiseChunk.aquifer();
        DensityVolume volume = noiseChunk.volume();
        DensitySampler.Bound finalDensity = noiseChunk.cachingSamplers().get(settings.noiseRouter().finalDensity());
        StateRef air = BlockProperties.AIR;
        try (ScopedDensityBuffer densityBuffer = finalDensity.sampleVolume(volume)) {
            for (int z = 0; z < volume.sizeZ(); ++z) {
                int blockZ = volume.blockZ(z);
                for (int x = 0; x < volume.sizeX(); ++x) {
                    int blockX = volume.blockX(x);
                    for (int y = volume.sizeY() - 1; y >= 0; --y) {
                        int blockY = volume.blockY(y);
                        float density = densityBuffer.get(volume.indexUnchecked(x, y, z));
                        StateRef state = aquifer.computeSubstance(blockX, blockY, blockZ, (double) density);
                        if (state == null) {
                            state = defaultBlock;
                        }
                        if (!state.equals(air)) {
                            chunk.setStateInSection(x, blockY, z, state);
                            oceanFloor.update(x, blockY, z, state);
                            worldSurface.update(x, blockY, z, state);
                            if (aquifer.shouldScheduleFluidUpdate() && BlockProperties.hasFluid(state)) {
                                chunk.markPosForPostProcessing(blockX, blockY, blockZ);
                            }
                        }
                    }
                }
            }
        }
    }
}
