package com.example.worldgen.material;

import com.example.worldgen.density.DensityFunction;
import com.example.worldgen.density.Holder;
import com.example.worldgen.density.WorldgenRegistries;
import com.example.worldgen.json.Json;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.material.condition.AbovePreliminarySurfaceCondition;
import com.example.worldgen.material.condition.BiomeCondition;
import com.example.worldgen.material.condition.CaveSurface;
import com.example.worldgen.material.condition.HoleCondition;
import com.example.worldgen.material.condition.MaterialCondition;
import com.example.worldgen.material.condition.NoiseThresholdCondition;
import com.example.worldgen.material.condition.NotCondition;
import com.example.worldgen.material.condition.SteepCondition;
import com.example.worldgen.material.condition.StoneDepthCondition;
import com.example.worldgen.material.condition.TemperatureCondition;
import com.example.worldgen.material.condition.VerticalGradientCondition;
import com.example.worldgen.material.condition.WaterCondition;
import com.example.worldgen.material.condition.YCondition;
import com.example.worldgen.material.rule.BandlandsRule;
import com.example.worldgen.material.rule.BlockRule;
import com.example.worldgen.material.rule.ConditionRule;
import com.example.worldgen.material.rule.MaterialRule;
import com.example.worldgen.material.rule.OreVeinRule;
import com.example.worldgen.material.rule.SequenceRule;
import com.example.worldgen.provider.VerticalAnchor;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * Decodes the {@code minecraft:worldgen/material_rule} and {@code minecraft:worldgen/material_condition} registries
 * ({@code data/<ns>/worldgen/material_rule/<path>.json}, {@code .../material_condition/<path>.json}) like the 26.3 codecs:
 * <ul>
 *     <li>{@code MaterialRule.CODEC} / {@code MaterialCondition.CODEC}: a string is a registry reference (decoded to
 *     {@link MaterialRule.HolderHolder} / {@link MaterialCondition.HolderHolder}, unknown ids are errors), an object is a
 *     direct value dispatched on {@code type} over the registered types (unknown types are errors);</li>
 *     <li>{@code BlockState.CODEC}: {@code either(block id -> defaultBlockState, {Name, Properties})}; a bare id becomes a
 *     {@link StateRef} without properties (the default state);</li>
 *     <li>{@code VerticalAnchor.CODEC}: exactly one of {@code absolute}, {@code above_bottom}, {@code below_top},
 *     {@code relative_to_sea_level}, each in {@code [DimensionType.MIN_Y, MAX_Y]};</li>
 *     <li>{@code RegistryCodecs.holderSet(BIOME)}: an id, a list of ids or a {@code #tag}
 *     ({@code data/<ns>/tags/worldgen/biome}); every biome must exist under {@code worldgen/biome};</li>
 *     <li>density functions through {@link WorldgenRegistries#parser()}; {@code Codec.intRange(-20, 20)} for
 *     {@code surface_depth_multiplier}, {@code floatRange(0, 1)} for {@code raw_ore_chance}; record codecs ignore unknown
 *     keys.</li>
 * </ul>
 * Registry entries are decoded once per id and shared by every reference.
 */
public final class MaterialRuleLoader {

    public static final List<String> RULE_TYPES = List.of("minecraft:block", "minecraft:bandlands", "minecraft:sequence",
            "minecraft:condition", "minecraft:ore_vein");
    public static final List<String> CONDITION_TYPES = List.of("minecraft:biome", "minecraft:noise_threshold",
            "minecraft:vertical_gradient", "minecraft:y_above", "minecraft:water", "minecraft:temperature", "minecraft:steep",
            "minecraft:not", "minecraft:hole", "minecraft:above_preliminary_surface", "minecraft:stone_depth");

    private final WorldgenRegistries registries;
    private final Path dataRoot;
    private final Map<String, Holder.Reference<MaterialRule>> rules = new HashMap<>();
    private final Map<String, Holder.Reference<MaterialCondition>> conditions = new HashMap<>();

    public MaterialRuleLoader(WorldgenRegistries registries) {
        this.registries = registries;
        this.dataRoot = registries.dataRoot();
    }

    /** The registered rule {@code id} (decoded the first time). */
    public synchronized MaterialRule rule(String id) {
        return new MaterialRule.HolderHolder(ruleReference(id));
    }

    /** The registered condition {@code id} (decoded the first time). */
    public synchronized MaterialCondition condition(String id) {
        return new MaterialCondition.HolderHolder(conditionReference(id));
    }

    /** The decoded value of rule {@code id} itself (not wrapped in a reference). */
    public synchronized MaterialRule ruleValue(String id) {
        return ruleReference(id).value();
    }

    public synchronized MaterialCondition conditionValue(String id) {
        return conditionReference(id).value();
    }

    public List<String> listRuleIds() {
        return registries.listIds("material_rule");
    }

    public List<String> listConditionIds() {
        return registries.listIds("material_condition");
    }

    private Holder.Reference<MaterialRule> ruleReference(String id) {
        String key = WorldgenRegistries.normalize(id);
        Holder.Reference<MaterialRule> ref = rules.get(key);
        if (ref != null) {
            return ref;
        }
        Path file = file(key, "material_rule");
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Unknown registry key in minecraft:worldgen/material_rule: " + key);
        }
        ref = new Holder.Reference<>(key);
        rules.put(key, ref);
        try {
            ref.bindValue(decodeRule(Json.parseWithNumberLiterals(WorldgenRegistries.read(file))));
        } catch (RuntimeException e) {
            rules.remove(key);
            throw new IllegalArgumentException("Invalid material rule " + key + ": " + e.getMessage(), e);
        }
        return ref;
    }

    private Holder.Reference<MaterialCondition> conditionReference(String id) {
        String key = WorldgenRegistries.normalize(id);
        Holder.Reference<MaterialCondition> ref = conditions.get(key);
        if (ref != null) {
            return ref;
        }
        Path file = file(key, "material_condition");
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Unknown registry key in minecraft:worldgen/material_condition: " + key);
        }
        ref = new Holder.Reference<>(key);
        conditions.put(key, ref);
        try {
            ref.bindValue(decodeCondition(Json.parseWithNumberLiterals(WorldgenRegistries.read(file))));
        } catch (RuntimeException e) {
            conditions.remove(key);
            throw new IllegalArgumentException("Invalid material condition " + key + ": " + e.getMessage(), e);
        }
        return ref;
    }

    private Path file(String key, String folder) {
        int colon = key.indexOf(':');
        return dataRoot.resolve(key.substring(0, colon)).resolve("worldgen").resolve(folder).resolve(key.substring(colon + 1) + ".json");
    }

    /** {@code MaterialRule.CODEC}. */
    public synchronized MaterialRule decodeRule(Object json) {
        if (json instanceof String id) {
            return new MaterialRule.HolderHolder(ruleReference(id));
        }
        Map<String, Object> m = object(json, "material rule");
        String type = WorldgenRegistries.normalize(string(required(m, "type"), "type"));
        return switch (type) {
            case "minecraft:block" -> new BlockRule(blockState(required(m, "result_state")));
            case "minecraft:bandlands" -> BandlandsRule.INSTANCE;
            case "minecraft:sequence" -> {
                List<MaterialRule> sequence = new ArrayList<>();
                for (Object element : list(required(m, "sequence"), "sequence")) {
                    sequence.add(decodeRule(element));
                }
                yield new SequenceRule(sequence);
            }
            case "minecraft:condition" -> new ConditionRule(decodeCondition(required(m, "if_true")), decodeRule(required(m, "then_run")));
            case "minecraft:ore_vein" -> {
                float chance = number(required(m, "raw_ore_chance"), "raw_ore_chance").floatValue();
                if (!(chance >= 0.0F && chance <= 1.0F)) {
                    throw new IllegalArgumentException("Value " + chance + " outside of range [0.0:1.0]");
                }
                yield new OreVeinRule(blockState(required(m, "ore_block")), blockState(required(m, "raw_ore_block")),
                        blockState(required(m, "filler_block")), chance, densityFunction(required(m, "density")),
                        densityFunction(required(m, "richness")), densityFunction(required(m, "filler_gap")));
            }
            default -> throw new IllegalArgumentException("Unknown material rule type: " + type);
        };
    }

    /** {@code MaterialCondition.CODEC}. */
    public synchronized MaterialCondition decodeCondition(Object json) {
        if (json instanceof String id) {
            return new MaterialCondition.HolderHolder(conditionReference(id));
        }
        Map<String, Object> m = object(json, "material condition");
        String type = WorldgenRegistries.normalize(string(required(m, "type"), "type"));
        return switch (type) {
            case "minecraft:biome" -> new BiomeCondition(biomeSet(required(m, "biome_is")));
            case "minecraft:noise_threshold" -> new NoiseThresholdCondition(
                    WorldgenRegistries.normalize(string(required(m, "noise"), "noise")),
                    number(required(m, "min_threshold"), "min_threshold").doubleValue(),
                    number(required(m, "max_threshold"), "max_threshold").doubleValue(),
                    m.containsKey("is_3d") && bool(m.get("is_3d"), "is_3d"));
            case "minecraft:vertical_gradient" -> new VerticalGradientCondition(
                    WorldgenRegistries.normalize(string(required(m, "random_name"), "random_name")),
                    anchor(required(m, "true_at_and_below")), anchor(required(m, "false_at_and_above")));
            case "minecraft:y_above" -> new YCondition(anchor(required(m, "anchor")),
                    multiplier(required(m, "surface_depth_multiplier")), bool(required(m, "add_stone_depth"), "add_stone_depth"));
            case "minecraft:water" -> new WaterCondition(number(required(m, "offset"), "offset").intValue(),
                    multiplier(required(m, "surface_depth_multiplier")), bool(required(m, "add_stone_depth"), "add_stone_depth"));
            case "minecraft:temperature" -> TemperatureCondition.INSTANCE;
            case "minecraft:steep" -> SteepCondition.INSTANCE;
            case "minecraft:not" -> new NotCondition(decodeCondition(required(m, "invert")));
            case "minecraft:hole" -> HoleCondition.INSTANCE;
            case "minecraft:above_preliminary_surface" -> AbovePreliminarySurfaceCondition.INSTANCE;
            case "minecraft:stone_depth" -> new StoneDepthCondition(number(required(m, "offset"), "offset").intValue(),
                    bool(required(m, "add_surface_depth"), "add_surface_depth"),
                    number(required(m, "secondary_depth_range"), "secondary_depth_range").intValue(),
                    CaveSurface.byName(string(required(m, "surface_type"), "surface_type")));
            default -> throw new IllegalArgumentException("Unknown material condition type: " + type);
        };
    }

    private DensityFunction densityFunction(Object json) {
        return registries.parser().parse(json);
    }

    /** {@code BlockState.CODEC}. */
    public static StateRef blockState(Object json) {
        if (json instanceof String id) {
            return StateRef.of(WorldgenRegistries.normalize(id));
        }
        Map<String, Object> m = object(json, "block state");
        String name = WorldgenRegistries.normalize(string(required(m, "Name"), "Name"));
        Map<String, String> properties = new TreeMap<>();
        if (m.containsKey("Properties")) {
            for (Map.Entry<String, Object> e : object(m.get("Properties"), "Properties").entrySet()) {
                properties.put(e.getKey(), string(e.getValue(), e.getKey()));
            }
        }
        return new StateRef(name, properties);
    }

    /** {@code VerticalAnchor.CODEC} ({@link VerticalAnchor#parse}; a value that does not decode is an {@link IllegalArgumentException}). */
    static VerticalAnchor anchor(Object json) {
        return VerticalAnchor.parse(json);
    }

    private static int multiplier(Object json) {
        int v = number(json, "surface_depth_multiplier").intValue();
        if (v < -20 || v > 20) {
            throw new IllegalArgumentException("Value " + v + " outside of range [-20:20]");
        }
        return v;
    }

    /** {@code RegistryCodecs.holderSet(Registries.BIOME)}. */
    private Set<String> biomeSet(Object json) {
        Set<String> out = new LinkedHashSet<>();
        if (json instanceof String s && s.startsWith("#")) {
            addTag(WorldgenRegistries.normalize(s.substring(1)), out, new LinkedHashSet<>());
            return out;
        }
        List<?> ids = json instanceof List<?> l ? l : List.of(json);
        for (Object id : ids) {
            out.add(biome(string(id, "biome_is")));
        }
        return out;
    }

    private String biome(String id) {
        String key = WorldgenRegistries.normalize(id);
        if (!Files.isRegularFile(file(key, "biome"))) {
            throw new IllegalArgumentException("Unknown registry key in minecraft:worldgen/biome: " + key);
        }
        return key;
    }

    private void addTag(String tag, Set<String> out, Set<String> visiting) {
        if (!visiting.add(tag)) {
            throw new IllegalArgumentException("Biome tag cycle at #" + tag);
        }
        int colon = tag.indexOf(':');
        Path file = dataRoot.resolve(tag.substring(0, colon)).resolve("tags").resolve("worldgen").resolve("biome")
                .resolve(tag.substring(colon + 1) + ".json");
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Missing biome tag #" + tag);
        }
        Map<String, Object> m = object(Json.parse(WorldgenRegistries.read(file)), "tag");
        for (Object entry : list(required(m, "values"), "values")) {
            String id;
            boolean required = true;
            if (entry instanceof Map<?, ?>) {
                Map<String, Object> e = object(entry, "tag entry");
                id = string(required(e, "id"), "id");
                if (e.containsKey("required")) {
                    required = bool(e.get("required"), "required");
                }
            } else {
                id = string(entry, "tag entry");
            }
            if (id.startsWith("#")) {
                addTag(WorldgenRegistries.normalize(id.substring(1)), out, visiting);
            } else if (required || Files.isRegularFile(file(WorldgenRegistries.normalize(id), "biome"))) {
                out.add(biome(id));
            }
        }
        visiting.remove(tag);
    }

    /** Every {@code minecraft:} id under {@code data/minecraft/worldgen/<folder>} (used by tests). */
    static List<String> ids(Path dataRoot, String folder) {
        Path base = dataRoot.resolve("minecraft").resolve("worldgen").resolve(folder);
        try (Stream<Path> files = Files.walk(base)) {
            return files.filter(p -> p.toString().endsWith(".json")).map(p -> {
                String rel = base.relativize(p).toString().replace('\\', '/');
                return "minecraft:" + rel.substring(0, rel.length() - 5);
            }).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Object required(Map<String, Object> m, String field) {
        Object value = m.get(field);
        if (value == null) {
            throw new IllegalArgumentException("No key " + field + " in " + m.keySet());
        }
        return value;
    }

    private static Number number(Object value, String field) {
        if (!(value instanceof Number n)) {
            throw new IllegalArgumentException(field + ": not a number: " + value);
        }
        return n;
    }

    private static String string(Object value, String field) {
        if (!(value instanceof String s)) {
            throw new IllegalArgumentException(field + ": not a string: " + value);
        }
        return s;
    }

    private static boolean bool(Object value, String field) {
        if (!(value instanceof Boolean b)) {
            throw new IllegalArgumentException(field + ": not a boolean: " + value);
        }
        return b;
    }

    private static List<?> list(Object value, String field) {
        if (!(value instanceof List<?> l)) {
            throw new IllegalArgumentException(field + ": not a list");
        }
        return l;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String field) {
        if (!(value instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(field + ": not an object: " + value);
        }
        return (Map<String, Object>) map;
    }
}
