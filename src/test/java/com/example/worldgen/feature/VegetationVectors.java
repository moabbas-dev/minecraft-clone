package com.example.worldgen.feature;

import com.example.worldgen.feature.level.BlockSurvival;
import com.example.worldgen.feature.level.FeatureChunk;
import com.example.worldgen.feature.level.GenRegion;
import com.example.worldgen.feature.stateprovider.BlockStateProvider;
import com.example.worldgen.feature.stateprovider.BlockStateProviders;
import com.example.worldgen.json.Json;
import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.random.WorldgenRandom;
import com.example.worldgen.random.XoroshiroRandomSource;
import com.example.worldgen.terrain.BlockProperties;
import com.example.worldgen.terrain.BlockTags;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * The port's side of the oracle's direct vectors (the golden's {@code SURV*}, {@code PROV*} and {@code FEAT*} sections, written
 * by the real 26.3 classes): {@code canSurvive} of every ported block class in fixed scenarios, every block state provider type
 * at fixed positions, and every named configured feature of the ported types placed at fixed positions over fresh terrain.
 * The scenario builders mirror the oracle's ({@code Vectors.build}, {@code Vectors.y}) line for line.
 */
final class VegetationVectors {

    private VegetationVectors() {
    }

    static final Pos ORIGIN = new Pos(0, 64, 0);

    static StateRef ref(String s) {
        StateRef p = StateRef.parse(s.startsWith("minecraft:") ? s : "minecraft:" + s);
        if (p.properties().isEmpty()) return BlockProperties.defaultState(p.id());
        return p;
    }

    /** A chunk over a map of blocks (everything else {@code fallback}); writes are kept, nothing else is modelled. */
    static final class MapChunk implements FeatureChunk {
        final Map<Pos, StateRef> blocks;
        final StateRef fallback;
        final int cx;
        final int cz;

        MapChunk(Map<Pos, StateRef> blocks, StateRef fallback, int cx, int cz) {
            this.blocks = blocks;
            this.fallback = fallback;
            this.cx = cx;
            this.cz = cz;
        }

        public int chunkX() {
            return cx;
        }

        public int chunkZ() {
            return cz;
        }

        public StateRef getBlockState(int x, int y, int z) {
            return blocks.getOrDefault(new Pos(x, y, z), fallback);
        }

        public void setBlockState(int x, int y, int z, StateRef state) {
            blocks.put(new Pos(x, y, z), state);
        }

        public StateRef getSectionBlockState(int x, int y, int z) {
            return getBlockState(x, y, z);
        }

        public void setSectionBlockState(int x, int y, int z, StateRef state) {
            setBlockState(x, y, z, state);
        }

        public int getHeight(HeightmapType type, int x, int z) {
            throw new UnsupportedOperationException();
        }

        public void markPosForPostProcessing(int x, int y, int z) {
        }

        public void scheduleFluidTick(int x, int y, int z, String fluid) {
        }

        public void scheduleBlockTick(int x, int y, int z, String block, int delay) {
        }

        public String getNoiseBiome(int quartX, int quartY, int quartZ) {
            throw new UnsupportedOperationException();
        }

        public void forEachStoredBiome(Consumer<String> consumer) {
        }
    }

    static GenRegion mapRegion(BlockTags tags, Map<Pos, StateRef> blocks, StateRef fallback, Pos centre) {
        int cx = centre.x() >> 4;
        int cz = centre.z() >> 4;
        return new GenRegion(0L, cx, cz, (x, z) -> new MapChunk(blocks, fallback, x, z), tags, -64, 384, 63);
    }

    /** Mirror of the oracle's {@code Vectors.build}. */
    static void build(Map<Pos, StateRef> b, String state, String support, String ctx) {
        Pos pos = ORIGIN;
        StateRef sup = ref(support);
        switch (ctx) {
            case "open" -> b.put(pos.below(), sup);
            case "water" -> {
                b.put(pos.below(), sup);
                b.put(pos, ref("water"));
                b.put(pos.above(), ref("water"));
            }
            case "wet_side" -> {
                b.put(pos.below(), sup);
                b.put(pos.below().relative(Direction.NORTH), ref("water"));
            }
            case "self_below" -> {
                StateRef s = ref(state);
                Map<String, String> p = new TreeMap<>(s.properties());
                if (p.containsKey("half")) p.put("half", "lower");
                b.put(pos.below(), new StateRef(s.id(), p));
                b.put(pos.below().below(), sup);
            }
            case "ceiling" -> b.put(pos.above(), sup);
            case "side_solid" -> {
                b.put(pos.below(), sup);
                b.put(pos.relative(Direction.EAST), ref("stone"));
            }
            case "side_lava" -> {
                b.put(pos.below(), sup);
                b.put(pos.relative(Direction.EAST), ref("lava"));
            }
            case "wall" -> {
                for (Direction d : Direction.values()) if (d.isHorizontal()) b.put(pos.relative(d), sup);
            }
            default -> throw new IllegalArgumentException(ctx);
        }
    }

