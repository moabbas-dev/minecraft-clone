package com.example.entity.ai;

import com.example.entity.Mob;
import com.example.entity.Targetable;
import com.example.world.World;
import org.joml.Vector3f;

import java.util.List;

/**
 * Water-avoiding random stroll (vanilla priority 5-6, speed x1.0, range 10 horizontal / 7 vertical).
 * The start chance is checked 20 times a second at 1 in 60 (about 3 s of standing between walks), and
 * a walk ends on arrival or after a timeout so a blocked path never traps the mob.
 */
public final class RandomStrollGoal implements Goal {
    private static final int ONE_IN = 60;
    private static final float MAX_WALK_SECONDS = 9f;

    private Vector3f target;
    private float walked;

    @Override
    public boolean usesMovement() {
        return true;
    }

    @Override
    public boolean canStart(Mob mob, World world, List<Targetable> candidates) {
        if (mob.getRecentHurtTimer() > 0f) return false; // panicking takes over
        if (!AiRolls.oneIn(mob, ONE_IN)) return false;
        target = StrollTargets.pick(world, mob.position, mob.getRandom(), 10, 7);
        walked = 0f;
        return target != null;
    }

    @Override
    public boolean canContinue(Mob mob, World world, List<Targetable> candidates) {
        return target != null && mob.getRecentHurtTimer() <= 0f && walked < MAX_WALK_SECONDS
                && Math.hypot(target.x - mob.position.x, target.z - mob.position.z) > 0.9f;
    }

    @Override
    public void stop(Mob mob, World world) {
        mob.setMoveDirection(0f, 0f);
        mob.getNavigator().stop();
        target = null;
    }

    @Override
    public void tick(Mob mob, World world, List<Targetable> candidates, float dt) {
        walked += dt;
        mob.getNavigator().moveToward(mob, world, target, dt);
    }
}
