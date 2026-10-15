package com.example.entity;

/**
 * A mob's head and body turning, following vanilla's LookControl and BodyRotationControl (rules read from the 26.3 client).
 * It runs in 20 Hz ticks; angles are radians in the entity yaw convention (0 = facing -Z, increasing clockwise from above).
 *
 * <p>Each tick: the head turns toward the look target by at most 10 degrees (its pitch by at most 40) while a look request is
 * active (a request lasts 2 ticks), else back toward the body; while walking it stays within 75 degrees of the body. The body
 * follows the movement heading while moving; when standing still it only turns once the head is more than 75 degrees away,
 * and after the head has held still for 10 ticks it creeps back so that head and body line up again over the next 10 ticks.
 */
public final class HeadControl {
    private static final float DEG = (float) Math.PI / 180f;
    static final float HEAD_SPEED = 10f * DEG;
    static final float MAX_HEAD_PITCH_STEP = 40f * DEG;
    static final float MAX_HEAD_YAW = 75f * DEG;
    static final float STABLE_ANGLE = 15f * DEG;
    static final float TICK = 0.05f;

    private boolean started;
    private float body, head, pitch;
    private float oldBody, oldHead, oldPitch;
    private float lastStableHead;
    private int stableTime;
    private int lookCooldown;
    private float lookX, lookY, lookZ;
    private float tickClock;

    /** Asks the head to look at a world point; vanilla keeps the request for 2 ticks, so goals repeat it every tick. */
    public void lookAt(float x, float y, float z) {
        lookX = x;
        lookY = y;
        lookZ = z;
        lookCooldown = 2;
    }

    /**
     * Advances by {@code dt} seconds.
     *
     * @param yaw      the mob's movement heading (vanilla yRot)
     * @param moved    horizontal distance covered during {@code dt}
     * @param pathing  whether the mob is walking toward something (the head is then kept near the body)
     * @param x,eyeY,z the mob's eye position
     */
    public void advance(float dt, float yaw, float moved, boolean pathing, float x, float eyeY, float z) {
        if (!started) {
            started = true;
            body = head = oldBody = oldHead = lastStableHead = yaw;
        }
        tickClock += dt;
        int ticks = 0;
        while (tickClock >= TICK && ticks < 5) {
            tickClock -= TICK;
            ticks++;
        }
        if (tickClock >= TICK) tickClock = 0f; // a long hitch: drop the backlog
        float perTick = ticks == 0 ? 0f : moved / ticks;
        for (int i = 0; i < ticks; i++) tick(yaw, perTick * perTick > 2.5e-7f, pathing, x, eyeY, z);
    }

    private void tick(float yaw, boolean moving, boolean pathing, float x, float eyeY, float z) {
        oldBody = body;
        oldHead = head;
        oldPitch = pitch;
        // LookControl.tick
        pitch = 0f;
        if (lookCooldown > 0) {
            lookCooldown--;
            float dx = lookX - x, dz = lookZ - z, dy = lookY - eyeY;
            if (Math.abs(dx) > 1e-5f || Math.abs(dz) > 1e-5f) {
                head = rotateTowards(head, (float) Math.atan2(dx, -dz), HEAD_SPEED);
            }
            float flat = (float) Math.sqrt(dx * dx + dz * dz);
            if (Math.abs(dy) > 1e-5f || flat > 1e-5f) {
                pitch = rotateTowards(pitch, -(float) Math.atan2(dy, flat), MAX_HEAD_PITCH_STEP);
            }
        } else {
            head = rotateTowards(head, body, HEAD_SPEED);
        }
        if (pathing) head = rotateIfNecessary(head, body, MAX_HEAD_YAW);
        // BodyRotationControl.clientTick
        if (moving) {
            body = yaw;
            head = rotateIfNecessary(head, body, MAX_HEAD_YAW);
            lastStableHead = head;
            stableTime = 0;
        } else if (Math.abs(wrap(head - lastStableHead)) > STABLE_ANGLE) {
            stableTime = 0;
            lastStableHead = head;
            body = rotateIfNecessary(body, head, MAX_HEAD_YAW);
        } else {
            stableTime++;
            if (stableTime > 10) {
                float f = Math.max(0f, Math.min(1f, (stableTime - 10) / 10f));
                body = rotateIfNecessary(body, head, MAX_HEAD_YAW * (1f - f));
            }
        }
    }

    /** The body's rendered yaw, interpolated between ticks; {@code fallback} before the first tick. */
    public float bodyYaw(float fallback) {
        return started ? lerpAngle(oldBody, body, tickClock / TICK) : fallback;
    }

    /** The head's turn relative to the body (vanilla netHeadYaw, positive = clockwise from above). */
    public float netHeadYaw() {
        if (!started) return 0f;
        float t = tickClock / TICK;
        return wrap(lerpAngle(oldHead, head, t) - lerpAngle(oldBody, body, t));
    }

    /** The head's pitch, positive = looking down. */
    public float headPitch() {
        return started ? oldPitch + (pitch - oldPitch) * (tickClock / TICK) : 0f;
    }

    static float wrap(float a) {
        float twoPi = (float) (Math.PI * 2);
        a = a % twoPi;
        if (a >= Math.PI) a -= twoPi;
        if (a < -Math.PI) a += twoPi;
        return a;
    }

    static float lerpAngle(float from, float to, float t) {
        return from + wrap(to - from) * t;
    }

    /** LookControl.rotateTowards: {@code from} moved toward {@code to} by at most {@code max}. */
    static float rotateTowards(float from, float to, float max) {
        float diff = wrap(to - from);
        return from + Math.max(-max, Math.min(max, diff));
    }

    /** Mth.rotateIfNecessary: {@code current} pulled to within {@code max} of {@code target}, on the side it is already on. */
    static float rotateIfNecessary(float current, float target, float max) {
        float d = wrap(target - current);
        float clamped = Math.max(-max, Math.min(max, d));
        return target - clamped;
    }
}
