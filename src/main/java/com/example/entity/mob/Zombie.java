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

    /** Vanilla melee damage: Easy 2.5, Normal 3.0, Hard 4.5. */
    public static float attackDamageFor(Difficulty difficulty) {
        return switch (difficulty) {
            case EASY -> 2.5f;
            case NORMAL -> 3.0f;
            case HARD -> 4.5f;
            case PEACEFUL -> 0f;
        };
    }

    private final boolean baby;

    public Zombie(Vector3f spawnPosition, float yaw, Supplier<Difficulty> difficulty) {
        this(spawnPosition, yaw, difficulty, false);
    }

    /** {@code baby}: vanilla's 5% variant - half size, +50% speed, 12 XP, still burns in sunlight. */
    public Zombie(Vector3f spawnPosition, float yaw, Supplier<Difficulty> difficulty, boolean baby) {
        super(spawnPosition, yaw, MAX_HEALTH);
        this.baby = baby;
        setNavigator(new GroundNavigator());
        getGoals()
                .add(0, new FindTargetGoal(SEARCH_RADIUS))
                .add(1, new MeleeAttackGoal(ATTACK_RANGE, ATTACK_COOLDOWN_SECONDS,
                        () -> attackDamageFor(difficulty.get())))
                .add(2, new MoveToTargetGoal(ATTACK_RANGE * 0.9f));
    }

    @Override
    public float getWidth() {
        return baby ? WIDTH * 0.5f : WIDTH;
    }

    @Override
    public boolean isBaby() {
        return baby;
    }

    @Override
    public boolean isHostile() {
        return true;
    }

    @Override
    protected void onTick(com.example.world.World world, float dt) {
        if (isSunlit()) setOnFire(8f); // vanilla: 160 ticks of fire, refreshed while exposed
    }

    @Override
    public float getHeight() {
        return baby ? HEIGHT * 0.5f : HEIGHT;
    }

    @Override
    protected float armorPoints() {
        return 2f; // vanilla zombies spawn with 2 natural armor points
    }

    @Override
    public int xpReward(Random random) {
        return baby ? 12 : 5;
    }

    @Override
    public String typeId() {
        return "zombie";
    }

    @Override
    public float getMoveSpeed() {
        return baby ? MOVE_SPEED * 1.5f : MOVE_SPEED;
    }

    @Override
    public List<BlockDrops.Resolved> getDrops(Random random) {
        java.util.List<BlockDrops.Resolved> out = new java.util.ArrayList<>(2);
        int count = random.nextInt(3); // 0-2, like vanilla
        if (count > 0) out.add(new BlockDrops.Resolved(BlockType.ROTTEN_FLESH, count));
        // Vanilla: a 2.5% chance of one iron ingot / carrot / potato; there is no carrot item here, so it is iron or potato.
        if (random.nextFloat() < 0.025f) {
            out.add(new BlockDrops.Resolved(random.nextBoolean() ? BlockType.IRON_INGOT : BlockType.POTATO, 1));
        }
        return out;
    }
}
