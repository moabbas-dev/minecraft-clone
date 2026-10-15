package com.example.cooking;

import com.example.inventory.ItemStack;
import com.example.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sparse per-world registry of every placed cooking station, ticked once per simulated tick from
 * {@code Game}'s fixed-step accumulator - the same "a plain map, iterated only over real
 * instances, never every block in every chunk" idiom {@code SaplingGrowth}/{@code ItemEntityManager}
 * already use in this project. A station's chunk not being currently loaded simply skips its
 * tick that pass (see {@link #tickAll}) - cheap, and correctly means an unloaded furnace doesn't
 * silently keep cooking (or burn through fuel) while nobody's around, matching real vanilla and
 * satisfying the "don't process unloaded stations" performance requirement without needing any
 * chunk-load/unload registration bookkeeping (unlike SaplingGrowth, nothing here needs pausing or
 * re-scanning a chunk's blocks - a station's identity is its own object, not encoded in a block
 * id, so it's always either present in this map or it was never placed / already broken).
 */
public class CookingStationManager {
    private final Map<Long, CookingStationState> stations = new HashMap<>();
    /** Block light level of a burning furnace, blast furnace or smoker (Blocks.litBlockEmission(13)). */
    public static final int LIT_EMISSION = 13;

    private static long packKey(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) | (((long) z & 0x3FFFFFFL) << 26) | (((long) y & 0xFFFL) << 52);
    }

    /** Creates a fresh, empty station at (x, y, z) - called right after a real block placement.
     *  Overwrites (without leaking anything - callers place only onto freshly-placed blocks). */
    public CookingStationState create(int x, int y, int z, CookingStationType type) {
        CookingStationState state = new CookingStationState(x, y, z, type);
        stations.put(packKey(x, y, z), state);
        return state;
    }

    /** Null if there's no station at this position. */
    public CookingStationState get(int x, int y, int z) {
        return stations.get(packKey(x, y, z));
    }

    /** Removes and returns the station at this position (null if there wasn't one) - called
     *  right after the block itself is broken, so the caller can drop its contents. */
    public CookingStationState remove(int x, int y, int z) {
        return stations.remove(packKey(x, y, z));
    }

    /**
     * Advances every station by exactly one simulated tick, skipping any whose chunk isn't
     * currently loaded and generated (see the class docs). Never called more than once per real
     * simulated tick (the fixed-step accumulator in Game guarantees that), so this is exactly
     * how many ticks pass, independent of render FPS.
     */
    public void tickAll(World world) {
        if (stations.isEmpty()) return;
        for (CookingStationState station : stations.values()) {
            int cx = World.worldToChunk(station.getX()), cz = World.worldToChunk(station.getZ());
            var chunk = world.getChunk(cx, cz);
            if (chunk != null && chunk.isGenerated()) {
                station.tick();
                // a burning furnace, blast furnace or smoker gives off block light 13 (vanilla's lit block emission)
                world.setLightEmissionOverride(station.getX(), station.getY(), station.getZ(), station.isBurning() ? LIT_EMISSION : 0);
            }
        }
    }

    public int count() {
        return stations.size();
    }

    /** Plain data snapshot of one station, for {@code WorldIO} to persist - see that class' own
     *  docs on why it doesn't depend on this package directly. */
    public record Snapshot(int x, int y, int z, String stationTypeName, ItemStack input, ItemStack fuel,
                            ItemStack output, int burnTimeRemainingTicks, int totalBurnTimeTicks,
                            int cookProgressTicks, float storedExperience) {
    }

    public List<Snapshot> snapshotAll() {
        List<Snapshot> list = new ArrayList<>(stations.size());
        for (CookingStationState s : stations.values()) {
            list.add(new Snapshot(s.getX(), s.getY(), s.getZ(), s.getType().name(), s.getInputStack(), s.getFuelStack(),
                    s.getOutputStack(), s.getBurnTimeRemainingTicks(), s.getTotalBurnTimeTicks(),
                    s.getCookProgressTicks(), s.getStoredExperience()));
        }
        return list;
    }

    /** Replaces every station this manager knows about with exactly what {@code snapshots}
     *  describes - called once, right after loading a world. An unknown/corrupt station-type
     *  name is skipped rather than thrown (a hand-edited or future-incompatible save must never
     *  crash the load, matching this project's existing save-tolerance convention). */
    public void restoreAll(List<Snapshot> snapshots) {
        stations.clear();
        for (Snapshot snap : snapshots) {
            CookingStationType type;
            try {
                type = CookingStationType.valueOf(snap.stationTypeName());
            } catch (IllegalArgumentException e) {
                continue;
            }
            CookingStationState state = new CookingStationState(snap.x(), snap.y(), snap.z(), type);
            state.setInputStack(snap.input());
            state.setFuelStack(snap.fuel());
            state.restoreOutputStack(snap.output());
            state.restoreProgress(snap.burnTimeRemainingTicks(), snap.totalBurnTimeTicks(),
                    snap.cookProgressTicks(), snap.storedExperience());
            stations.put(packKey(snap.x(), snap.y(), snap.z()), state);
        }
    }
}
