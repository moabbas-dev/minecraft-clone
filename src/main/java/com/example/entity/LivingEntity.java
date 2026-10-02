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
    private static final float HURT_FLASH_SECONDS = 0.3f;

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
        boolean applied = health.damage(amount, source);
        if (applied) hurtTime = HURT_FLASH_SECONDS;
        return applied;
    }

    public void heal(float amount) {
        health.heal(amount);
    }

    /** Advances the health cooldown and hurt-flash timer - call once per tick regardless of AI
     *  state (mirrors {@code HealthComponent.tick}'s own "always tick, dead or not" contract). */
    protected void tickHealth(float dt) {
        health.tick(dt);
        if (hurtTime > 0f) hurtTime = Math.max(0f, hurtTime - dt);
        if (health.isDead()) kill();
    }
}
