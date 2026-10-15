package com.example.entity.ai;

import com.example.entity.Targetable;
import com.example.entity.mob.Creeper;
import com.example.entity.Mob;
import com.example.world.Raycast;
import com.example.world.World;
import org.joml.Vector3f;

import java.util.List;

/**
 * Vanilla creeper behaviour: with a target within 3 blocks (7 once already swelling) and line of sight it stops, the
 * fuse counts up (30 ticks) and it detonates; losing range or sight lets the fuse count back down.
 */
public final class CreeperSwellGoal implements Goal {
    public static final float START_RANGE = 3f, KEEP_RANGE = 7f;

    private final Creeper creeper;

    public CreeperSwellGoal(Creeper creeper) {
        this.creeper = creeper;
    }

    private boolean wantsToSwell(Mob mob, World world) {
        if (!mob.hasTarget() || !mob.getTarget().isAlive()) return false;
        float range = creeper.getSwell() > 0f ? KEEP_RANGE : START_RANGE;
        if (mob.distanceToTarget() > range) return false;
        Vector3f from = mob.getPosition(), to = mob.getTarget().getPosition();
        Vector3f dir = new Vector3f(to).sub(from);
        float len = dir.length();
        return len < 1e-3f || Raycast.cast(world, from, dir, len - 0.05f) == null;
    }

    @Override
    public boolean canStart(Mob mob, World world, List<Targetable> candidates) {
        return wantsToSwell(mob, world) || creeper.getSwell() > 0f;
    }

    @Override
    public boolean canContinue(Mob mob, World world, List<Targetable> candidates) {
        return wantsToSwell(mob, world) || creeper.getSwell() > 0f;
    }

    @Override
    public void tick(Mob mob, World world, List<Targetable> candidates, float dt) {
        if (wantsToSwell(mob, world)) {
            if (creeper.getSwell() <= 0f) mob.emitGlobalSound("fuse");
            mob.setMoveDirection(0f, 0f);
            mob.getNavigator().stop();
            if (mob.hasTarget()) {
            mob.facePoint(mob.getTarget().getPosition());
            mob.lookAt(mob.getTarget().getEyePosition());
        }
            creeper.addSwell(dt * 20f);
        } else {
            creeper.addSwell(-dt * 20f);
        }
    }
}
