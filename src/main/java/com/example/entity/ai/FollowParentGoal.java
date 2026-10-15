package com.example.entity.ai;

import com.example.entity.Mob;
import com.example.entity.Targetable;
import com.example.entity.mob.PassiveMob;
import com.example.world.World;

import java.util.List;

/**
 * Vanilla FollowParentGoal (constants read from the 26.3 client): a baby looks for the nearest adult of its own kind within
 * 8 blocks horizontally and 4 vertically, and walks toward it unless it is already within 3 blocks. It keeps following while
 * the adult is alive and between 3 and 16 blocks away. Speed: x1.1 (cow x1.25).
 */
public final class FollowParentGoal implements Goal {
    private static final float SCAN_HORIZONTAL = 8f, SCAN_VERTICAL = 4f;
    private static final float TOO_CLOSE_SQ = 9f, TOO_FAR_SQ = 256f;

    private Mob parent;

    @Override
    public boolean usesMovement() {
        return true;
    }

    @Override
    public boolean canStart(Mob mob, World world, List<Targetable> candidates) {
        if (!mob.isBaby() || !(mob instanceof PassiveMob baby)) return false;
        Mob best = null;
        float bestDistSq = Float.MAX_VALUE;
        for (Mob other : mob.getNeighbors()) {
            if (other == mob || other.getClass() != mob.getClass() || other.isBaby() || !other.isAlive()) continue;
            float dx = other.position.x - mob.position.x, dy = other.position.y - mob.position.y, dz = other.position.z - mob.position.z;
            if (Math.abs(dx) > SCAN_HORIZONTAL || Math.abs(dz) > SCAN_HORIZONTAL || Math.abs(dy) > SCAN_VERTICAL) continue;
            float distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = other;
            }
        }
        if (best == null || bestDistSq < TOO_CLOSE_SQ) return false;
        parent = best;
        return true;
    }

    @Override
    public boolean canContinue(Mob mob, World world, List<Targetable> candidates) {
        if (!mob.isBaby() || parent == null || !parent.isAlive()) return false;
        float distSq = mob.position.distanceSquared(parent.position);
        return distSq >= TOO_CLOSE_SQ && distSq <= TOO_FAR_SQ;
    }

    @Override
    public void stop(Mob mob, World world) {
        mob.setMoveDirection(0f, 0f);
        mob.getNavigator().stop();
        parent = null;
    }

    @Override
    public void tick(Mob mob, World world, List<Targetable> candidates, float dt) {
        if (mob instanceof PassiveMob animal) mob.setSpeedMultiplier(animal.followParentSpeed());
        mob.getNavigator().moveToward(mob, world, parent.position, dt);
    }
}
