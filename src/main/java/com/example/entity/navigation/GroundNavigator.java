package com.example.entity.navigation;

import com.example.entity.Mob;
import com.example.world.World;
import lombok.Getter;
import org.joml.Vector3f;

/**
 * Walking navigation: follows a {@link Path} from {@link Pathfinder} one waypoint at a time,
 * only recomputing the path when it actually needs to (per the request's own "do not recompute
 * A* every frame" performance rule) - when there is no path yet, the current one is finished,
 * the target has moved meaningfully far from the goal the path was computed for, or a timeout
 * safety net trips (guards against a path that's silently gone stale for some other reason
 * rather than looping on a bad route forever).
 */
public final class GroundNavigator implements Navigator {
    private static final float WAYPOINT_REACH_DISTANCE = 0.4f;
    private static final float REPATH_TARGET_MOVE_DISTANCE = 3f;
    private static final float REPATH_TIMEOUT_SECONDS = 5f;

    @Getter
    private Path path;
    private Vector3f pathGoal;
    private float sincePathComputed;

    @Override
    public void moveToward(Mob mob, World world, Vector3f targetPosition, float dt) {
        sincePathComputed += dt;
        boolean needsNewPath = path == null || path.isComplete()
                || pathGoal == null || pathGoal.distance(targetPosition) > REPATH_TARGET_MOVE_DISTANCE
                || sincePathComputed > REPATH_TIMEOUT_SECONDS;
        if (needsNewPath) {
            path = Pathfinder.findPath(world, mob.position, targetPosition, mob.getHeight());
            pathGoal = new Vector3f(targetPosition);
            sincePathComputed = 0f;
        }

        if (path == null) {
            // Boxed in, or the bounded search found no reachable column at all - a direct step
            // still lets Mob's own 1-block step-up clear a simple obstacle, and is never worse
            // than standing still.
            steerTowards(mob, targetPosition);
            return;
        }

        Vector3f wp = path.currentWaypoint();
        if (horizontalDistanceSquared(mob, wp) <= WAYPOINT_REACH_DISTANCE * WAYPOINT_REACH_DISTANCE) {
            path.advance();
            if (path.isComplete()) {
                mob.setMoveDirection(0f, 0f);
                return;
            }
            wp = path.currentWaypoint();
        }
        steerTowards(mob, wp);
    }

    private static float horizontalDistanceSquared(Mob mob, Vector3f point) {
        float dx = point.x - mob.position.x, dz = point.z - mob.position.z;
        return dx * dx + dz * dz;
    }

    private static void steerTowards(Mob mob, Vector3f point) {
        float dx = point.x - mob.position.x, dz = point.z - mob.position.z;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len > 1e-4f) {
            mob.setMoveDirection(dx / len, dz / len);
            mob.facePoint(point);
        } else {
            mob.setMoveDirection(0f, 0f);
        }
    }

    @Override
    public void stop() {
        path = null;
        pathGoal = null;
    }

    @Override
    public boolean hasValidPath() {
        return path != null;
    }
}
