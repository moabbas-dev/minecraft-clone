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
        if (!isAlive()) {
            moveDirX = 0f;
            moveDirZ = 0f;
            return; // dead: no AI, no movement, no attacking - just settle under gravity below
        }
        goals.tick(this, world, candidates, dt);
        applyMovement(world, dt);
    }

    private void applyMovement(World world, float dt) {
        if (!Float.isNaN(desiredYaw)) {
            yaw = desiredYaw;
            desiredYaw = Float.NaN;
        }
        float dx = moveDirX * getMoveSpeed() * dt;
        float dz = moveDirZ * getMoveSpeed() * dt;
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
        moveDirX = 0f;
        moveDirZ = 0f;
    }
}
