package com.example.entity;

import com.example.survival.DamageSource;
import org.joml.Vector3f;

/**
 * Anything a mob's AI can search for, path toward, and attack - the player today, and later a
 * villager, an animal, or another mob, without the search/navigation/attack code ever needing to
 * know which. {@link LivingEntity} implements this directly (so mobs can target each other once
 * a second mob type exists); the player is a separate, specialized class (see the request's own
 * "Player should remain a specialized entity" rule), so {@code Game} wraps it in a small adapter
 * instead of {@code Player} implementing this itself.
 */
public interface Targetable {
    /** Center-of-mass-ish position used for distance/line-of-sight checks - not necessarily the
     *  same convention as any one implementor's own "position" field. */
    Vector3f getPosition();

    boolean isAlive();

    /** Routes through whatever centralized damage entry point the implementor already has
     *  (e.g. {@code HealthComponent.damage}, {@code PlayerSurvival.damage}) - never a second,
     *  bespoke damage path. Returns true if the damage actually landed (see those methods' own
     *  docs on invulnerability windows). */
    boolean damage(float amount, DamageSource source);

    /** Damage plus knockback away from {@code from} (strength 0.4 for an ordinary hit); the default is
     *  plain damage for targets that cannot be pushed. */
    default boolean hurtBy(float amount, DamageSource source, Vector3f from, float knockbackStrength) {
        return damage(amount, source);
    }
}
