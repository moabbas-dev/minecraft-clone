package com.example.physics;

import com.example.engine.Input;
import com.example.world.BlockType;
import com.example.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.glfw.GLFW.*;

/**
 * First-person controller: mouse look, WASD movement, gravity/jumping,
 * simple AABB-vs-voxel collision, and a creative-style double-space fly toggle.
 * Dimensions follow vanilla's player hitbox (0.6 wide, 1.8 tall, 1.62 eye height).
 */
public class Player {
    public static final float WIDTH = 0.6f;
    public static final float HEIGHT = 1.8f;
    public static final float EYE_HEIGHT = 1.62f;
    private static final float SNEAK_EYE_HEIGHT = 1.27f;
    private static final float EYE_HEIGHT_LERP_SPEED = 12f;

    private static final float GRAVITY = 28f;
    private static final float TERMINAL_VELOCITY = -60f;
    private static final float JUMP_VELOCITY = 8.6f;
    private static final float WALK_SPEED = 4.3f;
    private static final float SPRINT_MULTIPLIER = 1.6f;
    private static final float SNEAK_MULTIPLIER = 0.3f;
    private static final float SWIM_SPEED_MULTIPLIER = 0.6f;
    private static final float FLY_SPEED = 12f;
    private static final float MOUSE_SENSITIVITY = 0.0025f;
    private static final float DOUBLE_TAP_WINDOW = 0.3f;
    /** How fast actual movement speed eases toward its target when sprint/sneak toggles, so the
     *  transition feels like a smooth acceleration instead of an instant snap. */
    private static final float SPEED_LERP_RATE = 9f;

    // View bob: a subtle camera sway synced to footsteps while walking on the ground.
    private static final float BOB_FREQUENCY = 1.4f;
    private static final float BASE_BOB_AMPLITUDE = 0.045f;
    private static final float SPRINT_BOB_AMPLITUDE = 0.085f;
    private static final float SNEAK_BOB_AMPLITUDE = 0.02f;
    private static final float BOB_AMPLITUDE_LERP_RATE = 9f;

    // Swimming: eased acceleration toward a target vertical speed rather than an instant
    // velocity snap, so motion in water feels buoyant instead of jumpy.
    private static final float SWIM_UP_SPEED = 2.6f;
    private static final float SWIM_DOWN_SPEED = 2.2f;
    private static final float BUOYANCY_DRIFT_SPEED = 0.9f;
    private static final float WATER_VERTICAL_ACCEL = 10f;
    /** Cap on upward speed carried out of water so surfacing never launches the player like a jump. */
    private static final float SURFACE_EXIT_SPEED_CAP = 0.8f;

    /** Feet position (bottom-center of the hitbox). */
    public final Vector3f position;
    private float yaw = (float) Math.toRadians(-90); // facing -Z initially
    private float pitch = 0f;
    private float verticalVelocity = 0f;
    private boolean onGround = false;
    private boolean flying = false;
    private boolean wasInWater = false;
    private double lastSpacePressTime = -10;
    private double lastWPressTime = -10;
    private boolean sprintToggled = false;
    private boolean sprinting = false;
    private float currentEyeHeight = EYE_HEIGHT;
    private float currentSpeedMultiplier = 1f;
    private float bobPhase = 0f;
    private float bobAmplitude = 0f;

    public Player(Vector3f spawnPosition) {
        this.position = new Vector3f(spawnPosition);
    }

    public boolean isFlying() {
        return flying;
    }

    public boolean isSprinting() {
        return sprinting;
    }

    public float getBobPhase() {
        return bobPhase;
    }

    public float getBobAmplitude() {
        return bobAmplitude;
    }

    public void look(double dx, double dy) {
        yaw += (float) dx * MOUSE_SENSITIVITY;
        pitch -= (float) dy * MOUSE_SENSITIVITY;
        float limit = (float) Math.toRadians(89);
        pitch = Math.max(-limit, Math.min(limit, pitch));
    }

    public Vector3f frontVector() {
        float x = (float) (Math.cos(pitch) * Math.sin(yaw));
        float y = (float) Math.sin(pitch);
        float z = (float) (-Math.cos(pitch) * Math.cos(yaw));
        return new Vector3f(x, y, z).normalize();
    }

    public Matrix4f viewMatrix() {
        Vector3f eye = renderEyePosition();
        Vector3f front = frontVector();
        Vector3f center = new Vector3f(eye).add(front);
        return new Matrix4f().lookAt(eye, center, new Vector3f(0, 1, 0));
    }

    /** True logical eye position - used for raycasting/game logic, never includes view bob. */
    public Vector3f eyePosition() {
        return new Vector3f(position.x, position.y + currentEyeHeight, position.z);
    }

