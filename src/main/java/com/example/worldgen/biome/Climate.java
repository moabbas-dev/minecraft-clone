package com.example.worldgen.biome;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Port of {@code net.minecraft.world.level.biome.Climate} (Minecraft Java Edition 26.3).
 *
 * <p>Verified against the 26.3 client jar bytecode ({@code javap -c} of {@code Climate},
 * {@code Climate$Parameter}, {@code Climate$ParameterPoint}, {@code Climate$TargetPoint},
 * {@code Climate$ParameterList}, {@code Climate$RTree}, {@code Climate$RTree$Node},
 * {@code Climate$RTree$Leaf}, {@code Climate$RTree$SubTree}, {@code Climate$Sampler},
 * {@code Climate$DistanceMetric}) and against the real classes run as an oracle
 * (see {@code ClimateTest} / {@code OverworldBiomeBuilderTest}).
 *
 * <p>All climate values are quantised to {@code long} by multiplying the {@code float} by
 * {@link #QUANTIZATION_FACTOR} and truncating ({@code fmul; f2l}). Vanilla's
 * {@code com.mojang.datafixers.util.Pair<ParameterPoint, T>} is represented by {@link Entry}.
 * The codecs ({@code Parameter.CODEC}, {@code ParameterPoint.CODEC}, {@code ParameterList.codec})
 * are not ported: this project reads no climate parameters from JSON.
 */
public final class Climate {
    /** Vanilla {@code DEBUG_SLOW_BIOME_SEARCH}: false, so {@code findValue} always uses the R-tree. */
    private static final boolean DEBUG_SLOW_BIOME_SEARCH = false;
    /** Vanilla {@code QUANTIZATION_FACTOR = 10000.0F}. */
    private static final float QUANTIZATION_FACTOR = 10000.0F;
    /** Vanilla {@code PARAMETER_COUNT = 7} (six climate parameters plus the offset). */
    static final int PARAMETER_COUNT = 7;

    private Climate() {
    }

    /** Vanilla {@code Climate.target(float...)}: quantises each sampled noise value. */
    public static TargetPoint target(float temperature, float humidity, float continentalness, float erosion,
                                     float depth, float weirdness) {
        return new TargetPoint(quantizeCoord(temperature), quantizeCoord(humidity), quantizeCoord(continentalness),
            quantizeCoord(erosion), quantizeCoord(depth), quantizeCoord(weirdness));
    }

    /** Vanilla {@code Climate.parameters(FFFFFFF)}: every parameter is a {@link Parameter#point(float)}. */
    public static ParameterPoint parameters(float temperature, float humidity, float continentalness, float erosion,
                                            float depth, float weirdness, float offset) {
        return new ParameterPoint(Parameter.point(temperature), Parameter.point(humidity),
            Parameter.point(continentalness), Parameter.point(erosion), Parameter.point(depth),
            Parameter.point(weirdness), quantizeCoord(offset));
    }

    /** Vanilla {@code Climate.parameters(Parameter x6, float)}. */
    public static ParameterPoint parameters(Parameter temperature, Parameter humidity, Parameter continentalness,
                                            Parameter erosion, Parameter depth, Parameter weirdness, float offset) {
        return new ParameterPoint(temperature, humidity, continentalness, erosion, depth, weirdness,
            quantizeCoord(offset));
    }

    /** Vanilla {@code quantizeCoord}: {@code (long) (coord * 10000.0F)} with float multiplication. */
    public static long quantizeCoord(float coord) {
        return (long) (coord * QUANTIZATION_FACTOR);
    }

    /** Vanilla {@code unquantizeCoord}: {@code (float) coord / 10000.0F}. */
    public static float unquantizeCoord(long coord) {
        return (float) coord / QUANTIZATION_FACTOR;
    }

    /** Vanilla {@code Mth.square(long)}: {@code x * x}. */
    private static long square(long x) {
        return x * x;
    }

    /** One (parameter point, value) pair; stands in for vanilla's {@code Pair<ParameterPoint, T>}. */
    public record Entry<T>(ParameterPoint parameters, T value) {
    }

    /** Vanilla {@code Climate.Parameter}: a closed, quantised interval {@code [min, max]}. */
    public record Parameter(long min, long max) {
        public static Parameter point(float value) {
            return span(value, value);
        }

        public static Parameter span(float min, float max) {
            if (min > max) {
                throw new IllegalArgumentException("min > max: " + min + " " + max);
            }
            return new Parameter(quantizeCoord(min), quantizeCoord(max));
        }

        public static Parameter span(Parameter min, Parameter max) {
            if (min.min() > max.max()) {
                throw new IllegalArgumentException("min > max: " + min + " " + max);
            }
            return new Parameter(min.min(), max.max());
        }

        @Override
        public String toString() {
            return this.min == this.max
                ? String.format(Locale.ROOT, "%d", this.min)
                : String.format(Locale.ROOT, "[%d-%d]", this.min, this.max);
        }

        /** Vanilla {@code distance(long)}: 0 inside the interval, else the gap to the nearest end. */
        public long distance(long target) {
            long above = target - this.max;
            long below = this.min - target;
            return above > 0L ? above : Math.max(below, 0L);
        }

        /** Vanilla {@code span(Parameter)}: the union bounds; a null other returns this. */
        public Parameter span(Parameter other) {
            return other == null ? this
                : new Parameter(Math.min(this.min, other.min()), Math.max(this.max, other.max()));
        }
    }

    /** Vanilla {@code Climate.ParameterPoint}. */
    public record ParameterPoint(Parameter temperature, Parameter humidity, Parameter continentalness,
                                 Parameter erosion, Parameter depth, Parameter weirdness, long offset) {
        /** Vanilla {@code fitness}: sum of squared per-axis distances plus {@code offset^2}. */
        public long fitness(TargetPoint target) {
            return square(this.temperature.distance(target.temperature()))
                + square(this.humidity.distance(target.humidity()))
                + square(this.continentalness.distance(target.continentalness()))
                + square(this.erosion.distance(target.erosion()))
                + square(this.depth.distance(target.depth()))
                + square(this.weirdness.distance(target.weirdness()))
                + square(this.offset);
        }

        /** Vanilla {@code parameterSpace}: the six parameters then {@code Parameter(offset, offset)}. */
        List<Parameter> parameterSpace() {
            return List.of(this.temperature, this.humidity, this.continentalness, this.erosion, this.depth,
                this.weirdness, new Parameter(this.offset, this.offset));
        }
    }

    /** Vanilla {@code Climate.TargetPoint}: quantised sampled climate at one position. */
    public record TargetPoint(long temperature, long humidity, long continentalness, long erosion, long depth,
                              long weirdness) {
        /** Vanilla {@code toParameterArray}: the six values then 0 for the offset axis. */
        long[] toParameterArray() {
            return new long[]{this.temperature, this.humidity, this.continentalness, this.erosion, this.depth,
                this.weirdness, 0L};
        }
    }

    /** Vanilla {@code Climate.DistanceMetric}. */
    interface DistanceMetric<T> {
        long distance(RTree.Node<T> node, long[] target);
    }

    /**
     * One climate density as vanilla's {@code DensitySampler.Bound}: {@code sampleValue(blockX, blockY, blockZ)}
     * returning a {@code float}. The density-function engine supplies the implementations.
     */
    @FunctionalInterface
    public interface DensitySamplerBound {
        float sampleValue(int blockX, int blockY, int blockZ);
    }

    /**
     * Vanilla {@code Climate.Sampler} (26.3 is a record of six {@code DensitySampler.Bound}).
     * In 26.3 it has no spawn-finding logic; that moved out of {@code Climate}.
     */
    public record Sampler(DensitySamplerBound temperature, DensitySamplerBound humidity,
                          DensitySamplerBound continentalness, DensitySamplerBound erosion,
                          DensitySamplerBound depth, DensitySamplerBound weirdness) {
        /** Vanilla {@code sample}: converts quart coordinates to block ({@code QuartPos.toBlock = q << 2}). */
        public TargetPoint sample(int quartX, int quartY, int quartZ) {
            int blockX = quartX << 2;
            int blockY = quartY << 2;
            int blockZ = quartZ << 2;
            return target(this.temperature.sampleValue(blockX, blockY, blockZ),
                this.humidity.sampleValue(blockX, blockY, blockZ),
                this.continentalness.sampleValue(blockX, blockY, blockZ),
                this.erosion.sampleValue(blockX, blockY, blockZ),
                this.depth.sampleValue(blockX, blockY, blockZ),
                this.weirdness.sampleValue(blockX, blockY, blockZ));
        }
    }

    /** Vanilla {@code Climate.ParameterList}: the values plus an R-tree index over them. */
    public static class ParameterList<T> {
        private final List<Entry<T>> values;
        private final RTree<T> index;

        public ParameterList(List<Entry<T>> values) {
            this(values, RTree.CHILDREN_PER_NODE);
        }

        private ParameterList(List<Entry<T>> values, int childrenPerNode) {
            this.values = values;
            this.index = RTree.create(values, childrenPerNode);
        }

        public ParameterList<T> rebuildWithChildrenPerNode(int childrenPerNode) {
            return new ParameterList<>(this.values, childrenPerNode);
        }

        public List<Entry<T>> values() {
            return this.values;
        }

        /** Vanilla {@code findValue}: {@code DEBUG_SLOW_BIOME_SEARCH} is false so this is the index search. */
        public T findValue(TargetPoint target) {
            return DEBUG_SLOW_BIOME_SEARCH ? this.findValueBruteForce(target) : this.findValueIndex(target);
        }

        /** Vanilla {@code findValueBruteForce}: first entry with the strictly smallest fitness. */
        public T findValueBruteForce(TargetPoint target) {
            java.util.Iterator<Entry<T>> iterator = this.values().iterator();
            Entry<T> first = iterator.next();
            long bestFitness = first.parameters().fitness(target);
            T best = first.value();
            while (iterator.hasNext()) {
                Entry<T> entry = iterator.next();
                long fitness = entry.parameters().fitness(target);
                if (fitness < bestFitness) {
                    bestFitness = fitness;
                    best = entry.value();
                }
            }
            return best;
        }

        public T findValueIndex(TargetPoint target) {
            return this.findValueIndex(target, RTree.Node::distance);
        }

        T findValueIndex(TargetPoint target, DistanceMetric<T> distanceMetric) {
            return this.index.search(target, distanceMetric);
        }
    }

    /**
     * Vanilla {@code Climate.RTree}: built once per list; the search reuses the previous result of the same
     * thread as the initial candidate ({@code lastResult}), so results depend on query order exactly as in vanilla.
     */
    static final class RTree<T> {
        static final int CHILDREN_PER_NODE = 19;
        private final Node<T> root;
        private final ThreadLocal<Leaf<T>> lastResult = new ThreadLocal<>();

        private RTree(Node<T> root) {
            this.root = root;
        }

        static <T> RTree<T> create(List<Entry<T>> values) {
            return create(values, CHILDREN_PER_NODE);
        }

        static <T> RTree<T> create(List<Entry<T>> values, int childrenPerNode) {
            if (values.isEmpty()) {
                throw new IllegalArgumentException("Need at least one value to build the search tree.");
            }
            int dimensions = values.get(0).parameters().parameterSpace().size();
            if (dimensions != PARAMETER_COUNT) {
                throw new IllegalStateException("Expecting parameter space to be 7, got " + dimensions);
            }
            List<Leaf<T>> leaves = values.stream()
                .map(entry -> new Leaf<>(entry.parameters(), entry.value()))
                .collect(Collectors.toCollection(ArrayList::new));
            return new RTree<>(build(dimensions, leaves, childrenPerNode));
        }

        private static <T> Node<T> build(int dimensions, List<? extends Node<T>> children, int childrenPerNode) {
            if (children.isEmpty()) {
                throw new IllegalStateException("Need at least one child to build a node");
            }
            if (children.size() == 1) {
                return children.get(0);
            }
            if (children.size() <= childrenPerNode) {
                children.sort(Comparator.comparingLong(node -> {
                    long totalMagnitude = 0L;
                    for (int d = 0; d < dimensions; d++) {
                        Parameter parameter = node.parameterSpace[d];
                        totalMagnitude += Math.abs((parameter.min() + parameter.max()) / 2L);
                    }
                    return totalMagnitude;
                }));
                return new SubTree<>(children);
            }
            long minCost = Long.MAX_VALUE;
            int minDimension = -1;
            List<SubTree<T>> minBuckets = null;
            for (int d = 0; d < dimensions; d++) {
                sort(children, dimensions, d, false);
                List<SubTree<T>> buckets = bucketize(children, childrenPerNode);
                long totalCost = 0L;
                for (SubTree<T> bucket : buckets) {
                    totalCost += cost(bucket.parameterSpace);
                }
                if (minCost > totalCost) {
                    minCost = totalCost;
                    minDimension = d;
                    minBuckets = buckets;
                }
            }
            sort(minBuckets, dimensions, minDimension, true);
            return new SubTree<>(minBuckets.stream()
                .map(bucket -> build(dimensions, Arrays.asList(bucket.children), childrenPerNode))
                .collect(Collectors.toList()));
        }

        private static <T> void sort(List<? extends Node<T>> children, int dimensions, int dimension,
                                     boolean absolute) {
            Comparator<Node<T>> comparator = comparator(dimension, absolute);
            for (int d = 1; d < dimensions; d++) {
                comparator = comparator.thenComparing(comparator((dimension + d) % dimensions, absolute));
            }
            children.sort(comparator);
        }

        private static <T> Comparator<Node<T>> comparator(int dimension, boolean absolute) {
            return Comparator.comparingLong(node -> {
                Parameter parameter = node.parameterSpace[dimension];
                long center = (parameter.min() + parameter.max()) / 2L;
                return absolute ? Math.abs(center) : center;
            });
        }

        private static <T> List<SubTree<T>> bucketize(List<? extends Node<T>> nodes, int childrenPerNode) {
            List<SubTree<T>> buckets = new ArrayList<>();
            List<Node<T>> children = new ArrayList<>();
            int expectedChildrenCount = (int) Math.pow(childrenPerNode,
                Math.floor(Math.log(nodes.size() - 0.01) / Math.log(childrenPerNode)));
            for (Node<T> child : nodes) {
                children.add(child);
                if (children.size() >= expectedChildrenCount) {
                    buckets.add(new SubTree<>(children));
                    children = new ArrayList<>();
                }
            }
            if (!children.isEmpty()) {
                buckets.add(new SubTree<>(children));
            }
            return buckets;
        }

        private static long cost(Parameter[] parameterSpace) {
            long result = 0L;
            for (Parameter parameter : parameterSpace) {
                result += Math.abs(parameter.max() - parameter.min());
            }
            return result;
        }

        private static <T> List<Parameter> buildParameterSpace(List<? extends Node<T>> children) {
            if (children.isEmpty()) {
                throw new IllegalArgumentException("SubTree needs at least one child");
            }
            List<Parameter> bounds = new ArrayList<>();
            for (int d = 0; d < PARAMETER_COUNT; d++) {
                bounds.add(null);
            }
            for (Node<T> child : children) {
                for (int d = 0; d < PARAMETER_COUNT; d++) {
                    bounds.set(d, child.parameterSpace[d].span(bounds.get(d)));
                }
            }
            return bounds;
        }

        T search(TargetPoint target, DistanceMetric<T> distanceMetric) {
            long[] targetArray = target.toParameterArray();
            Leaf<T> leaf = this.root.search(targetArray, this.lastResult.get(), distanceMetric);
            this.lastResult.set(leaf);
            return leaf.value;
        }

        abstract static class Node<T> {
            protected final Parameter[] parameterSpace;

            protected Node(List<Parameter> parameterSpace) {
                this.parameterSpace = parameterSpace.toArray(new Parameter[0]);
            }

            protected abstract Leaf<T> search(long[] target, Leaf<T> candidate, DistanceMetric<T> distanceMetric);

            protected long distance(long[] target) {
                long distance = 0L;
                for (int i = 0; i < PARAMETER_COUNT; i++) {
                    distance += square(this.parameterSpace[i].distance(target[i]));
                }
                return distance;
            }

            @Override
            public String toString() {
                return Arrays.toString(this.parameterSpace);
            }
        }

        static final class Leaf<T> extends Node<T> {
            private final T value;

            private Leaf(ParameterPoint parameterPoint, T value) {
                super(parameterPoint.parameterSpace());
                this.value = value;
            }

            @Override
            protected Leaf<T> search(long[] target, Leaf<T> candidate, DistanceMetric<T> distanceMetric) {
                return this;
            }
        }

        static final class SubTree<T> extends Node<T> {
            private final Node<T>[] children;

            SubTree(List<? extends Node<T>> children) {
                this(buildParameterSpace(children), children);
            }

            @SuppressWarnings("unchecked")
            SubTree(List<Parameter> parameterSpace, List<? extends Node<T>> children) {
                super(parameterSpace);
                this.children = children.toArray(new Node[0]);
            }

            @Override
            protected Leaf<T> search(long[] target, Leaf<T> candidate, DistanceMetric<T> distanceMetric) {
                long minDistance = candidate == null ? Long.MAX_VALUE : distanceMetric.distance(candidate, target);
                Leaf<T> closestLeaf = candidate;
                for (Node<T> child : this.children) {
                    long childDistance = distanceMetric.distance(child, target);
                    if (minDistance > childDistance) {
                        Leaf<T> leaf = child.search(target, closestLeaf, distanceMetric);
                        long leafDistance = child == leaf ? childDistance : distanceMetric.distance(leaf, target);
                        if (minDistance > leafDistance) {
                            minDistance = leafDistance;
                            closestLeaf = leaf;
                        }
                    }
                }
                return closestLeaf;
            }
        }
    }
}
