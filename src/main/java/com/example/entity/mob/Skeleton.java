package com.example.entity.mob;

import com.example.entity.Mob;
import com.example.entity.ai.FindTargetGoal;
import com.example.entity.ai.RangedAttackGoal;
import com.example.entity.navigation.GroundNavigator;
import com.example.world.BlockDrops;
import com.example.world.BlockType;
import com.example.world.Difficulty;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/** Skeleton: 20 HP, 0.6 x 1.99, a bow archer that burns in daylight and drops bones and arrows. */
public class Skeleton extends Mob {
    public static final float WIDTH = 0.6f, HEIGHT = 1.99f, MAX_HEALTH = 20f, MOVE_SPEED = 2.5f, SEARCH_RADIUS = 16f;

    public Skeleton(Vector3f spawnPosition, float yaw, Supplier<Difficulty> difficulty) {
        super(spawnPosition, yaw, MAX_HEALTH);
        setNavigator(new GroundNavigator());
        getGoals()
                .add(0, new FindTargetGoal(SEARCH_RADIUS))
                .add(1, new RangedAttackGoal(difficulty));
    }

    @Override
    protected void onTick(com.example.world.World world, float dt) {
        if (isSunlit()) setOnFire(8f);
    }

    @Override
    public float getWidth() {
        return WIDTH;
    }

    @Override
    public float getHeight() {
        return HEIGHT;
    }

    @Override
    public float getMoveSpeed() {
        return MOVE_SPEED;
    }

    @Override
    public boolean isHostile() {
        return true;
    }

    @Override
    public String typeId() {
        return "skeleton";
    }

    @Override
    public int xpReward(Random random) {
        return 5;
    }

    @Override
    public List<BlockDrops.Resolved> getDrops(Random random) {
        List<BlockDrops.Resolved> out = new ArrayList<>(2);
        int bones = random.nextInt(3), arrows = random.nextInt(3);
        if (bones > 0) out.add(new BlockDrops.Resolved(BlockType.BONE, bones));
        if (arrows > 0) out.add(new BlockDrops.Resolved(BlockType.ARROW, arrows));
        return out;
    }
}
