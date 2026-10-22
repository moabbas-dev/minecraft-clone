package com.example.worldgen.terrain;

import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Port of {@code net.minecraft.world.level.levelgen.Heightmap} (26.3) for a {@link TerrainChunk}. Each column stores
 * {@code firstAvailable - minY} (vanilla packs it in a {@code SimpleBitStorage} of {@code ceillog2(height + 1)} bits;
 * a plain int per column holds the same values).
 *
 * <p>Predicates ({@code Heightmap.Types}, 26.3): {@code WORLD_SURFACE_WG}, {@code WORLD_SURFACE}: {@code !isAir()};
 * {@code OCEAN_FLOOR_WG}, {@code OCEAN_FLOOR}: {@code is(#blocks_motion_in_heightmap)};
 * {@code MOTION_BLOCKING}: {@code is(#blocks_motion_in_heightmap) || !getFluidState().isEmpty()};
 * {@code MOTION_BLOCKING_NO_LEAVES}: {@code is(#blocks_motion_in_heightmap_no_leaves) || !getFluidState().isEmpty()}.
 * In 26.3 these are block tags (data), not the old {@code blocksMotion()} material test.
 */
public final class Heightmap {

    private final TerrainChunk chunk;
    private final HeightmapType type;
    private final int[] data = new int[256];

    Heightmap(TerrainChunk chunk, HeightmapType type) {
        this.chunk = chunk;
        this.type = type;
    }

    public HeightmapType type() {
        return type;
    }

    /** {@code Heightmap.Types.isOpaque().test(state)}. */
    public static boolean isOpaque(HeightmapType type, StateRef state, BlockTags tags) {
        return switch (type) {
            case WORLD_SURFACE_WG, WORLD_SURFACE -> !BlockProperties.isAir(state);
            case OCEAN_FLOOR_WG, OCEAN_FLOOR -> {
                BlockProperties.entry(state.id());
                yield tags.is(state, BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP);
            }
            case MOTION_BLOCKING -> tags.is(state, BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP) || BlockProperties.hasFluid(state);
            case MOTION_BLOCKING_NO_LEAVES -> tags.is(state, BlockTags.BLOCKS_MOTION_IN_HEIGHTMAP_NO_LEAVES) || BlockProperties.hasFluid(state);
        };
    }

    /**
     * {@code primeHeightmaps(chunk, types)}: per column (x outer, z inner) scans down from
     * {@code getHighestSectionPosition() + 16 - 1} to {@code minY}; blocks other than {@code minecraft:air} are tested
     * against each remaining heightmap.
     */
    static void primeHeightmaps(TerrainChunk chunk, Set<HeightmapType> types) {
        if (types.isEmpty()) return;
        List<Heightmap> all = new ArrayList<>(types.size());
        for (HeightmapType t : HeightmapType.values()) {
            if (types.contains(t)) all.add(chunk.getOrCreateHeightmapUnprimed(t));
        }
        List<Heightmap> remaining = new ArrayList<>(all.size());
        int highestSectionPosition = chunk.getHighestSectionPosition() + 16;
        for (int x = 0; x < 16; ++x) {
            for (int z = 0; z < 16; ++z) {
                remaining.clear();
                remaining.addAll(all);
                for (int y = highestSectionPosition - 1; y >= chunk.minY(); --y) {
                    int p = chunk.paletteIndexLocal(x, y, z);
                    if (!chunk.paletteState(p).is("minecraft:air")) {
                        for (int i = 0; i < remaining.size(); ++i) {
                            Heightmap h = remaining.get(i);
                            if (chunk.opaque(h.type, p)) {
                                h.setHeight(x, z, y + 1);
                                remaining.remove(i);
                                --i;
                            }
                        }
                        if (remaining.isEmpty()) break;
                    }
                }
            }
        }
    }

    /** {@code update(localX, y, localZ, state)}: returns whether the column changed. */
    public boolean update(int localX, int y, int localZ, StateRef state) {
        return update(localX, y, localZ, chunk.opaque(type, chunk.paletteIndexOf(state)));
    }

    boolean update(int localX, int y, int localZ, boolean opaque) {
        int firstAvailable = getFirstAvailable(localX, localZ);
        if (y <= firstAvailable - 2) return false;
        if (opaque) {
            if (y >= firstAvailable) {
                setHeight(localX, localZ, y + 1);
                return true;
            }
        } else if (firstAvailable - 1 == y) {
            for (int yy = y - 1; yy >= chunk.minY(); --yy) {
                if (chunk.opaque(type, chunk.paletteIndexLocal(localX, yy, localZ))) {
                    setHeight(localX, localZ, yy + 1);
                    return true;
                }
            }
            setHeight(localX, localZ, chunk.minY());
            return true;
        }
        return false;
    }

    public int getFirstAvailable(int x, int z) {
        return data[x + z * 16] + chunk.minY();
    }

    public int getHighestTaken(int x, int z) {
        return getFirstAvailable(x, z) - 1;
    }

    private void setHeight(int x, int z, int height) {
        data[x + z * 16] = height - chunk.minY();
    }
}
