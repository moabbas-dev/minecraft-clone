package com.example.entity.ai;

import com.example.entity.Mob;
import com.example.entity.Targetable;
import com.example.world.World;
import org.joml.Vector3f;

import java.util.List;

/**
 * Priority 1 in vanilla: after being hurt a passive animal flees at {@code speedMultiplier} x its
 * normal speed (pig/cow 1.25, chicken 1.4). There is no fixed panic duration - it keeps picking new flee
 * points while the "recently hurt" timer lasts (about 5 s here) and stops when it runs out.
 */
public final class PanicGoal implements Goal {
    private final float speedMultiplier;
    private Vector3f target;
    private float stuckTimer;

    public PanicGoal(float speedMultiplier) {
        this.speedMultiplier = speedMultiplier;
    }

    @Override
    public boolean canStart(Mob mob, World world, List<Targetable> candidates) {
        return mob.getRecentHurtTimer() > 0f;
    }

    @Override
    public void start(Mob mob, World world) {
        target = null;
        stuckTimer = 0f;
    }

    @Override
    public void stop(Mob mob, World world) {
        mob.setMoveDirection(0f, 0f);
        mob.getNavigator().stop();
        target = null;
    }

    @Override
    public void tick(Mob mob, World world, List<Targetable> candidates, float dt) {
        mob.setSpeedMultiplier(speedMultiplier);
        stuckTimer += dt;
        boolean arrived = target != null
                && Math.hypot(target.x - mob.position.x, target.z - mob.position.z) < 0.9f;
        if (target == null || arrived || stuckTimer > 2.5f) {
            target = StrollTargets.fleePoint(world, mob.position, mob.getRandom(), 5f);
            mob.getNavigator().stop();
            stuckTimer = 0f;
        }
        if (target != null) mob.getNavigator().moveToward(mob, world, target, dt);
    }
}
