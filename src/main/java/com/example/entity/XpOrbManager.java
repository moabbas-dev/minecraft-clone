package com.example.entity;

import com.example.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.DoubleConsumer;

/**
 * Experience orbs (research: docs/research/survival-mechanics.md). A reward is split greedily into vanilla's orb
 * sizes (2477, 1237, 617, 307, 149, 73, 37, 17, 7, 3, 1); orbs fall, are drawn toward a player within 7.25
 * blocks, are collected on contact and vanish after 5 minutes. Pure simulation - the game draws them.
 */
public class XpOrbManager {
    public static final int[] ORB_SIZES = {2477, 1237, 617, 307, 149, 73, 37, 17, 7, 3, 1};
    public static final float ATTRACT_RADIUS = 7.25f;
    public static final float COLLECT_RADIUS = 1.0f;
    public static final float LIFETIME_SECONDS = 300f;
    public static final int MAX_ORBS = 300;
    private static final float GRAVITY = 20f;

    public static final class Orb {
        public final Vector3f position = new Vector3f();
        public final Vector3f velocity = new Vector3f();
        public final int value;
        public float age;

        Orb(int value) {
            this.value = value;
        }

        /** Index into {@link #ORB_SIZES}-style icon sizes: 0 (biggest) .. 10 (1 XP). */
        public int sizeIndex() {
            for (int i = 0; i < ORB_SIZES.length; i++) if (value >= ORB_SIZES[i]) return i;
            return ORB_SIZES.length - 1;
        }
    }

    private final List<Orb> orbs = new ArrayList<>();

    public List<Orb> orbs() {
        return orbs;
    }

    public void clear() {
        orbs.clear();
    }

    /** Splits {@code total} into vanilla orb values, largest first. */
    public static List<Integer> split(int total) {
        List<Integer> out = new ArrayList<>();
        int left = Math.max(0, total);
        for (int size : ORB_SIZES) {
            while (left >= size) {
                out.add(size);
                left -= size;
            }
        }
        return out;
    }

    public void spawn(float xp, Vector3f at, Random random) {
        for (int value : split(Math.round(xp))) {
            if (orbs.size() >= MAX_ORBS) orbs.remove(0);
            Orb o = new Orb(value);
            o.position.set(at);
            o.velocity.set((random.nextFloat() - 0.5f) * 3f, 2f + random.nextFloat() * 2f, (random.nextFloat() - 0.5f) * 3f);
            orbs.add(o);
        }
    }

    /** Moves orbs, pulls them toward the player and reports collected XP through {@code onCollect}. */
    public void update(World world, Vector3f player, float dt, DoubleConsumer onCollect) {
        for (int i = orbs.size() - 1; i >= 0; i--) {
            Orb o = orbs.get(i);
            o.age += dt;
            if (o.age >= LIFETIME_SECONDS) {
                orbs.remove(i);
                continue;
            }
            float dx = player.x - o.position.x, dy = player.y + 0.9f - o.position.y, dz = player.z - o.position.z;
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < COLLECT_RADIUS) {
                onCollect.accept(o.value);
                orbs.remove(i);
                continue;
            }
            if (dist < ATTRACT_RADIUS) {
                // Vanilla: velocity += direction * (1 - dist / 8)^2 * 0.1 per tick.
                float pull = (1f - dist / 8f);
                pull = pull * pull * 0.1f * 20f * 20f * dt; // blocks/tick^2 -> blocks/s per frame
                o.velocity.x += dx / dist * pull;
                o.velocity.y += dy / dist * pull;
                o.velocity.z += dz / dist * pull;
            } else {
                o.velocity.y -= GRAVITY * dt;
            }
            float drag = (float) Math.pow(0.98, dt * 20.0);
            o.velocity.mul(drag);
            float nx = o.position.x + o.velocity.x * dt, ny = o.position.y + o.velocity.y * dt, nz = o.position.z + o.velocity.z * dt;
            if (world.isSolidCollision((int) Math.floor(o.position.x), (int) Math.floor(ny), (int) Math.floor(o.position.z))) {
                o.velocity.y = 0f;
                ny = o.position.y;
            }
            if (world.isSolidCollision((int) Math.floor(nx), (int) Math.floor(o.position.y), (int) Math.floor(o.position.z))) {
                o.velocity.x = 0f;
                nx = o.position.x;
            }
            if (world.isSolidCollision((int) Math.floor(o.position.x), (int) Math.floor(o.position.y), (int) Math.floor(nz))) {
                o.velocity.z = 0f;
                nz = o.position.z;
            }
            o.position.set(nx, ny, nz);
        }
    }
}
