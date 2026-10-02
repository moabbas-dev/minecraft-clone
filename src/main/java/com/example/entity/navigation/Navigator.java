package com.example.entity.navigation;

import com.example.entity.Mob;
import com.example.world.World;
import org.joml.Vector3f;

/**
 * Steers a {@link Mob} toward a world point over successive ticks, however it sees fit -
 * {@link GroundNavigator} is the first (and today, only) implementation, walking on solid ground
 * via {@link Pathfinder}. A future flying or swimming mob supplies a different {@code Navigator}
 * without {@link Mob} or the goal system caring which.
 */
public interface Navigator {
    /** Advances one tick toward {@code targetPosition}, setting the mob's move direction/facing
     *  as needed (or leaving it at rest if already there / nothing useful to do). */
    void moveToward(Mob mob, World world, Vector3f targetPosition, float dt);

    /** Stops and discards any in-progress route - called when the goal driving movement stops
     *  (e.g. target lost, or now close enough to attack instead). */
    void stop();

    /** Debug/test introspection only. */
    boolean hasValidPath();
}
