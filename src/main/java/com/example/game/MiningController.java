package com.example.game;

import com.example.world.BlockDrops;
import com.example.world.BlockType;
import com.example.world.Raycast;
import com.example.world.World;

import java.util.List;
import java.util.Random;

/**
 * The block-breaking state machine and the break transaction, extracted from Game so it can be
 * tested without a window. Owns the target, the progress timer, and the crack stage; does NOT
 * own sounds, particles, or item entities - {@link #tick} returns a {@link Result} and the
 * caller reacts to it, which is what guarantees "exactly one break event per completed break":
 * only one tick can ever return {@code broken == true} for a given block.
 *
 * Transaction order on completion (all inside one tick, nothing can interleave): re-read the
 * block, roll its drops, remove the block, report. The drops are only ever rolled if the
 * removal is about to happen, so a cancelled or invalidated break can never leak a drop.
 */
public class MiningController {
    /** Number of discrete crack overlay stages (0..CRACK_STAGES-1). */
    public static final int CRACK_STAGES = 10;
    /** After a block breaks, the next one can't start for this long - stops a held button from
     *  chain-breaking instant blocks (saplings, TNT) in a single burst. */
    static final float BREAK_COOLDOWN_SECONDS = 0.2f;

    public record Result(boolean mining, boolean broken, BlockType brokenType, int x, int y, int z,
                         List<BlockDrops.Resolved> drops) {
        static final Result IDLE = new Result(false, false, null, 0, 0, 0, List.of());
        static final Result MINING = new Result(true, false, null, 0, 0, 0, List.of());
    }

    private final Random random;
    private boolean hasTarget = false;
    private int tx, ty, tz;
    private BlockType targetType = null;
    private float progress = 0f;
    private float required = 0f;
    private float cooldown = 0f;

    public MiningController(Random random) {
        this.random = random;
    }

    /**
     * Advances breaking by {@code dt}. {@code hit} is the caller's existing raycast result (this
     * class never raycasts). Any change of target - different block position, or the same
     * position now holding a different block - restarts progress from zero, so a block swapped
     * by another system mid-break is never broken on the old block's timer.
     */
    public Result tick(World world, Raycast.Hit hit, boolean attackHeld, float dt) {
        if (cooldown > 0f) cooldown = Math.max(0f, cooldown - dt);

        if (hit == null || !attackHeld) {
            cancel();
            return Result.IDLE;
        }

        BlockType current = world.getBlock(hit.x(), hit.y(), hit.z());
        if (current.isAir()) { // target vanished (or its chunk is unavailable)
            cancel();
            return Result.IDLE;
        }
        float breakTime = current.handBreakSeconds();
        if (breakTime < 0f) { // unbreakable
            cancel();
            return Result.IDLE;
        }
        if (cooldown > 0f) {
            cancel();
            return Result.IDLE;
        }

        if (!hasTarget || tx != hit.x() || ty != hit.y() || tz != hit.z() || targetType != current) {
            hasTarget = true;
            tx = hit.x();
            ty = hit.y();
            tz = hit.z();
            targetType = current;
            progress = 0f;
        }
        required = breakTime;
        progress += dt;

        if (progress < required) return Result.MINING;

        List<BlockDrops.Resolved> drops = BlockDrops.resolve(current, random);
        world.setBlock(tx, ty, tz, BlockType.AIR);
        Result done = new Result(false, true, current, tx, ty, tz, drops);
        cancel();
        cooldown = BREAK_COOLDOWN_SECONDS;
        return done;
    }

    /** Drops all progress. Safe to call any time (new world, quit to title, ...). */
    public void cancel() {
        hasTarget = false;
        targetType = null;
        progress = 0f;
        required = 0f;
    }

    public void reset() {
        cancel();
        cooldown = 0f;
    }

    public boolean isMining() {
        return hasTarget && progress > 0f;
    }

    /** 0..CRACK_STAGES-1 while a block is being mined, -1 when there is nothing to draw. */
    public int crackStage() {
        if (!isMining() || required <= 0f) return -1;
        return Math.min(CRACK_STAGES - 1, (int) (progress / required * CRACK_STAGES));
    }

    public int targetX() {
        return tx;
    }

    public int targetY() {
        return ty;
    }

    public int targetZ() {
        return tz;
    }
}
