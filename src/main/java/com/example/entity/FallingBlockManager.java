package com.example.entity;

import com.example.world.BlockType;
import com.example.world.Chunk;
import com.example.world.World;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Gravity blocks (research: docs/research/blocks-and-drops.md): sand, red sand and gravel with nothing solid
 * beneath them turn into a falling entity (gravity 0.04 b/tick^2 with 0.98 drag, i.e. 16 b/s^2), and become a block
 * again where they land - or drop as an item if the landing cell is taken. Falling sand does no impact damage.
 */
public class FallingBlockManager {
    public static final Set<BlockType> GRAVITY_BLOCKS = EnumSet.of(BlockType.SAND, BlockType.RED_SAND, BlockType.GRAVEL);
    public static final int MAX_FALLING = 128;
    private static final float GRAVITY = 16f;

    public static final class Falling {
        public final BlockType type;
        /** Bottom-center of the block. */
        public final Vector3f position = new Vector3f();
        public float velocityY;

        Falling(BlockType type, float x, float y, float z) {
            this.type = type;
            position.set(x, y, z);
        }
    }

    private final List<Falling> falling = new ArrayList<>();

    public List<Falling> entities() {
        return falling;
    }

    public void clear() {
        falling.clear();
    }

    public static boolean isGravityBlock(BlockType t) {
        return GRAVITY_BLOCKS.contains(t);
    }

    private static boolean canFallThrough(World world, int x, int y, int z) {
        return y > Chunk.MIN_Y && !world.isSolidCollision(x, y, z);
    }

    /** Starts every gravity block in the column from (x, y) upward that has lost its support. */
    public void checkColumn(World world, int x, int y, int z) {
        for (int yy = y; yy < y + 16; yy++) {
            BlockType t = world.getBlock(x, yy, z);
            if (!isGravityBlock(t)) return;
            if (!canFallThrough(world, x, yy - 1, z)) return;
            if (falling.size() >= MAX_FALLING) return;
            world.setBlock(x, yy, z, BlockType.AIR);
            falling.add(new Falling(t, x + 0.5f, yy, z + 0.5f));
        }
    }

    public void update(World world, float dt, ItemEntityManager items, Random random) {
        for (int i = falling.size() - 1; i >= 0; i--) {
            Falling f = falling.get(i);
            f.velocityY = (f.velocityY - GRAVITY * dt) * (float) Math.pow(0.98, dt * 20.0);
            float ny = f.position.y + f.velocityY * dt;
            int bx = (int) Math.floor(f.position.x), bz = (int) Math.floor(f.position.z);
            if (!canFallThrough(world, bx, (int) Math.floor(ny), bz)) {
                land(world, f, bx, (int) Math.floor(ny) + 1, bz, items, random);
                falling.remove(i);
            } else {
                f.position.y = ny;
            }
        }
    }

    private void land(World world, Falling f, int x, int y, int z, ItemEntityManager items, Random random) {
        if (world.getBlock(x, y, z) == BlockType.AIR || !world.isSolidCollision(x, y, z)) {
            world.setBlock(x, y, z, f.type);
        } else {
            items.spawn(f.type, 1, new Vector3f(x + 0.5f, y + 0.5f, z + 0.5f), random);
        }
    }

    /** Lands every in-flight block at once (before a save, so nothing in the air is lost). */
    public void settleAll(World world, ItemEntityManager items, Random random) {
        for (Falling f : new ArrayList<>(falling)) {
            int x = (int) Math.floor(f.position.x), z = (int) Math.floor(f.position.z);
            int y = (int) Math.floor(f.position.y);
            while (canFallThrough(world, x, y - 1, z)) y--;
            land(world, f, x, y, z, items, random);
        }
        falling.clear();
    }
}
