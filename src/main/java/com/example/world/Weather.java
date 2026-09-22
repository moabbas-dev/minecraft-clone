package com.example.world;

import java.util.Random;

/**
 * Vanilla's actual weather model: two flags (raining, thundering - thundering implies
 * raining), cycling between randomized clear and rain/storm periods.
 */
public class Weather {
    public enum State {CLEAR, RAIN, THUNDER}

    private static final float MIN_CLEAR_SECONDS = 90f;
    private static final float MAX_CLEAR_SECONDS = 240f;
    private static final float MIN_RAIN_SECONDS = 60f;
    private static final float MAX_RAIN_SECONDS = 150f;
    private static final float THUNDER_CHANCE = 0.35f;
    private static final float LIGHTNING_MIN_INTERVAL = 4f;
    private static final float LIGHTNING_MAX_INTERVAL = 18f;
    private static final float FLASH_DECAY_PER_SECOND = 2.5f;

    private final Random random;
    private State state = State.CLEAR;
    private float timeRemaining;
    private float lightningTimer;
    private float lightningFlash = 0f;

    public Weather(long seed) {
        this.random = new Random(seed ^ 0x5DEECE66DL);
        timeRemaining = randomBetween(MIN_CLEAR_SECONDS, MAX_CLEAR_SECONDS);
        lightningTimer = randomBetween(LIGHTNING_MIN_INTERVAL, LIGHTNING_MAX_INTERVAL);
    }

    public void update(float dt) {
        timeRemaining -= dt;
        if (timeRemaining <= 0) {
            advanceState();
        }
        if (state == State.THUNDER) {
            lightningTimer -= dt;
            if (lightningTimer <= 0) {
                lightningFlash = 1f;
                lightningTimer = randomBetween(LIGHTNING_MIN_INTERVAL, LIGHTNING_MAX_INTERVAL);
            }
        }
        lightningFlash = Math.max(0f, lightningFlash - dt * FLASH_DECAY_PER_SECOND);
    }

    private void advanceState() {
        if (state == State.CLEAR) {
            state = random.nextFloat() < THUNDER_CHANCE ? State.THUNDER : State.RAIN;
            timeRemaining = randomBetween(MIN_RAIN_SECONDS, MAX_RAIN_SECONDS);
        } else {
            state = State.CLEAR;
            timeRemaining = randomBetween(MIN_CLEAR_SECONDS, MAX_CLEAR_SECONDS);
        }
    }

    private float randomBetween(float min, float max) {
        return min + random.nextFloat() * (max - min);
    }

    public State getState() {
        return state;
    }

    public boolean isRaining() {
        return state == State.RAIN || state == State.THUNDER;
    }

    public boolean isThundering() {
        return state == State.THUNDER;
    }

    /** 0..1, spikes to 1 on a lightning strike and decays back to 0 - drive a screen flash with it. */
    public float getLightningFlash() {
        return lightningFlash;
    }
}
