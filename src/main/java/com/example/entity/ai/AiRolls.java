package com.example.entity.ai;

import com.example.entity.Mob;

/** Frame-rate independent "1 in N per game tick" rolls: vanilla checks these 20 times a second. */
final class AiRolls {
    private AiRolls() {
    }

    static boolean oneIn(Mob mob, int n) {
        float perTick = 1f / n;
        float perFrame = 1f - (float) Math.pow(1f - perTick, mob.getLastDt() * 20f);
        return mob.getRandom().nextFloat() < perFrame;
    }
}
