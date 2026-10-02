package com.example.entity;

import com.example.world.World;
import lombok.Getter;
import org.joml.Vector3f;

/**
 * The generic base every world-simulated thing shares: position, rotation, velocity, a
 * dimension-based bounding box, gravity/collision movement, and alive/dead state. {@code Player}
 * deliberately does NOT extend this (see the request's own "Player should remain a specialized
 * entity" rule) - its mouse-look/sprint/swim/fly/sneak/view-bob feature set is player-only and
 * would be dead weight on every mob. This class instead generalizes the same AABB-vs-voxel
 * collision/gravity shape {@link com.example.entity.ItemEntityManager} already proved out for
 * dropped items (substepped movement, axis-independent resolution, unloaded-chunk-acts-as-a-wall
 * safety) to an arbitrary width/height, so a mob gets the same "never falls through the world,
 * never tunnels through a wall" guarantee without a second physics implementation.
 *
 * <p>Position is the bottom-center of the bounding box (same convention as {@code Player.position}
 * and {@code ItemEntity.position}).
 */
public abstract class Entity {
    private static final float GRAVITY = 28f; // matches Player's own value
    private static final float TERMINAL_VELOCITY = -60f;
    private static final float MAX_SUBSTEP = 0.2f;
    private static final float VOID_Y = -16f;

    public final Vector3f position = new Vector3f();
    public final Vector3f velocity = new Vector3f();
    /** Radians, 0 = facing -Z (same convention as {@code Player.yaw}). */
    @Getter
    protected float yaw;
    @Getter
    private boolean onGround;
    @Getter
    private boolean alive = true;
    /** Horizontal knockback velocity in blocks per second, decaying with friction (see moveWithGravity). */
    protected float knockX, knockZ;
    private float fallDistance;

    protected Entity(Vector3f spawnPosition, float yaw) {
        this.position.set(spawnPosition);
        this.yaw = yaw;
    }

    public abstract float getWidth();

    public abstract float getHeight();

    protected void kill() {
        alive = false;
    }

    public Vector3f frontVector() {
        return new Vector3f((float) Math.sin(yaw), 0f, (float) -Math.cos(yaw));
    }

    /**
     * Applies gravity and moves by {@code (dx, dy, dz)} with full AABB-vs-voxel collision,
     * substepped so a fast-moving entity can't tunnel through a thin wall in one frame. Updates
     * {@link #onGround} and zeroes the velocity component on any axis a collision blocked.
     * {@code dt} only drives gravity integration here - horizontal movement this tick is
     * whatever the caller (a mob's navigation) already decided in {@code (dx, dz)}.
     */
    /** True when the last move was stopped by a wall on X or Z (spiders use it to start climbing). */
    protected boolean horizontalBlocked;

    protected void moveWithGravity(World world, float dx, float dz, float dt) {
        horizontalBlocked = false;
        dx += knockX * dt;
        dz += knockZ * dt;
        // Vanilla drag per tick: ground 0.6 * 0.91, air 0.91.
        float friction = (float) Math.pow(onGround ? 0.546 : 0.91, dt * 20.0);
        knockX *= friction;
        knockZ *= friction;
        if (Math.abs(knockX) < 0.02f) knockX = 0f;
        if (Math.abs(knockZ) < 0.02f) knockZ = 0f;
        float startY = position.y;
        velocity.y = Math.max(velocity.y - GRAVITY * dt, TERMINAL_VELOCITY);
        float dy = velocity.y * dt;

        float largest = Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz)));
        int steps = Math.max(1, (int) Math.ceil(largest / MAX_SUBSTEP));
        float sx = dx / steps, sy = dy / steps, sz = dz / steps;

        onGround = false;
        boolean yDone = false;
        for (int s = 0; s < steps; s++) {
            if (sx != 0f) {
                if (!intersectsSolid(world, position.x + sx, position.y, position.z)) position.x += sx;
                else { velocity.x = 0f; sx = 0f; horizontalBlocked = true; }
            }
            if (sz != 0f) {
                if (!intersectsSolid(world, position.x, position.y, position.z + sz)) position.z += sz;
                else { velocity.z = 0f; sz = 0f; horizontalBlocked = true; }
            }
            if (!yDone && sy != 0f) {
                if (!intersectsSolid(world, position.x, position.y + sy, position.z)) {
                    position.y += sy;
                } else {
                    if (sy < 0f) {
                        position.y = (float) Math.floor(position.y + sy) + 1f;
                        onGround = true;
                    }
                    velocity.y = 0f;
                    yDone = true;
                }
            }
        }
        if (position.y < VOID_Y) kill();

        if (world.getBlock((int) Math.floor(position.x), (int) Math.floor(position.y), (int) Math.floor(position.z))
                == com.example.world.BlockType.WATER) {
            fallDistance = 0f; // landing in water is safe
        } else if (position.y < startY) {
            fallDistance += startY - position.y;
        }
        if (onGround) {
            if (fallDistance > 0f) onLanded(fallDistance);
            fallDistance = 0f;
        }
    }

    /** Called once when this entity touches the ground after falling {@code distance} blocks. */
    protected void onLanded(float distance) {
    }

    /**
     * Vanilla LivingEntity.knockback: pushes away from {@code (fromX, fromZ)} with the given strength
     * (0.4 for an ordinary hit, +0.5 for a sprinting player attack); the vertical pop only happens
     * when grounded. Velocities convert between blocks/tick (formula) and blocks/second (here).
     */
    public void applyKnockback(float fromX, float fromZ, float strength) {
        float[] v = com.example.combat.CombatMath.knockback(knockX / 20f, velocity.y / 20f, knockZ / 20f, onGround,
                fromX - position.x, fromZ - position.z, strength);
        knockX = v[0] * 20f;
        knockZ = v[2] * 20f;
        if (onGround) {
            velocity.y = v[1] * 20f;
            onGround = false;
        }
    }

    /** Same "one block up" nudge real voxel-game mobs (and vanilla) get for free that this
     *  project's own {@code Player} does not implement - tried by {@code Mob}'s movement before
     *  falling back to a blocked move, so a 1-block curb or stair doesn't permanently wall off an
     *  otherwise-clear path (see the request's "basic one-block elevation changes" requirement).
     *  Returns true if stepping up by exactly 1 block clears the given horizontal move. */
    protected boolean canStepUp(World world, float dx, float dz) {
        return !intersectsSolid(world, position.x + dx, position.y + 1f, position.z + dz)
                && !intersectsSolid(world, position.x, position.y + 1f, position.z); // headroom right above the current spot too
    }

    protected void stepUp() {
        position.y += 1f;
        onGround = false;
    }

    public boolean intersectsSolid(World world, float x, float y, float z) {
        float half = getWidth() / 2f;
        int minX = (int) Math.floor(x - half), maxX = (int) Math.floor(x + half);
        int minY = (int) Math.floor(y), maxY = (int) Math.floor(y + getHeight() - 0.01f);
        int minZ = (int) Math.floor(z - half), maxZ = (int) Math.floor(z + half);
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    if (blocked(world, bx, by, bz)) return true;
                }
            }
        }
        return false;
    }

    /** Unloaded space counts as solid (same safety rule as {@code ItemEntityManager}) so an
     *  entity standing at the edge of loaded terrain never walks/falls into the void beyond it. */
    private static boolean blocked(World world, int bx, int by, int bz) {
        if (by < 0) return true;
        int cx = World.worldToChunk(bx), cz = World.worldToChunk(bz);
        var chunk = world.getChunk(cx, cz);
        if (chunk == null || !chunk.isGenerated()) return true;
        return world.isSolidCollision(bx, by, bz);
    }
}
