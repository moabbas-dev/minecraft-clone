package com.example.worldgen.feature.stateprovider;

import com.example.worldgen.feature.BlockStates;
import com.example.worldgen.feature.predicate.BlockPredicates;
import com.example.worldgen.json.Json;
import com.example.worldgen.provider.CodecJson;
import com.example.worldgen.terrain.BlockTags;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * {@code BlockStateProvider.CODEC} (26.3): {@code RegistryCodecs.holder(BLOCK_STATE_PROVIDER, DIRECT_CODEC)}: a string is a
 * reference to {@code worldgen/block_state_provider/<id>.json}; an object is {@code DIRECT_CODEC} =
 * {@code xor(BlockState.FULL_CODEC, TYPED_CODEC)}: a plain state ({@code {"id", "properties"}}) becomes a
 * {@code SimpleStateProvider}, an object with a {@code "type"} key is dispatched by type.
 */
public final class BlockStateProviders {

    /** A registered provider type that is not ported yet. */
    public static final class UnportedProviderException extends IllegalArgumentException {
        public UnportedProviderException(String message) {
            super(message);
        }
    }

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
            case "copy_properties", "dual_noise", "noise", "noise_threshold", "random_block", "randomized_int", "rotated", "weighted":
                throw new UnportedProviderException("block state provider type " + type + " is not ported");
            default:
                throw new CodecJson.CodecException("Unknown block state provider type: " + type);
        }
    }
}
