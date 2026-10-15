package com.example.entity;

import com.example.entity.mob.Chicken;
import com.example.entity.mob.Cow;
import com.example.entity.mob.Pig;
import com.example.world.BlockType;
import com.example.world.Chunk;
import com.example.world.World;
import org.joml.Vector3f;

import java.util.Random;

/**
 * Passive animal spawning (research: docs/research/passive-mobs.md): packs of one species placed when a
 * chunk is first generated (about 1 chunk in 10, 1-4 animals, no light/grass requirement), plus a slow
 * ongoing trickle near the player on grass with light level 9 or more, capped at 10 animals within
 * {@link #CAP_RADIUS} blocks. Species weights follow the grassy-biome table: pig 5, cow 4, chicken 5.
 */
public class PassiveSpawner {
    public static final float ATTEMPT_INTERVAL_SECONDS = 20f;
    public static final float MIN_DISTANCE = 24f, MAX_DISTANCE = 64f;
    public static final int MIN_LIGHT = 9;
    public static final int CAP = 10;
    public static final float CAP_RADIUS = 128f;
    public static final int PACK_SIZE = 4;

    private static final int[] WEIGHTS = {5, 4, 5}; // pig, cow, chicken
    private float timer = ATTEMPT_INTERVAL_SECONDS;
    private boolean enabled = true;

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public static int passiveCountNear(MobManager mobs, Vector3f center) {
        int n = 0;
        for (Mob m : mobs.mobs()) {
            if (m.isAlive() && m.isPersistent() && m.position.distanceSquared(center) <= CAP_RADIUS * CAP_RADIUS) n++;
        }
        return n;
    }

    /** The occasional natural pack near the player. */
    public void tick(World world, MobManager mobs, Vector3f playerPos, Random random, float dt) {
        if (!enabled) return;
        timer -= dt;
        if (timer > 0f) return;
        timer = ATTEMPT_INTERVAL_SECONDS;
        if (passiveCountNear(mobs, playerPos) >= CAP) return;

        double angle = random.nextDouble() * Math.PI * 2;
        float dist = MIN_DISTANCE + random.nextFloat() * (MAX_DISTANCE - MIN_DISTANCE);
        int bx = (int) Math.floor(playerPos.x + Math.cos(angle) * dist);
        int bz = (int) Math.floor(playerPos.z + Math.sin(angle) * dist);
        spawnPack(world, mobs, bx, bz, 1 + random.nextInt(PACK_SIZE), random, true);
    }

    /** Called once for a freshly generated (never saved) chunk: about 1 in 10 get a pack of 1-4. */
    public static int spawnForNewChunk(World world, MobManager mobs, Chunk chunk, Random random) {
        if (random.nextInt(10) != 0) return 0;
        int bx = chunk.worldOriginX() + random.nextInt(Chunk.SIZE_X);
        int bz = chunk.worldOriginZ() + random.nextInt(Chunk.SIZE_Z);
        return spawnPack(world, mobs, bx, bz, 1 + random.nextInt(PACK_SIZE), random, false);
    }

    /** Places up to {@code size} animals of one weighted-random species around (bx, bz); returns how many. */
    public static int spawnPack(World world, MobManager mobs, int bx, int bz, int size, Random random,
                                boolean requireGrassAndLight) {
        int species = pickSpecies(random);
        int placed = 0;
        for (int i = 0; i < size; i++) {
            int x = bx + random.nextInt(9) - 4, z = bz + random.nextInt(9) - 4;
            Chunk c = world.getChunk(World.worldToChunk(x), World.worldToChunk(z));
            if (c == null || !c.isGenerated()) continue;
            int top = world.getColumnHeightAt(x, z);
            Integer y = MobSpawner.findSpawnY(world, x, z, top, 6);
            if (y == null) continue;
            BlockType ground = world.getBlock(x, y - 1, z);
            if (ground.isFluid() || ground.name().endsWith("LEAVES")) continue;
            if (requireGrassAndLight) {
                if (ground != BlockType.GRASS_BLOCK) continue;
                // Animal.isBrightEnoughToSpawn: getRawBrightness(pos, 0) > 8 - the sky light is not dimmed for it
                if (world.getRawBrightness(x, y, z, 0) < MIN_LIGHT) continue;
            }
            Vector3f at = new Vector3f(x + 0.5f, y, z + 0.5f);
            float yaw = random.nextFloat() * (float) (Math.PI * 2);
            Mob mob = switch (species) {
                case 0 -> new Pig(at, yaw);
                case 1 -> new Cow(at, yaw);
                default -> new Chicken(at, yaw);
            };
            // Vanilla: the first member of a group is always an adult, every later one has a 5% chance of being a baby.
            if (placed > 0 && random.nextFloat() <= com.example.entity.mob.PassiveMob.BABY_SPAWN_CHANCE
                    && mob instanceof com.example.entity.mob.PassiveMob animal) {
                animal.makeBaby();
            }
            if (mobs.spawn(mob)) placed++;
        }
        return placed;
    }

    static int pickSpecies(Random random) {
        int total = 0;
        for (int w : WEIGHTS) total += w;
        int roll = random.nextInt(total);
        for (int i = 0; i < WEIGHTS.length; i++) {
            if (roll < WEIGHTS[i]) return i;
            roll -= WEIGHTS[i];
        }
        return 0;
    }
}
