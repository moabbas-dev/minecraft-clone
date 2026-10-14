package com.example.entity.mob;

import com.example.entity.Mob;
import com.example.entity.ai.FloatGoal;
import com.example.entity.ai.LookAtPlayerGoal;
import com.example.entity.ai.PanicGoal;
import com.example.entity.ai.RandomLookAroundGoal;
import com.example.entity.ai.RandomStrollGoal;
import com.example.entity.navigation.GroundNavigator;
import com.example.world.BlockDrops;
import com.example.world.BlockType;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Shared shape of the farm animals (research: docs/research/passive-mobs.md): float, panic, stroll,
 * look at player, random look - the vanilla goal list with its priorities - persist forever, 1-3 XP.
 * Subclasses add the stats, panic speed and drops.
 */
public abstract class PassiveMob extends Mob {
    /** Vanilla's baby age: -24000 ticks, i.e. 20 minutes until it is an adult. */
    public static final float BABY_SECONDS = 1200f;
    /** Chance that a later member of a naturally spawned group is a baby (AgeableMob's default 0.05). */
    public static final float BABY_SPAWN_CHANCE = 0.05f;

    private float babySecondsLeft;

    protected PassiveMob(Vector3f spawnPosition, float yaw, float maxHealth, float panicMultiplier) {
        super(spawnPosition, yaw, maxHealth);
        setNavigator(new GroundNavigator());
        getGoals()
                .add(0, new FloatGoal())
                .add(1, new PanicGoal(panicMultiplier))
                .add(5, new RandomStrollGoal())
                .add(6, new LookAtPlayerGoal())
                .add(7, new RandomLookAroundGoal());
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    /** Makes this animal a baby that grows up after {@link #BABY_SECONDS}. */
    public void makeBaby() {
        babySecondsLeft = BABY_SECONDS;
    }

    @Override
    public boolean isBaby() {
        return babySecondsLeft > 0f;
    }

    /** 1 for an adult, 0.5 for a baby: babies have half the adult's hitbox in every direction. */
    protected float babyScale() {
        return isBaby() ? 0.5f : 1f;
    }

    @Override
    protected void onTick(com.example.world.World world, float dt) {
        if (babySecondsLeft > 0f) {
            babySecondsLeft = Math.max(0f, babySecondsLeft - dt);
            if (babySecondsLeft == 0f) grewUp();
        }
    }

    /** Called once when a baby becomes an adult. */
    protected void grewUp() {
    }

    /** Babies give no experience. */
    @Override
    public int xpReward(Random random) {
        return isBaby() ? 0 : 1 + random.nextInt(3);
    }

    /** A baby is saved as minus its remaining seconds, an adult as whatever the species keeps (the chicken's egg timer). */
    @Override
    public float extraState() {
        return isBaby() ? -babySecondsLeft : adultExtraState();
    }

    @Override
    public void restoreExtraState(float value) {
        if (value < 0f) babySecondsLeft = Math.min(BABY_SECONDS, -value);
        else restoreAdultExtraState(value);
    }

    protected float adultExtraState() {
        return 0f;
    }

    protected void restoreAdultExtraState(float value) {
    }

    /** One drop line: {@code min..max} uniformly (each count equally likely, ignoring Looting). */
    protected static void roll(List<BlockDrops.Resolved> out, Random random, BlockType item, int min, int max) {
        int n = min + random.nextInt(max - min + 1);
        if (n > 0) out.add(new BlockDrops.Resolved(item, n));
    }

    protected static List<BlockDrops.Resolved> newDrops() {
        return new ArrayList<>(2);
    }
}
