package com.example.survival;

import lombok.Getter;

/**
 * Reusable health/damage/death state for any one thing that can take damage - the player today,
 * a future mob tomorrow, with zero code shared or duplicated: a mob would just own its own
 * instance of this exact class. Deliberately knows nothing about {@code Player}, hunger,
 * inventory, or rendering - just health, a hurt/invulnerability cooldown, and death.
 *
 * Tick-based (driven by real elapsed {@code dt}, this project's established simulation idiom -
 * see {@code SaplingGrowth}/{@code GameAudio} for the same pattern - never by frame count), so
 * the same behavior holds at any frame rate.
 */
public class HealthComponent {
    public static final float DEFAULT_MAX_HEALTH = 20f;
    /** Real vanilla's own value: 10 ticks at 20 ticks/second. */
    public static final float HURT_COOLDOWN_SECONDS = 0.5f;

    @Getter
    private final float maxHealth;
    @Getter
    private float currentHealth;
    @Getter
    private boolean dead;
    private float hurtCooldown;

    public HealthComponent() {
        this(DEFAULT_MAX_HEALTH);
    }

    public HealthComponent(float maxHealth) {
        this.maxHealth = maxHealth;
        this.currentHealth = maxHealth;
    }

    public boolean isAlive() {
        return !dead;
    }

    /** Seconds left before this can take damage again - 0 means it's vulnerable right now. */
    public float hurtCooldownRemaining() {
        return hurtCooldown;
    }

    /** Advances the hurt cooldown. Call once per frame with the real elapsed time, alive or not
     *  (a no-op once the cooldown reaches zero). */
    public void tick(float dt) {
        if (hurtCooldown > 0f) hurtCooldown = Math.max(0f, hurtCooldown - dt);
    }

    /**
     * Applies damage, respecting the hurt-cooldown invulnerability window (one global cooldown
     * across every source, matching real vanilla's own default behavior - not per-source).
     * Already-dead never takes further damage, and a non-positive amount is rejected outright.
     *
     * @return true if the damage actually landed (starts a fresh cooldown, and may cause death);
     *         false if it was blocked (dead, invulnerable, or a non-positive amount) - callers
     *         that need to react only to a real hit (e.g. hunger's "receiving damage" exhaustion
     *         rule) should gate on this, not assume every call landed.
     */
    public boolean damage(float amount, DamageSource source) {
        if (dead || amount <= 0f || hurtCooldown > 0f) return false;
        currentHealth = Math.max(0f, currentHealth - amount);
        hurtCooldown = HURT_COOLDOWN_SECONDS;
        if (currentHealth <= 0f) dead = true;
        return true;
    }

    /** No-op once dead - healing a corpse makes no sense, and matches real vanilla (only a
     *  respawn, not healing, clears death). */
    public void heal(float amount) {
        if (dead || amount <= 0f) return;
        currentHealth = Math.min(maxHealth, currentHealth + amount);
    }

    /** Directly sets health (clamped to [0, maxHealth]) - e.g. the starvation floor, or a
     *  future set-health test/debug command. Reaching 0 this way triggers death exactly like
     *  {@link #damage}; going back above 0 does NOT itself revive - see {@link #reset()}. */
    public void setHealth(float amount) {
        currentHealth = Math.max(0f, Math.min(maxHealth, amount));
        if (currentHealth <= 0f) dead = true;
    }

    /** Full heal, clears death and the hurt cooldown - the respawn hook this milestone's request
     *  explicitly asks to leave in place without building a full respawn screen yet. */
    public void reset() {
        currentHealth = maxHealth;
        dead = false;
        hurtCooldown = 0f;
    }
}
