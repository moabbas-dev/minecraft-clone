package com.example.entity.ai;

import com.example.entity.Mob;
import com.example.entity.Targetable;
import com.example.survival.DamageSource;
import com.example.world.Raycast;
import com.example.world.World;
import org.joml.Vector3f;

import java.util.List;

/**
 * Attacks the current target once in range, on a fixed cooldown, only with a clear line of
 * sight - reuses {@link Raycast}, this project's one existing voxel raycast implementation
 * (block-targeting/placement), rather than a second one. Damage is supplied by
 * {@link DamageAmountProvider} (e.g. a difficulty-based lookup) so this goal itself stays
 * mob-agnostic, and always goes through {@link Targetable#damage}, the exact centralized damage
 * entry point every other damage source in this project already uses.
 */
public final class MeleeAttackGoal implements Goal {
    /** Supplies how much damage the next swing deals - looked up fresh each swing rather than
     *  cached, so a live difficulty change takes effect immediately. */
    @FunctionalInterface
    public interface DamageAmountProvider {
        float damageAmount();
    }

    private final float attackRange;
    private final float attackCooldownSeconds;
    private final DamageAmountProvider damageProvider;
    private float cooldown;

    public MeleeAttackGoal(float attackRange, float attackCooldownSeconds, DamageAmountProvider damageProvider) {
        this.attackRange = attackRange;
        this.attackCooldownSeconds = attackCooldownSeconds;
        this.damageProvider = damageProvider;
    }

    @Override
    public boolean canStart(Mob mob, World world, List<Targetable> candidates) {
        return mob.hasTarget() && mob.getTarget().isAlive() && mob.distanceToTarget() <= attackRange;
    }

    @Override
    public void tick(Mob mob, World world, List<Targetable> candidates, float dt) {
        Targetable target = mob.getTarget();
        if (target == null) return;
        mob.facePoint(target.getPosition());
        if (cooldown > 0f) {
            cooldown = Math.max(0f, cooldown - dt);
            return;
        }
        if (!hasLineOfSight(mob, world, target)) return; // wait for a clear shot; cooldown untouched
        cooldown = attackCooldownSeconds; // the swing happens now, landing or not - a fixed rhythm, not "retry every tick until it lands"
        target.hurtBy(damageProvider.damageAmount(), DamageSource.MOB, mob.position, com.example.combat.CombatMath.BASE_KNOCKBACK);
    }

    /** Casts from the mob's own "eye" (its position accessor already returns a mid-body point -
     *  close enough for a melee reach check) toward the target; any solid block strictly nearer
     *  than the target blocks the attack, exactly like the existing block-targeting raycast
     *  already treats visibility for the player. */
    private static boolean hasLineOfSight(Mob mob, World world, Targetable target) {
        Vector3f from = mob.getPosition();
        Vector3f to = target.getPosition();
        Vector3f delta = new Vector3f(to).sub(from);
        float distance = delta.length();
        if (distance < 1e-4f) return true;
        Raycast.Hit hit = Raycast.cast(world, from, delta, distance - 0.05f);
        return hit == null;
    }
}
