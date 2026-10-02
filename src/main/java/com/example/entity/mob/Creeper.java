package com.example.entity.mob;

import com.example.entity.Mob;
import com.example.entity.ai.CreeperSwellGoal;
import com.example.entity.ai.FindTargetGoal;
import com.example.entity.ai.MoveToTargetGoal;
import com.example.entity.navigation.GroundNavigator;
import com.example.world.BlockDrops;
import com.example.world.BlockType;
import com.example.world.Difficulty;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;
import java.util.function.Supplier;

/** Creeper: 20 HP, 0.6 x 1.7, swells for 30 ticks within 3 blocks of its target, then blows up (power 3). */
public class Creeper extends Mob {
    public static final float WIDTH = 0.6f, HEIGHT = 1.7f, MAX_HEALTH = 20f, MOVE_SPEED = 2.5f;
    public static final float FUSE_TICKS = 30f, EXPLOSION_POWER = 3f, SEARCH_RADIUS = 16f;

    private final Supplier<Difficulty> difficulty;
    private float swell; // ticks, 0..FUSE_TICKS

    public Creeper(Vector3f spawnPosition, float yaw, Supplier<Difficulty> difficulty) {
        super(spawnPosition, yaw, MAX_HEALTH);
        this.difficulty = difficulty;
        setNavigator(new GroundNavigator());
        getGoals()
                .add(0, new FindTargetGoal(SEARCH_RADIUS))
                .add(2, new MoveToTargetGoal(CreeperSwellGoal.START_RANGE * 0.6f))
                .add(3, new CreeperSwellGoal(this)); // after movement so its "stand still" wins while swelling
    }

    public float getSwell() {
        return swell;
    }

    /** 0..1 fuse progress, used by the renderer for the swelling pulse and white flash. */
    public float getSwellFraction() {
        return swell / FUSE_TICKS;
    }

    public void addSwell(float ticks) {
        swell = Math.max(0f, Math.min(FUSE_TICKS, swell + ticks));
        if (swell >= FUSE_TICKS) requestExplosion(EXPLOSION_POWER);
    }

    @Override
    public Difficulty explosionDifficulty() {
        return difficulty.get();
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
        return "creeper";
    }

    @Override
    protected boolean makesAmbientSound() {
        return false; // creepers are silent until hurt
    }

    @Override
    public int xpReward(Random random) {
        return 5;
    }

    @Override
    public List<BlockDrops.Resolved> getDrops(Random random) {
        int n = random.nextInt(3); // 0-2 gunpowder
        return n == 0 ? List.of() : List.of(new BlockDrops.Resolved(BlockType.GUNPOWDER, n));
    }
}
