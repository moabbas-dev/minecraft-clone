package com.example.entity.ai;

import com.example.entity.Mob;
import com.example.entity.Targetable;
import com.example.world.World;

import java.util.List;

/** Priority 0 in vanilla: while standing in water, keep pushing up so the mob floats instead of sinking. */
public final class FloatGoal implements Goal {
    @Override
    public boolean canStart(Mob mob, World world, List<Targetable> candidates) {
        return mob.isInWater(world);
    }

    @Override
    public void tick(Mob mob, World world, List<Targetable> candidates, float dt) {
        mob.requestSwimUp();
    }
}
