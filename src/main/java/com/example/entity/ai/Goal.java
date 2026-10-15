package com.example.entity.ai;

import com.example.entity.Mob;
import com.example.entity.Targetable;
import com.example.world.World;

import java.util.List;

/**
 * One independent, reusable unit of mob behavior - the architecture the request asks for by
 * name ("canStart/canContinue/start/stop/tick"), so a future mob composes its own behavior from
 * a different mix of these instead of one growing {@code Zombie.update()}. {@link GoalSelector}
 * is what actually drives the lifecycle below; a {@code Goal} implementation never calls its own
 * {@code start}/{@code stop}.
 */
public interface Goal {
    /** May this goal begin now? Checked only while the goal is NOT currently running. */
    boolean canStart(Mob mob, World world, List<Targetable> candidates);

    /** May this goal keep running? Checked only while the goal IS currently running - a natural
     *  place for "target moved out of range" / "target died" to hand control back to whichever
     *  other goal becomes eligible next (e.g. attack range lost -&gt; movement goal resumes). */
    default boolean canContinue(Mob mob, World world, List<Targetable> candidates) {
        return canStart(mob, world, candidates);
    }

    /**
     * Whether this goal steers the mob (vanilla's MOVE flag). Of the goals that do, only the highest-priority running one is
     * allowed to run: a lower one is held back (and stopped) while a higher one is active, so panicking beats following a
     * parent beats strolling.
     */
    default boolean usesMovement() {
        return false;
    }

    /** Called exactly once, the tick this goal transitions from not-running to running. */
    default void start(Mob mob, World world) {
    }

    /** Called exactly once, the tick this goal transitions from running to not-running - the
     *  place to clear any state that shouldn't leak into the next time this goal starts (e.g.
     *  a movement goal releasing its hold on the mob's move direction). */
    default void stop(Mob mob, World world) {
    }

    /** Called every tick this goal is running, after start()/canContinue() for that tick. */
    void tick(Mob mob, World world, List<Targetable> candidates, float dt);
}
