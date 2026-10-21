package com.example.worldgen.density.op;

import com.example.worldgen.Interval;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.DensitySampler;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.density.DfRewriteRule;
import com.example.worldgen.density.SamplerContext;
import com.example.worldgen.density.ScopedDensityBuffer;
import com.example.worldgen.density.generator.ConstantFunction;

import java.util.logging.Logger;

/**
 * Port of {@code net.minecraft.world.level.levelgen.densityfunction.op.BinaryFunction} (26.3): {@code add},
 * {@code sub}, {@code mul}, {@code div}, {@code min}, {@code max}, JSON fields {@code left} and {@code right}.
 * Compilation specialises constant operands (left constant first, then right; {@code sub x - c} becomes
 * {@code add -c}, {@code div x / c} becomes {@code mul 1/c}); {@code min}/{@code max} with non-overlapping input
 * ranges compile to just the winning side, otherwise the point sampler skips the right input when the left value
 * already decides ({@code left <= right.min} / {@code left >= right.max}). The point {@code mul}/{@code div} skip
 * the right input when the left is 0. Volume and point paths are kept separate as in vanilla.
 */
public record BinaryFunction(Type type, DensityFunction left, DensityFunction right) implements DensityFunction {

    private static final Logger LOGGER = Logger.getLogger(BinaryFunction.class.getName());

    @Override
    public DensitySampler compileSampler(CompileContext context) {
        DensitySampler leftSampler = left.compileSampler(context);
        DensitySampler rightSampler = right.compileSampler(context);
        switch (type) {
            case ADD -> {
                if (left instanceof ConstantFunction c) {
                    return new ConstAddSampler(rightSampler, c.value());
                }
                if (right instanceof ConstantFunction c) {
                    return new ConstAddSampler(leftSampler, c.value());
                }
                return new AddSampler(leftSampler, rightSampler);
            }
            case SUB -> {
                if (left instanceof ConstantFunction c) {
                    return new ConstSubSampler(c.value(), rightSampler);
                }
                if (right instanceof ConstantFunction c) {
                    return new ConstAddSampler(leftSampler, -c.value());
                }
                return new SubSampler(leftSampler, rightSampler);
            }
            case MUL -> {
                if (left instanceof ConstantFunction c) {
                    return new ConstMulSampler(rightSampler, c.value());
                }
                if (right instanceof ConstantFunction c) {
                    return new ConstMulSampler(leftSampler, c.value());
                }
                return new MulSampler(leftSampler, rightSampler);
            }
            case DIV -> {
                if (left instanceof ConstantFunction c) {
                    return new ConstDivSampler(c.value(), rightSampler);
                }
                if (right instanceof ConstantFunction c) {
                    return new ConstMulSampler(leftSampler, 1.0F / c.value());
                }
                return new DivSampler(leftSampler, rightSampler);
            }
            case MIN -> {
                Interval leftRange = left.range();
                Interval rightRange = right.range();
                if (leftRange.max() < rightRange.min()) {
                    warnNonIntersecting();
                    return leftSampler;
                }
                if (rightRange.max() < leftRange.min()) {
                    warnNonIntersecting();
                    return rightSampler;
                }
                if (left instanceof ConstantFunction c) {
                    return new ConstMinSampler(rightSampler, c.value());
                }
                if (right instanceof ConstantFunction c) {
                    return new ConstMinSampler(leftSampler, c.value());
                }
                return new MinSampler(leftSampler, rightSampler, rightRange.min());
            }
            case MAX -> {
                Interval leftRange = left.range();
                Interval rightRange = right.range();
                if (leftRange.min() > rightRange.max()) {
                    warnNonIntersecting();
                    return leftSampler;
                }
                if (rightRange.min() > leftRange.max()) {
                    warnNonIntersecting();
                    return rightSampler;
                }
                if (left instanceof ConstantFunction c) {
                    return new ConstMaxSampler(rightSampler, c.value());
                }
                if (right instanceof ConstantFunction c) {
                    return new ConstMaxSampler(leftSampler, c.value());
                }
                return new MaxSampler(leftSampler, rightSampler, rightRange.max());
            }
            default -> throw new IllegalStateException(type.toString());
        }
    }

    private void warnNonIntersecting() {
        LOGGER.warning("Compiling a " + type + " function between two non-overlapping inputs: " + left + " ("
                + left.range() + ") and " + right + " (" + right.range() + ")");
    }

    @Override
    public DensityFunction rewriteChildren(DfRewriteRule rule) {
        DensityFunction newLeft = rule.rewrite(left);
        DensityFunction newRight = rule.rewrite(right);
        return newLeft == left && right == newRight ? this : new BinaryFunction(type, newLeft, newRight);
    }

    @Override
    public Interval range() {
        Interval l = left.range();
        Interval r = right.range();
        return switch (type) {
            case ADD -> Interval.add(l, r);
            case SUB -> Interval.sub(l, r);
            case MUL -> Interval.mul(l, r);
            case DIV -> Interval.div(l, r);
            case MIN -> Interval.min(l, r);
            case MAX -> Interval.max(l, r);
        };
    }

    @Override
    public int domainAxes() {
        return left.domainAxes() | right.domainAxes();
    }

    /** {@code BinaryFunction.Type} with its JSON id. */
    public enum Type {
        ADD("add"),
        SUB("sub"),
        MUL("mul"),
        DIV("div"),
        MIN("min"),
        MAX("max");

        public final String id;

        Type(String id) {
            this.id = id;
        }
    }

