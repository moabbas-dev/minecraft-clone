package com.example.entity.ai;

import com.example.entity.Mob;
import com.example.entity.Targetable;
import com.example.world.Difficulty;
import com.example.world.Raycast;
import com.example.world.World;
import org.joml.Vector3f;

import java.util.List;
import java.util.function.Supplier;

/**
 * Vanilla skeleton bow AI: shoots within 15 blocks with line of sight (every 40 ticks, 20 on Hard), backs away when
 * the target is within 4 blocks and closes in beyond 14. Arrows leave at 1.6 blocks/tick aimed 0.2 blocks higher per
 * horizontal block, with inaccuracy 14 - 4 x difficulty id (10 / 6 / 2).
 */
public final class RangedAttackGoal implements Goal {
    public static final float SHOOT_RANGE = 15f, RETREAT_RANGE = 4f, APPROACH_RANGE = 14f;
    public static final float ARROW_SPEED = 1.6f * 20f; // blocks per second

    private final Supplier<Difficulty> difficulty;
    private float cooldown = 1f;

    public RangedAttackGoal(Supplier<Difficulty> difficulty) {
        this.difficulty = difficulty;
    }

    /** Seconds between shots: 2 s on Easy/Normal, 1 s on Hard. */
    public static float intervalFor(Difficulty d) {
        return d == Difficulty.HARD ? 1f : 2f;
    }

    /** Arrow damage after vanilla's difficulty scaling (base ceil(2 x 1.6) = 4). */
    public static float damageFor(Difficulty d) {
        return switch (d) {
            case PEACEFUL -> 0f;
            case EASY -> 3f;
            case NORMAL -> 4f;
            case HARD -> 5f;
        };
    }

    /** Vanilla inaccuracy parameter: 14 - 4 x (Easy 1, Normal 2, Hard 3). */
    public static float inaccuracyFor(Difficulty d) {
        return switch (d) {
            case EASY -> 10f;
            case NORMAL -> 6f;
            case HARD -> 2f;
            case PEACEFUL -> 14f;
        };
    }

    @Override
    public boolean canStart(Mob mob, World world, List<Targetable> candidates) {
        return mob.hasTarget() && mob.getTarget().isAlive();
    }

    @Override
    public boolean canContinue(Mob mob, World world, List<Targetable> candidates) {
        return canStart(mob, world, candidates);
    }

    @Override
    public void stop(Mob mob, World world) {
        mob.setMoveDirection(0f, 0f);
        mob.getNavigator().stop();
    }

    @Override
    public void tick(Mob mob, World world, List<Targetable> candidates, float dt) {
        Targetable target = mob.getTarget();
        if (target == null) return;
        Vector3f me = mob.getPosition(), them = target.getPosition();
        float dist = me.distance(them);
        boolean sight = lineOfSight(world, me, them);
        if (dist < RETREAT_RANGE) {
            float dx = me.x - them.x, dz = me.z - them.z;
            float len = (float) Math.hypot(dx, dz);
            if (len > 1e-3f) mob.setMoveDirection(dx / len, dz / len);
        } else if (dist > APPROACH_RANGE || !sight) {
            mob.getNavigator().moveToward(mob, world, them, dt);
        } else {
            mob.setMoveDirection(0f, 0f);
            mob.getNavigator().stop();
        }
        if (dist <= SHOOT_RANGE && sight) {
            mob.facePoint(them);
            cooldown -= dt;
            if (cooldown <= 0f) {
                cooldown = intervalFor(difficulty.get());
                shoot(mob, them);
            }
        }
    }

    private void shoot(Mob mob, Vector3f target) {
        Difficulty d = difficulty.get();
        Vector3f origin = new Vector3f(mob.position.x, mob.position.y + mob.getHeight() * 0.85f, mob.position.z);
        float dx = target.x - origin.x, dz = target.z - origin.z;
        float horizontal = (float) Math.hypot(dx, dz);
        Vector3f dir = new Vector3f(dx, target.y - origin.y + horizontal * 0.2f, dz).normalize();
        float scatter = 0.0075f * inaccuracyFor(d);
        dir.add((float) mob.getRandom().nextGaussian() * scatter, (float) mob.getRandom().nextGaussian() * scatter,
                (float) mob.getRandom().nextGaussian() * scatter).normalize();
        mob.launchProjectile(origin, dir.mul(ARROW_SPEED), damageFor(d));
        mob.emitGlobalSound("arrow.shoot");
    }

    private static boolean lineOfSight(World world, Vector3f from, Vector3f to) {
        Vector3f dir = new Vector3f(to).sub(from);
        float len = dir.length();
        return len < 1e-3f || Raycast.cast(world, from, dir, len - 0.05f) == null;
    }
}
