package com.example.survival;

/**
 * What caused a {@link HealthComponent#damage} call. Every entry this milestone's request asks
 * to prepare for exists here, whether or not something actually triggers it yet (see each
 * value's own note) - the point is that a future mob, fire block, cactus, drowning timer, or
 * void-fall check all call the exact same {@link HealthComponent#damage} method with one of
 * these, never a bespoke per-source damage path.
 */
public enum DamageSource {
    /** Catch-all / test-controller damage. */
    GENERIC,
    /** Wired up this round - see {@code PlayerSurvival}'s fall tracking. */
    FALL,
    /** Not wired up yet - no mobs exist. Reserved so a future mob's attack calls this directly. */
    MOB,
    /** Not wired up yet - no fire/lava-contact detection exists. */
    FIRE,
    /** Not wired up yet - {@code Player.isHeadSubmerged()} exists, a breath/drown timer doesn't. */
    DROWNING,
    /** Not wired up yet - no cactus block exists. */
    CACTUS,
    /** Wired up this round - see {@code PlayerSurvival}'s starvation tick. */
    STARVATION,
    /** Not wired up yet - no void/kill-plane check exists below the world. */
    VOID
}
