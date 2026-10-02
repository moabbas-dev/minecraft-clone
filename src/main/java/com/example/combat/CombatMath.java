package com.example.combat;

import com.example.world.BlockType;

/** The pure formulas of the 1.9+ melee system (see docs/research/melee-combat.md). */
public final class CombatMath {
    private CombatMath() {
    }

    public static final float CRIT_MULTIPLIER = 1.5f;
    /** Charge needed for crits, sweeps and sprint knockback (damage multiplier about 84.8%). */
    public static final float SPECIAL_ATTACK_CHARGE = 0.9f;
    public static final float BASE_KNOCKBACK = 0.4f;
    public static final float SPRINT_KNOCKBACK_BONUS = 0.5f;
    public static final float SWEEP_DAMAGE = 1f;
    public static final float SWEEP_KNOCKBACK_FACTOR = 0.8f;

    /** q = min(1, (t + 0.5) / T) with t in ticks since the last attack/switch and T the period in ticks. */
    public static float charge(float ticksSinceAttack, float periodTicks) {
        return Math.min(1f, (ticksSinceAttack + 0.5f) / periodTicks);
    }

    /** 0.2 + 0.8 * q^2 */
    public static float damageMultiplier(float q) {
        return 0.2f + 0.8f * q * q;
    }

    /** Final player melee damage before the target armor. */
    public static float attackDamage(BlockType held, float q, boolean critical) {
        float d = WeaponStats.damage(held) * damageMultiplier(q);
        return critical ? d * CRIT_MULTIPLIER : d;
    }

    /** Critical hit: falling (airborne, moving down), not in water or flying, and not a sprint-knockback hit. */
    public static boolean isCritical(float charge, boolean onGround, float verticalVelocity, boolean inWater,
                                     boolean flying, boolean sprintHit) {
        return charge >= SPECIAL_ATTACK_CHARGE && !onGround && verticalVelocity < 0f && !inWater && !flying && !sprintHit;
    }

    public static boolean isSprintHit(float charge, boolean sprinting) {
        return charge >= SPECIAL_ATTACK_CHARGE && sprinting;
    }

    public static boolean isSweep(BlockType held, float charge, boolean onGround, boolean critical, boolean sprintHit) {
        return WeaponStats.isSword(held) && charge >= SPECIAL_ATTACK_CHARGE && onGround && !critical && !sprintHit;
    }

    public static float knockbackStrength(boolean sprintHit) {
        return BASE_KNOCKBACK + (sprintHit ? SPRINT_KNOCKBACK_BONUS : 0f);
    }

    /**
     * Vanilla LivingEntity.knockback. Velocities are in blocks per tick; (dirX, dirZ) points from the
     * victim toward the attacker (normalized here). Returns {vx, vy, vz} after the hit.
     */
    public static float[] knockback(float vx, float vy, float vz, boolean onGround, float dirX, float dirZ, float strength) {
        float len = (float) Math.sqrt(dirX * dirX + dirZ * dirZ);
        float dx = len < 1e-6f ? 0f : dirX / len, dz = len < 1e-6f ? 0f : dirZ / len;
        float nvy = onGround ? Math.min(0.4f, vy / 2f + strength) : vy;
        return new float[]{vx / 2f - strength * dx, nvy, vz / 2f - strength * dz};
    }
}
