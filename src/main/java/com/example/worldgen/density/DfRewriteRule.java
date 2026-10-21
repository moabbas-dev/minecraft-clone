package com.example.worldgen.density;

import com.example.worldgen.density.generator.ConstantFunction;
import com.example.worldgen.density.generator.GradientFunction;
import com.example.worldgen.density.op.SliceFunction;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.DfRewriteRule} (26.3): tree rewrites applied by
 * {@link DensityFunctionCompiler}. {@link #INLINE_REFERENCE} replaces a registry reference by its value;
 * {@link #SLICE_UNIFORM_AXES} wraps every sub-tree that depends on fewer axes than its parent in
 * {@link SliceFunction}s at coordinate 0 for the dropped axes (X, then Z, then Y; never twice; constants and
 * gradients are left alone), so its evaluation is shared across the axes it ignores.
 */
@FunctionalInterface
public interface DfRewriteRule {

    DfRewriteRule INLINE_REFERENCE = function -> {
        if (function instanceof DensityFunctions.HolderHolder holderHolder) {
            return holderHolder.function().value();
        }
        return function;
    };

    DfRewriteRule SLICE_UNIFORM_AXES = new SliceUniformAxes(DensityFunction.ALL_AXES);

    static DfRewriteRule sequence(DfRewriteRule... rules) {
        return function -> {
            for (DfRewriteRule rule : rules) {
                function = rule.rewrite(function);
            }
            return function;
        };
    }

    DensityFunction rewrite(DensityFunction function);

    /** {@code DfRewriteRule.SliceUniformAxes}. */
    record SliceUniformAxes(int parentDomainAxes) implements DfRewriteRule {

        @Override
        public DensityFunction rewrite(DensityFunction function) {
            if (shouldSkip(function)) {
                return function;
            }
            int domainAxes = function.domainAxes();
            if (parentDomainAxes == domainAxes) {
                return function.rewriteChildren(this);
            }
            DensityFunction newFunction = function.rewriteChildren(new SliceUniformAxes(domainAxes));
            int removedAxes = parentDomainAxes & ~domainAxes;
            return removeAxes(newFunction, removedAxes);
        }

        private DensityFunction removeAxes(DensityFunction function, int axes) {
            int filteredAxes = axes & ~getExistingRemovedAxes(function);
            if ((filteredAxes & DensityFunction.AXIS_X) != 0) {
                function = new SliceFunction(Axis.X, 0, function);
            }
            if ((filteredAxes & DensityFunction.AXIS_Z) != 0) {
                function = new SliceFunction(Axis.Z, 0, function);
            }
            if ((filteredAxes & DensityFunction.AXIS_Y) != 0) {
                function = new SliceFunction(Axis.Y, 0, function);
            }
            return function;
        }

        private static int getExistingRemovedAxes(DensityFunction function) {
            int axes = 0;
            while (function instanceof SliceFunction slice) {
                axes |= DensityFunction.axesFrom(slice.axis());
                function = slice.input();
            }
            return axes;
        }

        private static boolean shouldSkip(DensityFunction function) {
            return function instanceof ConstantFunction || function instanceof GradientFunction;
        }
    }
}