    public record AddSampler(DensitySampler left, DensitySampler right) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            left.sampleVolume(context, outputBuffer, volume);
            try (ScopedDensityBuffer rightBuffer = context.acquireBuffer(volume)) {
                right.sampleVolume(context, rightBuffer, volume);
                for (int i = 0; i < outputBuffer.size(); ++i) {
                    outputBuffer.addTo(i, rightBuffer.get(i));
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return left.sampleValue(context, blockX, blockY, blockZ) + right.sampleValue(context, blockX, blockY, blockZ);
        }
    }

    public record ConstAddSampler(DensitySampler left, float right) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            left.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.addTo(i, right);
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return left.sampleValue(context, blockX, blockY, blockZ) + right;
        }
    }

    public record MulSampler(DensitySampler left, DensitySampler right) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            left.sampleVolume(context, outputBuffer, volume);
            try (ScopedDensityBuffer rightBuffer = context.acquireBuffer(volume)) {
                right.sampleVolume(context, rightBuffer, volume);
                for (int i = 0; i < outputBuffer.size(); ++i) {
                    outputBuffer.set(i, outputBuffer.get(i) * rightBuffer.get(i));
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            float l = left.sampleValue(context, blockX, blockY, blockZ);
            return l == 0.0F ? 0.0F : l * right.sampleValue(context, blockX, blockY, blockZ);
        }
    }

    public record ConstMulSampler(DensitySampler left, float right) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            left.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.set(i, outputBuffer.get(i) * right);
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return left.sampleValue(context, blockX, blockY, blockZ) * right;
        }
    }

    public record ConstDivSampler(float left, DensitySampler right) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            right.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.set(i, left / outputBuffer.get(i));
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return left / right.sampleValue(context, blockX, blockY, blockZ);
        }
    }

    public record SubSampler(DensitySampler left, DensitySampler right) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            left.sampleVolume(context, outputBuffer, volume);
            try (ScopedDensityBuffer rightBuffer = context.acquireBuffer(volume)) {
                right.sampleVolume(context, rightBuffer, volume);
                for (int i = 0; i < outputBuffer.size(); ++i) {
                    outputBuffer.addTo(i, -rightBuffer.get(i));
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return left.sampleValue(context, blockX, blockY, blockZ) - right.sampleValue(context, blockX, blockY, blockZ);
        }
    }

    public record ConstSubSampler(float left, DensitySampler right) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            right.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                outputBuffer.set(i, left - outputBuffer.get(i));
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return left - right.sampleValue(context, blockX, blockY, blockZ);
        }
    }

    public record DivSampler(DensitySampler left, DensitySampler right) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            left.sampleVolume(context, outputBuffer, volume);
            try (ScopedDensityBuffer rightBuffer = context.acquireBuffer(volume)) {
                right.sampleVolume(context, rightBuffer, volume);
                for (int i = 0; i < outputBuffer.size(); ++i) {
                    outputBuffer.set(i, outputBuffer.get(i) / rightBuffer.get(i));
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            float l = left.sampleValue(context, blockX, blockY, blockZ);
            return l == 0.0F ? 0.0F : l / right.sampleValue(context, blockX, blockY, blockZ);
        }
    }

    public record MinSampler(DensitySampler left, DensitySampler right, float rightMinValue) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            left.sampleVolume(context, outputBuffer, volume);
            try (ScopedDensityBuffer rightBuffer = context.acquireBuffer(volume)) {
                right.sampleVolume(context, rightBuffer, volume);
                for (int i = 0; i < outputBuffer.size(); ++i) {
                    float rightValue = rightBuffer.get(i);
                    if (rightValue < outputBuffer.get(i)) {
                        outputBuffer.set(i, rightValue);
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            float l = left.sampleValue(context, blockX, blockY, blockZ);
            return l <= rightMinValue ? l : Math.min(l, right.sampleValue(context, blockX, blockY, blockZ));
        }
    }

    public record MaxSampler(DensitySampler left, DensitySampler right, float rightMaxValue) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            left.sampleVolume(context, outputBuffer, volume);
            try (ScopedDensityBuffer rightBuffer = context.acquireBuffer(volume)) {
                right.sampleVolume(context, rightBuffer, volume);
                for (int i = 0; i < outputBuffer.size(); ++i) {
                    float rightValue = rightBuffer.get(i);
                    if (rightValue > outputBuffer.get(i)) {
                        outputBuffer.set(i, rightValue);
                    }
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            float l = left.sampleValue(context, blockX, blockY, blockZ);
            return l >= rightMaxValue ? l : Math.max(l, right.sampleValue(context, blockX, blockY, blockZ));
        }
    }

    public record ConstMinSampler(DensitySampler left, float right) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            left.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                if (right < outputBuffer.get(i)) {
                    outputBuffer.set(i, right);
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return Math.min(left.sampleValue(context, blockX, blockY, blockZ), right);
        }
    }

    public record ConstMaxSampler(DensitySampler left, float right) implements DensitySampler {
        @Override
        public void sampleVolume(SamplerContext context, DensityBuffer outputBuffer, DensityVolume volume) {
            left.sampleVolume(context, outputBuffer, volume);
            for (int i = 0; i < outputBuffer.size(); ++i) {
                if (right > outputBuffer.get(i)) {
                    outputBuffer.set(i, right);
                }
            }
        }

        @Override
        public float sampleValue(SamplerContext context, int blockX, int blockY, int blockZ) {
            return Math.max(left.sampleValue(context, blockX, blockY, blockZ), right);
        }
    }
}
