package com.example.world;

import java.util.HashMap;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

/**
 * Water and lava as vanilla's FlowingFluid / LavaFluid / LiquidBlock run them (numbers read from the 26.3 client): scheduled fluid
 * ticks - water every 5 ticks, overworld lava every 30 (a lava block that rises is slower, 4x, three times in four) - each
 * recomputing the block's level from its neighbours and then spreading: down first, and sideways (towards the nearest hole within
 * 4 blocks for water, 2 for lava) when it cannot go down or is a source. Water loses 1 level per block, lava 2; two water sources
 * beside a flowing cell with something solid or a source beneath make another source; a fluid with the same fluid above it is a
 * full falling column. Lava beside water (above or to the side) turns to obsidian (a source) or cobblestone, and lava that flows
 * down onto water makes stone.
 *
 * <p>A cell in a chunk that is not loaded behaves like a solid wall, so nothing spreads into or out of unloaded terrain.
 * Not simulated: fluid pushing entities, lava starting fires, buckets of other fluids, waterlogging, nether lava rules.
 */
public final class FluidSim {
    public static final int WATER_DELAY = 5, LAVA_DELAY = 30;
    public static final int WATER_SLOPE = 4, LAVA_SLOPE = 2;
    private static final int MAX_PER_TICK = 4096;
    /** FlowingFluid's horizontal directions: north (-z), east (+x), south (+z), west (-x). */
    private static final int[][] HORIZONTAL = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};

    /** What the game wants to hear about: a replaced plant that should drop, and the lava fizz. */
    public interface Listener {
        void destroyed(int x, int y, int z, BlockType old);

        void fizz(int x, int y, int z);
    }

    private final Map<Long, Long> scheduled = new HashMap<>();
    private final PriorityQueue<long[]> queue = new PriorityQueue<>((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : Long.compare(a[1], b[1]));
    private long now;
    private long sequence;
    private Listener listener;
    private final Random random = new Random(1234);

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public int pending() {
        return scheduled.size();
    }

    public long now() {
        return now;
    }

    public void clear() {
        scheduled.clear();
        queue.clear();
    }

    // ---- positions ----

    private static long key(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) | (((long) z & 0x3FFFFFFL) << 26) | (((long) (y - Chunk.MIN_Y) & 0x1FFL) << 52);
    }

    private static int keyX(long k) {
        return (int) (k << 38 >> 38);
    }

    private static int keyZ(long k) {
        return (int) ((k >> 26) << 38 >> 38);
    }

    private static int keyY(long k) {
        return (int) ((k >> 52) & 0x1FF) + Chunk.MIN_Y;
    }

    public static int delayOf(BlockType fluid) {
        return fluid.isLava() ? LAVA_DELAY : WATER_DELAY;
    }

    public static int dropOff(boolean lava) {
        return lava ? 2 : 1;
    }

    public static int slopeDistance(boolean lava) {
        return lava ? LAVA_SLOPE : WATER_SLOPE;
    }

    /** FluidState.getOwnHeight: amount / 9 (a source is 8/9 high). */
    public static float ownHeight(BlockType fluid) {
        return fluid.fluidAmount() / 9f;
    }

    // ---- scheduling ----

    public void schedule(int x, int y, int z, int delay) {
        long k = key(x, y, z);
        if (scheduled.containsKey(k)) return;
        long due = now + Math.max(1, delay);
        scheduled.put(k, due);
        queue.add(new long[]{due, sequence++, k});
    }

    /**
     * Told by {@link World#setBlock} that the block at (x, y, z) changed: the fluids it touches get a tick, and a lava block it
     * touches (or is) reacts with water at once.
     */
    void blockChanged(World world, int x, int y, int z) {
        for (int i = 0; i < 7; i++) {
            int nx = x + (i == 1 ? 1 : i == 2 ? -1 : 0), ny = y + (i == 3 ? 1 : i == 4 ? -1 : 0), nz = z + (i == 5 ? 1 : i == 6 ? -1 : 0);
            BlockType n = world.getBlock(nx, ny, nz);
            if (!n.isFluid()) continue;
            if (n.isLava() && lavaMeetsWater(world, nx, ny, nz, n)) continue;
            schedule(nx, ny, nz, delayOf(n));
        }
    }

    /** LiquidBlock.shouldSpreadLiquid: lava with water above or beside it becomes obsidian (a source) or cobblestone. True when it reacted. */
    private boolean lavaMeetsWater(World world, int x, int y, int z, BlockType lava) {
        int[][] around = {{0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
        for (int[] d : around) {
            if (!world.getBlock(x + d[0], y + d[1], z + d[2]).isWater()) continue;
            world.setBlock(x, y, z, lava.isFluidSource() ? BlockType.OBSIDIAN : BlockType.COBBLESTONE);
            if (listener != null) listener.fizz(x, y, z);
            return true;
        }
        return false;
    }

    /**
     * Scheduled fluid ticks are not saved, so when a chunk comes back from disk every fluid that may not have settled gets a tick
     * again: any flowing block, and any source with room beside or below it to spread into. The ticks wait a second so that the
     * neighbouring chunks are loaded by then (an unloaded neighbour counts as a wall).
     */
    void chunkRestored(World world, Chunk chunk) {
        int ox = chunk.worldOriginX(), oz = chunk.worldOriginZ();
        for (int lx = 0; lx < Chunk.SIZE_X; lx++) {
            for (int lz = 0; lz < Chunk.SIZE_Z; lz++) {
                int top = Math.min(Chunk.MAX_Y, chunk.getColumnHeight(lx, lz));
                for (int y = Chunk.MIN_Y; y <= top; y++) {
                    BlockType b = chunk.getLocal(lx, y, lz);
                    if (!b.isFluid()) continue;
                    if (b.isFluidSource() && !hasRoomToSpread(chunk, lx, y, lz, b.isLava())) continue;
                    schedule(ox + lx, y, oz + lz, RESTORE_DELAY + delayOf(b));
                }
            }
        }
    }

    private static final int RESTORE_DELAY = 20;

    /** A source can still spread when the cell below or one of the four beside it (inside the chunk) can hold fluid without being the same source. */
    private static boolean hasRoomToSpread(Chunk chunk, int lx, int y, int lz, boolean lava) {
        if (y > Chunk.MIN_Y && canPassThrough(chunk.getLocal(lx, y - 1, lz), lava)) return true;
        for (int[] d : HORIZONTAL) {
            int nx = lx + d[0], nz = lz + d[1];
            if (nx < 0 || nx >= Chunk.SIZE_X || nz < 0 || nz >= Chunk.SIZE_Z) continue; // across the border: left to the neighbour's own scan
            if (canPassThrough(chunk.getLocal(nx, y, nz), lava)) return true;
        }
        return false;
    }

    // ---- ticking ----

    /** One game tick (20 Hz): runs the fluid ticks that have come due. */
    public void tick(World world) {
        now++;
        int done = 0;
        while (!queue.isEmpty() && queue.peek()[0] <= now && done++ < MAX_PER_TICK) {
            long[] e = queue.poll();
            Long due = scheduled.get(e[2]);
            if (due == null || due != e[0]) continue;
            scheduled.remove(e[2]);
            tickFluid(world, keyX(e[2]), keyY(e[2]), keyZ(e[2]));
        }
    }

    /** Runs the pending ticks until none are left or {@code maxTicks} have passed (tests and the controller's "settle"). */
    public int settle(World world, int maxTicks) {
        int ticks = 0;
        while (!scheduled.isEmpty() && ticks < maxTicks) {
            tick(world);
            ticks++;
        }
        return ticks;
    }

    private static boolean loaded(World world, int x, int z) {
        Chunk c = world.getChunk(World.worldToChunk(x), World.worldToChunk(z));
        return c != null && c.isGenerated();
    }

    /** The block at a cell, with unloaded terrain standing in as solid bedrock. */
    private static BlockType at(World world, int x, int y, int z) {
        if (y < Chunk.MIN_Y) return BlockType.BEDROCK;
        if (y > Chunk.MAX_Y) return BlockType.AIR;
        return loaded(world, x, z) ? world.getBlock(x, y, z) : BlockType.BEDROCK;
    }

    private static boolean sameFluid(BlockType b, boolean lava) {
        return lava ? b.isLava() : b.isWater();
    }

    /** FlowingFluid.canHoldFluid: anything that does not stop movement (air, plants, torches and fluids). */
    private static boolean canHold(BlockType b) {
        return !b.isSolidCollision();
    }

    /** canPassThrough: not a source of this fluid already, and able to hold it. */
    private static boolean canPassThrough(BlockType b, boolean lava) {
        return !(sameFluid(b, lava) && b.isFluidSource()) && canHold(b);
    }

    /** FluidState.canBeReplacedWith for what is there now: air and plants always; water only from above by lava; lava only by water, when 4/9 high or more. */
    private static boolean canBeReplacedWith(BlockType existing, boolean newLava, boolean down) {
        if (!existing.isFluid()) return true;
        if (existing.isWater()) return down && newLava;
        return !newLava && ownHeight(existing) >= 0.44444445f;
    }

    /** FlowingFluid.getNewLiquid: the block this cell should be, from its neighbours. */
    static BlockType newLiquid(World world, int x, int y, int z, boolean lava) {
        int highest = 0, sources = 0;
        for (int[] d : HORIZONTAL) {
            BlockType n = at(world, x + d[0], y, z + d[1]);
            if (!sameFluid(n, lava)) continue;
            if (n.isFluidSource()) sources++;
            highest = Math.max(highest, n.fluidAmount());
        }
        if (!lava && sources >= 2) {
            BlockType below = at(world, x, y - 1, z);
            if (below.isSolidCollision() || (below.isWater() && below.isFluidSource())) return BlockType.WATER;
        }
        if (sameFluid(at(world, x, y + 1, z), lava)) return BlockType.fluidBlock(lava, 8, true);
        int amount = highest - dropOff(lava);
        return amount <= 0 ? BlockType.AIR : BlockType.fluidBlock(lava, amount, false);
    }

    private void tickFluid(World world, int x, int y, int z) {
        if (!loaded(world, x, z)) return;
        BlockType b = world.getBlock(x, y, z);
        if (!b.isFluid()) return;
        boolean lava = b.isLava();
        if (!b.isFluidSource()) {
            BlockType next = newLiquid(world, x, y, z, lava);
            if (next != b) {
                int delay = delayOf(b);
                if (lava && !next.isAir() && !b.isFluidFalling() && !next.isFluidFalling() && ownHeight(next) > ownHeight(b) && random.nextInt(4) != 0) delay *= 4;
                if (!next.isAir()) schedule(x, y, z, delay); // before the change, which would schedule it at the normal delay
                world.setBlock(x, y, z, next);
                b = next;
            }
        }
        if (b.isFluid()) spread(world, x, y, z, b);
    }

    /** FlowingFluid.spread. */
    private void spread(World world, int x, int y, int z, BlockType b) {
        boolean lava = b.isLava();
        BlockType below = at(world, x, y - 1, z);
        if (canPassThrough(below, lava)) {
            BlockType newBelow = newLiquid(world, x, y - 1, z, lava);
            if (!newBelow.isAir() && canBeReplacedWith(below, lava, true) && canHold(below)) {
                spreadTo(world, x, y - 1, z, below, newBelow, true);
                if (sourceNeighbours(world, x, y, z, lava) >= 3) spreadToSides(world, x, y, z, b);
                return;
            }
        }
        if (b.isFluidSource() || !isHole(world, x, y - 1, z, lava)) spreadToSides(world, x, y, z, b);
    }

    private int sourceNeighbours(World world, int x, int y, int z, boolean lava) {
        int n = 0;
        for (int[] d : HORIZONTAL) {
            BlockType o = at(world, x + d[0], y, z + d[1]);
            if (sameFluid(o, lava) && o.isFluidSource()) n++;
        }
        return n;
    }

    /** isWaterHole: the cell below lets the fluid go down (it holds the same fluid or could hold it). */
    private boolean isHole(World world, int x, int belowY, int z, boolean lava) {
        BlockType below = at(world, x, belowY, z);
        return sameFluid(below, lava) || canHold(below);
    }

    private void spreadToSides(World world, int x, int y, int z, BlockType b) {
        boolean lava = b.isLava();
        int amount = b.isFluidFalling() ? 7 : b.fluidAmount() - dropOff(lava);
        if (amount <= 0) return;
        int best = 1000;
        BlockType[] result = new BlockType[4];
        for (int i = 0; i < 4; i++) {
            int nx = x + HORIZONTAL[i][0], nz = z + HORIZONTAL[i][1];
            BlockType n = at(world, nx, y, nz);
            if (!canPassThrough(n, lava)) continue;
            BlockType flow = newLiquid(world, nx, y, nz, lava);
            if (flow.isAir() || !canHold(n)) continue;
            int dist = isHole(world, nx, y - 1, nz, lava) ? 0 : slopeDistance(world, nx, y, nz, 1, i ^ 2, lava);
            if (dist < best) java.util.Arrays.fill(result, null);
            if (dist <= best) {
                result[i] = flow;
                best = dist;
            }
        }
        for (int i = 0; i < 4; i++) {
            if (result[i] == null) continue;
            int nx = x + HORIZONTAL[i][0], nz = z + HORIZONTAL[i][1];
            BlockType n = at(world, nx, y, nz);
            if (canBeReplacedWith(n, lava, false) && canHold(n)) spreadTo(world, nx, y, nz, n, result[i], false);
        }
    }

    /** FlowingFluid.getSlopeDistance: how many blocks away the nearest hole is, looking at most {@link #slopeDistance(boolean)} blocks. */
    private int slopeDistance(World world, int x, int y, int z, int depth, int from, boolean lava) {
        int best = 1000;
        for (int i = 0; i < 4; i++) {
            if (i == from) continue;
            int nx = x + HORIZONTAL[i][0], nz = z + HORIZONTAL[i][1];
            BlockType n = at(world, nx, y, nz);
            if (!canPassThrough(n, lava)) continue;
            if (isHole(world, nx, y - 1, nz, lava)) return depth;
            if (depth < slopeDistance(lava)) {
                int d = slopeDistance(world, nx, y, nz, depth + 1, i ^ 2, lava);
                if (d < best) best = d;
            }
        }
        return best;
    }

    /** FlowingFluid.spreadTo (and LavaFluid's override): breaks what is there, then places the fluid; lava going down onto water makes stone. */
    private void spreadTo(World world, int x, int y, int z, BlockType existing, BlockType fluid, boolean down) {
        if (down && fluid.isLava() && existing.isWater()) {
            world.setBlock(x, y, z, BlockType.STONE);
            if (listener != null) listener.fizz(x, y, z);
            return;
        }
        if (!existing.isAir() && !existing.isFluid() && listener != null) listener.destroyed(x, y, z, existing);
        world.setBlock(x, y, z, fluid);
    }
}
