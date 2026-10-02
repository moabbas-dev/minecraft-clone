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
        public boolean stuck;
        public float age;

        Arrow(Vector3f pos, Vector3f vel, float damage) {
            position.set(pos);
            origin.set(pos);
            velocity.set(vel);
            facing.set(vel);
            this.damage = damage;
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
        arrows.add(new Arrow(origin, velocity, damage));
    }

    /** @param sound receives "arrow.hit" at the impact point. */
    public void update(World world, List<Targetable> targets, float dt, Consumer<Vector3f> hitSound) {
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
            if (world.isSolidCollision((int) Math.floor(next.x), (int) Math.floor(next.y), (int) Math.floor(next.z))) {
                a.stuck = true;
                a.age = 0f;
                a.velocity.zero();
                hitSound.accept(a.position);
                continue;
            }
            boolean hit = false;
            for (Targetable t : targets) {
                if (!t.isAlive()) continue;
                Vector3f p = t.getPosition();
                if (Math.abs(next.x - p.x) < 0.45f && Math.abs(next.z - p.z) < 0.45f && Math.abs(next.y - p.y) < 0.95f) {
                    t.hurtBy(a.damage, DamageSource.ARROW, a.origin, 0.4f);
                    hitSound.accept(next);
                    hit = true;
                    break;
                }
            }
            if (hit) {
                arrows.remove(i);
            } else {
                a.position.set(next);
            }
        }
    }
}
