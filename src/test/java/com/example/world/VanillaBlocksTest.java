package com.example.world;

import com.example.world.VanillaBlocks.UnsupportedBlockStateException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class VanillaBlocksTest {

    @Test
    void everyVanillaBlockRoundTrips() {
        int mapped = 0;
        for (BlockType t : BlockType.values()) {
            if (!VanillaBlocks.isVanillaBlock(t)) continue;
            mapped++;
            assertEquals(t, VanillaBlocks.byVanilla(VanillaBlocks.vanillaId(t), VanillaBlocks.vanillaProperties(t)), t.name());
        }
        assertTrue(mapped > 80, "only " + mapped + " blocks mapped");
    }

    @Test
    void defaultStatesAndKnownIds() {
        assertEquals(BlockType.STONE, VanillaBlocks.byVanilla("minecraft:stone"));
        assertEquals(BlockType.STONE, VanillaBlocks.byVanilla("stone", null));
        assertEquals(BlockType.DEEPSLATE, VanillaBlocks.byVanilla("minecraft:deepslate", Map.of("axis", "y")));
        assertEquals(BlockType.GRASS_BLOCK, VanillaBlocks.byVanilla("minecraft:grass_block", Map.of("snowy", "false")));
        assertEquals(BlockType.ORANGE_TERRACOTTA, VanillaBlocks.byVanilla("minecraft:orange_terracotta"));
        assertEquals("minecraft:powder_snow", VanillaBlocks.vanillaId(BlockType.POWDER_SNOW));
        assertEquals("minecraft:wall_torch", VanillaBlocks.vanillaId(BlockType.WALL_TORCH_EAST));
        assertEquals(Map.of("facing", "east"), VanillaBlocks.vanillaProperties(BlockType.WALL_TORCH_EAST));
    }

    @Test
    void leafDecayPropertiesAreAcceptedButWaterloggedIsNot() {
        assertEquals(BlockType.OAK_LEAVES, VanillaBlocks.byVanilla("minecraft:oak_leaves", Map.of("distance", "1", "persistent", "true")));
        assertThrows(UnsupportedBlockStateException.class,
                () -> VanillaBlocks.byVanilla("minecraft:oak_leaves", Map.of("waterlogged", "true")));
        assertThrows(UnsupportedBlockStateException.class,
                () -> VanillaBlocks.byVanilla("minecraft:oak_leaves", Map.of("distance", "8")));
    }

    @Test
    void fluidLevelsFollowLiquidBlock() {
        assertEquals(BlockType.WATER, VanillaBlocks.byVanilla("minecraft:water", Map.of("level", "0")));
        assertEquals(BlockType.WATER_FLOW_7, VanillaBlocks.byVanilla("minecraft:water", Map.of("level", "1")));
        assertEquals(BlockType.WATER_FLOW_1, VanillaBlocks.byVanilla("minecraft:water", Map.of("level", "7")));
        assertEquals(BlockType.WATER_FALLING, VanillaBlocks.byVanilla("minecraft:water", Map.of("level", "8")));
        assertEquals(BlockType.WATER_FALLING, VanillaBlocks.byVanilla("minecraft:water", Map.of("level", "15")));
        assertEquals(BlockType.LAVA, VanillaBlocks.byVanilla("minecraft:lava"));
        assertEquals(BlockType.LAVA_FLOW_6, VanillaBlocks.byVanilla("minecraft:lava", Map.of("level", "2")));
        assertEquals(BlockType.LAVA_FALLING, VanillaBlocks.byVanilla("minecraft:lava", Map.of("level", "9")));
        // overworld lava drops 2 a block: the engine has no lava with an odd amount
        assertThrows(UnsupportedBlockStateException.class, () -> VanillaBlocks.byVanilla("minecraft:lava", Map.of("level", "1")));
        assertThrows(UnsupportedBlockStateException.class, () -> VanillaBlocks.byVanilla("minecraft:lava", Map.of("level", "7")));
    }

    @Test
    void unsupportedStatesThrowWithTheStateInTheException() {
        UnsupportedBlockStateException e = assertThrows(UnsupportedBlockStateException.class,
                () -> VanillaBlocks.byVanilla("minecraft:oak_log", Map.of("axis", "x")));
        assertEquals("minecraft:oak_log", e.id());
        assertEquals(Map.of("axis", "x"), e.properties());
        assertThrows(UnsupportedBlockStateException.class, () -> VanillaBlocks.byVanilla("minecraft:deepslate", Map.of("axis", "z")));
        assertThrows(UnsupportedBlockStateException.class, () -> VanillaBlocks.byVanilla("minecraft:grass_block", Map.of("snowy", "true")));
        assertThrows(UnsupportedBlockStateException.class, () -> VanillaBlocks.byVanilla("minecraft:furnace", Map.of("facing", "east")));
        assertThrows(UnsupportedBlockStateException.class, () -> VanillaBlocks.byVanilla("minecraft:oak_sapling", Map.of("stage", "1")));
        assertThrows(UnsupportedBlockStateException.class, () -> VanillaBlocks.byVanilla("minecraft:stone", Map.of("axis", "y")));
        assertThrows(UnsupportedBlockStateException.class, () -> VanillaBlocks.byVanilla("minecraft:deepslate_copper_ore"));
        assertThrows(UnsupportedBlockStateException.class, () -> VanillaBlocks.byVanilla("minecraft:cave_air"));
        assertThrows(UnsupportedBlockStateException.class, () -> VanillaBlocks.byVanilla("minecraft:basalt"));
    }

    @Test
    void itemsAndInternalStatesHaveNoVanillaBlock() {
        for (BlockType item : new BlockType[]{BlockType.STICK, BlockType.IRON_PICKAXE, BlockType.APPLE, BlockType.COAL,
                BlockType.WATER_BUCKET, BlockType.RAW_IRON, BlockType.OAK_SAPLING_STAGE_2, BlockType.BOW_PULLING_1}) {
            assertFalse(VanillaBlocks.isVanillaBlock(item), item.name());
            assertThrows(IllegalArgumentException.class, () -> VanillaBlocks.vanillaId(item));
        }
        for (BlockType t : BlockType.values()) {
            if (VanillaBlocks.isVanillaBlock(t)) {
                assertFalse(ChunkMeshBuilder.isFlatIconItem(t) && !BlockSupport.isTorch(t) && !ChunkMeshBuilder.isCrossShapedPlant(t),
                        t + " is an item but has a vanilla block id");
            }
        }
    }

    // ---- coverage of the vanilla overworld material rules ----

    private static final Path MATERIAL_RULES = Path.of("data", "minecraft", "worldgen", "material_rule");

    /**
     * Every block the 26.3 OVERWORLD material rules can write: the root overworld.json and every rule it references,
     * including the ore veins, bedrock and the surface rules, plus BandlandsRule's band palette, which is code rather than
     * JSON (MaterialSystem's static initializer: white, orange, yellow, brown, red and light gray terracotta, and terracotta).
     */
    @Test
    void everyBlockTheOverworldMaterialRulesWriteIsMappable() throws IOException {
        Assumptions.assumeTrue(Files.isRegularFile(MATERIAL_RULES.resolve("overworld.json")), "restored data/ folder is absent");
        Set<String> visited = new HashSet<>();
        Set<String> states = new TreeSet<>();
        List<Map<String, String>> withProps = new ArrayList<>();
        Deque<String> todo = new ArrayDeque<>(List.of("minecraft:overworld"));
        boolean bandlands = false;
        while (!todo.isEmpty()) {
            String ref = todo.pop();
            if (!visited.add(ref)) continue;
            Path file = MATERIAL_RULES.resolve(ref.substring("minecraft:".length()) + ".json");
            assertTrue(Files.isRegularFile(file), "missing referenced material rule " + ref);
            Deque<Object[]> nodes = new ArrayDeque<>();
            nodes.push(new Object[]{null, MiniJson.parse(Files.readString(file))});
            while (!nodes.isEmpty()) {
                Object[] kv = nodes.pop();
                String key = (String) kv[0];
                Object node = kv[1];
                if (node instanceof Map<?, ?> map) {
                    if ("minecraft:bandlands".equals(map.get("type"))) bandlands = true;
                    if (isBlockKey(key) && map.containsKey("Name")) { // the object form of a block state
                        @SuppressWarnings("unchecked") Map<String, String> props = (Map<String, String>) map.get("Properties");
                        Map<String, String> state = new LinkedHashMap<>();
                        state.put("Name", (String) map.get("Name"));
                        if (props != null) state.putAll(props);
                        withProps.add(state);
                        continue;
                    }
                    for (Map.Entry<?, ?> e : map.entrySet()) nodes.push(new Object[]{e.getKey(), e.getValue()});
                } else if (node instanceof List<?> list) {
                    for (Object o : list) nodes.push(new Object[]{key, o});
                } else if (node instanceof String s) {
                    if (isBlockKey(key)) states.add(s);
                    else if (key != null && (key.equals("sequence") || key.equals("then_run"))) todo.push(s);
                }
            }
        }
        assertTrue(bandlands, "the overworld surface rule should contain the bandlands rule");
        states.addAll(List.of("minecraft:white_terracotta", "minecraft:orange_terracotta", "minecraft:terracotta",
                "minecraft:yellow_terracotta", "minecraft:brown_terracotta", "minecraft:red_terracotta", "minecraft:light_gray_terracotta"));
        assertTrue(visited.contains("minecraft:overworld/copper_ore_vein") && visited.contains("minecraft:bedrock_floor"), visited.toString());

        List<String> failures = new ArrayList<>();
        for (String id : states) {
            try {
                VanillaBlocks.byVanilla(id);
            } catch (UnsupportedBlockStateException e) {
                failures.add(e.getMessage());
            }
        }
        for (Map<String, String> s : withProps) {
            Map<String, String> props = new LinkedHashMap<>(s);
            String id = props.remove("Name");
            try {
                VanillaBlocks.byVanilla(id, props);
            } catch (UnsupportedBlockStateException e) {
                failures.add(e.getMessage());
            }
        }
        assertEquals(List.of(), failures, "blocks written by the overworld material rules: " + states);
    }

    private static boolean isBlockKey(String key) {
        return key != null && (key.equals("result_state") || key.equals("filler_block") || key.equals("ore_block") || key.equals("raw_ore_block"));
    }

    /** Just enough JSON for the material rule files: objects, arrays, strings, numbers, booleans and null. */
    private static final class MiniJson {
        private final String s;
        private int i;

        private MiniJson(String s) {
            this.s = s;
        }

        static Object parse(String text) {
            MiniJson p = new MiniJson(text);
            Object v = p.value();
            p.ws();
            if (p.i != p.s.length()) throw new IllegalArgumentException("trailing JSON at " + p.i);
            return v;
        }

        private void ws() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        }

        private Object value() {
            ws();
            char c = s.charAt(i);
            if (c == '{') {
                i++;
                Map<String, Object> m = new LinkedHashMap<>();
                ws();
                if (s.charAt(i) == '}') {
                    i++;
                    return m;
                }
                while (true) {
                    ws();
                    String k = string();
                    ws();
                    expect(':');
                    m.put(k, value());
                    ws();
                    if (s.charAt(i++) == '}') return m;
                }
            }
            if (c == '[') {
                i++;
                List<Object> l = new ArrayList<>();
                ws();
                if (s.charAt(i) == ']') {
                    i++;
                    return l;
                }
                while (true) {
                    l.add(value());
                    ws();
                    if (s.charAt(i++) == ']') return l;
                }
            }
            if (c == '"') return string();
            int start = i;
            while (i < s.length() && ",}] \t\r\n".indexOf(s.charAt(i)) < 0) i++;
            String lit = s.substring(start, i);
            return switch (lit) {
                case "true" -> Boolean.TRUE;
                case "false" -> Boolean.FALSE;
                case "null" -> null;
                default -> Double.parseDouble(lit);
            };
        }

        private void expect(char c) {
            if (s.charAt(i++) != c) throw new IllegalArgumentException("expected " + c + " at " + (i - 1));
        }

        private String string() {
            expect('"');
            StringBuilder b = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> b.append('\n');
                        case 't' -> b.append('\t');
                        case 'r' -> b.append('\r');
                        case 'b' -> b.append('\b');
                        case 'f' -> b.append('\f');
                        case 'u' -> {
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> b.append(e);
                    }
                } else {
                    b.append(c);
                }
            }
        }
    }
}
