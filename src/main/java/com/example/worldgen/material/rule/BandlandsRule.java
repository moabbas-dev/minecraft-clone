package com.example.worldgen.material.rule;

import com.example.worldgen.material.MaterialRuleContext;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.rule.BandlandsRule} (26.3, type {@code minecraft:bandlands},
 * no fields): evaluates to {@code context::getBand}, i.e. {@code MaterialSystem.getBand(x, y, z)}.
 */
public enum BandlandsRule implements MaterialRule {
    INSTANCE;

    @Override
    public RuleEvaluator compile(MaterialRuleContext context) {
        return context::getBand;
    }
}
