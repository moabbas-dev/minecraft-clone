package com.example.worldgen.biome;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * Port of {@code net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList} and its
 * {@code Preset} record (Minecraft Java Edition 26.3), with biomes identified by resource id strings.
 *
 * <p>Verified from the 26.3 bytecode of {@code MultiNoiseBiomeSourceParameterList$Preset},
 * {@code $Preset$1} (the nether table, plain data) and {@code $Preset$2} (the overworld table, which calls
 * {@code generateOverworldBiomes}: {@code new OverworldBiomeBuilder().addBiomes(...)} into an
 * {@code ImmutableList}, then {@code new Climate.ParameterList(list)}). The data-pack file
 * {@code worldgen/multi_noise_biome_source_parameter_list/overworld.json} only names this preset.
 */
public final class MultiNoiseBiomeSourceParameterList {
    private final Preset preset;
    private final Climate.ParameterList<String> parameters;

    public MultiNoiseBiomeSourceParameterList(Preset preset) {
        this.preset = preset;
        this.parameters = preset.provider().get();
    }

    public Preset preset() {
        return this.preset;
    }

    public Climate.ParameterList<String> parameters() {
        return this.parameters;
    }

    /** Vanilla {@code Preset(Identifier id, SourceProvider provider)}. */
    public record Preset(String id, Supplier<Climate.ParameterList<String>> provider) {
        /** Vanilla {@code Preset.NETHER} ({@code $Preset$1.apply}). */
        public static final Preset NETHER = new Preset("minecraft:nether", () -> new Climate.ParameterList<>(List.of(
            new Climate.Entry<>(Climate.parameters(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F), Biomes.NETHER_WASTES),
            new Climate.Entry<>(Climate.parameters(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F),
                Biomes.SOUL_SAND_VALLEY),
            new Climate.Entry<>(Climate.parameters(0.4F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F), Biomes.CRIMSON_FOREST),
            new Climate.Entry<>(Climate.parameters(0.0F, 0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.375F),
                Biomes.WARPED_FOREST),
            new Climate.Entry<>(Climate.parameters(-0.5F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.175F),
                Biomes.BASALT_DELTAS))));

        /** Vanilla {@code Preset.OVERWORLD} ({@code $Preset$2.apply} -> {@code generateOverworldBiomes}). */
        public static final Preset OVERWORLD = new Preset("minecraft:overworld", Preset::generateOverworldBiomes);

        /** Vanilla {@code BY_NAME}. */
        public static final Map<String, Preset> BY_NAME = Map.of(NETHER.id(), NETHER, OVERWORLD.id(), OVERWORLD);

        private static Climate.ParameterList<String> generateOverworldBiomes() {
            List<Climate.Entry<String>> builder = new ArrayList<>();
            new OverworldBiomeBuilder().addBiomes(builder::add);
            return new Climate.ParameterList<>(List.copyOf(builder));
        }

        /** Vanilla {@code usedBiomes}: biome ids of the table, distinct, in table order. */
        public Stream<String> usedBiomes() {
            return this.provider.get().values().stream().map(Climate.Entry::value).distinct();
        }
    }
}
