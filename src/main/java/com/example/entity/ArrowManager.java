package com.example.entity;

import com.example.survival.DamageSource;
import com.example.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Arrows in flight and stuck in blocks (research: docs/research/hostile-mob-models.md): gravity 0.05 blocks/tick^2
 * (20 b/s^2), drag 0.99 per tick, they lodge in the first solid block and despawn after 1200 ticks (60 s), and a hit
 * on a target deals the arrow's damage through the normal damage pipeline.
 */
public class ArrowManager {
    public static final float GRAVITY = 20f;
    public static final float STUCK_LIFETIME_SECONDS = 60f;
    public static final float FLIGHT_LIFETIME_SECONDS = 20f;
    public static final int MAX_ARROWS = 64;

    public static final class Arrow {
        public final Vector3f position = new Vector3f();
        public final Vector3f velocity = new Vector3f();
        public final Vector3f origin = new Vector3f();
        /** Direction the shaft points (last flight direction once stuck). */
        public final Vector3f facing = new Vector3f();
        public final float damage;
        /** Shot by the player: it hurts mobs (not the player), its damage comes from its speed at impact, and it can be picked up again. */
        public final boolean playerShot;
        public final boolean critical;
        public boolean stuck;
        public float age;

        Arrow(Vector3f pos, Vector3f vel, float damage, boolean playerShot, boolean critical) {
            position.set(pos);
            origin.set(pos);
            velocity.set(vel);
            facing.set(vel);
            this.damage = damage;
            this.playerShot = playerShot;
            this.critical = critical;
        }
    }

    private final List<Arrow> arrows = new ArrayList<>();

    public List<Arrow> arrows() {
        return arrows;
    }

    public void clear() {
        arrows.clear();
    }

    public void fire(Vector3f origin, Vector3f velocity, float damage) {
        if (arrows.size() >= MAX_ARROWS) arrows.remove(0);
        arrows.add(new Arrow(origin, velocity, damage, false, false));
    }

    /** An arrow loosed by the player's bow ({@code velocity} in blocks per second). */
    public void firePlayer(Vector3f origin, Vector3f velocity, boolean critical) {
        if (arrows.size() >= MAX_ARROWS) arrows.remove(0);
        arrows.add(new Arrow(origin, velocity, 0f, true, critical));
    }

    /**
     * Picks up the player's own arrows that have lodged in a block and are within a block of {@code at}
     * (AbstractArrow.playerTouch); returns how many were taken.
     */
    public int pickUp(Vector3f at) {
        int taken = 0;
        for (int i = arrows.size() - 1; i >= 0; i--) {
            Arrow a = arrows.get(i);
            if (a.playerShot && a.stuck && a.age > 0.25f && a.position.distanceSquared(at) < 1.5f * 1.5f) {
                arrows.remove(i);
                taken++;
            }
        }
        return taken;
    }

    /** @param sound receives "arrow.hit" at the impact point. */
    public void update(World world, List<Targetable> targets, float dt, Consumer<Vector3f> hitSound) {
        update(world, targets, List.of(), dt, hitSound, new java.util.Random());
    }

    /** @param playerArrowTargets what the player's arrows can hit (the mobs); {@code targets} is what the others can hit (the player). */
    public void update(World world, List<Targetable> targets, List<? extends Targetable> playerArrowTargets, float dt,
                       Consumer<Vector3f> hitSound, java.util.Random random) {
        for (int i = arrows.size() - 1; i >= 0; i--) {
            Arrow a = arrows.get(i);
            a.age += dt;
            if (a.stuck) {
                if (a.age > STUCK_LIFETIME_SECONDS) arrows.remove(i);
                continue;
            }
            if (a.age > FLIGHT_LIFETIME_SECONDS) {
                arrows.remove(i);
                continue;
            }
            a.velocity.y -= GRAVITY * dt;
            a.velocity.mul((float) Math.pow(0.99, dt * 20.0));
            if (a.velocity.lengthSquared() > 1e-4f) a.facing.set(a.velocity);
            Vector3f next = new Vector3f(a.position).fma(dt, a.velocity);
            // The step is swept in quarter-block pieces so a fast arrow neither flies through a wall nor through a mob.
            int steps = Math.max(1, (int) Math.ceil(a.position.distance(next) / 0.25f));
            Vector3f previous = new Vector3f(a.position), q = new Vector3f();
            boolean ended = false;
            for (int s = 1; s <= steps && !ended; s++) {
                q.set(a.position).lerp(next, s / (float) steps);
                if (world.isSolidCollision((int) Math.floor(q.x), (int) Math.floor(q.y), (int) Math.floor(q.z))) {
                    a.position.set(previous);
                    a.stuck = true;
                    a.age = 0f;
                    a.velocity.zero();
                    hitSound.accept(a.position);
                    ended = true;
                    break;
                }
                for (Targetable t : a.playerShot ? playerArrowTargets : targets) {
                    if (!t.isAlive()) continue;
                    Vector3f p = t.getPosition();
                    if (Math.abs(q.x - p.x) < 0.45f && Math.abs(q.z - p.z) < 0.45f && Math.abs(q.y - p.y) < 0.95f) {
                        float damage = a.playerShot
                                ? com.example.combat.BowMath.hitDamage(a.velocity.length() / 20f, a.critical, random) : a.damage;
                        t.hurtBy(damage, a.playerShot ? DamageSource.PLAYER : DamageSource.ARROW, a.origin, 0.4f);
                        hitSound.accept(q);
                        arrows.remove(i);
                        ended = true;
                        break;
                    }
                }
                previous.set(q);
            }
            if (!ended) a.position.set(next);
        }
    }
}
