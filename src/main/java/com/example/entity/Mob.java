package com.example.entity;

import com.example.entity.ai.GoalSelector;
import com.example.entity.navigation.Navigator;
import com.example.world.World;
import lombok.Getter;
import lombok.Setter;
import org.joml.Vector3f;

import java.util.List;

/**
 * A LivingEntity with AI: a target, a {@link Navigator} it can path with, and a
 * {@link GoalSelector} full of independent {@link com.example.entity.ai.Goal}s that decide what
 * it wants to do each tick. Concrete mobs (see {@code com.example.entity.mob.Zombie}) only add
 * stats and wire up which goals they run - none of this class, the goal system, or the
 * navigation system knows anything zombie-specific, so a future skeleton/spider/cow reuses all
 * of it unchanged.
 */
public abstract class Mob extends LivingEntity {
    @Getter
    private final GoalSelector goals = new GoalSelector();
    @Getter
    @Setter
    private Navigator navigator;
    /** Whatever this mob is currently trying to reach/attack - null means no valid target
     *  (the "idle" state: no goal that needs a target can start, so the mob just stands still). */
    @Getter
    @Setter
    private Targetable target;

    /** Per-mob random source for AI rolls (strolling, looking around, drops). */
    @Getter
    protected final java.util.Random random = new java.util.Random();
    /** Set by goals each tick (reset to 1 before they run): e.g. panic runs faster. */
    @Getter
    @Setter
    private float speedMultiplier = 1f;
    private boolean swimUpRequested;
    /** Duration of the current tick, for frame-rate independent AI rolls (see AiRolls). */
    @Getter
    private float lastDt;
    private static final float TURN_RATE = 12f; // radians per second

    /** One sound a mob wants played (resolved to a registry id like {@code mob.pig.say}); drained by MobManager. */
    public record SoundEvent(String id, float x, float y, float z) {
    }

    private final List<SoundEvent> pendingSounds = new java.util.ArrayList<>(2);
    private boolean wasAlive = true;
    private float lastHurtTime;
    private float ambientTicks;       // vanilla ambientSoundTime: counts up, a sound resets it to -80
    private float ambientAccumulator; // fractional game ticks carried between frames
    private float stepDistance;
    private static final float STEP_EVERY_BLOCKS = 0.9f;
    private static final float AMBIENT_INTERVAL_TICKS = 80f;

    private float moveDirX, moveDirZ;
    /** Animation bookkeeping only (renderer reads these): advances while moving. */
    @Getter
    private float walkPhase;
    @Getter
    private float walkAmount;
    /** Set by whatever goal wants to face something this tick (movement direction or a target
     *  while attacking); consumed once per tick by {@link #applyMovement}. NaN = no request. */
    private float desiredYaw = Float.NaN;

    protected Mob(Vector3f spawnPosition, float yaw, float maxHealth) {
        super(spawnPosition, yaw, maxHealth);
    }

    public abstract float getMoveSpeed();

    /** Combined sky/block light level (0-15) at this mob, set by MobManager (spiders are neutral in bright light). */
    @Getter
    @Setter
    private int lightLevel;
    /** Set when a mob is removed without loot or a death sound (an exploded creeper). */
    @Getter
    private boolean silentRemoval;
    private float explosionPower;

    /** Whether the mob makes the vanilla random ambient noise (creepers do not). */
    protected boolean makesAmbientSound() {
        return true;
    }

    /** An arrow (or other missile) a mob wants launched, handed to the projectile system by MobManager. */
    public record ProjectileRequest(Vector3f origin, Vector3f velocity, float damage) {
    }

    private final List<ProjectileRequest> pendingProjectiles = new java.util.ArrayList<>(1);

    public void launchProjectile(Vector3f origin, Vector3f velocity, float damage) {
        pendingProjectiles.add(new ProjectileRequest(origin, velocity, damage));
    }

    public List<ProjectileRequest> drainProjectiles() {
        if (pendingProjectiles.isEmpty()) return List.of();
        List<ProjectileRequest> out = new java.util.ArrayList<>(pendingProjectiles);
        pendingProjectiles.clear();
        return out;
    }

