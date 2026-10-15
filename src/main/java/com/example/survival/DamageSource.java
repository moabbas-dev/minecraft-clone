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
    /** Wired up this round - a mob's melee attack goal calls this directly (see
     *  {@code com.example.entity.ai.MeleeAttackGoal}). */
    MOB,
    /** Wired up this round - the player's own melee attack on a mob (see
     *  {@code Game}'s entity-attack handling in {@code handleInteraction}). */
    PLAYER,
    /** Not wired up yet - no fire/lava-contact detection exists. */
    FIRE,
    /** Not wired up yet - {@code Player.isHeadSubmerged()} exists, a breath/drown timer doesn't. */
    DROWNING,
    /** Not wired up yet - no cactus block exists. */
    CACTUS,
    /** Wired up this round - see {@code PlayerSurvival}'s starvation tick. */
    STARVATION,
    /** Falling out of the world: 4 damage whenever the hurt cooldown allows, below y = -64 (see {@code PlayerSurvival}). */
    VOID,
    /** Creeper and TNT blasts: reduced by armor like vanilla. */
    EXPLOSION,
    /** A skeleton arrow in flight. */
    ARROW,
    /** Poison and other magic: ignores armor. */
    MAGIC,
    /** A lightning bolt: 5 damage, reduced by armor. */
    LIGHTNING,
    /** Burning (1 damage a second while alight): ignores armor. */
    ON_FIRE;

    /** Sources ordinary armor does not reduce (research: docs/research/armor.md). GENERIC is the test
     *  controller's exact-amount damage. */
    public boolean bypassesArmor() {
        return this == FALL || this == STARVATION || this == DROWNING || this == VOID || this == GENERIC || this == MAGIC || this == ON_FIRE;
    }
}
