package com.example.worldgen.density;

import com.example.worldgen.Interval;
import com.example.worldgen.density.op.CacheFunction;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.DensityFunctionCompiler} (26.3): compiles a
 * {@link DensityFunction} into a {@link DensitySampler} once per function (memoised by value equality). The
 * optimiser first inlines references and replaces every {@link CacheFunction} with a shared prepared cache (keyed
 * by the cache's input, so equal caches share one cache id), then applies
 * {@link DfRewriteRule#SLICE_UNIFORM_AXES}.
 */
public class DensityFunctionCompiler {

    private final DensityFunction.CompileContext context;
    private final Map<DensityFunction, DensitySampler> samplers = new ConcurrentHashMap<>();
    private final Function<DensityFunction, DensitySampler> optimizeAndCompile = this::optimizeAndCompile;
    private final DfRewriteRule optimizerRule;
    private final ReentrantLock compileLock = new ReentrantLock();
    private final Map<DensityFunction, PreparedCache> preparedCaches = new HashMap<>();
    private int nextCacheId;

    public DensityFunctionCompiler(DensityFunction.CompileContext context) {
        this.optimizerRule = DfRewriteRule.sequence(new DfRewriteRule() {
            @Override
            public DensityFunction rewrite(DensityFunction function) {
                function = DfRewriteRule.INLINE_REFERENCE.rewrite(function);
                if (function instanceof CacheFunction cache) {
                    return reuseOrPrepareCache(cache);
                }
                return function.rewriteChildren(this);
            }
        }, DfRewriteRule.SLICE_UNIFORM_AXES);
        this.context = context;
    }

    public DensitySampler getSampler(DensityFunction function) {
        return samplers.computeIfAbsent(function, optimizeAndCompile);
    }

    private DensitySampler optimizeAndCompile(DensityFunction function) {
        compileLock.lock();
        try {
            DensityFunction optimizedFunction = optimizerRule.rewrite(function);
            return optimizedFunction.compileSampler(context);
        } finally {
            compileLock.unlock();
        }
    }

    private DensityFunction reuseOrPrepareCache(CacheFunction cache) {
        PreparedCache prepared = preparedCaches.get(cache.input());
        if (prepared == null) {
            prepared = prepareCache(cache);
            preparedCaches.put(cache.input(), prepared);
        }
        return prepared;
    }

    private PreparedCache prepareCache(CacheFunction cache) {
        int id = nextCacheId++;
        DensityFunction input = optimizerRule.rewrite(cache.input());
        CachingDensitySampler cachingSampler = new CachingDensitySampler(id, input.compileSampler(context));
        return new PreparedCache(id, input.range(), input.domainAxes(), cachingSampler);
    }

    /** {@code DensityFunctionCompiler.PreparedCache}: a cache already compiled to its {@link CachingDensitySampler}. */
    record PreparedCache(int id, Interval range, int domainAxes, DensitySampler cachingSampler) implements DensityFunction {

        @Override
        public DensitySampler compileSampler(CompileContext context) {
            return cachingSampler;
        }

        @Override
        public DensityFunction rewriteChildren(DfRewriteRule rule) {
            return this;
        }
    }
}
