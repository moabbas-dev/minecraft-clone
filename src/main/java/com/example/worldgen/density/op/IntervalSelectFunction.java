package com.example.worldgen.density.op;

import com.example.worldgen.Interval;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.DfRewriteRule;
import com.example.worldgen.density.SamplerContext;
import com.example.worldgen.density.ScopedDensityBuffer;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.op.IntervalSelectFunction} (26.3), JSON
 * {@code minecraft:interval_select}: {@code input}, {@code thresholds} (ascending floats, one fewer than functions)
 * and {@code functions} (at least 2). Picks the first function {@code i} with {@code input < thresholds[i]}, else
 * the last. Thresholds are kept as a list so value equality matches vanilla's {@code FloatList}.
 */
public record IntervalSelectFunction(DensityFunction input, List<Float> thresholds, List<DensityFunction> functions)
        implements DensityFunction {

    public IntervalSelectFunction {
        thresholds = List.copyOf(thresholds);
        functions = List.copyOf(functions);
    }

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        DensitySampler inputSampler = input.compileSampler(context);
        if (thresholds.size() == 1) {
            float threshold = thresholds.get(0);
            DensitySampler ifBelow = functions.get(0).compileSampler(context);
            DensitySampler ifAbove = functions.get(functions.size() - 1).compileSampler(context);
            return new SingleThresholdSampler(inputSampler, threshold, ifBelow, ifAbove);
        }
        float[] thresholdArray = new float[thresholds.size()];
        for (int i = 0; i < thresholdArray.length; i++) {
            thresholdArray[i] = thresholds.get(i);
        }
        DensitySampler[] samplers = new DensitySampler[functions.size()];
        for (int i = 0; i < samplers.length; i++) {
            samplers[i] = functions.get(i).compileSampler(context);
        }
        return new Sampler(inputSampler, thresholdArray, samplers);
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction newInput = rule.rewrite(input);
        boolean functionsChanged = false;
        List<DensityFunction> newFunctions = new ArrayList<>(functions.size());
        for (DensityFunction function : functions) {
            DensityFunction newFunction = rule.rewrite(function);
            if (newFunction != function) {
                functionsChanged = true;
            }
            newFunctions.add(newFunction);
        }
        return newInput == input && !functionsChanged ? this : new IntervalSelectFunction(newInput, thresholds, newFunctions);
    }

    @Override
    public Interval range() {
        List<Interval> ranges = new ArrayList<>(functions.size());
        for (DensityFunction function : functions) {
            ranges.add(function.range());
        }
        return Interval.encapsulating(ranges);
    }

    @Override
    public int domainAxes() {
        int axes = input.domainAxes();
        for (DensityFunction function : functions) {
            axes |= function.domainAxes();
        }
        return axes;
    }

    private record Sampler(DensitySampler input, float[] thresholds, DensitySampler[] samplers) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            input.sampleVolume(context, outputBuffer, volume);
            ScopedDensityBuffer[] buffers = new ScopedDensityBuffer[samplers.length];
            try {
                for (int i = 0; i < samplers.length; ++i) {
                    ScopedDensityBuffer buffer = context.acquireBuffer(volume);
                    buffers[i] = buffer;
                    samplers[i].sampleVolume(context, buffer, volume);
                }
                for (int i = 0; i < outputBuffer.size(); ++i) {
                    int samplerIndex = selectSamplerIndex(outputBuffer.get(i));
                    outputBuffer.set(i, buffers[samplerIndex].get(i));
                }
            } finally {
                for (ScopedDensityBuffer buffer : buffers) {
                    if (buffer != null) {
                        buffer.close();
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            float in = input.sampleValue(context, blockX, blockY, blockZ);
            return samplers[selectSamplerIndex(in)].sampleValue(context, blockX, blockY, blockZ);
        }

        private int selectSamplerIndex(float in) {
            for (int i = 0; i < thresholds.length; ++i) {
                if (in < thresholds[i]) {
                    return i;
                }
            }
            return samplers.length - 1;
        }
    }

    private record SingleThresholdSampler(DensitySampler input, float threshold, DensitySampler ifBelow,
                                          DensitySampler ifAbove) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            input.sampleVolume(context, outputBuffer, volume);
            try (ScopedDensityBuffer ifBelowBuffer = context.acquireBuffer(volume)) {
                ifBelow.sampleVolume(context, ifBelowBuffer, volume);
                try (ScopedDensityBuffer ifAboveBuffer = context.acquireBuffer(volume)) {
                    ifAbove.sampleVolume(context, ifAboveBuffer, volume);
                    for (int i = 0; i < outputBuffer.size(); ++i) {
                        float in = outputBuffer.get(i);
                        outputBuffer.set(i, in < threshold ? ifBelowBuffer.get(i) : ifAboveBuffer.get(i));
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            float in = input.sampleValue(context, blockX, blockY, blockZ);
            return in < threshold ? ifBelow.sampleValue(context, blockX, blockY, blockZ) : ifAbove.sampleValue(context, blockX, blockY, blockZ);
        }
    }
}
