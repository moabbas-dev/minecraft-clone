package com.example.worldgen.feature.predicate;

import com.example.worldgen.feature.BlockSet;
import com.example.worldgen.feature.Direction;
import com.example.worldgen.feature.Pos;
import com.example.worldgen.provider.CodecJson;
import com.example.worldgen.provider.VerticalAnchor;
import com.example.worldgen.terrain.BlockTags;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code BlockPredicate.CODEC} ({@code BuiltInRegistries.BLOCK_PREDICATE_TYPE.byNameCodec().dispatch("type", ...)}) for the
 * 16 types of {@code BlockPredicateType} (26.3). Field codecs from the bytecode: the state-testing types and
 * {@code has_sturdy_face}, {@code inside_world_bounds} take {@code Vec3i.offsetCodec(16).optionalFieldOf("offset", ZERO)};
 * {@code unobstructed} takes {@code Vec3i.CODEC.optionalFieldOf("offset", ZERO)}; {@code volume_match} requires
 * {@code min <= max} per axis; {@code would_survive} takes the same offset field and {@code BlockState.CODEC} {@code state}.
 */
public final class BlockPredicates {

    private static final Pos ZERO = new Pos(0, 0, 0);

    private BlockPredicates() {
    }

    public static BlockPredicate parse(Object json, BlockTags tags) {
        Map<String, Object> m = CodecJson.object(json, "block predicate");
        String type = CodecJson.type(m);
        switch (type) {
            case "matching_blocks":
                return new BlockPredicate.MatchingBlocks(offset(m), BlockSet.parse(CodecJson.field(m, "blocks"), tags));
            case "matching_block_tag": {
                String tag = id(CodecJson.field(m, "tag"));
                tags.members(tag);
                return new BlockPredicate.MatchingBlockTag(offset(m), tag, tags);
            }
            case "matching_fluids":
                return new BlockPredicate.MatchingFluids(offset(m), ids(CodecJson.field(m, "fluids")));
            case "matching_biomes":
                return new BlockPredicate.MatchingBiomes(ids(CodecJson.field(m, "biomes")));
            case "has_sturdy_face":
                return new BlockPredicate.HasSturdyFace(offset(m), Direction.parse(CodecJson.field(m, "direction")));
            case "solid":
                return new BlockPredicate.Solid(offset(m));
            case "replaceable":
                return new BlockPredicate.Replaceable(offset(m));
            case "would_survive":
                return new BlockPredicate.WouldSurvive(offset(m), com.example.worldgen.feature.BlockStates.decode(CodecJson.field(m, "state")),
                        com.example.worldgen.feature.level.BlockSurvival.of(tags));
            case "inside_world_bounds":
                return new BlockPredicate.InsideWorldBounds(offset(m));
            case "any_of":
                return new BlockPredicate.AnyOf(list(CodecJson.field(m, "predicates"), tags));
            case "all_of":
                return new BlockPredicate.AllOf(list(CodecJson.field(m, "predicates"), tags));
            case "not":
                return new BlockPredicate.Not(parse(CodecJson.field(m, "predicate"), tags));
            case "true":
                return BlockPredicate.True.INSTANCE;
            case "unobstructed": {
                Object o = m.get("offset");
                return new BlockPredicate.Unobstructed(o == null ? ZERO : Pos.parse(o));
            }
            case "height_range":
                return new BlockPredicate.HeightRange(VerticalAnchor.parse(CodecJson.field(m, "min_inclusive")),
                        VerticalAnchor.parse(CodecJson.field(m, "max_inclusive")));
            case "volume_match": {
                Pos min = Pos.parseOffset(CodecJson.field(m, "min"), 16);
                Pos max = Pos.parseOffset(CodecJson.field(m, "max"), 16);
                if (min.x() > max.x() || min.y() > max.y() || min.z() > max.z()) {
                    throw new CodecJson.CodecException("min bound cannot be larger than max bound");
                }
                return new BlockPredicate.VolumeMatch(min, max, parse(CodecJson.field(m, "match"), tags));
            }
            default:
                throw new CodecJson.CodecException("Unknown block predicate type: " + type);
        }
    }

    private static Pos offset(Map<String, Object> m) {
        Object o = m.get("offset");
        return o == null ? ZERO : Pos.parseOffset(o, 16);
    }

    private static List<BlockPredicate> list(Object json, BlockTags tags) {
        List<BlockPredicate> out = new ArrayList<>();
        for (Object o : CodecJson.list(json, "block predicates")) out.add(parse(o, tags));
        return List.copyOf(out);
    }

    private static String id(Object json) {
        if (!(json instanceof String s)) throw new CodecJson.CodecException("Not an id: " + json);
        return s.indexOf(':') < 0 ? "minecraft:" + s : s;
    }

    /** A direct registry holder set: one id string or a list of ids (tags are not used by these fields in the data). */
    private static List<String> ids(Object json) {
        if (json instanceof String s) {
            if (s.startsWith("#")) throw new CodecJson.CodecException("tag holder sets are not supported here: " + s);
            return List.of(id(s));
        }
        List<String> out = new ArrayList<>();
        for (Object o : CodecJson.list(json, "holder set")) out.add(id(o));
        return List.copyOf(out);
    }
}