    /**
     * Eye position including the view-bob sway, for rendering only (camera + anything that
     * should visually track the bob, like the held-item view model). Kept separate from
     * {@link #eyePosition()} so bob never introduces jitter into block targeting.
     */
    public Vector3f renderEyePosition() {
        Vector3f base = eyePosition();
        if (bobAmplitude > 0.0005f) {
            Vector3f front = frontVector();
            Vector3f right = new Vector3f(-front.z, 0, front.x);
            if (right.lengthSquared() > 1e-6f) right.normalize();
            else right.set(1, 0, 0);
            float sway = (float) Math.sin(bobPhase);
            float bob = (float) Math.abs(Math.sin(bobPhase));
            base.add(new Vector3f(right).mul(sway * bobAmplitude * 0.5f));
            base.y += bob * bobAmplitude - bobAmplitude * 0.5f;
        }
        return base;
    }

    public void update(float dt, Input input, World world, double now) {
        if (input.consumeKeyPressed(GLFW_KEY_SPACE)) {
            if (now - lastSpacePressTime < DOUBLE_TAP_WINDOW) {
                flying = !flying;
                verticalVelocity = 0;
            }
            lastSpacePressTime = now;
        }

        Vector3f front = frontVector();
        Vector3f forwardFlat = new Vector3f(front.x, 0, front.z);
        if (forwardFlat.lengthSquared() < 1e-6f) forwardFlat.set(0, 0, -1);
        forwardFlat.normalize();
        // cross(front, worldUp) = (-front.z, 0, front.x); this was previously negated, which
        // swapped A and D (e.g. facing -Z, true right is +X, but the old formula gave -X).
        Vector3f right = new Vector3f(-front.z, 0, front.x).normalize();

        Vector3f wish = new Vector3f();
        if (input.isKeyDown(GLFW_KEY_W)) wish.add(forwardFlat);
        if (input.isKeyDown(GLFW_KEY_S)) wish.sub(forwardFlat);
        if (input.isKeyDown(GLFW_KEY_D)) wish.add(right);
        if (input.isKeyDown(GLFW_KEY_A)) wish.sub(right);
        if (wish.lengthSquared() > 1e-6f) wish.normalize();

        boolean wDown = input.isKeyDown(GLFW_KEY_W);
        if (input.consumeKeyPressed(GLFW_KEY_W)) {
            if (now - lastWPressTime < DOUBLE_TAP_WINDOW) {
                sprintToggled = true;
            }
            lastWPressTime = now;
        }

        boolean sneaking = input.isKeyDown(GLFW_KEY_LEFT_SHIFT) && !flying;
        // Sprint stops the moment you're not actively holding W forward, or start sneaking -
        // matches vanilla (double-tap W to start, release W or sneak to stop). Ctrl still works
        // as a direct hold-to-sprint alternative alongside the double-tap toggle.
        if (!wDown || sneaking) sprintToggled = false;
        sprinting = !sneaking && (sprintToggled || input.isKeyDown(GLFW_KEY_LEFT_CONTROL));

        boolean inWater = world.getBlock(
                (int) Math.floor(position.x), (int) Math.floor(position.y + 0.5f), (int) Math.floor(position.z))
                == BlockType.WATER;

        float targetEyeHeight = sneaking ? SNEAK_EYE_HEIGHT : EYE_HEIGHT;
        currentEyeHeight += (targetEyeHeight - currentEyeHeight) * Math.min(1f, EYE_HEIGHT_LERP_SPEED * dt);

        // Ease actual speed toward its target rather than snapping instantly, so starting or
        // stopping a sprint feels like a smooth acceleration - matches vanilla's feel instead
        // of an instant/robotic speed change.
        float targetMultiplier = sprinting ? SPRINT_MULTIPLIER : 1f;
        if (sneaking) targetMultiplier *= SNEAK_MULTIPLIER;
        if (inWater && !flying) targetMultiplier *= SWIM_SPEED_MULTIPLIER;
        currentSpeedMultiplier += (targetMultiplier - currentSpeedMultiplier) * Math.min(1f, SPEED_LERP_RATE * dt);
        float speed = WALK_SPEED * currentSpeedMultiplier;

        boolean movingOnGround = wish.lengthSquared() > 1e-6f && onGround && !flying;
        if (movingOnGround) {
            bobPhase += speed * dt * BOB_FREQUENCY;
            float targetBobAmplitude = sprinting ? SPRINT_BOB_AMPLITUDE : (sneaking ? SNEAK_BOB_AMPLITUDE : BASE_BOB_AMPLITUDE);
            bobAmplitude += (targetBobAmplitude - bobAmplitude) * Math.min(1f, BOB_AMPLITUDE_LERP_RATE * dt);
        } else {
            bobAmplitude += (0f - bobAmplitude) * Math.min(1f, (BOB_AMPLITUDE_LERP_RATE * 0.6f) * dt);
        }

        float dx = wish.x * speed * dt;
        float dz = wish.z * speed * dt;
        float dy;

        if (flying) {
            float flyY = 0;
            if (input.isKeyDown(GLFW_KEY_SPACE)) flyY += 1;
            if (input.isKeyDown(GLFW_KEY_LEFT_SHIFT)) flyY -= 1;
            dy = flyY * FLY_SPEED * dt;
            verticalVelocity = 0;
            onGround = false;
        } else if (inWater) {
            // Ease toward a target vertical speed instead of snapping, and never let the
            // player launch out of the water: holding space swims up to a capped speed,
            // holding shift swims down, and idling drifts gently up (buoyancy) like vanilla.
            float target;
            if (input.isKeyDown(GLFW_KEY_SPACE)) target = SWIM_UP_SPEED;
            else if (sneaking) target = -SWIM_DOWN_SPEED;
            else target = BUOYANCY_DRIFT_SPEED;

            if (verticalVelocity < target) {
                verticalVelocity = Math.min(target, verticalVelocity + WATER_VERTICAL_ACCEL * dt);
            } else {
                verticalVelocity = Math.max(target, verticalVelocity - WATER_VERTICAL_ACCEL * dt);
            }
            dy = verticalVelocity * dt;
        } else {
            verticalVelocity -= GRAVITY * dt;
            verticalVelocity = Math.max(verticalVelocity, TERMINAL_VELOCITY);
            if (wasInWater) {
                // Just surfaced: kill any leftover swim-up momentum so breaking the surface
                // doesn't fling the player into the air like a jump.
                verticalVelocity = Math.min(verticalVelocity, SURFACE_EXIT_SPEED_CAP);
            }
            if (input.isKeyDown(GLFW_KEY_SPACE) && onGround) {
                verticalVelocity = JUMP_VELOCITY;
                onGround = false;
            }
            dy = verticalVelocity * dt;
        }
        wasInWater = inWater;

        moveWithCollision(world, dx, dy, dz, sneaking);
    }