    /** Asks MobManager to detonate an explosion of this power at the mob's position, then removes the mob silently. */
    public void requestExplosion(float power) {
        explosionPower = power;
        silentRemoval = true;
        getHealth().setHealth(0f);
    }

    public float takeExplosion() {
        float p = explosionPower;
        explosionPower = 0f;
        return p;
    }

    /** Difficulty used to scale explosion damage; only creepers care. */
    public com.example.world.Difficulty explosionDifficulty() {
        return com.example.world.Difficulty.NORMAL;
    }

    /** Queues a sound by its full registry id (not prefixed with the mob type). */
    public void emitGlobalSound(String id) {
        pendingSounds.add(new SoundEvent(id, position.x, position.y, position.z));
    }

    /** Hostile mobs despawn on Peaceful and use the hostile sound category. */
    public boolean isHostile() {
        return false;
    }

    /** Set by MobManager each tick: direct sunlight reaches this mob (daytime, open sky, dry, not raining). */
    @Getter
    @Setter
    private boolean sunlit;
    private float fireSeconds;
    private float fireTimer;
    /** Seconds since a player was last within 32 blocks (vanilla random-despawn clock). */
    public float farFromPlayerSeconds;

    /** Sets the mob alight for {@code seconds} (never shortens an existing burn). */
    public void setOnFire(float seconds) {
        if (seconds > fireSeconds) fireSeconds = seconds;
    }

    public boolean isOnFire() {
        return fireSeconds > 0f;
    }

    private void tickFire(World world, float dt) {
        if (fireSeconds <= 0f) return;
        if (isInWater(world)) {
            fireSeconds = 0f;
            return;
        }
        fireSeconds -= dt;
        fireTimer += dt;
        if (fireTimer >= 1f) { // vanilla: 1 HP per second while burning
            fireTimer -= 1f;
            damage(1f, com.example.survival.DamageSource.FIRE);
        }
    }

    /** Baby variant flag (zombies only so far): half size, faster. */
    public boolean isBaby() {
        return false;
    }

    /** Stable id used in saves ("zombie", "pig", ...). */
    public abstract String typeId();

    /** Persistent mobs (passive animals) never despawn by distance. */
    public boolean isPersistent() {
        return false;
    }

    /** Per-type save value (e.g. a chicken's egg timer); 0 when the type has none. */
    public float extraState() {
        return 0f;
    }

    public void restoreExtraState(float value) {
    }

    /** Called every tick while alive, before goals - for per-type timers and physics tweaks. */
    protected void onTick(World world, float dt) {
    }

    /** An item this mob produced this tick (a laid egg), consumed by whoever polls it; null if none. */
    public com.example.world.BlockType takeLaidItem() {
        return null;
    }

    /** Asks the physics step to push this mob up out of water this tick (the float goal). */
    public void requestSwimUp() {
        swimUpRequested = true;
    }

    public boolean isInWater(World world) {
        return world.getBlock((int) Math.floor(position.x), (int) Math.floor(position.y + 0.1f), (int) Math.floor(position.z))
                == com.example.world.BlockType.WATER;
    }

    /** Drop-handling hook: what this mob yields on death. {@code MobManager} rolls it exactly
     *  once and spawns the result through the existing item-entity system. Default: nothing. */
    public List<com.example.world.BlockDrops.Resolved> getDrops(java.util.Random random) {
        return List.of();
    }

    /** Normalized-ish horizontal direction this mob wants to move this tick (0,0 = hold still).
     *  Called by movement goals (e.g. {@code MoveToTargetGoal}); overwritten every tick, so a
     *  goal that stops calling this simply stops the mob, no explicit "clear" needed. */
    public void setMoveDirection(float dx, float dz) {
        this.moveDirX = dx;
        this.moveDirZ = dz;
    }

    /** Requests facing toward a world point by the end of this tick (e.g. "look at the target
     *  while attacking", or "face the direction you're walking"). Last caller within a tick
     *  wins - both movement and attack goals request the same thing (face the target) whenever
     *  both would apply, so this never fights itself in practice. */
    public void facePoint(Vector3f point) {
        float dx = point.x - position.x, dz = point.z - position.z;
        if (dx * dx + dz * dz > 1e-6f) desiredYaw = (float) Math.atan2(dx, -dz);
    }

