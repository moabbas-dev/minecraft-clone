package com.example.entity;

import com.example.entity.mob.Zombie;
import com.example.world.Chunk;
import com.example.world.Difficulty;
import com.example.world.World;
import org.joml.Vector3f;

import java.util.Random;
import java.util.function.Supplier;

/**
 * Controlled hostile spawning, deliberately far simpler than vanilla's full algorithm: every
 * {@link #ATTEMPT_INTERVAL_SECONDS} it makes one bounded attempt at a random column in an
 * annulus around the player, and spawns only if the spot passes the rules in
 * {@link #isValidSpawn} (the hook point where future rules - biome, mob category, spawn
 * weights - get added). Light is the darkness gate: combined sky+block light at the spot must be
 * at or below {@link #MAX_SPAWN_LIGHT}, which is vanilla's own hostile-spawn rule and naturally
 * means "night outdoors or inside caves/dark rooms" without hard-coding time of day.
 */
public class MobSpawner {
    public static final float ATTEMPT_INTERVAL_SECONDS = 2f;
    public static final float MIN_DISTANCE = 12f;
    public static final float MAX_DISTANCE = 32f;
    public static final int MAX_SPAWN_LIGHT = 7;
    public static final int MAX_ZOMBIES = 8;

    private float timer = ATTEMPT_INTERVAL_SECONDS;
    private boolean enabled = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** @param daylightFactor 0 (night) .. 1 (day) from the game's day/night cycle; skylight is
     *                        scaled by it so "dark" means the right thing at night outdoors. */
    public void tick(World world, MobManager mobs, Vector3f playerPos, float daylightFactor,
                     Supplier<Difficulty> difficulty, Random random, float dt) {
        if (!enabled) return;
        timer -= dt;
        if (timer > 0f) return;
        timer = ATTEMPT_INTERVAL_SECONDS;
        if (mobs.countAlive() >= MAX_ZOMBIES) return;

        double angle = random.nextDouble() * Math.PI * 2;
        float dist = MIN_DISTANCE + random.nextFloat() * (MAX_DISTANCE - MIN_DISTANCE);
        int x = (int) Math.floor(playerPos.x + Math.cos(angle) * dist);
        int z = (int) Math.floor(playerPos.z + Math.sin(angle) * dist);
        Integer y = findSpawnY(world, x, z, (int) Math.floor(playerPos.y));
        if (y == null) return;
        if (!isValidSpawn(world, x, y, z, playerPos, daylightFactor)) return;

        Vector3f at = new Vector3f(x + 0.5f, y, z + 0.5f);
        mobs.spawn(new Zombie(at, random.nextFloat() * (float) (Math.PI * 2), difficulty));
    }

    /** Standable Y near the player's own height (bounded vertical scan, not the whole column). */
    public static Integer findSpawnY(World world, int x, int z, int nearY) {
        int lo = Math.max(1, nearY - 8), hi = Math.min(Chunk.SIZE_Y - 3, nearY + 8);
        for (int y = hi; y >= lo; y--) {
            if (world.isSolidCollision(x, y - 1, z) && !world.isSolidCollision(x, y, z) && !world.isSolidCollision(x, y + 1, z)) {
                return y;
            }
        }
        return null;
    }

    public static boolean isValidSpawn(World world, int x, int y, int z, Vector3f playerPos, float daylightFactor) {
        Chunk c = world.getChunk(World.worldToChunk(x), World.worldToChunk(z));
        if (c == null || !c.isGenerated()) return false;
        // Not inside solid blocks / enough room for the 1.95-tall box.
        for (int dy = 0; dy < 2; dy++) {
            if (world.isSolidCollision(x, y + dy, z)) return false;
        }
        if (world.getBlock(x, y, z) == com.example.world.BlockType.WATER) return false;
        float dx = x + 0.5f - playerPos.x, dz = z + 0.5f - playerPos.z;
        if (dx * dx + dz * dz < MIN_DISTANCE * MIN_DISTANCE) return false; // never on top of the player
        int sky = Math.round(world.getSkyLight(x, y, z) * daylightFactor);
        int light = Math.max(sky, world.getBlockLight(x, y, z));
        return light <= MAX_SPAWN_LIGHT;
    }
}
