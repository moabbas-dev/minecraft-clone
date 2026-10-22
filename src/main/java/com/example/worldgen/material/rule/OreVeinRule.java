package com.example.worldgen.material.rule;

import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.material.MaterialRuleContext;
import com.example.worldgen.random.PositionalRandomFactory;
import com.example.worldgen.random.RandomSource;

/**
 * Port of {@code net.minecraft.world.level.levelgen.material.rule.OreVeinRule} (26.3, type {@code minecraft:ore_vein},
 * fields {@code ore_block}, {@code raw_ore_block}, {@code filler_block}, {@code raw_ore_chance} (float in [0, 1]),
 * {@code density}, {@code richness}, {@code filler_gap}). With the release values of {@code SharedConstants.DEBUG_DISABLE_ORE_VEINS}
 * and {@code DEBUG_ORE_VEINS} (both false), {@code compile} is: {@code density} and {@code richness} sampled over the
 * context's chunk volume ({@code getDensitiesInChunk(f, true)}), {@code filler_gap} per point ({@code false}), the random
 * factory {@code minecraft:ore}; the evaluator ({@code lambda$compile$1}):
 * <pre>
 * d = density; if (d &lt;= 0) return null;
 * r = ore.at(x, y, z); if (r.nextFloat() &gt; d) return null;
 * if (r.nextFloat() &lt; richness &amp;&amp; fillerGap &lt; 0) return r.nextFloat() &lt; rawOreChance ? rawOreBlock : oreBlock;
 * return fillerBlock;
 * </pre>
 */
public record OreVeinRule(StateRef oreBlock, StateRef rawOreBlock, StateRef fillerBlock, float rawOreChance,
                          DensityFunction density, DensityFunction richness, DensityFunction fillerGap) implements MaterialRule {

    @Override
    public RuleEvaluator compile(MaterialRuleContext context) {
        MaterialRuleContext.DensityGetter densityGetter = context.getDensitiesInChunk(density, true);
        MaterialRuleContext.DensityGetter richnessGetter = context.getDensitiesInChunk(richness, true);
        MaterialRuleContext.DensityGetter fillerGapGetter = context.getDensitiesInChunk(fillerGap, false);
        PositionalRandomFactory oreRandom = context.getOrCreateRandomFactory("minecraft:ore");
        return (x, y, z) -> {
            float d = densityGetter.get();
            if (d <= 0.0F) {
                return null;
            }
            RandomSource random = oreRandom.at(x, y, z);
            if (random.nextFloat() > d) {
                return null;
            }
            float rich = richnessGetter.get();
            if (random.nextFloat() < rich && fillerGapGetter.get() < 0.0F) {
                return random.nextFloat() < rawOreChance ? rawOreBlock : oreBlock;
            }
            return fillerBlock;
        };
    }
}