    /** Queues {@code mob.<typeId>.<suffix>} at this mob's position. */
    public void emitSound(String suffix) {
        pendingSounds.add(new SoundEvent("mob." + typeId() + "." + suffix, position.x, position.y, position.z));
    }

    /** Hands over (and clears) the sounds queued since the last call. */
    public List<SoundEvent> drainSounds() {
        if (pendingSounds.isEmpty()) return List.of();
        List<SoundEvent> out = new java.util.ArrayList<>(pendingSounds);
        pendingSounds.clear();
        return out;
    }

    private void updateSounds(float dt) {
        boolean aliveNow = isAlive();
        if (wasAlive && !aliveNow) {
            if (!silentRemoval) emitSound("death");
        } else if (aliveNow && getHurtTime() > lastHurtTime + 1e-4f) {
            emitSound("hurt");
        }
        wasAlive = aliveNow;
        lastHurtTime = getHurtTime();
        if (!aliveNow) return;
        // Vanilla: every tick, if nextInt(1000) < ambientSoundTime++ the mob speaks and the counter drops to -80.
        if (!makesAmbientSound()) return;
        ambientAccumulator += dt * 20f;
        while (ambientAccumulator >= 1f) {
            ambientAccumulator -= 1f;
            if (random.nextInt(1000) < ambientTicks++) {
                emitSound("say");
                ambientTicks = -AMBIENT_INTERVAL_TICKS;
            }
        }
    }

    public boolean hasTarget() {
        return target != null;
    }

    /** Straight-line distance to the current target's position, or {@link Float#MAX_VALUE} with
     *  none set - callers that need "is there even a target" should check {@link #hasTarget()}
     *  first rather than comparing against this sentinel. */
    public float distanceToTarget() {
        if (target == null) return Float.MAX_VALUE;
        return getPosition().distance(target.getPosition());
    }

    /**
     * Runs AI (if alive) then physics. {@code candidates} is the bounded list of everything this
     * mob's goals are allowed to search/target this tick - see {@code MobManager}, which is the
     * one place deciding what that list contains (today: just the player).
     */
    public void tick(World world, List<Targetable> candidates, float dt) {
        tickHealth(dt);
        updateSounds(dt);
        if (!isAlive()) {
            moveDirX = 0f;
            moveDirZ = 0f;
            moveWithGravity(world, 0f, 0f, dt); // dead: no AI/attacks, but still falls and slides from knockback
            return;
        }
        speedMultiplier = 1f;
        lastDt = dt;
        tickFire(world, dt);
        onTick(world, dt);
        goals.tick(this, world, candidates, dt);
        applyMovement(world, dt);
    }

    private void applyMovement(World world, float dt) {
        if (!Float.isNaN(desiredYaw)) {
            float delta = (float) Math.atan2(Math.sin(desiredYaw - yaw), Math.cos(desiredYaw - yaw));
            float step = TURN_RATE * dt;
            yaw += Math.max(-step, Math.min(step, delta));
            desiredYaw = Float.NaN;
        }
        if (swimUpRequested) {
            velocity.y = Math.max(velocity.y, 2.5f);
            swimUpRequested = false;
        }
        float dx = moveDirX * getMoveSpeed() * speedMultiplier * dt;
        float dz = moveDirZ * getMoveSpeed() * speedMultiplier * dt;
        if ((dx != 0f || dz != 0f) && isOnGround()
                && intersectsSolid(world, position.x + dx, position.y, position.z + dz)
                && canStepUp(world, dx, dz)) {
            stepUp();
        }
        float movedX = position.x, movedZ = position.z;
        moveWithGravity(world, dx, dz, dt);
        float moved = (float) Math.hypot(position.x - movedX, position.z - movedZ);
        float target = moved > 1e-4f ? 1f : 0f;
        walkAmount += (target - walkAmount) * Math.min(1f, 10f * dt);
        walkPhase += moved * 6f;
        if (isOnGround() && moved > 1e-4f) {
            stepDistance += moved;
            if (stepDistance >= STEP_EVERY_BLOCKS) {
                stepDistance = 0f;
                emitSound("step");
            }
        }
        moveDirX = 0f;
        moveDirZ = 0f;
    }
}