    /** One {@code V} line: the results of a state on a support over all contexts ({@code T}, {@code F}, {@code R}, {@code X}). */
    static String survival(BlockTags tags, String state, String support, List<String> contexts) {
        StringBuilder sb = new StringBuilder();
        BlockSurvival survival = BlockSurvival.of(tags);
        for (String ctx : contexts) {
            Map<Pos, StateRef> b = new HashMap<>();
            build(b, state, support, ctx);
            GenRegion level = mapRegion(tags, b, StateRef.of("minecraft:air"), ORIGIN);
            String r;
            try {
                r = survival.canSurvive(ref(state), level, ORIGIN) ? "T" : "F";
            } catch (UnportedFeature.Reached e) {
                r = "R";
            } catch (RuntimeException e) {
                r = "X";
            }
            sb.append(r);
        }
        return sb.toString();
    }

    /** The {@code Q} lines of one provider (48 positions, getState then getOptionalState, the draw count). */
    static void provider(BlockStateProviders providers, BlockTags tags, int p, String spec, List<String> out) {
        Object json = spec.startsWith("{") ? Json.parseWithNumberLiterals(spec) : spec;
        BlockStateProvider provider = providers.parse(json);
        StateRef fallback = StateRef.of("minecraft:big_dripleaf_stem", "facing", "east", "waterlogged", "true");
        WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(1000L + p));
        for (int i = 0; i < 48; i++) {
            Pos pos = new Pos(i * 37 - 1000, i % 7 * 13 - 20, i * 53 + 300);
            GenRegion level = mapRegion(tags, new HashMap<>(), fallback, pos);
            StateRef s = provider.getState(level, random, pos);
            StateRef o = provider.getOptionalState(level, random, pos);
            out.add("Q " + p + " " + i + " " + s + " " + o + " " + random.getCount());
        }
    }

    /** Mirror of the oracle's {@code Vectors.y}. */
    static int y(GenRegion level, int k, int x, int z) {
        switch (k % 4) {
            case 0:
                return level.getHeight(HeightmapType.WORLD_SURFACE, x, z);
            case 1:
                return level.getHeight(HeightmapType.OCEAN_FLOOR, x, z);
            case 2:
                return level.getHeight(HeightmapType.MOTION_BLOCKING, x, z);
            default:
                for (int y = 40; y > -50; y--) {
                    if (BlockProperties.isAir(level.getBlockState(x, y, z)) && !BlockProperties.isAir(level.getBlockState(x, y - 1, z))) {
                        return y;
                    }
                }
                return -64;
        }
    }

    /** The events of one {@code FEATCASE}: every listed feature at 8 positions over the fresh terrain of the region. */
    static void features(FeatureLoader loader, List<String> featureIds, GenRegion level, long seed, int cx, int cz, List<String> out) {
        DecorationGenerator gen = new DecorationGenerator() {
            public int minY() {
                return -64;
            }

            public int genDepth() {
                return 384;
            }

            public int seaLevel() {
                return 63;
            }

            public boolean biomeHasFeature(String biome, PlacedFeature feature) {
                throw new IllegalStateException("no biome check in direct placement");
            }
        };
        for (int fi = 0; fi < featureIds.size(); fi++) {
            Feature f = loader.feature("minecraft:" + featureIds.get(fi));
            for (int k = 0; k < 8; k++) {
                int x = cx * 16 + ((k * 5 + fi) & 15);
                int z = cz * 16 + ((k * 11 + fi * 3) & 15);
                int y = y(level, k, x, z);
                out.add("G " + fi + " " + k + " " + x + " " + y + " " + z);
                WorldgenRandom random = new WorldgenRandom(new XoroshiroRandomSource(seed ^ (fi * 7919L + k)));
                try {
                    f.place(level, gen, random, new Pos(x, y, z));
                } catch (UnportedFeature.Reached e) {
                    out.add("R");
                }
                out.add("D " + random.getCount());
            }
        }
    }
}
