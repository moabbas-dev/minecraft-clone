package com.example.entity.ai;

import com.example.entity.MobSpawner;
import com.example.world.BlockType;
import com.example.world.World;
import org.joml.Vector3f;

import java.util.Random;

/** Picks walkable ground points near a mob for wandering and fleeing (water-avoiding like vanilla's stroll). */
final class StrollTargets {
    private StrollTargets() {
    }

    /** A random standable, non-water point within {@code horizontal} blocks, or null if the roll found none. */
    static Vector3f pick(World world, Vector3f from, Random random, int horizontal, int vertical) {
        int x = (int) Math.floor(from.x) + random.nextInt(horizontal * 2 + 1) - horizontal;
        int z = (int) Math.floor(from.z) + random.nextInt(horizontal * 2 + 1) - horizontal;
        Integer y = MobSpawner.findSpawnY(world, x, z, (int) Math.floor(from.y), vertical);
        if (y == null) return null;
        if (world.getBlock(x, y - 1, z) == BlockType.WATER || world.getBlock(x, y, z) == BlockType.WATER) return null;
        return new Vector3f(x + 0.5f, y, z + 0.5f);
    }

    /** A point roughly {@code distance} blocks away in a random direction (panic flee target). */
    static Vector3f fleePoint(World world, Vector3f from, Random random, float distance) {
        for (int attempt = 0; attempt < 6; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            int x = (int) Math.floor(from.x + Math.cos(angle) * distance);
            int z = (int) Math.floor(from.z + Math.sin(angle) * distance);
            Integer y = MobSpawner.findSpawnY(world, x, z, (int) Math.floor(from.y), 4);
            if (y != null && world.getBlock(x, y - 1, z) != BlockType.WATER) return new Vector3f(x + 0.5f, y, z + 0.5f);
        }
        return null;
    }
}
