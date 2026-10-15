package com.example.entity.ai;

import com.example.entity.Mob;
import com.example.entity.Targetable;
import com.example.world.World;
import org.joml.Vector3f;

import java.util.List;

/** Vanilla priority 7: an idle mob occasionally (2% per tick) turns its head to a random direction for 1-2 seconds. */
public final class RandomLookAroundGoal implements Goal {
    private static final int CHANCE_ONE_IN = 50; // vanilla: 2% per tick

    private float yaw;
    private float remaining;

    @Override
    public boolean canStart(Mob mob, World world, List<Targetable> candidates) {
        if (mob.getWalkAmount() > 0.1f || !AiRolls.oneIn(mob, CHANCE_ONE_IN)) return false;
        yaw = mob.getRandom().nextFloat() * (float) (Math.PI * 2);
        remaining = 1f + mob.getRandom().nextFloat();
        return true;
    }

    @Override
    public boolean canContinue(Mob mob, World world, List<Targetable> candidates) {
        return remaining > 0f && mob.getWalkAmount() < 0.5f;
    }

    @Override
    public void tick(Mob mob, World world, List<Targetable> candidates, float dt) {
        remaining -= dt;
        // vanilla: look at a point 1 block away in a random direction, at the mob's own eye height
        mob.lookAt(new Vector3f(mob.position.x + (float) Math.cos(yaw), mob.position.y + mob.getEyeHeight(), mob.position.z + (float) Math.sin(yaw)));
    }
}
