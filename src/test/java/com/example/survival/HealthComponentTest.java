package com.example.survival;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HealthComponentTest {

    @Test
    void startsAtFullHealthAliveWithNoCooldown() {
        HealthComponent h = new HealthComponent();
        assertEquals(20f, h.getMaxHealth());
        assertEquals(20f, h.getCurrentHealth());
        assertTrue(h.isAlive());
        assertFalse(h.isDead());
        assertEquals(0f, h.hurtCooldownRemaining());
    }

    @Test
    void damageReducesHealthAndHealingRestoresItWithoutExceedingMax() {
        HealthComponent h = new HealthComponent();
        assertTrue(h.damage(5f, DamageSource.GENERIC));
        assertEquals(15f, h.getCurrentHealth());
        h.tick(HealthComponent.HURT_COOLDOWN_SECONDS + 0.01f); // clear the cooldown so heal-then-verify-cap isn't confused by it
        h.heal(100f);
        assertEquals(20f, h.getCurrentHealth(), "healing must never exceed max health");
    }

    @Test
    void healthNeverGoesNegative() {
        HealthComponent h = new HealthComponent();
        assertTrue(h.damage(999f, DamageSource.GENERIC));
        assertEquals(0f, h.getCurrentHealth());
    }

    @Test
    void enoughDamageKillsAndDeathIsObservable() {
        HealthComponent h = new HealthComponent();
        h.damage(20f, DamageSource.GENERIC);
        assertEquals(0f, h.getCurrentHealth());
        assertTrue(h.isDead());
        assertFalse(h.isAlive());
    }

    @Test
    void aDeadEntityTakesNoFurtherDamageOrHealing() {
        HealthComponent h = new HealthComponent();
        h.damage(20f, DamageSource.GENERIC);
        h.tick(10f); // well past any cooldown
        assertFalse(h.damage(5f, DamageSource.GENERIC), "already dead");
        assertEquals(0f, h.getCurrentHealth());
        h.heal(10f);
        assertEquals(0f, h.getCurrentHealth(), "healing a corpse does nothing");
    }

    @Test
    void repeatedDamageEveryFrameIsBlockedByTheHurtCooldown() {
        HealthComponent h = new HealthComponent();
        assertTrue(h.damage(1f, DamageSource.GENERIC));
        for (int i = 0; i < 10; i++) {
            assertFalse(h.damage(1f, DamageSource.GENERIC), "still on cooldown, tick " + i);
        }
        assertEquals(19f, h.getCurrentHealth(), "none of the blocked calls should have landed");
    }

    @Test
    void damageWorksAgainOnceTheCooldownExpires() {
        HealthComponent h = new HealthComponent();
        h.damage(1f, DamageSource.GENERIC);
        float dt = 0.05f;
        int ticks = (int) Math.ceil(HealthComponent.HURT_COOLDOWN_SECONDS / dt) + 1;
        for (int i = 0; i < ticks; i++) h.tick(dt);
        assertEquals(0f, h.hurtCooldownRemaining());
        assertTrue(h.damage(1f, DamageSource.GENERIC), "cooldown must have expired by now");
        assertEquals(18f, h.getCurrentHealth());
    }

    @Test
    void theCooldownIsTimeBasedNotFrameBased() {
        // The same total elapsed real time, spread across very different frame counts (very
        // different dt per call), must produce the same outcome - the whole point of driving
        // this off dt rather than a frame counter.
        HealthComponent slow30fps = new HealthComponent();
        HealthComponent fast144fps = new HealthComponent();
        slow30fps.damage(1f, DamageSource.GENERIC);
        fast144fps.damage(1f, DamageSource.GENERIC);

        float totalTime = HealthComponent.HURT_COOLDOWN_SECONDS + 0.1f;
        float dt30 = 1f / 30f;
        for (float t = 0; t < totalTime; t += dt30) slow30fps.tick(dt30);
        float dt144 = 1f / 144f;
        for (float t = 0; t < totalTime; t += dt144) fast144fps.tick(dt144);

        assertEquals(0f, slow30fps.hurtCooldownRemaining());
        assertEquals(0f, fast144fps.hurtCooldownRemaining());
        assertTrue(slow30fps.damage(1f, DamageSource.GENERIC));
        assertTrue(fast144fps.damage(1f, DamageSource.GENERIC));
    }

    @Test
    void nonPositiveDamageIsRejected() {
        HealthComponent h = new HealthComponent();
        assertFalse(h.damage(0f, DamageSource.GENERIC));
        assertFalse(h.damage(-5f, DamageSource.GENERIC));
        assertEquals(20f, h.getCurrentHealth());
    }

    @Test
    void setHealthClampsAndCanTriggerOrClearDeath() {
        HealthComponent h = new HealthComponent();
        h.setHealth(-5f);
        assertEquals(0f, h.getCurrentHealth());
        assertTrue(h.isDead());
        h.setHealth(999f);
        assertEquals(20f, h.getCurrentHealth(), "setHealth clamps to max even after death");
    }

    @Test
    void resetFullyRevivesClearsCooldownAndDeath() {
        HealthComponent h = new HealthComponent();
        h.damage(20f, DamageSource.GENERIC);
        assertTrue(h.isDead());
        h.reset();
        assertFalse(h.isDead());
        assertEquals(20f, h.getCurrentHealth());
        assertEquals(0f, h.hurtCooldownRemaining());
    }

    @Test
    void aCustomMaxHealthIsRespected() {
        HealthComponent h = new HealthComponent(10f);
        assertEquals(10f, h.getMaxHealth());
        assertEquals(10f, h.getCurrentHealth());
        h.heal(100f);
        assertEquals(10f, h.getCurrentHealth());
    }
}
