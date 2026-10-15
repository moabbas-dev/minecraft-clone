package com.example.entity.mob;

import com.example.world.BlockDrops;
import com.example.world.BlockType;
import com.example.world.World;
import org.joml.Vector3f;

import java.util.List;
import java.util.Random;

/**
 * Chicken: 4 HP, 0.4 x 0.7, panics at x1.4, drops exactly 1 raw chicken and 0-2 feathers, takes no fall
 * damage and flaps to fall slowly, and lays an egg every 6000-12000 ticks (5-10 minutes).
 */
public class Chicken extends PassiveMob {
    public static final float WIDTH = 0.4f, HEIGHT = 0.7f, MAX_HEALTH = 4f, MOVE_SPEED = 2.5f;
    private static final float MIN_EGG_SECONDS = 300f, MAX_EGG_SECONDS = 600f;
    /** Vanilla scales downward velocity by 0.6 each tick while airborne. */
    private static final float FALL_DRAG_PER_TICK = 0.6f;

    private float eggTimer;
    private boolean eggReady;
    // Wing flap (Chicken.aiStep): flapSpeed rises 1.2 per tick in the air and falls 0.3 on the ground, flapping decays by 0.9
    private float flap, oFlap, flapSpeed, oFlapSpeed, flapping, flapClock;

    public Chicken(Vector3f spawnPosition, float yaw) {
        super(spawnPosition, yaw, MAX_HEALTH, 1.4f);
        eggTimer = nextEggDelay(random);
    }

    static float nextEggDelay(Random random) {
        return MIN_EGG_SECONDS + random.nextFloat() * (MAX_EGG_SECONDS - MIN_EGG_SECONDS);
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
        return "chicken";
    }

    @Override
    protected boolean fallImmune() {
        return true;
    }

    /** The wing angle phase between ticks (the model opens the wings by (sin(flap) + 1) * flapSpeed). */
    public float getFlap() {
        return oFlap + (flap - oFlap) * (flapClock / 0.05f);
    }

    public float getFlapSpeed() {
        return oFlapSpeed + (flapSpeed - oFlapSpeed) * (flapClock / 0.05f);
    }

    private void tickWings(float dt) {
        flapClock += dt;
        int guard = 0;
        while (flapClock >= 0.05f && guard++ < 5) {
            flapClock -= 0.05f;
            oFlap = flap;
            oFlapSpeed = flapSpeed;
            flapSpeed = Math.max(0f, Math.min(1f, flapSpeed + (isOnGround() ? -1f : 4f) * 0.3f));
            if (!isOnGround() && flapping < 1f) flapping = 1f;
            flapping *= 0.9f;
            flap += flapping * 2f;
        }
        if (flapClock >= 0.05f) flapClock = 0f;
    }

    @Override
    protected void onTick(World world, float dt) {
        super.onTick(world, dt);
        tickWings(dt);
        if (!isOnGround() && velocity.y < 0f) {
            velocity.y *= (float) Math.pow(FALL_DRAG_PER_TICK, dt * 20f); // wings flap: slow fall
        }
        if (isBaby()) return; // chicks lay no eggs
        eggTimer -= dt;
        if (eggTimer <= 0f) {
            eggReady = true;
            eggTimer = nextEggDelay(random);
        }
    }

    @Override
    protected void grewUp() {
        eggTimer = nextEggDelay(random);
    }

    @Override
    public BlockType takeLaidItem() {
        if (!eggReady) return null;
        eggReady = false;
        emitSound("plop");
        return BlockType.EGG;
    }

    @Override
    protected float adultExtraState() {
        return eggTimer;
    }

    @Override
    protected void restoreAdultExtraState(float value) {
        if (value > 0f && value <= MAX_EGG_SECONDS) eggTimer = value;
    }

    @Override
    public List<BlockDrops.Resolved> getDrops(Random random) {
        List<BlockDrops.Resolved> out = newDrops();
        if (isBaby()) return out; // baby animals drop nothing
        roll(out, random, BlockType.RAW_CHICKEN, 1, 1);
        roll(out, random, BlockType.FEATHER, 0, 2);
        return out;
    }
}
