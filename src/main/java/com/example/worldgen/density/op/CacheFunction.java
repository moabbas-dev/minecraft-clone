package com.example.worldgen.density.op;

import com.example.worldgen.Interval;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensityFunctionCompiler;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DfRewriteRule;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.op.CacheFunction} (26.3), JSON
 * {@code minecraft:cache} ({@code input}). In 26.3 this single marker replaces {@code flat_cache},
 * {@code cache_2d}, {@code cache_once} and {@code cache_all_in_cell}. It cannot be compiled directly:
 * {@link DensityFunctionCompiler} deduplicates it into a {@code CachingDensitySampler} slot of the
 * {@code SamplerContext}.
 */
public record CacheFunction(DensityFunction input) implements DensityFunction {

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        throw new IllegalStateException("Cannot compile cache before it has been deduplicated");
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction newInput = rule.rewrite(input);
        return newInput == input ? this : new CacheFunction(newInput);
    }

    @Override
    public Interval range() {
        return input.range();
    }

    @Override
    public int domainAxes() {
        return input.domainAxes();
    }
}
