package com.example.physics;

import com.example.engine.InputState;
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
    /**
     * Adapted for this project's own 1.8-tall standing box - not a verified exact vanilla
     * number (vanilla's swim-pose hitbox is a similarly reduced, roughly cube-shaped box, but
     * this project picks its own constant sized to its own scale rather than copying a number
     * that assumes vanilla's exact standing height).
     */
    private static final float SWIMMING_HEIGHT = 0.6f;
    private static final float SWIMMING_EYE_HEIGHT = 0.45f;
    private static final float EYE_HEIGHT_LERP_SPEED = 12f;
    private static final float HITBOX_HEIGHT_LERP_SPEED = 8f;

    private static final float GRAVITY = 28f;
    private static final float TERMINAL_VELOCITY = -60f;
    private static final float JUMP_VELOCITY = 8.6f;
    private static final float WALK_SPEED = 4.3f;
    private static final float SPRINT_MULTIPLIER = 1.6f;
    private static final float SNEAK_MULTIPLIER = 0.3f;
    private static final float FLY_SPEED = 12f;
    private static final float MOUSE_SENSITIVITY = 0.0025f;
    private static final float DOUBLE_TAP_WINDOW = 0.3f;
    /** How fast actual movement speed eases toward its target when sprint/sneak/swim state
     *  changes, so the transition feels like a smooth acceleration instead of an instant snap. */
    private static final float SPEED_LERP_RATE = 9f;

    // View bob: a subtle camera sway synced to footsteps while walking on the ground.
    private static final float BOB_FREQUENCY = 1.4f;
    private static final float BASE_BOB_AMPLITUDE = 0.045f;
    private static final float SPRINT_BOB_AMPLITUDE = 0.085f;
    private static final float SNEAK_BOB_AMPLITUDE = 0.02f;
    private static final float BOB_AMPLITUDE_LERP_RATE = 9f;

    // Swimming: adapted relative to this project's own WALK_SPEED/SPRINT_MULTIPLIER, not a
    // claim of exact vanilla values - swimming is deliberately slower than walking at the
    // surface (matches the general vanilla feel of water being sluggish) but sprint-swimming
    // still meaningfully outpaces plain swimming, same relationship as land sprint vs walk.
    private static final float SWIM_SPEED = 2.8f;
    private static final float SWIM_SPRINT_MULTIPLIER = 1.4f;
    // Horizontal drag: unlike land movement (which tracks input directly, no coasting), water
    // eases the actual horizontal velocity toward the input direction instead of snapping to
    // it - this is what "water drag" means concretely here, without inventing a new physics
    // model, and is the one deliberate feel difference from just being slow flying.
    private static final float SWIM_HORIZONTAL_ACCEL = 5f;
    // Eased acceleration toward a target vertical speed rather than an instant velocity snap,
    // so motion in water feels buoyant instead of jumpy.
    private static final float SWIM_UP_SPEED = 2.6f;
    private static final float SWIM_DOWN_SPEED = 2.2f;
    private static final float BUOYANCY_DRIFT_SPEED = 0.9f;
    private static final float WATER_VERTICAL_ACCEL = 10f;
    /** Cap on upward speed carried out of water so surfacing never launches the player like a jump. */
    private static final float SURFACE_EXIT_SPEED_CAP = 0.8f;
    /** How far above the feet to also sample for water, so partial submersion (not just feet-
     *  exactly-in-water) still counts as swimming - generous on purpose, see the hysteresis
     *  note on {@link #updateWaterState}. */
    private static final float SWIM_ENTRY_SAMPLE_HEIGHT = 0.4f;

    /** Feet position (bottom-center of the hitbox). */
    public final Vector3f position;
    private float yaw = (float) Math.toRadians(-90); // facing -Z initially
    private float pitch = 0f;
    private float verticalVelocity = 0f;
    private boolean onGround = false;
    private boolean flying = false;
    private boolean wasInWater = false;
    /** Feet in water (possibly only partially submerged) - see {@link #updateWaterState}. */
    private boolean swimming = false;
    /** Eye/camera position specifically inside a water block - fully submerged. */
    private boolean headSubmerged = false;
    private double lastSpacePressTime = -10;
    private double lastWPressTime = -10;
    private boolean sprintToggled = false;
    private boolean sprinting = false;
    private float currentEyeHeight = EYE_HEIGHT;
    private float currentHitboxHeight = HEIGHT;
    private float currentSpeed = 0f;
    private float horizontalVelX = 0f, horizontalVelZ = 0f;
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

    public boolean isSwimming() {
        return swimming;
    }

    public boolean isHeadSubmerged() {
        return headSubmerged;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    /** Test/controller-only: instantly relocates the player, clearing residual fall/swim
     *  velocity so the teleport doesn't inherit momentum from wherever it was before. */
    public void teleport(float x, float y, float z) {
        position.set(x, y, z);
        verticalVelocity = 0f;
        horizontalVelX = 0f;
        horizontalVelZ = 0f;
    }

    /** Test/controller-only: same clamp {@link #look} applies, but settable directly rather
     *  than as a mouse-delta nudge. */
    public void setYaw(float radians) {
        this.yaw = radians;
    }

    public void setPitch(float radians) {
        float limit = (float) Math.toRadians(89);
        this.pitch = Math.max(-limit, Math.min(limit, radians));
    }

    /** Current eased collision height - standing, swimming, or somewhere in between mid-transition. */
    public float currentHitboxHeight() {
        return currentHitboxHeight;
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

    public void update(float dt, InputState input, World world, double now) {
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

        updateWaterState(world);
        // Flying overrides water physics entirely (matches the pre-existing precedent of
        // flying already overriding sneaking) - swimming/headSubmerged themselves still
        // reflect the raw water-detection state for anything that doesn't care about flying.
        boolean waterActive = swimming && !flying;

        float targetEyeHeight = waterActive ? SWIMMING_EYE_HEIGHT : sneaking ? SNEAK_EYE_HEIGHT : EYE_HEIGHT;
        currentEyeHeight += (targetEyeHeight - currentEyeHeight) * Math.min(1f, EYE_HEIGHT_LERP_SPEED * dt);

        // Only probe for standing headroom when actually trying to grow back toward full
        // height - if already standing, there's nothing to check, so this adds zero per-tick
        // cost to ordinary land movement away from any water.
        boolean canStand = waterActive || currentHitboxHeight >= HEIGHT - 0.001f || hasHeadroomForStanding(world);
        float targetHitboxHeight = (waterActive || !canStand) ? SWIMMING_HEIGHT : HEIGHT;
        currentHitboxHeight += (targetHitboxHeight - currentHitboxHeight) * Math.min(1f, HITBOX_HEIGHT_LERP_SPEED * dt);

        // Ease actual speed toward its target rather than snapping instantly, so starting or
        // stopping a sprint (or entering/leaving water) feels like a smooth acceleration -
        // matches vanilla's feel instead of an instant/robotic speed change. Swimming uses its
        // own base speed rather than a multiplier on WALK_SPEED (adapted, not vanilla-exact -
        // see the constant javadocs), so this eases the actual speed value directly instead of
        // a multiplier, avoiding a discontinuity at the moment the base speed itself would
        // otherwise switch.
        float targetSpeed = waterActive
                ? SWIM_SPEED * (sprinting ? SWIM_SPRINT_MULTIPLIER : 1f)
                : WALK_SPEED * (sprinting ? SPRINT_MULTIPLIER : 1f) * (sneaking ? SNEAK_MULTIPLIER : 1f);
        currentSpeed += (targetSpeed - currentSpeed) * Math.min(1f, SPEED_LERP_RATE * dt);
        float speed = currentSpeed;

        boolean movingOnGround = wish.lengthSquared() > 1e-6f && onGround && !flying;
        if (movingOnGround) {
            bobPhase += speed * dt * BOB_FREQUENCY;
            float targetBobAmplitude = sprinting ? SPRINT_BOB_AMPLITUDE : (sneaking ? SNEAK_BOB_AMPLITUDE : BASE_BOB_AMPLITUDE);
            bobAmplitude += (targetBobAmplitude - bobAmplitude) * Math.min(1f, BOB_AMPLITUDE_LERP_RATE * dt);
        } else {
            bobAmplitude += (0f - bobAmplitude) * Math.min(1f, (BOB_AMPLITUDE_LERP_RATE * 0.6f) * dt);
        }

        float dx, dz;
        if (waterActive) {
            // Water drag: eases horizontal velocity toward the input direction instead of
            // tracking it instantly (unlike land movement, which has always snapped directly
            // to wish*speed) - this is the concrete difference between swimming and slow
            // flying that the request asked for, without inventing a new physics model.
            horizontalVelX += (wish.x * speed - horizontalVelX) * Math.min(1f, SWIM_HORIZONTAL_ACCEL * dt);
            horizontalVelZ += (wish.z * speed - horizontalVelZ) * Math.min(1f, SWIM_HORIZONTAL_ACCEL * dt);
        } else {
            // No drag on land/flying - preserves the exact original instant-response feel.
            horizontalVelX = wish.x * speed;
            horizontalVelZ = wish.z * speed;
        }
        dx = horizontalVelX * dt;
        dz = horizontalVelZ * dt;
        float dy;

        if (flying) {
            float flyY = 0;
            if (input.isKeyDown(GLFW_KEY_SPACE)) flyY += 1;
            if (input.isKeyDown(GLFW_KEY_LEFT_SHIFT)) flyY -= 1;
            dy = flyY * FLY_SPEED * dt;
            verticalVelocity = 0;
            onGround = false;
        } else if (waterActive) {
            // Ease toward a target vertical speed instead of snapping, and never let the
            // player launch out of the water: holding space swims up to a capped speed,
            // holding shift swims down, idling drifts gently up (buoyancy) like vanilla, and -
            // the one real behavioral addition here - holding forward with no explicit
            // vertical key instead swims toward wherever the camera is actually looking
            // (front.y, the full pitch-aware direction), so looking up/down while swimming
            // forward actually rises/dives instead of only ever moving horizontally like
            // flying does. Explicit space/shift still take priority when held, for precise
            // control - this is a plain if/else, so these can never stack into an unbounded
            // combined speed.
            float target;
            if (input.isKeyDown(GLFW_KEY_SPACE)) target = SWIM_UP_SPEED;
            else if (sneaking) target = -SWIM_DOWN_SPEED;
            else if (wDown) target = front.y * speed;
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
            if (wasInWater && !input.isKeyDown(GLFW_KEY_SPACE)) {
                // Just surfaced and not actively still trying to rise: kill any leftover
                // swim-up momentum so breaking the surface doesn't fling the player into the
                // air like a jump. Gated on space specifically still being held (not just
                // "was swimming") - without this, a player deliberately holding space to climb
                // out onto a ledge or clear a deep pool got capped the instant their feet left
                // the water, then gravity immediately pulled them back down into it,
                // oscillating right at the surface line instead of ever actually climbing out.
                verticalVelocity = Math.min(verticalVelocity, SURFACE_EXIT_SPEED_CAP);
            }
            if (input.isKeyDown(GLFW_KEY_SPACE) && onGround) {
                verticalVelocity = JUMP_VELOCITY;
                onGround = false;
            }
            dy = verticalVelocity * dt;
        }
        wasInWater = waterActive;

        moveWithCollision(world, dx, dy, dz, sneaking);
    }

    /**
     * Sets {@link #swimming} (feet in or near water) and {@link #headSubmerged} (eye position
     * inside water). Uses a small hysteresis band rather than one exact-point check: entering
     * is generous (water at the feet OR a bit above counts), but leaving only happens once
     * neither the feet nor just below them still read as water - a single ambiguous reading
     * right at the surface line keeps the previous state instead of flipping, which is what
     * actually prevents flicker exactly at the boundary (the underlying block grid is discrete,
     * so a literal single-point check right on a boundary Y would otherwise be one floating-
     * point rounding away from toggling every tick).
     */
    private void updateWaterState(World world) {
        int bx = (int) Math.floor(position.x);
        int bz = (int) Math.floor(position.z);
        boolean atFeet = world.getBlock(bx, (int) Math.floor(position.y), bz) == BlockType.WATER;
        boolean aboveFeet = world.getBlock(bx, (int) Math.floor(position.y + SWIM_ENTRY_SAMPLE_HEIGHT), bz) == BlockType.WATER;
        if (atFeet || aboveFeet) {
            swimming = true;
        } else {
            boolean belowFeet = world.getBlock(bx, (int) Math.floor(position.y - 0.05f), bz) == BlockType.WATER;
            if (!belowFeet) swimming = false;
        }

        Vector3f eye = eyePosition();
        headSubmerged = world.getBlock((int) Math.floor(eye.x), (int) Math.floor(eye.y), (int) Math.floor(eye.z)) == BlockType.WATER;
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

    /** Is there enough headroom to stand at full height right now - checked before letting the
     *  eased hitbox target grow back from a reduced (swimming) height, so surfacing under a
     *  low ceiling can't clip the player into it. */
    private boolean hasHeadroomForStanding(World world) {
        return !intersectsSolidAt(world, position.x, position.y, position.z, HEIGHT);
    }

    private boolean intersectsSolid(World world, float x, float y, float z) {
        return intersectsSolidAt(world, x, y, z, currentHitboxHeight);
    }

    private boolean intersectsSolidAt(World world, float x, float y, float z, float height) {
        float half = WIDTH / 2f;
        int minX = (int) Math.floor(x - half);
        int maxX = (int) Math.floor(x + half);
        int minY = (int) Math.floor(y);
        int maxY = (int) Math.floor(y + height - 0.01f);
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
