package com.example.worldgen.density;

import com.example.worldgen.biome.Climate;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.DensitySampler} (26.3): the compiled form of a
 * {@link DensityFunction}. {@link #sampleValue} evaluates one block position; {@link #sampleVolume} fills a buffer
 * for a whole {@link DensityVolume}. The two paths are separate code in vanilla and can round differently (for
 * example amplitude products in noise volumes), so both are ported verbatim and callers must use the same path
 * vanilla uses.
 */
public interface DensitySampler {

    void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume);

    float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ);

    /** {@code DensitySampler.sampleVolumeNaive}: per-point evaluation in buffer order. */
    static void sampleVolumeNaive(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume,
                                  DensitySampler sampler) {
        int index = 0;
        for (int z = 0; z < volume.sizeZ(); ++z) {
            int blockZ = volume.blockZ(z);
            for (int x = 0; x < volume.sizeX(); ++x) {
                int blockX = volume.blockX(x);
                for (int y = 0; y < volume.sizeY(); ++y) {
                    int blockY = volume.blockY(y);
                    outputBuffer.set(index++, sampler.sampleValue(context, blockX, blockY, blockZ));
                }
            }
        }
    }

    default Bound bind(SamplerContext context) {
        return new Bound(this, context);
    }

    /**
     * {@code DensitySampler.Bound}: a sampler bound to a context. It is also the
     * {@link Climate.DensitySamplerBound} the ported {@link Climate.Sampler} consumes.
     */
    record Bound(DensitySampler sampler, SamplerContext context) implements Climate.DensitySamplerBound {

        @Override
        public float sampleValue(int blockX, int blockY, int blockZ) {
            return sampler.sampleValue(context, blockX, blockY, blockZ);
        }

        public void sampleVolume(DensityBuffer outputBuffer, DensityVolume volume) {
            sampler.sampleVolume(context, outputBuffer, volume);
        }

        public ScopedDensityBuffer sampleVolume(DensityVolume volume) {
            ScopedDensityBuffer buffer = context.acquireBuffer(volume);
            sampleVolume(buffer, volume);
            return buffer;
        }
    }
}
