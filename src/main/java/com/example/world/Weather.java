package com.example.world;

import lombok.Getter;

import java.util.Random;

/**
 * Vanilla's actual weather model: two flags (raining, thundering - thundering implies
 * raining), cycling between randomized clear and rain/storm periods.
 */
public class Weather {
    public enum State {CLEAR, RAIN, THUNDER}

    private static final float MIN_CLEAR_SECONDS = 600f; // vanilla 12000-180000 ticks
    private static final float MAX_CLEAR_SECONDS = 9000f;
    private static final float MIN_RAIN_SECONDS = 600f; // vanilla rain 12000-24000 ticks
    private static final float MAX_RAIN_SECONDS = 1200f;
    private static final float THUNDER_CHANCE = 0.35f;
    private static final float LIGHTNING_MIN_INTERVAL = 4f;
    private static final float LIGHTNING_MAX_INTERVAL = 18f;
    private static final float FLASH_DECAY_PER_SECOND = 2.5f;
    /** How fast stormIntensity eases toward its target - same X += (target-X)*min(1,RATE*dt)
     *  idiom used everywhere else in this codebase (Player's speed/eye-height/bob easing,
     *  Game's FOV easing). Reaches ~86% of the way in about 5 seconds. */
    private static final float STORM_INTENSITY_RATE = 0.4f;

    private final Random random;
    @Getter
    private State state = State.CLEAR;
    private float timeRemaining;
    private float lightningTimer;
    /** 0..1, spikes to 1 on a lightning strike and decays back to 0 - drive a screen flash with it. */
    @Getter
    private float lightningFlash = 0f;
    /** Seconds the sky stays lit: a lit bolt sets ClientLevel.skyFlashTime to 2 ticks every tick it is alight. */
    private float skyFlashRemaining;
    private static final float SKY_FLASH_SECONDS = 0.1f;
    /**
     * 0..1, eased toward 1 while raining/thundering and toward 0 otherwise - unlike
     * {@link #isRaining()} (an instant flag flip the moment the state timer expires), this fades
     * smoothly, so anything driven by it (sky/fog blending in Game.renderWorld) doesn't snap the
     * instant a storm starts or ends. Use this instead of isRaining() for anything visual.
     */
    @Getter
    private float stormIntensity = 0f;

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
        if (skyFlashRemaining > 0f) skyFlashRemaining -= dt;
        lightningFlash = Math.max(0f, lightningFlash - dt * FLASH_DECAY_PER_SECOND);

        float targetIntensity = isRaining() ? 1f : 0f;
        stormIntensity += (targetIntensity - stormIntensity) * Math.min(1f, STORM_INTENSITY_RATE * dt);
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

    /** True while a lightning flash lights the sky (its colour and the lightmap's sky factor jump for a few ticks). */
    public boolean isSkyFlashing() {
        return skyFlashRemaining > 0f;
    }

    /** A bolt struck: the thunder event (what {@link #getLightningFlash} reports for a moment, which the audio reads). */
    public void strike() {
        lightningFlash = 1f;
    }

    /** A bolt is lit this tick: the sky stays flashed for 2 more ticks. */
    public void flashSky() {
        skyFlashRemaining = SKY_FLASH_SECONDS;
    }

    public boolean isRaining() {
        return state == State.RAIN || state == State.THUNDER;
    }

    public boolean isThundering() {
        return state == State.THUNDER;
    }

    /** Test/controller-only: immediately forces the weather state, bypassing the normal
     *  timer-driven cycle. Deliberately does NOT snap stormIntensity - it still eases toward
     *  the new state's target on the next update() the same as an organic transition, so this
     *  can't be used to fake an instant fog/sky change either. */
    public void forceState(State newState) {
        this.state = newState;
        this.timeRemaining = newState == State.CLEAR
                ? randomBetween(MIN_CLEAR_SECONDS, MAX_CLEAR_SECONDS)
                : randomBetween(MIN_RAIN_SECONDS, MAX_RAIN_SECONDS);
    }
}
