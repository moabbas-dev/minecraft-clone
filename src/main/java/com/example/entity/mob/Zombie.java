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

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/**
 * The first concrete mob: stats plus which reusable goals it runs. Health is a plain
 * {@code HealthComponent} owned by {@code LivingEntity} - there is no zombie-specific health or
 * damage code anywhere. Movement speed, attack range and cooldown are this project's own
 * adapted values (vanilla-consistent in feel, not extracted from game data).
 */
public class Zombie extends Mob {
    public static final float WIDTH = 0.6f;
    public static final float HEIGHT = 1.95f;
    public static final float MAX_HEALTH = 20f;
    public static final float MOVE_SPEED = 2.3f;
    public static final float ATTACK_RANGE = 1.7f;
    public static final float ATTACK_COOLDOWN_SECONDS = 1.0f;
    public static final float SEARCH_RADIUS = 20f;

    /** The clean difficulty lookup the request asks for - Easy 2.5, Normal 3.0, Hard 4.0. */
    public static float attackDamageFor(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 2.5f;
            case NORMAL -> 3.0f;
            case HARD -> 4.0f;
        };
    }

    public Zombie(Vector3f spawnPosition, float yaw, Supplier<Difficulty> difficulty) {
        super(spawnPosition, yaw, MAX_HEALTH);
        setNavigator(new GroundNavigator());
        getGoals()
                .add(0, new FindTargetGoal(SEARCH_RADIUS))
                .add(1, new MeleeAttackGoal(ATTACK_RANGE, ATTACK_COOLDOWN_SECONDS,
                        () -> attackDamageFor(difficulty.get())))
                .add(2, new MoveToTargetGoal(ATTACK_RANGE * 0.9f));
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
    public List<BlockDrops.Resolved> getDrops(Random random) {
        int count = random.nextInt(3); // 0-2, like vanilla
        return count == 0 ? List.of() : List.of(new BlockDrops.Resolved(BlockType.ROTTEN_FLESH, count));
    }
}
