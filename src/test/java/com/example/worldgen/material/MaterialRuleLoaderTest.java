package com.example.worldgen.material;

import com.example.worldgen.density.WorldgenRegistries;
import com.example.worldgen.json.Json;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.material.condition.CaveSurface;
import com.example.worldgen.material.condition.MaterialCondition;
import com.example.worldgen.material.condition.StoneDepthCondition;
import com.example.worldgen.material.condition.WaterCondition;
import com.example.worldgen.material.rule.BlockRule;
import com.example.worldgen.material.rule.MaterialRule;
import com.example.worldgen.material.rule.OreVeinRule;
import com.example.worldgen.material.rule.SequenceRule;
import com.example.worldgen.provider.VerticalAnchor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Decoding of the 26.3 {@code material_rule/**} and {@code material_condition/**} registries. */
class MaterialRuleLoaderTest {

    private static final Path DATA = Path.of("data");
    private MaterialRuleLoader loader;

    @BeforeEach
    void setUp() {
        assumeTrue(Files.isDirectory(DATA.resolve("minecraft/worldgen/material_rule")), "vanilla data not restored");
        loader = new MaterialRuleLoader(new WorldgenRegistries(DATA));
    }

    @Test
    void everyRuleAndConditionFileDecodes() {
        List<String> rules = loader.listRuleIds();
        List<String> conditions = loader.listConditionIds();
        assertEquals(42, rules.size());
        assertEquals(8, conditions.size());
        for (String id : rules) {
            assertTrue(loader.ruleValue(id) != null, id);
        }
        for (String id : conditions) {
            assertTrue(loader.conditionValue(id) != null, id);
        }
    }

    @Test
    void decodesFieldsLikeTheVanillaCodecs() {
        MaterialRule root = loader.ruleValue("minecraft:overworld");
        SequenceRule sequence = assertInstanceOf(SequenceRule.class, root);
        assertEquals(5, sequence.sequence().size());
        assertInstanceOf(MaterialRule.HolderHolder.class, sequence.sequence().get(0));
        OreVeinRule copper = assertInstanceOf(OreVeinRule.class, loader.ruleValue("overworld/copper_ore_vein"));
        assertEquals(StateRef.of("minecraft:copper_ore"), copper.oreBlock());
        assertEquals(StateRef.of("minecraft:granite"), copper.fillerBlock());
        assertEquals(0.02F, copper.rawOreChance());
        assertEquals(new StoneDepthCondition(0, true, 6, CaveSurface.FLOOR), loader.conditionValue("minecraft:deep_under_floor"));
        assertEquals(new WaterCondition(-1, 0, false), loader.conditionValue("minecraft:not_underwater"));
        assertEquals(VerticalAnchor.AboveBottom.class,
                MaterialRuleLoader.anchor(Json.parse("{\"above_bottom\": 5}")).getClass());
        assertEquals(StateRef.of("minecraft:water", "level", "0"),
                MaterialRuleLoader.blockState(Json.parse("{\"Name\": \"water\", \"Properties\": {\"level\": \"0\"}}")));
        BlockRule block = assertInstanceOf(BlockRule.class, loader.decodeRule(Json.parse("{\"type\": \"block\", \"result_state\": \"stone\"}")));
        assertEquals(StateRef.of("minecraft:stone"), block.resultState());
    }

    @Test
    void rejectsUnknownTypesIdsAndOutOfRangeValues() {
        assertThrows(IllegalArgumentException.class, () -> loader.decodeRule(Json.parse("{\"type\": \"minecraft:surface\"}")));
        assertThrows(IllegalArgumentException.class, () -> loader.decodeCondition(Json.parse("{\"type\": \"minecraft:biome_tag\"}")));
        assertThrows(IllegalArgumentException.class, () -> loader.decodeRule(Json.parse("\"minecraft:does_not_exist\"")));
        assertThrows(IllegalArgumentException.class, () -> loader.decodeCondition(Json.parse("\"minecraft:does_not_exist\"")));
        assertThrows(IllegalArgumentException.class, () -> loader.decodeCondition(Json.parse(
                "{\"type\": \"biome\", \"biome_is\": \"minecraft:no_such_biome\"}")));
        assertThrows(IllegalArgumentException.class, () -> loader.decodeCondition(Json.parse(
                "{\"type\": \"y_above\", \"anchor\": {\"absolute\": 0}, \"surface_depth_multiplier\": 21, \"add_stone_depth\": false}")));
        assertThrows(IllegalArgumentException.class, () -> MaterialRuleLoader.anchor(Json.parse("{\"absolute\": 0, \"below_top\": 0}")));
        assertThrows(IllegalArgumentException.class, () -> loader.decodeRule(Json.parse(
                "{\"type\": \"ore_vein\", \"ore_block\": \"a\", \"raw_ore_block\": \"b\", \"filler_block\": \"c\", \"raw_ore_chance\": 1.5,"
                        + " \"density\": 0, \"richness\": 0, \"filler_gap\": 0}")));
        MaterialCondition tagged = loader.decodeCondition(Json.parse("{\"type\": \"biome\", \"biome_is\": [\"plains\", \"minecraft:desert\"]}"));
        assertEquals(java.util.Set.of("minecraft:plains", "minecraft:desert"),
                ((com.example.worldgen.material.condition.BiomeCondition) tagged).biomes());
    }
}
