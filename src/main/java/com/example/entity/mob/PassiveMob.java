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

    /** Animal.setInLove: 600 ticks; after breeding the parents wait 6000 ticks (AgeableMob.setAge(6000)). */
    public static final float LOVE_SECONDS = 30f;
    public static final float BREED_COOLDOWN_SECONDS = 300f;

    private float babySecondsLeft;
    private float loveSeconds, cooldownSeconds;
    private boolean birthPending;

    protected PassiveMob(Vector3f spawnPosition, float yaw, float maxHealth, float panicMultiplier) {
        super(spawnPosition, yaw, maxHealth);
        setNavigator(new GroundNavigator());
        getGoals()
                .add(0, new FloatGoal())
                .add(1, new PanicGoal(panicMultiplier))
                .add(2, new com.example.entity.ai.BreedGoal())
                .add(4, new com.example.entity.ai.FollowParentGoal())
                .add(5, new RandomStrollGoal())
                .add(6, new LookAtPlayerGoal())
                .add(7, new RandomLookAroundGoal());
    }

    /** The speed multiplier of a baby following its parent (vanilla: 1.1, the cow 1.25). */
    public float followParentSpeed() {
        return 1.1f;
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

    /** What this species can be fed to breed it (or to grow a baby faster); nothing by default. */
    public boolean isBreedingItem(BlockType item) {
        return false;
    }

    public boolean isInLove() {
        return loveSeconds > 0f;
    }

    /** Fed a breeding item: an adult that is not on cooldown falls in love for 30 s; a baby grows up 10% of its remaining time sooner. */
    public boolean feed(BlockType item) {
        if (!isBreedingItem(item)) return false;
        if (isBaby()) {
            babySecondsLeft = Math.max(0.05f, babySecondsLeft - (float) Math.floor(babySecondsLeft * 0.1f));
            return true;
        }
        if (loveSeconds > 0f || cooldownSeconds > 0f) return false;
        loveSeconds = LOVE_SECONDS;
        return true;
    }

    /** BreedGoal.breed: both parents leave love and start their cooldown; this one asks for the baby. */
    public void breedWith(PassiveMob partner) {
        endLove();
        partner.endLove();
        birthPending = true;
    }

    private void endLove() {
        loveSeconds = 0f;
        cooldownSeconds = BREED_COOLDOWN_SECONDS;
    }

    /** True once after {@link #breedWith}: the manager then spawns the baby. */
    public boolean takeBirth() {
        boolean b = birthPending;
        birthPending = false;
        return b;
    }

    /** Seconds left of the love state (for tests and the HUD-less hearts). */
    public float loveSecondsLeft() {
        return loveSeconds;
    }

    public float cooldownSecondsLeft() {
        return cooldownSeconds;
    }

    @Override
    protected void onTick(com.example.world.World world, float dt) {
        if (loveSeconds > 0f) loveSeconds = Math.max(0f, loveSeconds - dt);
        if (cooldownSeconds > 0f) cooldownSeconds = Math.max(0f, cooldownSeconds - dt);
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
