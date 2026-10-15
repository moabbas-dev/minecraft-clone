package com.example.entity.ai;

import com.example.entity.Mob;
import com.example.entity.Targetable;
import com.example.world.World;

import java.util.List;

/** Vanilla priority 6-7: an idle animal sometimes turns its head to watch a player within 6 blocks for 2-4 seconds. */
public final class LookAtPlayerGoal implements Goal {
    private static final float RANGE = 6f;
    private static final int CHANCE_ONE_IN = 50;

    private Targetable watched;
    private float remaining;

    @Override
    public boolean canStart(Mob mob, World world, List<Targetable> candidates) {
        if (mob.getWalkAmount() > 0.1f || !AiRolls.oneIn(mob, CHANCE_ONE_IN)) return false;
        for (Targetable t : candidates) {
            if (t.isAlive() && t.getPosition().distance(mob.getPosition()) <= RANGE) {
                watched = t;
                remaining = 2f + mob.getRandom().nextFloat() * 2f;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canContinue(Mob mob, World world, List<Targetable> candidates) {
        return remaining > 0f && watched != null && watched.isAlive()
                && watched.getPosition().distance(mob.getPosition()) <= RANGE && mob.getWalkAmount() < 0.5f;
    }

    @Override
    public void tick(Mob mob, World world, List<Targetable> candidates, float dt) {
        remaining -= dt;
        mob.lookAt(watched.getEyePosition());
    }
}
