package com.example.worldgen.density.op;

import com.example.worldgen.Interval;
import com.example.worldgen.density.DensityBlender;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityContextKeys;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.DfRewriteRule;
import com.example.worldgen.density.SamplerContext;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.op.BlendDensityFunction} (26.3), JSON
 * {@code minecraft:blend_density} ({@code input}): passes the input through the context's blender
 * ({@link DensityContextKeys#BLENDER}) when one is present and not empty.
 */
public record BlendDensityFunction(DensityFunction input) implements DensityFunction {

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        return new Sampler(input.compileSampler(context));
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction newInput = rule.rewrite(input);
        return newInput == input ? this : new BlendDensityFunction(newInput);
    }

    @Override
    public Interval range() {
        return input.range();
    }

    @Override
    public int domainAxes() {
        return input.domainAxes();
    }

    private record Sampler(DensitySampler input) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            input.sampleVolume(context, outputBuffer, volume);
            DensityBlender blender = context.getField(DensityContextKeys.BLENDER);
            if (blender != null && !blender.isEmpty()) {
                int index = 0;
                for (int z = 0; z < volume.sizeZ(); ++z) {
                    int blockZ = volume.blockZ(z);
                    for (int x = 0; x < volume.sizeX(); ++x) {
                        int blockX = volume.blockX(x);
                        for (int y = 0; y < volume.sizeY(); ++y) {
                            int blockY = volume.blockY(y);
                            outputBuffer.set(index, blender.blendDensity(blockX, blockY, blockZ, outputBuffer.get(index)));
                            ++index;
                        }
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            float in = input.sampleValue(context, blockX, blockY, blockZ);
            DensityBlender blender = context.getField(DensityContextKeys.BLENDER);
            return blender != null && !blender.isEmpty() ? blender.blendDensity(blockX, blockY, blockZ, in) : in;
        }
    }
}
