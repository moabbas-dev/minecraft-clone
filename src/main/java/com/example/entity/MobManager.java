package com.example.entity;

import com.example.world.BlockDrops;
import com.example.world.Chunk;
import com.example.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Owns every live mob: ticking (AI + physics), the death/removal lifecycle, distance despawn,
 * and the ray test the player's melee attack uses. Headless; rendering only reads
 * {@link #mobs()}. Mirrors {@link ItemEntityManager}'s conventions (single-threaded, a mob in an
 * unloaded chunk is frozen rather than allowed to fall into the void).
 *
 * <p>Lifecycle: a mob whose health reaches zero stops all AI/movement immediately (see
 * {@code Mob.tick}), its drops are rolled and spawned exactly once, and the corpse stays in the
 * list for {@link #CORPSE_SECONDS} before removal so death can be observed/processed.
 */
public class MobManager {
    public static final int MAX_MOBS = 32;
    public static final float CORPSE_SECONDS = 1.0f;
    public static final float DESPAWN_DISTANCE = 96f;

    private final List<Mob> mobs = new ArrayList<>();
    private final List<Mob> view = Collections.unmodifiableList(mobs);
    private final List<Mob> deadHandled = new ArrayList<>();
    private final java.util.Map<Mob, Float> corpseTimers = new java.util.IdentityHashMap<>();

    public List<Mob> mobs() {
        return view;
    }

    public int size() {
        return mobs.size();
    }

    public void clear() {
        mobs.clear();
        deadHandled.clear();
        corpseTimers.clear();
    }

    /** Adds a mob; false if the population cap is reached. */
    public boolean spawn(Mob mob) {
        if (mobs.size() >= MAX_MOBS) return false;
        mobs.add(mob);
        return true;
    }

    public int countAlive() {
        int n = 0;
        for (Mob m : mobs) if (m.isAlive()) n++;
        return n;
    }

    /**
     * @param candidates what mob AI may target this tick (today just the player) - the one place
     *                   that bounds the search set, so goals never scan the whole world.
     * @param playerPos  used only for distance despawn.
     */
    public void tick(World world, List<Targetable> candidates, Vector3f playerPos,
                     ItemEntityManager items, Random random, float dt) {
        for (int i = 0; i < mobs.size(); i++) {
            Mob m = mobs.get(i);
            if (!chunkLoaded(world, m.position.x, m.position.z)) continue; // frozen
            m.tick(world, candidates, dt);

            if (!m.isAlive() && !deadHandled.contains(m)) {
                deadHandled.add(m);
                corpseTimers.put(m, CORPSE_SECONDS);
                Vector3f at = new Vector3f(m.position.x, m.position.y + m.getHeight() * 0.5f, m.position.z);
                for (BlockDrops.Resolved d : m.getDrops(random)) {
                    items.spawn(d.item(), d.count(), at, random);
                }
            }
        }

        for (int i = mobs.size() - 1; i >= 0; i--) {
            Mob m = mobs.get(i);
            boolean remove = false;
            if (!m.isAlive()) {
                float left = corpseTimers.getOrDefault(m, 0f) - dt;
                corpseTimers.put(m, left);
                remove = left <= 0f;
            } else if (m.position.distance(playerPos) > DESPAWN_DISTANCE) {
                remove = true;
            }
            if (remove) {
                mobs.remove(i);
                deadHandled.remove(m);
                corpseTimers.remove(m);
            }
        }
    }

    /** Nearest living mob whose bounding box the ray enters within {@code maxDistance}, or null.
     *  Writes the entry distance into {@code outDistance[0]} when non-null so the caller can
     *  compare it with a block hit distance. */
    public Mob raycast(Vector3f origin, Vector3f dir, float maxDistance, float[] outDistance) {
        Vector3f d = new Vector3f(dir).normalize();
        Mob best = null;
        float bestT = maxDistance;
        for (Mob m : mobs) {
            if (!m.isAlive()) continue;
            float t = rayAabb(origin, d, m);
            if (t >= 0f && t <= bestT) {
                bestT = t;
                best = m;
            }
        }
        if (best != null && outDistance != null) outDistance[0] = bestT;
        return best;
    }

    private static float rayAabb(Vector3f o, Vector3f d, Mob m) {
        float hw = m.getWidth() / 2f;
        float[] min = {m.position.x - hw, m.position.y, m.position.z - hw};
        float[] max = {m.position.x + hw, m.position.y + m.getHeight(), m.position.z + hw};
        float[] origin = {o.x, o.y, o.z};
        float[] dir = {d.x, d.y, d.z};
        float tMin = 0f, tMax = Float.MAX_VALUE;
        for (int a = 0; a < 3; a++) {
            if (Math.abs(dir[a]) < 1e-8f) {
                if (origin[a] < min[a] || origin[a] > max[a]) return -1f;
            } else {
                float t1 = (min[a] - origin[a]) / dir[a], t2 = (max[a] - origin[a]) / dir[a];
                if (t1 > t2) { float tmp = t1; t1 = t2; t2 = tmp; }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) return -1f;
            }
        }
        return tMin;
    }

    private static boolean chunkLoaded(World world, float x, float z) {
        Chunk c = world.getChunk(World.worldToChunk((int) Math.floor(x)), World.worldToChunk((int) Math.floor(z)));
        return c != null && c.isGenerated();
    }
}
