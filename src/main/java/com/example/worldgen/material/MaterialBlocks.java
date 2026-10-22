package com.example.worldgen.material;

import com.example.worldgen.level.StateRef;

import java.util.Set;

/**
 * The two block-state queries {@code MaterialSystem} makes on chunk blocks, for {@link StateRef}s:
 * <ul>
 *     <li>{@code BlockState.isAir()}: {@code air}, {@code cave_air}, {@code void_air} ({@link StateRef#isAir()});</li>
 *     <li>{@code !BlockState.getFluidState().isEmpty()}: the 26.3 block classes that override {@code getFluidState} (found by
 *     scanning the client jar for the method) return a non-empty fluid either unconditionally ({@code LiquidBlock}:
 *     {@code water}, {@code lava}; {@code BubbleColumnBlock}, {@code KelpBlock}, {@code KelpPlantBlock}, {@code SeagrassBlock},
 *     {@code TallSeagrassBlock}: {@code Fluids.WATER.getSource(false)}) or when their {@code waterlogged} property is
 *     {@code true} (the other overriding classes).</li>
 * </ul>
 * A state without properties stands for the block's default state (as {@code BlockState.CODEC} decodes a bare block id);
 * {@code waterlogged} defaults to {@code false} in every block that has it.
 */
public final class MaterialBlocks {

    public static final StateRef VOID_AIR = StateRef.of("minecraft:void_air");

    private static final Set<String> ALWAYS_FLUID = Set.of("minecraft:water", "minecraft:lava", "minecraft:bubble_column",
            "minecraft:kelp", "minecraft:kelp_plant", "minecraft:seagrass", "minecraft:tall_seagrass");

    private MaterialBlocks() {
    }

    public static boolean isAir(StateRef state) {
        return state.isAir();
    }

    /** {@code !state.getFluidState().isEmpty()}. */
    public static boolean hasFluid(StateRef state) {
        return ALWAYS_FLUID.contains(state.id()) || "true".equals(state.properties().get("waterlogged"));
    }
}
