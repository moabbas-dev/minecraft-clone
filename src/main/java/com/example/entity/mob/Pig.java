package com.example.entity.mob;

import com.example.world.BlockDrops;
import com.example.world.BlockType;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

/** Pig: 10 HP, 0.9 x 0.9, panics at x1.25, drops 1-3 raw porkchop. */
public class Pig extends PassiveMob {
    public static final float WIDTH = 0.9f, HEIGHT = 0.9f, MAX_HEALTH = 10f, MOVE_SPEED = 2.5f;

    public Pig(Vector3f spawnPosition, float yaw) {
        super(spawnPosition, yaw, MAX_HEALTH, 1.25f);
    }

    @Override
    public float getWidth() {
        return WIDTH * babyScale();
    }

    @Override
    public float getHeight() {
        return HEIGHT * babyScale();
    }

    @Override
    public float getMoveSpeed() {
        return MOVE_SPEED;
    }

    @Override
    public String typeId() {
        return "pig";
    }

    @Override
    public List<BlockDrops.Resolved> getDrops(Random random) {
        List<BlockDrops.Resolved> out = newDrops();
        if (isBaby()) return out; // baby animals drop nothing
        roll(out, random, BlockType.RAW_PORKCHOP, 1, 3);
        return out;
    }
}
