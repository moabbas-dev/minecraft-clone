package com.example.worldgen.material.condition;

import com.example.worldgen.material.MaterialRuleContext;

import java.util.function.DoubleSupplier;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.condition.NoiseThresholdCondition} (26.3, type
 * {@code minecraft:noise_threshold}, fields {@code noise} (noise registry id), {@code min_threshold}, {@code max_threshold}
 * (doubles), optional {@code is_3d} (default {@code false})). The value is {@code context.getNoiseSampler(noise, is3d)} (the
 * float noise widened to double, sampled at {@code (blockX, 0, blockZ)} or {@code (blockX, blockY, blockZ)} and cached per
 * XZ or Y update); the condition is {@code min <= v && v <= max}.
 */
public record NoiseThresholdCondition(String noise, double minThreshold, double maxThreshold, boolean is3d)
        implements MaterialCondition {

    @Override
    public ConditionEvaluator compile(MaterialRuleContext context) {
        DoubleSupplier sampler = context.getNoiseSampler(noise, is3d);
        return () -> {
            double v = sampler.getAsDouble();
            return v >= minThreshold && v <= maxThreshold;
        };
    }
}
