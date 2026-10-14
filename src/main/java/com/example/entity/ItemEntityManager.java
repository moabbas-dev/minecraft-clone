package com.example.entity;

import com.example.inventory.Inventory;
import com.example.world.BlockType;
import com.example.world.Chunk;
import com.example.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Owns every dropped item: spawning (with merging), physics, lifetime, and pickup. Headless -
 * rendering only reads {@link #entities()}. Single-threaded like the rest of the game loop.
 *
 * Chunk safety: an item whose chunk isn't loaded is frozen (no physics, no aging) rather than
 * dropped or allowed to fall through the world, and unloaded space is treated as solid so an
 * item moving toward an unloaded neighbor stops at the boundary instead of falling into it.
 * Items are therefore never silently deleted because a chunk was temporarily unavailable; they
 * only leave the world by being picked up, timing out, or hitting the entity cap.
 */
public class ItemEntityManager {
    /** Hard cap on live items. Overflow evicts the OLDEST item - a deliberate, documented loss
     *  to bound per-frame cost (same policy as vanilla's item despawning), not a silent bug. */
    public static final int MAX_ENTITIES = 256;
    public static final float LIFETIME_SECONDS = 300f;
    public static final float PICKUP_DELAY_SECONDS = 0.5f;
    /** Horizontal pickup distance in blocks, measured from the player's feet position. */
    public static final float PICKUP_RADIUS = 1.25f;
    /** Player vertical window for pickup: from a little below the feet to head height. */
    static final float PICKUP_Y_BELOW = 0.6f;
    static final float PICKUP_Y_ABOVE = 1.9f;

    static final float HALF_WIDTH = 0.125f;
    static final float HEIGHT = 0.25f;
    static final float GRAVITY = 20f;
    static final float TERMINAL_VELOCITY = 40f;
    /** Items nearer than this (and same type) merge into one entity on spawn. */
    static final float MERGE_RADIUS = 0.75f;
    static final float MAX_SUBSTEP = 0.2f;
    static final float VOID_Y = -16f;

    private final List<ItemEntity> entities = new ArrayList<>();
    private final List<ItemEntity> view = Collections.unmodifiableList(entities);
    private final int maxStack;

    public ItemEntityManager(int maxStack) {
        this.maxStack = maxStack;
    }

    public List<ItemEntity> entities() {
        return view;
    }

    public int size() {
        return entities.size();
    }

    public void clear() {
        entities.clear();
    }

    /**
     * Drops {@code count} of {@code type} at {@code center} (a block center) with a small random
     * horizontal spread and upward pop. Merges into a nearby same-type stack when there's room.
     */
    public void spawn(BlockType type, int count, Vector3f center, Random random) {
        spawn(type, count, 0, center, random);
    }

    /** Same, for an item carrying {@code damage} spent durability (damageable items never merge). */
    public void spawn(BlockType type, int count, int damage, Vector3f center, Random random) {
        if (type == null || type == BlockType.AIR || count <= 0) return;
        int remaining = count;
        int stackMax = com.example.inventory.ItemStack.maxStackFor(type, maxStack);

        for (ItemEntity e : entities) {
            if (remaining <= 0) break;
            if (!e.alive || e.getType() != type || e.getDamage() != damage || e.getCount() >= stackMax) continue;
            float dx = e.position.x - center.x, dz = e.position.z - center.z, dy = e.position.y - center.y;
            if (dx * dx + dz * dz > MERGE_RADIUS * MERGE_RADIUS || Math.abs(dy) > 1f) continue;
            int take = Math.min(remaining, stackMax - e.getCount());
            e.addCount(take);
            remaining -= take;
        }

        while (remaining > 0) {
            int stack = Math.min(remaining, stackMax);
            remaining -= stack;
            ItemEntity e = new ItemEntity(type, stack, PICKUP_DELAY_SECONDS, damage);
            e.position.set(center.x, center.y - HEIGHT / 2f, center.z);
            e.velocity.set((random.nextFloat() - 0.5f) * 2.4f, 2.5f + random.nextFloat(), (random.nextFloat() - 0.5f) * 2.4f);
            if (entities.size() >= MAX_ENTITIES) entities.remove(0);
            entities.add(e);
        }
    }

    /** Seconds before a player-thrown item can be picked up again (vanilla: 40 ticks). */
    public static final float THROWN_PICKUP_DELAY_SECONDS = 2f;

    /** Vanilla Q-toss velocity in blocks/second: along the view direction at 0.3 b/tick, +0.1 up, with a little scatter. */
    public static Vector3f throwVelocity(Vector3f front, Random random) {
        float k = random.nextFloat() * (float) (Math.PI * 2), l = random.nextFloat() * 0.02f;
        return new Vector3f(front.x * 0.3f + l * (float) Math.cos(k), front.y * 0.3f + 0.1f + 0.1f * (random.nextFloat() - random.nextFloat()),
                front.z * 0.3f + l * (float) Math.sin(k)).mul(20f);
    }

    /** Throws a stack from {@code from} (eye height) along {@code front}; it cannot be picked up for 2 seconds. */
    public void throwItem(BlockType type, int count, int damage, Vector3f from, Vector3f front, Random random) {
        if (type == null || type == BlockType.AIR || count <= 0) return;
        ItemEntity e = new ItemEntity(type, count, THROWN_PICKUP_DELAY_SECONDS, damage);
        e.position.set(from.x, from.y - HEIGHT / 2f, from.z);
        e.velocity.set(throwVelocity(front, random));
        if (entities.size() >= MAX_ENTITIES) entities.remove(0);
        entities.add(e);
    }

    public void update(World world, float dt) {
        for (int i = 0; i < entities.size(); i++) {
            ItemEntity e = entities.get(i);
            if (!e.alive) continue;
            if (!chunkLoaded(world, e.position.x, e.position.z)) continue; // frozen, see class docs

            e.age += dt;
            if (e.age >= LIFETIME_SECONDS) {
                e.kill();
                continue;
            }
            stepPhysics(e, world, dt);
            if (e.position.y < VOID_Y) e.kill();
        }
        entities.removeIf(e -> !e.alive);
    }

    /**
     * Moves as much of each eligible nearby stack as fits into {@code inventory}. Returns the
     * number of items that transferred at least one item (so the caller can play ONE pickup
     * sound per update, not one per frame per item). Partially-fitting stacks shrink and stay in
     * the world; a full inventory leaves everything untouched. No line-of-sight check - like
     * vanilla, an item behind a thin wall can still be collected.
     */
    /** One stack (or part of one) that just flew into the player: where it lay and how old it was, for the pickup animation. */
    public record Collected(BlockType type, int count, int damage, float x, float y, float z, float age) {
    }

    private final java.util.List<Collected> collected = new java.util.ArrayList<>();

    /** The items collected since the last call (oldest first); the list is cleared. */
    public java.util.List<Collected> drainCollected() {
        if (collected.isEmpty()) return java.util.List.of();
        java.util.List<Collected> out = new java.util.ArrayList<>(collected);
        collected.clear();
        return out;
    }

    public int collect(Vector3f playerFeet, Inventory inventory) {
        int transfers = 0;
        for (int i = 0; i < entities.size(); i++) {
            ItemEntity e = entities.get(i);
            if (!e.canBePickedUp()) continue;
            float dx = e.position.x - playerFeet.x, dz = e.position.z - playerFeet.z;
            float dy = e.position.y - playerFeet.y;
            if (dx * dx + dz * dz > PICKUP_RADIUS * PICKUP_RADIUS) continue;
            if (dy < -PICKUP_Y_BELOW || dy > PICKUP_Y_ABOVE) continue;

            int added = inventory.add(e.getType(), e.getCount(), e.getDamage());
            if (added > 0) {
                collected.add(new Collected(e.getType(), added, e.getDamage(), e.position.x, e.position.y, e.position.z, e.getAge()));
                e.removeCount(added);
                transfers++;
            }
        }
        entities.removeIf(e -> !e.alive);
        return transfers;
    }

    // ---- physics ----

    private static boolean chunkLoaded(World world, float x, float z) {
        Chunk c = world.getChunk(World.worldToChunk((int) Math.floor(x)), World.worldToChunk((int) Math.floor(z)));
        return c != null && c.isGenerated();
    }

    private static boolean blocked(World world, int bx, int by, int bz) {
        if (by < 0) return true;
        if (!chunkLoaded(world, bx, bz)) return true; // unloaded space acts as a wall/floor
        return world.isSolidCollision(bx, by, bz);
    }

    static boolean collides(World world, float x, float y, float z) {
        int minX = (int) Math.floor(x - HALF_WIDTH), maxX = (int) Math.floor(x + HALF_WIDTH);
        int minY = (int) Math.floor(y), maxY = (int) Math.floor(y + HEIGHT - 0.001f);
        int minZ = (int) Math.floor(z - HALF_WIDTH), maxZ = (int) Math.floor(z + HALF_WIDTH);
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    if (blocked(world, bx, by, bz)) return true;
                }
            }
        }
        return false;
    }

    private static void stepPhysics(ItemEntity e, World world, float dt) {
        Vector3f p = e.position, v = e.velocity;

        // A block placed on top of an item (or a spawn inside terrain) must not leave it
        // permanently embedded: nudge it upward until it's free.
        for (int i = 0; i < 16 && collides(world, p.x, p.y, p.z); i++) p.y += 0.25f;

        v.y = Math.max(v.y - GRAVITY * dt, -TERMINAL_VELOCITY);

        float dx = v.x * dt, dy = v.y * dt, dz = v.z * dt;
        float largest = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        int steps = Math.max(1, (int) Math.ceil(largest / MAX_SUBSTEP));
        float sx = dx / steps, sy = dy / steps, sz = dz / steps;

        e.onGround = false;
        boolean yDone = false;
        for (int s = 0; s < steps; s++) {
            if (sx != 0f) {
                if (!collides(world, p.x + sx, p.y, p.z)) p.x += sx;
                else { v.x = 0f; sx = 0f; }
            }
            if (sz != 0f) {
                if (!collides(world, p.x, p.y, p.z + sz)) p.z += sz;
                else { v.z = 0f; sz = 0f; }
            }
            if (!yDone && sy != 0f) {
                if (!collides(world, p.x, p.y + sy, p.z)) {
                    p.y += sy;
                } else {
                    if (sy < 0f) {
                        // Landed: rest exactly on top of the block whose layer we just entered.
                        p.y = (float) Math.floor(p.y + sy) + 1f;
                        e.onGround = true;
                    }
                    v.y = 0f;
                    yDone = true;
                }
            }
        }

        if (e.onGround) {
            float friction = Math.max(0f, 1f - 10f * dt);
            v.x *= friction;
            v.z *= friction;
            if (Math.abs(v.x) < 0.02f) v.x = 0f;
            if (Math.abs(v.z) < 0.02f) v.z = 0f;
        }
    }
}
