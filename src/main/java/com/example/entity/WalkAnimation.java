package com.example.entity;

/**
 * Vanilla's WalkAnimationState: each 20 Hz tick the speed eases toward min(4 * distance moved, 1) by 0.4 and the position
 * advances by that speed (babies advance 3 times as fast). The model's legs swing by cos(position * 0.6662) * 1.4 * speed.
 */
public final class WalkAnimation {
    private static final float TICK = 0.05f;
    private float speedOld, speed, position, positionScale = 1f;
    private float clock;

    /** Advances by {@code dt} seconds, during which the mob moved {@code moved} blocks horizontally. */
    public void advance(float dt, float moved, boolean baby) {
        clock += dt;
        int ticks = 0;
        while (clock >= TICK && ticks < 5) {
            clock -= TICK;
            ticks++;
        }
        if (clock >= TICK) clock = 0f;
        float perTick = ticks == 0 ? 0f : moved / ticks;
        for (int i = 0; i < ticks; i++) {
            speedOld = speed;
            speed += (Math.min(perTick * 4f, 1f) - speed) * 0.4f;
            position += speed;
            positionScale = baby ? 3f : 1f;
        }
    }

    /** A dead mob's legs stop (vanilla stop()). */
    public void stop() {
        speedOld = 0f;
        speed = 0f;
        position = 0f;
    }

    /** The limb position at the current partial tick: (position - speed * (1 - partial)) * scale. */
    public float position() {
        float partial = clock / TICK;
        return (position - speed * (1f - partial)) * positionScale;
    }

    /** The limb speed at the current partial tick, at most 1. */
    public float speed() {
        float partial = clock / TICK;
        return Math.min(speedOld + (speed - speedOld) * partial, 1f);
    }
}
