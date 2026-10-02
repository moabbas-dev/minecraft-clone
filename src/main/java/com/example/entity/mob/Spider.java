package com.example.entity.mob;

import com.example.entity.Mob;
import com.example.entity.ai.FindTargetGoal;
import com.example.entity.ai.MeleeAttackGoal;
import com.example.entity.ai.MoveToTargetGoal;
import com.example.entity.navigation.GroundNavigator;
import com.example.world.BlockDrops;
import com.example.world.BlockType;
import com.example.world.Difficulty;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * Spider: 16 HP, 1.4 x 0.9. Hostile at light level 11 or below and neutral at 12 or above unless it was just hurt;
 * climbs the wall it walks into. Melee 2 HP (3 on Hard). The leap attack is not implemented.
 */
public class Spider extends Mob {
    public static final float WIDTH = 1.4f, HEIGHT = 0.9f, MAX_HEALTH = 16f, MOVE_SPEED = 3.0f, SEARCH_RADIUS = 16f;
    public static final int NEUTRAL_LIGHT = 12;
    private static final float CLIMB_SPEED = 4f;

    public static float attackDamageFor(Difficulty d) {
        return switch (d) {
            case PEACEFUL -> 0f;
            case EASY, NORMAL -> 2f;
            case HARD -> 3f;
        };
    }

    public Spider(Vector3f spawnPosition, float yaw, Supplier<Difficulty> difficulty) {
        super(spawnPosition, yaw, MAX_HEALTH);
        setNavigator(new GroundNavigator());
        getGoals()
                .add(0, new FindTargetGoal(SEARCH_RADIUS, m -> m.getLightLevel() < NEUTRAL_LIGHT || m.getRecentHurtTimer() > 0f))
                .add(1, new MeleeAttackGoal(1.9f, 1.0f, () -> attackDamageFor(difficulty.get())))
                .add(2, new MoveToTargetGoal(1.6f));
    }

    @Override
    protected void onTick(com.example.world.World world, float dt) {
        if (horizontalBlocked && hasTarget()) velocity.y = CLIMB_SPEED; // walks up the wall it is pushing into
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
        return "spider";
    }

    @Override
    public int xpReward(Random random) {
        return 5;
    }

    @Override
    public List<BlockDrops.Resolved> getDrops(Random random) {
        List<BlockDrops.Resolved> out = new ArrayList<>(2);
        int string = random.nextInt(3);
        if (string > 0) out.add(new BlockDrops.Resolved(BlockType.STRING, string));
        if (random.nextInt(3) == 0) out.add(new BlockDrops.Resolved(BlockType.SPIDER_EYE, 1));
        return out;
    }
}
