package com.example.worldgen.feature.predicate;

import com.example.worldgen.feature.BlockStates;
import com.example.worldgen.feature.Pos;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.provider.CodecJson;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.terrain.BlockTags;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Port of {@code net.minecraft.world.level.levelgen.structure.templatesystem.RuleTest} (26.3) and every type of
 * {@code RuleTestType} ({@code all_of, always_true, any_of, block_match, blockstate_match, not, tag_match, height_match,
 * random_block_match, random_blockstate_match}), bodies from the bytecode. The dispatch key is {@code predicate_type}.
 * {@code blockstate_match} compares canonical vanilla states by identity, which is {@link StateRef} equality.
 */
public interface RuleTest {

    boolean test(StateRef state, Pos pos, RandomSource random);

    static RuleTest parse(Object json, BlockTags tags) {
        Map<String, Object> m = CodecJson.object(json, "rule test");
        Object t = CodecJson.field(m, "predicate_type");
        String type = CodecJson.type(Map.of("type", t));
        switch (type) {
            case "all_of": {
                List<RuleTest> rules = list(CodecJson.field(m, "rules"), tags);
                return (state, pos, random) -> {
                    for (RuleTest r : rules) if (!r.test(state, pos, random)) return false;
                    return true;
                };
            }
            case "any_of": {
                List<RuleTest> rules = list(CodecJson.field(m, "rules"), tags);
                return (state, pos, random) -> {
                    for (RuleTest r : rules) if (r.test(state, pos, random)) return true;
                    return false;
                };
            }
            case "always_true":
                return (state, pos, random) -> true;
            case "block_match": {
                String block = blockId(CodecJson.field(m, "block"));
                return (state, pos, random) -> state.id().equals(block);
            }
            case "blockstate_match": {
                StateRef s = BlockStates.decode(CodecJson.field(m, "block_state"));
                return (state, pos, random) -> state.equals(s);
            }
            case "not": {
                RuleTest rule = parse(CodecJson.field(m, "rule"), tags);
                return (state, pos, random) -> !rule.test(state, pos, random);
            }
            case "tag_match": {
                String tag = blockId(CodecJson.field(m, "tag"));
                tags.members(tag);
                return (state, pos, random) -> tags.is(state, tag);
            }
            case "height_match": {
                int min = CodecJson.intField(m, "min_inclusive");
                int max = CodecJson.intField(m, "max_inclusive");
                return (state, pos, random) -> min <= pos.y() && pos.y() <= max;
            }
            case "random_block_match": {
                String block = blockId(CodecJson.field(m, "block"));
                float probability = CodecJson.floatField(m, "probability");
                return (state, pos, random) -> state.id().equals(block) && random.nextFloat() < probability;
            }
            case "random_blockstate_match": {
                StateRef s = BlockStates.decode(CodecJson.field(m, "block_state"));
                float probability = CodecJson.floatField(m, "probability");
                return (state, pos, random) -> state.equals(s) && random.nextFloat() < probability;
            }
            default:
                throw new CodecJson.CodecException("Unknown rule test type: " + type);
        }
    }

    private static List<RuleTest> list(Object json, BlockTags tags) {
        List<RuleTest> out = new ArrayList<>();
        for (Object o : CodecJson.list(json, "rule tests")) out.add(parse(o, tags));
        return List.copyOf(out);
    }

    private static String blockId(Object json) {
        if (!(json instanceof String s)) throw new CodecJson.CodecException("Not an id: " + json);
        return s.indexOf(':') < 0 ? "minecraft:" + s : s;
    }
}
