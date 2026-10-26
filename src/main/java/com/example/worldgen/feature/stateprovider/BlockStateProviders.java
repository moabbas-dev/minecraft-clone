package com.example.worldgen.feature.stateprovider;

import com.example.worldgen.feature.BlockStates;
import com.example.worldgen.feature.Direction;
import com.example.worldgen.feature.predicate.BlockPredicates;
import com.example.worldgen.json.Json;
import com.example.worldgen.noise.NoiseParametersLoader;
import com.example.worldgen.noise.NormalNoise;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.provider.CodecJson;
import com.example.worldgen.provider.IntProvider;
import com.example.worldgen.provider.WeightedList;
import com.example.worldgen.terrain.BlockTags;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code BlockStateProvider.CODEC} (26.3): {@code RegistryCodecs.holder(BLOCK_STATE_PROVIDER, DIRECT_CODEC)}: a string is a
 * reference to {@code worldgen/block_state_provider/<id>.json}; an object is {@code DIRECT_CODEC} =
 * {@code xor(BlockState.FULL_CODEC, TYPED_CODEC)}: a plain state ({@code {"id", "properties"}}) becomes a
 * {@code SimpleStateProvider}, an object with a {@code "type"} key is dispatched by type. Field codecs per type (bytecode):
 * <ul>
 *     <li>{@code weighted}: {@code entries} = {@code WeightedList.nonEmptyCodec(BlockState.CODEC)};</li>
 *     <li>{@code noise_threshold}: {@code seed} (long), {@code noise} ({@code NormalNoise.DIRECT_CODEC}), {@code scale}
 *     ({@code ExtraCodecs.POSITIVE_FLOAT}), {@code threshold} ([-1, 1]), {@code high_chance} ([0, 1]),
 *     {@code default_state}, {@code low_states} and {@code high_states} (non-empty {@code BlockState.CODEC} lists);</li>
 *     <li>{@code noise}: {@code seed}, {@code noise}, {@code scale}, {@code states} (non-empty);</li>
 *     <li>{@code dual_noise}: {@code variety} ({@code InclusiveRange.codec(Codec.INT, 1, 64)}: {@code [min, max]} or
 *     {@code {min_inclusive, max_inclusive}}, min &lt;= max), {@code slow_noise}, {@code slow_scale} (positive) and the
 *     {@code noise} fields;</li>
 *     <li>{@code random_block}: {@code blocks} ({@code RegistryCodecs.holderSet(BLOCK)}: an id, {@code #tag} or a list);</li>
 *     <li>{@code randomized_int}: {@code source} (provider), {@code property} (string), {@code values} ({@code IntProviders.CODEC});</li>
 *     <li>{@code rotated}: {@code state} (provider), optional {@code direction};</li>
 *     <li>{@code copy_properties}: {@code source} (provider);</li>
 *     <li>{@code rule_based}: optional {@code fallback}, {@code rules} of {@code {if_true, then}};</li>
 *     <li>{@code simple}: {@code state}.</li>
 * </ul>
 */
public final class BlockStateProviders {

    /** {@code BlockStateProviderTypes.bootstrap} (26.3), in registration order. */
    public static final List<String> REGISTERED_TYPES = List.of("copy_properties", "dual_noise", "noise", "noise_threshold",
            "random_block", "randomized_int", "rotated", "rule_based", "simple", "weighted");

    private final Path dataRoot;
    private final BlockTags tags;

    public BlockStateProviders(Path dataRoot, BlockTags tags) {
        this.dataRoot = dataRoot;
        this.tags = tags;
    }

    public BlockStateProvider parse(Object json) {
        if (json instanceof String ref) {
            String id = ref.indexOf(':') < 0 ? "minecraft:" + ref : ref;
            Path file = dataRoot.resolve(id.substring(0, id.indexOf(':'))).resolve("worldgen").resolve("block_state_provider")
                    .resolve(id.substring(id.indexOf(':') + 1) + ".json");
            try {
                return parse(Json.parseWithNumberLiterals(Files.readString(file, StandardCharsets.UTF_8)));
            } catch (IOException e) {
                throw new UncheckedIOException("missing block state provider " + id, e);
            }
        }
        Map<String, Object> m = CodecJson.object(json, "block state provider");
        boolean typed = m.containsKey("type");
        boolean state = m.containsKey("id");
        if (typed == state) throw new CodecJson.CodecException("Neither or both of state / typed provider: " + m);
        if (state) return new BlockStateProvider.Simple(BlockStates.decodeFull(m));
        String type = CodecJson.type(m);
        switch (type) {
            case "simple":
                return new BlockStateProvider.Simple(BlockStates.decode(CodecJson.field(m, "state")));
            case "rule_based": {
                Object fb = m.get("fallback");
                List<BlockStateProvider.Rule> rules = new ArrayList<>();
                for (Object r : CodecJson.list(CodecJson.field(m, "rules"), "rules")) {
                    Map<String, Object> rm = CodecJson.object(r, "rule");
                    rules.add(new BlockStateProvider.Rule(BlockPredicates.parse(CodecJson.field(rm, "if_true"), tags),
                            parse(CodecJson.field(rm, "then"))));
                }
                return new BlockStateProvider.RuleBased(fb == null ? null : parse(fb), List.copyOf(rules));
            }
            case "weighted":
                return new BlockStateProvider.Weighted(WeightedList.parseNonEmpty(CodecJson.field(m, "entries"), BlockStates::decode));
            case "noise_threshold":
                return new BlockStateProvider.NoiseThreshold(seed(m), noise(m, "noise"), positiveFloat(m, "scale"),
                        CodecJson.floatInRange(CodecJson.field(m, "threshold"), -1.0F, 1.0F, "threshold"),
                        CodecJson.floatInRange(CodecJson.field(m, "high_chance"), 0.0F, 1.0F, "high_chance"),
                        BlockStates.decode(CodecJson.field(m, "default_state")), states(m, "low_states"), states(m, "high_states"));
            case "noise":
                return new BlockStateProvider.NoiseStates(seed(m), noise(m, "noise"), positiveFloat(m, "scale"), states(m, "states"));
            case "dual_noise": {
                int[] variety = inclusiveRange(CodecJson.field(m, "variety"), 1, 64);
                return new BlockStateProvider.DualNoise(variety[0], variety[1], noise(m, "slow_noise"), positiveFloat(m, "slow_scale"),
                        seed(m), noise(m, "noise"), positiveFloat(m, "scale"), states(m, "states"));
            }
            case "random_block":
                return new BlockStateProvider.RandomBlock(holderSet(CodecJson.field(m, "blocks")));
            case "randomized_int": {
                Object property = CodecJson.field(m, "property");
                if (!(property instanceof String p)) throw new CodecJson.CodecException("Not a string: " + property);
                return new BlockStateProvider.RandomizedInt(parse(CodecJson.field(m, "source")), p,
                        IntProvider.parse(CodecJson.field(m, "values")));
            }
            case "rotated": {
                Object d = m.get("direction");
                return new BlockStateProvider.Rotated(parse(CodecJson.field(m, "state")), d == null ? null : Direction.parse(d));
            }
            case "copy_properties":
                return new BlockStateProvider.CopyProperties(parse(CodecJson.field(m, "source")));
            default:
                throw new CodecJson.CodecException("Unknown block state provider type: " + type);
        }
    }

    private static long seed(Map<String, Object> m) {
        Object o = CodecJson.field(m, "seed");
        if (!(o instanceof Number n)) throw new CodecJson.CodecException("Not a number: " + o);
        return n.longValue();
    }

    /** {@code NormalNoise.DIRECT_CODEC} ({@link NoiseParametersLoader#decode}, which reads the plain JSON number forms). */
    private static NormalNoise noise(Map<String, Object> m, String field) {
        return NormalNoise.of(NoiseParametersLoader.decode(plainNumbers(CodecJson.field(m, field))));
    }

    /** Number literals as {@code Json.parse} gives them: an integer literal as Long, any other number as Double. */
    private static Object plainNumbers(Object json) {
        if (json instanceof Json.NumberLiteral n) {
            String s = n.literal();
            boolean integral = s.indexOf('.') < 0 && s.indexOf('e') < 0 && s.indexOf('E') < 0;
            return integral ? (Object) Long.parseLong(s) : (Object) Double.parseDouble(s);
        }
        if (json instanceof Map<?, ?> map) {
            Map<String, Object> out = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) out.put((String) e.getKey(), plainNumbers(e.getValue()));
            return out;
        }
        if (json instanceof List<?> list) {
            List<Object> out = new ArrayList<>();
            for (Object o : list) out.add(plainNumbers(o));
            return out;
        }
        return json;
    }

    /** {@code ExtraCodecs.POSITIVE_FLOAT}: a float {@code > 0}. */
    private static float positiveFloat(Map<String, Object> m, String field) {
        float f = CodecJson.floatValue(CodecJson.field(m, field), field);
        if (!(f > 0.0F)) throw new CodecJson.CodecException("Value must be positive: " + f);
        return f;
    }

    private static List<StateRef> states(Map<String, Object> m, String field) {
        List<StateRef> out = new ArrayList<>();
        for (Object o : CodecJson.list(CodecJson.field(m, field), field)) out.add(BlockStates.decode(o));
        if (out.isEmpty()) throw new CodecJson.CodecException("List must have contents: " + field);
        return List.copyOf(out);
    }

    private static int[] inclusiveRange(Object json, int min, int max) {
        int lo;
        int hi;
        if (json instanceof List<?> l) {
            if (l.size() != 2) throw new CodecJson.CodecException("Input is not a list of 2 elements: " + json);
            lo = CodecJson.intValue(l.get(0), "min_inclusive");
            hi = CodecJson.intValue(l.get(1), "max_inclusive");
        } else {
            Map<String, Object> m = CodecJson.object(json, "inclusive range");
            lo = CodecJson.intField(m, "min_inclusive");
            hi = CodecJson.intField(m, "max_inclusive");
        }
        if (lo > hi) throw new CodecJson.CodecException("min_inclusive must be less than or equal to max_inclusive");
        if (lo < min || hi > max) throw new CodecJson.CodecException("Range limit too large, expected within [" + min + "-" + max + "]");
        return new int[]{lo, hi};
    }

    /**
     * {@code RegistryCodecs.holderSet(BLOCK)}: one id, a {@code #tag} (its contents in {@code TagLoader} order: file order,
     * nested tags expanded in place, duplicates dropped by the {@code LinkedHashSet}) or a list of ids.
     */
    private List<String> holderSet(Object json) {
        if (json instanceof String s) {
            if (s.startsWith("#")) return List.copyOf(orderedTag(normalize(s.substring(1)), new LinkedHashSet<>()));
            return List.of(normalize(s));
        }
        List<String> out = new ArrayList<>();
        for (Object o : CodecJson.list(json, "holder set")) {
            if (!(o instanceof String s)) throw new CodecJson.CodecException("Not an id: " + o);
            out.add(normalize(s));
        }
        return List.copyOf(out);
    }

    @SuppressWarnings("unchecked")
    private Set<String> orderedTag(String tag, Set<String> visiting) {
        if (!visiting.add(tag)) throw new IllegalStateException("tag cycle at " + tag);
        Path file = dataRoot.resolve(tag.substring(0, tag.indexOf(':'))).resolve("tags").resolve("block")
                .resolve(tag.substring(tag.indexOf(':') + 1) + ".json");
        Object json;
        try {
            json = Json.parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("missing block tag " + tag, e);
        }
        Set<String> out = new LinkedHashSet<>();
        for (Object v : (List<Object>) ((Map<String, Object>) json).get("values")) {
            String id = v instanceof Map<?, ?> mm ? (String) mm.get("id") : (String) v;
            if (id.startsWith("#")) out.addAll(orderedTag(normalize(id.substring(1)), visiting));
            else out.add(normalize(id));
        }
        visiting.remove(tag);
        return out;
    }

    private static String normalize(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }
}
