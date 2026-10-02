package com.example.entity.ai;

import com.example.entity.Mob;
import com.example.entity.Targetable;
import com.example.world.World;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs a priority-ordered list of independent {@link Goal}s against one {@link Mob} each tick.
 * Lower {@code priority} runs first and is checked first, matching real vanilla's own
 * convention, but this selector deliberately has no "conflicting goal" flag system (unlike
 * vanilla's real one) - with today's 3 zombie goals their start/continue conditions are already
 * mutually exclusive by construction (movement stops being eligible exactly when attack range is
 * reached, and vice versa), so multiple goals simply run concurrently without stepping on each
 * other. A future mob whose goals genuinely conflict (e.g. two different movement goals) would
 * need to extend this - documented rather than pre-built, since nothing needs it yet.
 */
public final class GoalSelector {
    private record Entry(int priority, Goal goal) {
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Set<Goal> running = new HashSet<>();

    public GoalSelector add(int priority, Goal goal) {
        entries.add(new Entry(priority, goal));
        entries.sort(Comparator.comparingInt(Entry::priority));
        return this;
    }

    public void tick(Mob mob, World world, List<Targetable> candidates, float dt) {
        for (Entry e : entries) {
            Goal goal = e.goal();
            if (running.contains(goal)) {
                if (!goal.canContinue(mob, world, candidates)) {
                    goal.stop(mob, world);
                    running.remove(goal);
                }
            } else if (goal.canStart(mob, world, candidates)) {
                goal.start(mob, world);
                running.add(goal);
            }
        }
        for (Entry e : entries) {
            if (running.contains(e.goal())) e.goal().tick(mob, world, candidates, dt);
        }
    }

    /** For debug/test introspection only - not consulted by any gameplay logic. */
    public boolean isRunning(Goal goal) {
        return running.contains(goal);
    }
}
