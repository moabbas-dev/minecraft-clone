package com.example.worldgen.feature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * Port of {@code net.minecraft.world.level.biome.FeatureSorter.buildFeaturesPerStep(sources, getter, true)} (26.3) with
 * {@code net.minecraft.util.Graph.depthFirstSearch}:
 * <ol>
 *     <li>per source (biome, in the given order), its step lists are flattened into {@code FeatureData(featureIndex, step,
 *     feature)}; {@code featureIndex} is a first-encounter counter per feature object (identity, shared across sources);</li>
 *     <li>a {@code TreeMap} ordered by {@code (step, featureIndex)} maps each entry to the {@code TreeSet} of its successors in
 *     any source (consecutive entries, across step boundaries);</li>
 *     <li>depth-first search over the keys in map order (successors in set order), post-order collected, then reversed;</li>
 *     <li>a cycle throws ({@code "Feature order cycle found"}; with {@code tryReducingError} the sources involved are named);</li>
 *     <li>step {@code s} keeps the sorted entries of step {@code s}; a feature's index in that list is the {@code index} of
 *     {@code setFeatureSeed(decorationSeed, index, step)}.</li>
 * </ol>
 */
public final class FeatureSorter {

    /** {@code StepFeatureData}: the step's features and the identity index lookup. */
    public record StepFeatureData<F>(List<F> features, Map<F, Integer> indexMapping) {
        StepFeatureData(List<F> features) {
            this(features, identityIndex(features));
        }

        private static <F> Map<F, Integer> identityIndex(List<F> features) {
            Map<F, Integer> m = new IdentityHashMap<>();
            for (int i = 0; i < features.size(); i++) m.put(features.get(i), i);
            return m;
        }

        /** {@code indexMapping().applyAsInt(feature)} ({@code Util.createIndexIdentityLookup}). */
        public int indexOf(F feature) {
            Integer i = indexMapping.get(feature);
            if (i == null) throw new IllegalArgumentException("feature not in this step: " + feature);
            return i;
        }
    }

    private record FeatureData<F>(int featureIndex, int step, F feature) {
    }

    private FeatureSorter() {
    }

    public static <T, F> List<StepFeatureData<F>> buildFeaturesPerStep(List<T> featureSources, Function<T, List<List<F>>> featureGetter,
                                                                       boolean tryReducingError) {
        Map<F, Integer> featureIndex = new IdentityHashMap<>();
        int[] nextFeatureIndex = {0};
        Comparator<FeatureData<F>> comparator = Comparator.<FeatureData<F>>comparingInt(FeatureData::step)
                .thenComparingInt(FeatureData::featureIndex);
        Map<FeatureData<F>, Set<FeatureData<F>>> edges = new TreeMap<>(comparator);
        int maxStep = 0;
        for (T source : featureSources) {
            List<FeatureData<F>> featureList = new ArrayList<>();
            List<List<F>> featuresForStep = featureGetter.apply(source);
            maxStep = Math.max(maxStep, featuresForStep.size());
            for (int i = 0; i < featuresForStep.size(); ++i) {
                for (F feature : featuresForStep.get(i)) {
                    int index = featureIndex.computeIfAbsent(feature, f -> nextFeatureIndex[0]++);
                    featureList.add(new FeatureData<>(index, i, feature));
                }
            }
            for (int i = 0; i < featureList.size(); ++i) {
                Set<FeatureData<F>> data = edges.computeIfAbsent(featureList.get(i), k -> new TreeSet<>(comparator));
                if (i < featureList.size() - 1) data.add(featureList.get(i + 1));
            }
        }
        Set<FeatureData<F>> discovered = new TreeSet<>(comparator);
        Set<FeatureData<F>> currentlyVisiting = new TreeSet<>(comparator);
        List<FeatureData<F>> sortedFeatures = new ArrayList<>();
        for (FeatureData<F> feature : edges.keySet()) {
            if (!currentlyVisiting.isEmpty()) {
                throw new IllegalStateException("You somehow broke the universe; DFS bork (iteration finished with non-empty in-progress vertex set");
            }
            if (!discovered.contains(feature) && depthFirstSearch(edges, discovered, currentlyVisiting, sortedFeatures, feature)) {
                if (!tryReducingError) throw new IllegalStateException("Feature order cycle found");
                List<T> reducedSources = new ArrayList<>(featureSources);
                int lastSize;
                do {
                    lastSize = reducedSources.size();
                    ListIterator<T> iterator = reducedSources.listIterator();
                    while (iterator.hasNext()) {
                        T source = iterator.next();
                        iterator.remove();
                        try {
                            buildFeaturesPerStep(reducedSources, featureGetter, false);
                        } catch (IllegalStateException e) {
                            continue;
                        }
                        iterator.add(source);
                    }
                } while (lastSize != reducedSources.size());
                throw new IllegalStateException("Feature order cycle found, involved sources: " + reducedSources);
            }
        }
        Collections.reverse(sortedFeatures);
        List<StepFeatureData<F>> features = new ArrayList<>();
        for (int step = 0; step < maxStep; ++step) {
            List<F> featuresInStep = new ArrayList<>();
            for (FeatureData<F> d : sortedFeatures) {
                if (d.step() == step) featuresInStep.add(d.feature());
            }
            features.add(new StepFeatureData<>(List.copyOf(featuresInStep)));
        }
        return List.copyOf(features);
    }

    /** {@code Graph.depthFirstSearch}: true when a cycle is found. */
    private static <T> boolean depthFirstSearch(Map<T, Set<T>> edges, Set<T> discovered, Set<T> currentlyVisiting, List<T> postOrder,
                                                T current) {
        if (discovered.contains(current)) return false;
        if (currentlyVisiting.contains(current)) return true;
        currentlyVisiting.add(current);
        for (T next : edges.getOrDefault(current, Set.of())) {
            if (depthFirstSearch(edges, discovered, currentlyVisiting, postOrder, next)) return true;
        }
        currentlyVisiting.remove(current);
        discovered.add(current);
        postOrder.add(current);
        return false;
    }
}
