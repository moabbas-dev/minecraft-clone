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
    public static final int MAX_MOBS = 160;
    private XpOrbManager xpOrbs;
    private java.util.function.Consumer<Mob.ProjectileRequest> projectileSink = r -> {
    };

    /** Where mob-launched arrows go (the game wires this to its ArrowManager). */
    public void setProjectileSink(java.util.function.Consumer<Mob.ProjectileRequest> sink) {
        this.projectileSink = sink == null ? r -> {
        } : sink;
    }

    /** When set, a player-killed mob drops real XP orbs instead of queuing XP for direct pickup. */
    public void setXpOrbs(XpOrbManager orbs) {
        this.xpOrbs = orbs;
    }
    private boolean raining;

    /** Fed by the game each frame so zombies know whether the sun reaches them. */
    public void setRaining(boolean raining) {
        this.raining = raining;
    }

    /** Peaceful removes every hostile mob at once, like vanilla. */
    public void removeHostile() {
        mobs.removeIf(Mob::isHostile);
    }

    private int lightAt(World world, Mob m) {
        int x = (int) Math.floor(m.position.x), y = (int) Math.floor(m.position.y + m.getEyeHeight()), z = (int) Math.floor(m.position.z);
        return world.getRawBrightness(x, y, z);
    }

    private boolean sunReaches(World world, Mob m) {
        if (!world.monstersBurn() || raining || m.isInWater(world)) return false;
        int x = (int) Math.floor(m.position.x), z = (int) Math.floor(m.position.z);
        int headY = (int) Math.floor(m.position.y + m.getHeight() - 0.1f);
        return world.getSkyLight(x, headY, z) >= 15;
    }
    private java.util.function.Consumer<Mob.SoundEvent> soundSink = e -> {
    };

    /** Where mob sound events go each tick (the game wires this to its audio engine). */
    public void setSoundSink(java.util.function.Consumer<Mob.SoundEvent> sink) {
        this.soundSink = sink == null ? e -> {
        } : sink;
    }
    /** Mobs farther than this from the player are not simulated (they simply wait). */
    public static final float SIMULATION_DISTANCE = 64f;
    public static final float CORPSE_SECONDS = 1.0f;
    public static final float DESPAWN_DISTANCE = 128f; // vanilla: beyond 128 blocks a mob despawns at once

    private final List<Mob> mobs = new ArrayList<>();
    private final List<Mob> view = Collections.unmodifiableList(mobs);
    private final List<Mob> deadHandled = new ArrayList<>();
    private final java.util.Map<Mob, Float> corpseTimers = new java.util.IdentityHashMap<>();
    private int pendingExperience;

    /** XP earned by player kills since the last call (the caller adds it to the player). */
    public int drainExperience() {
        int xp = pendingExperience;
        pendingExperience = 0;
        return xp;
    }

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

    public int countAliveHostile() {
        int n = 0;
        for (Mob m : mobs) if (m.isAlive() && !m.isPersistent()) n++;
        return n;
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
            if (m.isAlive() && m.position.distanceSquared(playerPos) > SIMULATION_DISTANCE * SIMULATION_DISTANCE) continue;
            m.setSunlit(m.isAlive() && sunReaches(world, m));
            m.setLightLevel(lightAt(world, m));
            m.setNeighbors(mobs);
            m.tick(world, candidates, dt);
            if (m.isAlive() && m instanceof com.example.entity.mob.PassiveMob parent && parent.takeBirth()) giveBirth(parent, random);
            com.example.world.BlockType laid = m.isAlive() ? m.takeLaidItem() : null;
            if (laid != null) {
                items.spawn(laid, 1, new Vector3f(m.position.x, m.position.y + 0.3f, m.position.z), random);
            }
            for (Mob.ProjectileRequest r : m.drainProjectiles()) projectileSink.accept(r);
            float blast = m.takeExplosion();
            if (blast > 0f) {
                List<Targetable> victims = new ArrayList<>(candidates);
                for (Mob other : mobs) if (other != m && other.isAlive()) victims.add(other);
                Vector3f at = new Vector3f(m.position.x, m.position.y + m.getHeight() * 0.5f, m.position.z);
                Explosions.detonate(world, at, blast, victims, items, random, m.explosionDifficulty());
                soundSink.accept(new Mob.SoundEvent("explode", at.x, at.y, at.z));
            }
            for (Mob.SoundEvent e : m.drainSounds()) soundSink.accept(e);

            if (!m.isAlive() && !deadHandled.contains(m)) {
                deadHandled.add(m);
                if (m.isSilentRemoval()) {
                    corpseTimers.put(m, 0f); // exploded: no loot, no XP, no corpse
                    continue;
                }
                corpseTimers.put(m, CORPSE_SECONDS);
                Vector3f at = new Vector3f(m.position.x, m.position.y + m.getHeight() * 0.5f, m.position.z);
                if (m.getLastDamageSource() == com.example.survival.DamageSource.PLAYER) {
                    if (xpOrbs != null) xpOrbs.spawn(m.xpReward(random), at, random);
                    else pendingExperience += m.xpReward(random);
                }
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
            } else if (!m.isPersistent()) {
                float dist = m.position.distance(playerPos);
                if (dist > DESPAWN_DISTANCE) {
                    remove = true;
                } else if (dist > 32f) {
                    // Vanilla: after 30 s without a player within 32 blocks, 1/800 per tick to despawn.
                    m.farFromPlayerSeconds += dt;
                    float perFrame = 1f - (float) Math.pow(1f - 1f / 800f, dt * 20f);
                    remove = m.farFromPlayerSeconds > 30f && random.nextFloat() < perFrame;
                } else {
                    m.farFromPlayerSeconds = 0f;
                }
            }
            if (remove) {
                mobs.remove(i);
                deadHandled.remove(m);
                corpseTimers.remove(m);
            }
        }
    }

    /** BreedGoal.breed: a baby of the parents' kind is born where they stand and 1-7 experience drops. */
    private void giveBirth(com.example.entity.mob.PassiveMob parent, Random random) {
        Vector3f at = new Vector3f(parent.position);
        float yaw = parent.getBodyYaw();
        com.example.entity.mob.PassiveMob baby = switch (parent.typeId()) {
            case "pig" -> new com.example.entity.mob.Pig(at, yaw);
            case "cow" -> new com.example.entity.mob.Cow(at, yaw);
            case "chicken" -> new com.example.entity.mob.Chicken(at, yaw);
            default -> null;
        };
        if (baby != null) {
            baby.makeBaby();
            spawn(baby);
        }
        int xp = 1 + random.nextInt(7);
        if (xpOrbs != null) xpOrbs.spawn(xp, new Vector3f(at.x, at.y + 0.5f, at.z), random);
        else pendingExperience += xp;
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

    /** Mobs the player's sword sweep also hits: alive, not the primary target, hitbox touching the primary's
     *  box grown by 1 block horizontally / 0.25 vertically, feet within 3 blocks of the attacker's feet. */
    public List<Mob> sweepTargets(Mob primary, Vector3f attackerFeet) {
        List<Mob> out = new ArrayList<>();
        float phw = primary.getWidth() / 2f + 1f;
        for (Mob m : mobs) {
            if (m == primary || !m.isAlive()) continue;
            float mhw = m.getWidth() / 2f;
            boolean overlapX = Math.abs(m.position.x - primary.position.x) <= phw + mhw;
            boolean overlapZ = Math.abs(m.position.z - primary.position.z) <= phw + mhw;
            boolean overlapY = m.position.y <= primary.position.y + primary.getHeight() + 0.25f
                    && m.position.y + m.getHeight() >= primary.position.y - 0.25f;
            if (overlapX && overlapZ && overlapY && Math.abs(m.position.y - attackerFeet.y) <= 3f) out.add(m);
        }
        return out;
    }

    /** Distance along a ray to an axis-aligned box, or -1 on a miss (slab method). */
    public static float rayBoxDistance(Vector3f o, Vector3f d, float[] min, float[] max) {
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

    private static float rayAabb(Vector3f o, Vector3f d, Mob m) {
        float hw = m.getWidth() / 2f;
        float[] min = {m.position.x - hw, m.position.y, m.position.z - hw};
        float[] max = {m.position.x + hw, m.position.y + m.getHeight(), m.position.z + hw};
        return rayBoxDistance(o, d, min, max);
    }

    private static boolean chunkLoaded(World world, float x, float z) {
        Chunk c = world.getChunk(World.worldToChunk((int) Math.floor(x)), World.worldToChunk((int) Math.floor(z)));
        return c != null && c.isGenerated();
    }
}
