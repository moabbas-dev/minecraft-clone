package com.example.entity.ai;

import com.example.entity.Mob;
import com.example.entity.Targetable;
import com.example.entity.mob.PassiveMob;
import com.example.world.World;

import java.util.List;

/**
 * Vanilla BreedGoal (constants read from the 26.3 client): an adult in love looks for another adult of its own kind, also in
 * love, within 8 blocks and walks to it at normal speed. After 60 ticks of that, once the two are within 3 blocks of each
 * other (distance squared under 9), they breed: both go out of love and have to wait 6000 ticks before breeding again, and a
 * baby is born (see {@code MobManager}). The goal gives up when the partner leaves love or 60 ticks pass without a breed.
 */
public final class BreedGoal implements Goal {
    private static final float PARTNER_RANGE = 8f;
    private static final float BREED_TICKS = 60f;
    private static final float MATE_DISTANCE_SQ = 9f;

    private PassiveMob partner;
    private float loveTicks;

    @Override
    public boolean usesMovement() {
        return true;
    }

    @Override
    public boolean canStart(Mob mob, World world, List<Targetable> candidates) {
        if (!(mob instanceof PassiveMob me) || me.isBaby() || !me.isInLove()) return false;
        partner = nearestPartner(me);
        return partner != null;
    }

    @Override
    public boolean canContinue(Mob mob, World world, List<Targetable> candidates) {
        return partner != null && partner.isAlive() && partner.isInLove() && loveTicks < BREED_TICKS;
    }

    @Override
    public void stop(Mob mob, World world) {
        mob.setMoveDirection(0f, 0f);
        mob.getNavigator().stop();
        partner = null;
        loveTicks = 0f;
    }

    @Override
    public void tick(Mob mob, World world, List<Targetable> candidates, float dt) {
        if (!(mob instanceof PassiveMob me) || partner == null) return;
        loveTicks += dt * 20f;
        mob.setSpeedMultiplier(1f);
        mob.getNavigator().moveToward(mob, world, partner.position, dt);
        if (loveTicks >= BREED_TICKS && mob.position.distanceSquared(partner.position) < MATE_DISTANCE_SQ) {
            me.breedWith(partner);
        }
    }

    private static PassiveMob nearestPartner(PassiveMob me) {
        PassiveMob best = null;
        float bestSq = Float.MAX_VALUE;
        for (Mob other : me.getNeighbors()) {
            if (other == me || other.getClass() != me.getClass() || !(other instanceof PassiveMob o) || !o.isAlive() || o.isBaby() || !o.isInLove()) continue;
            float d = me.position.distanceSquared(o.position);
            if (d <= PARTNER_RANGE * PARTNER_RANGE && d < bestSq) {
                bestSq = d;
                best = o;
            }
        }
        return best;
    }
}
