package com.example.worldgen.levelgen;

import com.example.worldgen.density.Holder;
import com.example.worldgen.density.WorldgenRegistries;
import com.example.worldgen.noise.Noise;
import com.example.worldgen.noise.NormalNoise;
import com.example.worldgen.random.PositionalRandomFactory;

/**
 * Port of {@code net.minecraft.world.level.levelgen.Noises} (26.3): the noise registry keys referenced from code
 * and {@link #instantiate}, which seeds a registered noise with {@code random.fromHashOf(key)} (the key's
 * {@code Identifier} string, e.g. {@code "minecraft:offset"}) and calls {@code NormalNoise.create}.
 */
public final class Noises {

    public static final String TEMPERATURE = "minecraft:temperature";
    public static final String VEGETATION = "minecraft:vegetation";
    public static final String CONTINENTALNESS = "minecraft:continentalness";
    public static final String EROSION = "minecraft:erosion";
    public static final String TEMPERATURE_LARGE = "minecraft:temperature_large";
    public static final String VEGETATION_LARGE = "minecraft:vegetation_large";
    public static final String CONTINENTALNESS_LARGE = "minecraft:continentalness_large";
    public static final String EROSION_LARGE = "minecraft:erosion_large";
    public static final String RIDGE = "minecraft:ridge";
    public static final String SHIFT = "minecraft:offset";
    public static final String TEMPERATURE_NETHER = "minecraft:nether/temperature";
    public static final String VEGETATION_NETHER = "minecraft:nether/vegetation";
    public static final String AQUIFER_BARRIER = "minecraft:aquifer_barrier";
    public static final String AQUIFER_FLUID_LEVEL_FLOODEDNESS = "minecraft:aquifer_fluid_level_floodedness";
    public static final String AQUIFER_LAVA = "minecraft:aquifer_lava";
    public static final String AQUIFER_FLUID_LEVEL_SPREAD = "minecraft:aquifer_fluid_level_spread";
    public static final String PILLAR = "minecraft:pillar";
    public static final String PILLAR_RARENESS = "minecraft:pillar_rareness";
    public static final String PILLAR_THICKNESS = "minecraft:pillar_thickness";
    public static final String SPAGHETTI_2D = "minecraft:spaghetti_2d";
    public static final String SPAGHETTI_2D_ELEVATION = "minecraft:spaghetti_2d_elevation";
    public static final String SPAGHETTI_2D_MODULATOR = "minecraft:spaghetti_2d_modulator";
    public static final String SPAGHETTI_2D_THICKNESS = "minecraft:spaghetti_2d_thickness";
    public static final String SPAGHETTI_3D_1 = "minecraft:spaghetti_3d_1";
    public static final String SPAGHETTI_3D_2 = "minecraft:spaghetti_3d_2";
    public static final String SPAGHETTI_3D_RARITY = "minecraft:spaghetti_3d_rarity";
    public static final String SPAGHETTI_3D_THICKNESS = "minecraft:spaghetti_3d_thickness";
    public static final String SPAGHETTI_ROUGHNESS = "minecraft:spaghetti_roughness";
    public static final String SPAGHETTI_ROUGHNESS_MODULATOR = "minecraft:spaghetti_roughness_modulator";
    public static final String CAVE_ENTRANCE = "minecraft:cave_entrance";
    public static final String CAVE_LAYER = "minecraft:cave_layer";
    public static final String CAVE_CHEESE = "minecraft:cave_cheese";
    public static final String ORE_VEININESS = "minecraft:ore_veininess";
    public static final String ORE_VEIN_A = "minecraft:ore_vein_a";
    public static final String ORE_VEIN_B = "minecraft:ore_vein_b";
    public static final String ORE_GAP = "minecraft:ore_gap";
    public static final String NOODLE = "minecraft:noodle";
    public static final String NOODLE_THICKNESS = "minecraft:noodle_thickness";
    public static final String NOODLE_RIDGE_A = "minecraft:noodle_ridge_a";
    public static final String NOODLE_RIDGE_B = "minecraft:noodle_ridge_b";
    public static final String JAGGED = "minecraft:jagged";
    public static final String SURFACE = "minecraft:surface";
    public static final String SURFACE_SECONDARY = "minecraft:surface_secondary";
    public static final String CLAY_BANDS_OFFSET = "minecraft:clay_bands_offset";
    public static final String BADLANDS_PILLAR = "minecraft:badlands_pillar";
    public static final String BADLANDS_PILLAR_ROOF = "minecraft:badlands_pillar_roof";
    public static final String BADLANDS_SURFACE = "minecraft:badlands_surface";
    public static final String ICEBERG_PILLAR = "minecraft:iceberg_pillar";
    public static final String ICEBERG_PILLAR_ROOF = "minecraft:iceberg_pillar_roof";
    public static final String ICEBERG_SURFACE = "minecraft:iceberg_surface";
    public static final String SULFUR_CAVE_GRADIENT = "minecraft:sulfur_cave_gradient";
    public static final String SWAMP = "minecraft:surface_swamp";
    public static final String CALCITE = "minecraft:calcite";
    public static final String GRAVEL = "minecraft:gravel";
    public static final String POWDER_SNOW = "minecraft:powder_snow";
    public static final String PACKED_ICE = "minecraft:packed_ice";
    public static final String ICE = "minecraft:ice";
    public static final String SOUL_SAND_LAYER = "minecraft:soul_sand_layer";
    public static final String GRAVEL_LAYER = "minecraft:gravel_layer";
    public static final String PATCH = "minecraft:patch";
    public static final String SMALL_PATCH = "minecraft:small_patch";
    public static final String NETHERRACK = "minecraft:netherrack";
    public static final String NETHER_WART = "minecraft:nether_wart";
    public static final String NETHER_STATE_SELECTOR = "minecraft:nether_state_selector";

    private Noises() {
    }

    /** {@code Noises.instantiate}: {@code noises.getOrThrow(name).value().create(context.fromHashOf(name))}. */
    public static Noise instantiate(WorldgenRegistries noises, PositionalRandomFactory context, String name) {
        Holder<NormalNoise> holder = noises.noise(name);
        return holder.value().create(context.fromHashOf(WorldgenRegistries.normalize(name)));
    }
}
