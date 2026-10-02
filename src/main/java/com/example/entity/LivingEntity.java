package com.example.entity;

import com.example.survival.DamageSource;
import com.example.survival.HealthComponent;
import lombok.Getter;
import org.joml.Vector3f;

/**
 * Entity + health/damage/death, reused as-is from the health/hunger/damage milestone - this is
 * the "Zombie -&gt; LivingEntity -&gt; HealthComponent" shape the request requires verbatim, and
 * the reason there is no {@code ZombieHealth} anywhere in this codebase: a zombie just owns one
 * more instance of the exact same {@link HealthComponent} the player already owns, with zero
 * mob-specific code duplicated into it. {@link HealthComponent} itself already documented this
 * exact reuse as its own design intent (see its class doc) before a second consumer existed.
 */
public abstract class LivingEntity extends Entity implements Targetable {
    @Getter
    private final HealthComponent health;
    /** Seconds left in a brief post-hit state a renderer could use for a hurt flash/tint -
     *  purely cosmetic bookkeeping, never gates any gameplay rule (unlike the invulnerability
     *  window, which {@link HealthComponent} already owns and enforces on its own). */
    @Getter
    private float hurtTime;
    private static final float HURT_FLASH_SECONDS = 0.5f; // vanilla: 10 ticks
    /** The source of the most recent damage that landed (used to credit XP to a player kill). */
    @Getter
    private DamageSource lastDamageSource;
    /** Seconds since death (drives the 1 s topple animation). */
    @Getter
    protected float deathTime;
    /** Seconds left of the "recently hurt" state that makes passive animals panic (vanilla ~100 ticks). */
    @Getter
    protected float recentHurtTimer;
    public static final float PANIC_SECONDS = 5f;

    protected LivingEntity(Vector3f spawnPosition, float yaw, float maxHealth) {
        super(spawnPosition, yaw);
        this.health = new HealthComponent(maxHealth);
    }

    @Override
    public Vector3f getPosition() {
        return new Vector3f(position.x, position.y + getHeight() * 0.5f, position.z);
    }

    // isAlive() is inherited directly from Entity - tickHealth() below keeps Entity's own
    // "alive" flag in sync with HealthComponent's death state, so Targetable.isAlive() and
    // Entity.isAlive() are never two independent sources of truth.

    @Override
    public boolean damage(float amount, DamageSource source) {
        float taken = source.bypassesArmor()
                ? amount
                : com.example.combat.ArmorRules.reduce(amount, armorPoints(), armorToughness());
        boolean applied = health.damage(taken, source);
        if (applied) {
            hurtTime = HURT_FLASH_SECONDS;
            recentHurtTimer = PANIC_SECONDS;
            lastDamageSource = source;
        }
        return applied;
    }

    /** Damage plus knockback away from the attacker position when the hit landed. */
    public boolean hurtBy(float amount, DamageSource source, Vector3f attackerPos, float knockbackStrength) {
        boolean applied = damage(amount, source);
        if (applied && knockbackStrength > 0f) applyKnockback(attackerPos.x, attackerPos.z, knockbackStrength);
        return applied;
    }

    /** Natural armor points (zombie: 2); toughness stays 0 for every mob here. */
    protected float armorPoints() {
        return 0f;
    }

    protected float armorToughness() {
        return 0f;
    }

    /** Experience granted to a player who kills this mob. */
    public int xpReward(java.util.Random random) {
        return 0;
    }

    /** Fall damage: 1 HP per block beyond 3 (chickens override to be immune). */
    @Override
    protected void onLanded(float distance) {
        if (fallImmune() || !isAlive()) return;
        int dmg = (int) Math.ceil(Math.max(0f, distance - 3f));
        if (dmg > 0) damage(dmg, DamageSource.FALL);
    }

    protected boolean fallImmune() {
        return false;
    }

    public void heal(float amount) {
        health.heal(amount);
    }

    /** Advances the health cooldown and hurt-flash timer - call once per tick regardless of AI
     *  state (mirrors {@code HealthComponent.tick}'s own "always tick, dead or not" contract). */
    protected void tickHealth(float dt) {
        health.tick(dt);
        if (hurtTime > 0f) hurtTime = Math.max(0f, hurtTime - dt);
        if (recentHurtTimer > 0f) recentHurtTimer = Math.max(0f, recentHurtTimer - dt);
        if (health.isDead()) {
            kill();
            deathTime += dt;
        }
    }
}
