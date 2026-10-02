package com.example.entity.ai;

import com.example.entity.Mob;
import com.example.entity.Targetable;
import com.example.world.World;
import org.joml.Vector3f;

import java.util.List;

/**
 * Background target acquisition: picks the nearest alive candidate within {@code searchRadius}
 * when the mob has none, and drops an existing target once it dies or drifts past
 * {@code searchRadius * LOSE_RADIUS_FACTOR} (a slightly larger "let go" radius than the
 * "grab" radius, so a target sitting exactly at the boundary doesn't flicker acquired/lost every
 * tick). Never scans on every single tick - only every {@link #RESCAN_INTERVAL_SECONDS}, and
 * only when there currently is no target - satisfying "don't scan every entity every tick"
 * without needing a spatial index for the one target type (Player) that exists today; see the
 * class doc on {@code MobManager} for where a real spatial bound would be added once more
 * targetable entity types exist.
 */
public final class FindTargetGoal implements Goal {
    private static final float RESCAN_INTERVAL_SECONDS = 0.5f;
    private static final float LOSE_RADIUS_FACTOR = 1.5f;

    private final float searchRadius;
    private final float loseRadiusSq;
    private float timer;

    public FindTargetGoal(float searchRadius) {
        this.searchRadius = searchRadius;
        float loseRadius = searchRadius * LOSE_RADIUS_FACTOR;
        this.loseRadiusSq = loseRadius * loseRadius;
    }

    @Override
    public boolean canStart(Mob mob, World world, List<Targetable> candidates) {
        return true; // always eligible - this is the permanent background goal
    }

    @Override
    public void tick(Mob mob, World world, List<Targetable> candidates, float dt) {
        Vector3f mobPos = mob.getPosition();
        Targetable current = mob.getTarget();
        if (current != null) {
            if (!current.isAlive() || mobPos.distanceSquared(current.getPosition()) > loseRadiusSq) {
                mob.setTarget(null);
                current = null;
            }
        }

        timer -= dt;
        if (timer > 0f || current != null) return;
        timer = RESCAN_INTERVAL_SECONDS;

        Targetable nearest = null;
        float nearestDistSq = searchRadius * searchRadius;
        for (int i = 0; i < candidates.size(); i++) {
            Targetable c = candidates.get(i);
            if (!c.isAlive()) continue;
            float distSq = mobPos.distanceSquared(c.getPosition());
            if (distSq <= nearestDistSq) {
                nearestDistSq = distSq;
                nearest = c;
            }
        }
        if (nearest != null) mob.setTarget(nearest);
    }
}
