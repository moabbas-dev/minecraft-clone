package com.example.entity.mob;

import com.example.world.BlockDrops;
import com.example.world.BlockType;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

/** Cow: 10 HP, 0.9 x 1.4, panics at x1.25, drops 1-3 raw beef and 0-2 leather. */
public class Cow extends PassiveMob {
    public static final float WIDTH = 0.9f, HEIGHT = 1.4f, MAX_HEALTH = 10f, MOVE_SPEED = 2.0f;

    public Cow(Vector3f spawnPosition, float yaw) {
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
    public float followParentSpeed() {
        return 1.25f;
    }

    @Override
    public String typeId() {
        return "cow";
    }

    @Override
    public List<BlockDrops.Resolved> getDrops(Random random) {
        List<BlockDrops.Resolved> out = newDrops();
        if (isBaby()) return out; // baby animals drop nothing
        roll(out, random, BlockType.RAW_BEEF, 1, 3);
        roll(out, random, BlockType.LEATHER, 0, 2);
        return out;
    }
}
