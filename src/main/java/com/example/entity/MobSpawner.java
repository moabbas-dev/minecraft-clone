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
 * weights - get added). Light is the darkness gate, vanilla's {@code Monster.isDarkEnoughToSpawn} with the overworld
 * numbers: the raw sky light must not beat a random 0-31, the block light must be 0, and the brightness (block light or
 * the sky light dimmed by the sky darkness, 10 in a thunderstorm) must not exceed a random 0-7. That naturally means
 * "night outdoors, storms, or inside caves/dark rooms" without hard-coding the time of day.
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

    public void tick(World world, MobManager mobs, Vector3f playerPos, boolean thundering,
                     Supplier<Difficulty> difficulty, Random random, float dt) {
        if (!enabled || difficulty.get() == Difficulty.PEACEFUL) return;
        timer -= dt;
        if (timer > 0f) return;
        timer = ATTEMPT_INTERVAL_SECONDS;
        if (mobs.countAliveHostile() >= MAX_ZOMBIES) return;

        double angle = random.nextDouble() * Math.PI * 2;
        float dist = MIN_DISTANCE + random.nextFloat() * (MAX_DISTANCE - MIN_DISTANCE);
        int x = (int) Math.floor(playerPos.x + Math.cos(angle) * dist);
        int z = (int) Math.floor(playerPos.z + Math.sin(angle) * dist);
        Integer y = findSpawnY(world, x, z, (int) Math.floor(playerPos.y));
        if (y == null) return;
        if (!isValidSpawn(world, x, y, z, playerPos, thundering, random)) return;

        Vector3f at = new Vector3f(x + 0.5f, y, z + 0.5f);
        float yaw = random.nextFloat() * (float) (Math.PI * 2);
        // Vanilla overworld monster weights: spider, zombie, skeleton and creeper are all about equally likely.
        switch (random.nextInt(4)) {
            case 0 -> mobs.spawn(new com.example.entity.mob.Skeleton(at, yaw, difficulty));
            case 1 -> mobs.spawn(new com.example.entity.mob.Creeper(at, yaw, difficulty));
            case 2 -> mobs.spawn(new com.example.entity.mob.Spider(at, yaw, difficulty));
            default -> mobs.spawn(new Zombie(at, yaw, difficulty, random.nextFloat() < 0.05f)); // 5% of zombies are babies
        }
    }

    /** Standable Y near the player's own height (bounded vertical scan, not the whole column). */
    public static Integer findSpawnY(World world, int x, int z, int nearY) {
        return findSpawnY(world, x, z, nearY, 8);
    }

    /** Same, scanning {@code range} blocks above and below {@code nearY}. */
    public static Integer findSpawnY(World world, int x, int z, int nearY, int range) {
        int lo = Math.max(1, nearY - range), hi = Math.min(Chunk.SIZE_Y - 3, nearY + range);
        for (int y = hi; y >= lo; y--) {
            if (world.isSolidCollision(x, y - 1, z) && !world.isSolidCollision(x, y, z) && !world.isSolidCollision(x, y + 1, z)) {
                return y;
            }
        }
        return null;
    }

    public static boolean isValidSpawn(World world, int x, int y, int z, Vector3f playerPos, boolean thundering, Random random) {
        Chunk c = world.getChunk(World.worldToChunk(x), World.worldToChunk(z));
        if (c == null || !c.isGenerated()) return false;
        // Not inside solid blocks / enough room for the 1.95-tall box.
        for (int dy = 0; dy < 2; dy++) {
            if (world.isSolidCollision(x, y + dy, z)) return false;
        }
        if (world.getBlock(x, y, z).isFluid()) return false;
        float dx = x + 0.5f - playerPos.x, dz = z + 0.5f - playerPos.z;
        if (dx * dx + dz * dz < MIN_DISTANCE * MIN_DISTANCE) return false; // never on top of the player
        return isDarkEnoughToSpawn(world, x, y, z, thundering, random);
    }

    /** Monster.isDarkEnoughToSpawn with the overworld's monster_spawn_light_level (uniform 0-7) and block light limit 0. */
    public static boolean isDarkEnoughToSpawn(World world, int x, int y, int z, boolean thundering, Random random) {
        if (world.getSkyLight(x, y, z) > random.nextInt(32)) return false;
        if (world.getBlockLight(x, y, z) > 0) return false;
        int brightness = thundering ? world.getRawBrightness(x, y, z, 10) : world.getRawBrightness(x, y, z);
        return brightness <= random.nextInt(MAX_SPAWN_LIGHT + 1);
    }
}