    private void moveWithCollision(World world, float dx, float dy, float dz, boolean sneaking) {
        // While sneaking on the ground, don't step off an edge with nothing underneath -
        // matches vanilla's ledge safety.
        boolean edgeSafe = sneaking && onGround;
        if (dx != 0) {
            float newX = position.x + dx;
            boolean blocked = intersectsSolid(world, newX, position.y, position.z)
                    || (edgeSafe && !hasGroundBelow(world, newX, position.z));
            if (!blocked) {
                position.x = newX;
            }
        }
        if (dz != 0) {
            float newZ = position.z + dz;
            boolean blocked = intersectsSolid(world, position.x, position.y, newZ)
                    || (edgeSafe && !hasGroundBelow(world, position.x, newZ));
            if (!blocked) {
                position.z = newZ;
            }
        }
        if (dy != 0) {
            float newY = position.y + dy;
            if (!intersectsSolid(world, position.x, newY, position.z)) {
                position.y = newY;
                onGround = false;
            } else {
                if (dy < 0) onGround = true;
                verticalVelocity = 0;
            }
        }
    }

    /** Is there a block within a hair's width below the feet at this (x,z)? Used for sneak edge-safety. */
    private boolean hasGroundBelow(World world, float x, float z) {
        float half = WIDTH / 2f;
        int minX = (int) Math.floor(x - half);
        int maxX = (int) Math.floor(x + half);
        int minZ = (int) Math.floor(z - half);
        int maxZ = (int) Math.floor(z + half);
        int by = (int) Math.floor(position.y - 0.05f);
        for (int bx = minX; bx <= maxX; bx++) {
            for (int bz = minZ; bz <= maxZ; bz++) {
                if (world.isSolidCollision(bx, by, bz)) return true;
            }
        }
        return false;
    }

    private boolean intersectsSolid(World world, float x, float y, float z) {
        float half = WIDTH / 2f;
        int minX = (int) Math.floor(x - half);
        int maxX = (int) Math.floor(x + half);
        int minY = (int) Math.floor(y);
        int maxY = (int) Math.floor(y + HEIGHT - 0.01f);
        int minZ = (int) Math.floor(z - half);
        int maxZ = (int) Math.floor(z + half);
        for (int bx = minX; bx <= maxX; bx++) {
            for (int by = minY; by <= maxY; by++) {
                for (int bz = minZ; bz <= maxZ; bz++) {
                    if (world.isSolidCollision(bx, by, bz)) return true;
                }
            }
        }
        return false;
    }
}
