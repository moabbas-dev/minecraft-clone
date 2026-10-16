package com.example.game;

import com.example.inventory.Inventory;
import com.example.inventory.ItemStack;
import com.example.world.BlockSupport;
import com.example.world.BlockType;
import com.example.world.Chunk;
import com.example.world.Raycast;
import com.example.world.SaplingGrowth;
import com.example.world.SaplingSpecies;
import com.example.world.World;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Placing the selected inventory item as a block, as one transaction: validate everything
 * first, write the block, and only then consume exactly one item. A failed validation changes
 * nothing - not the world, not the inventory - and reports why, so the caller only ever plays
 * the placement sound / swing for {@link Outcome#PLACED}.
 */
public final class BlockPlacer {
    private BlockPlacer() {
    }

    public enum Outcome {PLACED, NO_ITEM, NOT_PLACEABLE, INVALID_TARGET}

    public record Result(Outcome outcome, BlockType type, int x, int y, int z) {
        static Result failed(Outcome outcome) {
            return new Result(outcome, null, 0, 0, 0);
        }

        public boolean placed() {
            return outcome == Outcome.PLACED;
        }
    }

    /** Whether a candidate block position would overlap the player's hitbox (Game supplies the real check). */
    @FunctionalInterface
    public interface PlayerOverlap {
        boolean overlaps(int x, int y, int z);
    }

    private static final Set<BlockType> PLACEABLE = new HashSet<>(Arrays.asList(BlockType.PLACEABLE));

    public static boolean isPlaceable(BlockType type) {
        return PLACEABLE.contains(type);
    }

    public static Result tryPlace(World world, Inventory inventory, Raycast.Hit hit, PlayerOverlap playerOverlap,
                                  SaplingGrowth saplingGrowth) {
        return tryPlace(world, inventory, false, hit, playerOverlap, saplingGrowth);
    }

    /** {@code offhand} places the offhand stack instead of the selected hotbar stack. */
    public static Result tryPlace(World world, Inventory inventory, boolean offhand, Raycast.Hit hit, PlayerOverlap playerOverlap,
                                  SaplingGrowth saplingGrowth) {
        ItemStack held = offhand ? inventory.getOffhand() : inventory.selected();
        if (held.isEmpty()) return Result.failed(Outcome.NO_ITEM);
        BlockType block = held.type();
        if (!PLACEABLE.contains(block)) return Result.failed(Outcome.NOT_PLACEABLE);

        int x = hit.placeX(), y = hit.placeY(), z = hit.placeZ();
        if (block == BlockType.TORCH) {
            // A torch stands on the top of a sturdy block or is stuck to the side of one (never the underside), and needs a free cell.
            if (!BlockSupport.isSturdy(hit.type()) || world.getBlock(x, y, z) != BlockType.AIR) return Result.failed(Outcome.INVALID_TARGET);
            int nx = x - hit.x(), ny = y - hit.y(), nz = z - hit.z();
            if (ny < 0) return Result.failed(Outcome.INVALID_TARGET);
            block = ny > 0 ? BlockType.TORCH : BlockSupport.wallTorchFacing(nx, nz);
            if (block == null) return Result.failed(Outcome.INVALID_TARGET);
        }
        // World.setBlock silently ignores out-of-range Y - without this check the item would be
        // consumed for a block that was never written.
        if (!Chunk.inWorld(y)) return Result.failed(Outcome.INVALID_TARGET);

        SaplingSpecies sapling = SaplingSpecies.bySaplingBlock(block);
        if (sapling != null) {
            if (!Game.isDirectlyAboveGround(hit) || !sapling.validGround.contains(hit.type())) {
                return Result.failed(Outcome.INVALID_TARGET);
            }
        }
        if (block.isSolidCollision() && playerOverlap.overlaps(x, y, z)) return Result.failed(Outcome.INVALID_TARGET);

        world.setBlock(x, y, z, block);
        if (sapling != null) saplingGrowth.registerPlanted(x, y, z, sapling);
        if (offhand) inventory.offhandSlot().removeOne(0);
        else inventory.removeOne(inventory.getSelectedSlot());
        return new Result(Outcome.PLACED, block, x, y, z);
    }
}
