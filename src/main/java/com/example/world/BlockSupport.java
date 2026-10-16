package com.example.world;

/**
 * The blocks that need something to stand on or hang from, and what happens to them when it goes: a torch needs a block with a
 * sturdy top face under it, a wall torch one with a sturdy side face behind it (it pops off as an item when that is gone), and
 * farmland turns back into dirt when a solid block is put on top of it (FarmBlock.canSurvive).
 */
public final class BlockSupport {
    private BlockSupport() {
    }

    public static boolean isFloorTorch(BlockType t) {
        return t == BlockType.TORCH;
    }

    public static boolean isWallTorch(BlockType t) {
        return t == BlockType.WALL_TORCH_EAST || t == BlockType.WALL_TORCH_WEST || t == BlockType.WALL_TORCH_NORTH || t == BlockType.WALL_TORCH_SOUTH;
    }

    public static boolean isTorch(BlockType t) {
        return isFloorTorch(t) || isWallTorch(t);
    }

    /** FarmBlock's MOISTURE state, 0-7 (0 for anything that is not farmland). */
    public static int moisture(BlockType t) {
        if (t == BlockType.FARMLAND) return 0;
        if (t == BlockType.FARMLAND_MOIST) return 7;
        return t.id >= 247 && t.id <= 252 ? t.id - 246 : 0;
    }

    /** The farmland block with this moisture, 0-7. */
    public static BlockType farmland(int moisture) {
        if (moisture <= 0) return BlockType.FARMLAND;
        if (moisture >= 7) return BlockType.FARMLAND_MOIST;
        return BlockType.byId(246 + moisture);
    }

    public static boolean isFarmland(BlockType t) {
        return t == BlockType.FARMLAND || t == BlockType.FARMLAND_MOIST || (t.id >= 247 && t.id <= 252);
    }

    /**
     * The wall torch whose flame points along (dx, dz) (the face of the support block it is stuck to), or null.
     * "Facing east" is the vanilla blockstate: the torch is on the west side of its cell... that is, stuck to the block at -x.
     */
    public static BlockType wallTorchFacing(int dx, int dz) {
        if (dx == 1 && dz == 0) return BlockType.WALL_TORCH_EAST;
        if (dx == -1 && dz == 0) return BlockType.WALL_TORCH_WEST;
        if (dx == 0 && dz == -1) return BlockType.WALL_TORCH_NORTH;
        if (dx == 0 && dz == 1) return BlockType.WALL_TORCH_SOUTH;
        return null;
    }

    /** The direction (dx, dz) a wall torch faces; the block it is stuck to is the one behind it. */
    public static int[] facingOf(BlockType wallTorch) {
        return switch (wallTorch) {
            case WALL_TORCH_EAST -> new int[]{1, 0};
            case WALL_TORCH_WEST -> new int[]{-1, 0};
            case WALL_TORCH_NORTH -> new int[]{0, -1};
            case WALL_TORCH_SOUTH -> new int[]{0, 1};
            default -> throw new IllegalArgumentException(wallTorch + " is not a wall torch");
        };
    }

    /**
     * Whether a block can hold a torch on one of its faces: it has a full solid face. Leaves, chests (not a full block), farmland
     * (its top is 1/16 low), water and every non-solid block cannot; glass, ice and the like can.
     */
    public static boolean isSturdy(BlockType t) {
        return t.isSolidCollision() && t != BlockType.CHEST && !ChunkMeshBuilder.isAnyLeaves(t) && !isFarmland(t) && !isTorch(t);
    }

    /** Whether the block of this type at (x, y, z) still has what it needs. */
    public static boolean canStay(World world, int x, int y, int z, BlockType type) {
        if (isFloorTorch(type)) return isSturdy(world.getBlock(x, y - 1, z));
        if (isWallTorch(type)) {
            int[] f = facingOf(type);
            return isSturdy(world.getBlock(x - f[0], y, z - f[1]));
        }
        if (isFarmland(type)) return !world.isSolidCollision(x, y + 1, z);
        return true;
    }

    /**
     * Called after the block at (x, y, z) changed: checks the six neighbours that might have depended on it. A torch that lost its
     * support is removed (and reported to the world's detach listener, which drops the item); farmland that got a solid block on
     * top turns into dirt.
     */
    static void blockChanged(World world, int x, int y, int z) {
        check(world, x, y + 1, z);
        check(world, x, y - 1, z);
        check(world, x + 1, y, z);
        check(world, x - 1, y, z);
        check(world, x, y, z + 1);
        check(world, x, y, z - 1);
    }

    private static void check(World world, int x, int y, int z) {
        BlockType t = world.getBlock(x, y, z);
        if (!isTorch(t) && !isFarmland(t)) return;
        if (canStay(world, x, y, z, t)) return;
        if (isFarmland(t)) {
            world.setBlock(x, y, z, BlockType.DIRT);
        } else {
            world.setBlock(x, y, z, BlockType.AIR);
            world.notifyDetached(x, y, z, t);
        }
    }
}
