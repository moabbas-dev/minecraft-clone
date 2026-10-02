package com.example.entity.ai;

import com.example.entity.Mob;
import com.example.entity.Targetable;
import com.example.entity.navigation.Navigator;
import com.example.world.World;

import java.util.List;

/**
 * Chases the mob's current target via its {@link Navigator} until within {@code stopDistance}
 * (real vanilla's own approach: movement and melee attack are two separate goals that hand off
 * by range, not one goal that both walks and swings). Any {@link Navigator} implementation
 * works here - this goal never touches pathfinding directly.
 */
public final class MoveToTargetGoal implements Goal {
    private final float stopDistance;

    public MoveToTargetGoal(float stopDistance) {
        this.stopDistance = stopDistance;
    }

    @Override
    public boolean canStart(Mob mob, World world, List<Targetable> candidates) {
        return mob.hasTarget() && mob.distanceToTarget() > stopDistance;
    }

    @Override
    public void stop(Mob mob, World world) {
        mob.setMoveDirection(0f, 0f);
        mob.getNavigator().stop();
    }

    @Override
    public void tick(Mob mob, World world, List<Targetable> candidates, float dt) {
        mob.getNavigator().moveToward(mob, world, mob.getTarget().getPosition(), dt);
    }
}
