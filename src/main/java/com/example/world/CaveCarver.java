package com.example.world;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Random;

/**
 * Classic (pre-1.18) carver-style cave and ravine generation: deterministic random-walk
 * "worms" that curve through 3D space and carve an ellipsoid out at each step - the historical
 * Minecraft 1.17.1-era approach, not 1.18's 3D density-noise cave system (deliberately not
 * implemented here - see the task that introduced this class for the full scope decision).
 *
 * <p><b>Chunk-boundary strategy</b> (the central design problem - see class docs on
 * {@link TerrainGenerator}): {@code TerrainGenerator.generate(chunk, world)} is one-shot and
 * never reads a neighboring chunk's blocks (confirmed by audit - tree placement stays a full
 * block inland from every edge specifically to avoid this problem rather than solve it).
 * Chunks are also generated one at a time, synchronously, in whatever order the player happens
 * to explore in - there is no guarantee a tunnel's "first" chunk generates before chunks it
 * later reaches. So a tunnel cannot be carved once into "the world" the way vanilla's own
 * carvers effectively appear to from the outside. Instead, for a chunk being generated, this
 * carves against every ORIGIN chunk within {@link #ORIGIN_RADIUS_CHUNKS} of it: each origin
 * chunk deterministically re-derives (from a seed keyed only on the origin's own coordinates -
 * never on which chunk is currently asking) the exact same candidate cave/ravine system every
 * single time, and only the portion of its path that geometrically lands inside the chunk
 * currently being generated actually gets carved. This is the same mechanism vanilla's real
 * carvers use (seeded per "decoration chunk", applied across a radius of chunks around it), and
 * it is what makes the result independent of chunk load order and of whether any particular
 * neighboring chunk happens to be in memory yet.
 *
 * <p>All numeric tuning below is a project-specific starting point (see Phase 2 of the task),
 * not a verified exact Minecraft 1.17.1 source constant, except where a comment says otherwise.
 * All radius/width/length values are in blocks.
 */
public final class CaveCarver {
    private CaveCarver() {
    }

    // ---- Reach bound / chunk-boundary margin ------------------------------------------------

    /** Longest any single tunnel or ravine (including one branch hop) can possibly reach from
     *  its own origin point, in blocks - used only to size {@link #ORIGIN_RADIUS_CHUNKS}. */
    private static final int MAX_SYSTEM_REACH_BLOCKS = 105;
    /** How many chunks out from the chunk being generated to check for cave/ravine origins -
     *  ceil(reach / 16) plus one chunk of margin so a system starting right at the edge of that
     *  radius, curving toward us, still can't be missed. */
    private static final int ORIGIN_RADIUS_CHUNKS = (MAX_SYSTEM_REACH_BLOCKS + Chunk.SIZE_X - 1) / Chunk.SIZE_X + 1;
    /** Hard cap on total carve-walk steps across every system from every origin scanned for one
     *  chunk - bounds worst-case per-chunk generation cost even if several systems all happen to
     *  land near the same chunk at once (see {@link #carve}). */
    private static final int MAX_TOTAL_STEPS_PER_CHUNK = 420;

    // ---- Determinism seed salts (distinct from TerrainGenerator.chunkRandom's tree-decoration
    // stream, so the two independently-purposed random sequences never collide or correlate) ---
    private static final long CAVE_SEED_SALT = 0x9A3E_2F5D_11B7_00C1L;
    private static final int CAVE_ORIGIN_MUL_X = 916_129_711;
    private static final int CAVE_ORIGIN_MUL_Z = 275_604_541;

    // ---- Cave tunnels -------------------------------------------------------------------------

    private static final float CAVE_START_PROBABILITY = 0.15f; // Phase 2 suggested range 0.08-0.20
    private static final int MIN_CAVE_Y = 5;
    /** Most biome surface heights sit well above this (Biome.baseHeight ranges 40-95, see
     *  Biome.java) - keeps the bulk of caves meaningfully underground rather than skimming the
     *  surface, while still leaving room for occasional entrances (Phase 8). */
    private static final int MAX_CAVE_Y = 100;
    private static final int CAVE_MIN_LENGTH = 24;
    private static final int CAVE_MAX_LENGTH = 55;
    private static final float CAVE_MIN_RADIUS = 1.2f;
    private static final float CAVE_MAX_RADIUS = 3.2f;
    private static final float CHAMBER_RADIUS_MIN = 3.8f;
    private static final float CHAMBER_RADIUS_MAX = 5.5f;
    private static final float CHAMBER_PROBABILITY_PER_STEP = 0.015f;
    private static final int CHAMBER_DURATION_STEPS = 5;
    private static final int CHAMBER_COOLDOWN_STEPS = 35;
    private static final float BRANCH_PROBABILITY_PER_STEP = 0.045f;
    private static final int MAX_BRANCH_DEPTH = 2;
    private static final int MAX_BRANCHES_PER_SYSTEM = 3;
    private static final int MAX_CARVE_STEPS_PER_SYSTEM = 180; // hard safety cap incl. every branch
    /** Generous upper bound on how much farther any branch a job might still spawn could reach
     *  beyond the job's own remaining travel - used only by the target-relevance early exit
     *  below, never affects what gets carved (see {@link #walkSystem}). */
    private static final float BRANCH_REACH_SLACK_BLOCKS = 40f;
    private static final float YAW_CHANGE_STRENGTH = 0.35f; // radians of drift range per step
    private static final float PITCH_CHANGE_STRENGTH = 0.16f;
    private static final float MAX_PITCH = 0.6f; // radians (~34 degrees) - keeps tunnels mostly horizontal
    private static final float STEP_SIZE = 1.0f; // blocks per step
    private static final int RADIUS_RETARGET_INTERVAL = 6; // steps between picking a new target radius
    private static final float RADIUS_LERP_RATE = 0.25f; // smooths retargets instead of jumping

    // ---- Ravines --------------------------------------------------------------------------------

    private static final float RAVINE_START_PROBABILITY = 0.03f; // rarer than caves (Phase 5.5)
    private static final int MIN_RAVINE_Y = 10;
    private static final int MAX_RAVINE_Y = 90; // allowed a bit closer to the surface than caves
    private static final int RAVINE_MIN_LENGTH = 60;
    private static final int RAVINE_MAX_LENGTH = 90;
    private static final float RAVINE_MIN_WIDTH = 1.4f;
    private static final float RAVINE_MAX_WIDTH = 3.4f;
    /** Ravines are far taller than they are wide - this ratio (not an absolute height) is the
     *  approximated characteristic, applied to whatever the current width happens to be. */
    private static final float RAVINE_VERTICAL_SCALE = 3.4f;
    private static final float RAVINE_YAW_CHANGE_STRENGTH = 0.12f; // gentler than caves - long, smooth gashes
    private static final float RAVINE_PITCH_CHANGE_STRENGTH = 0.05f;
    private static final float RAVINE_MAX_PITCH = 0.35f;
    private static final int MAX_CARVE_STEPS_PER_RAVINE = 100;

    // ---- Carveable-block policy (Phase 9) ------------------------------------------------------

    /** Blocks a fresh chunk's column-fill pass can actually produce that are safe to hollow out.
     *  Deliberately excludes BEDROCK (never carved) and WATER/ICE (left untouched - a tunnel
     *  simply meets standing water at the boundary instead of draining it, since this project has
     *  no fluid-propagation system to keep a carved-open body of water filled/consistent - see
     *  the task's own Phase 6 guidance to use the minimal safe behavior rather than build one).
     *  AIR is intentionally absent too - carving air is always a harmless no-op either way. */
    private static boolean isCarveable(BlockType type) {
        return switch (type) {
            case STONE, DIRT, GRASS_BLOCK, SAND, SANDSTONE, GRAVEL, SNOW_BLOCK, TERRACOTTA, RED_SAND,
                    MUD, GOLD_ORE, IRON_ORE, COAL_ORE, DIAMOND_ORE -> true;
            default -> false;
        };
    }

    // ---- Entry point ---------------------------------------------------------------------------

    /** Carves this chunk's share of every cave/ravine system whose origin chunk is within reach -
     *  called from {@link TerrainGenerator#generate} after the column-fill/water pass and before
     *  tree placement, so trees never get placed over a hole a cave happened to open at the
     *  surface (their own suitability check already re-reads the real post-carve block). */
    public static void carve(Chunk chunk, long worldSeed) {
        int cx = chunk.chunkX;
        int cz = chunk.chunkZ;
        // Shared across every origin scanned for THIS chunk (never across different chunks or
        // calls) - bounds a single chunk's worst-case generation cost even on the rare occasion
        // several systems from different origins all land near it at once. The origin scan order
        // below is always the same fixed nested loop for a given (cx,cz), so which origins (if
        // any) get truncated by this budget is itself deterministic - this never makes the same
        // chunk generate differently between calls, it only bounds total work.
        int[] chunkBudget = {0};
        for (int ocx = cx - ORIGIN_RADIUS_CHUNKS; ocx <= cx + ORIGIN_RADIUS_CHUNKS; ocx++) {
            for (int ocz = cz - ORIGIN_RADIUS_CHUNKS; ocz <= cz + ORIGIN_RADIUS_CHUNKS; ocz++) {
                if (chunkBudget[0] >= MAX_TOTAL_STEPS_PER_CHUNK) return;
                if (!couldPossiblyReach(cx, cz, ocx, ocz)) continue; // cheap pre-check, no Random built
                carveSystemsFromOrigin(chunk, worldSeed, ocx, ocz, chunkBudget);
            }
        }
    }

    /** Coarse chunk-center-to-chunk-center distance check, purely to avoid constructing a Random
     *  (and doing any RNG draws) for origins that could not possibly reach the target chunk even
     *  in the most generous case - correctness never depends on this, it is only an optimization
     *  (Phase 14: "bounding-box checks before iterating"). */
    private static boolean couldPossiblyReach(int cx, int cz, int ocx, int ocz) {
        double dx = (ocx - cx) * (double) Chunk.SIZE_X;
        double dz = (ocz - cz) * (double) Chunk.SIZE_Z;
        double maxReach = MAX_SYSTEM_REACH_BLOCKS + Chunk.SIZE_X; // + a chunk-diagonal-ish margin
        return dx * dx + dz * dz <= maxReach * maxReach;
    }

    private static Random originRandom(long worldSeed, int ocx, int ocz) {
        long s = worldSeed + CAVE_SEED_SALT
                + (long) ocx * CAVE_ORIGIN_MUL_X
                + (long) ocz * CAVE_ORIGIN_MUL_Z;
        return new Random(s);
    }

    private static long mixSeed(long base, int step, int salt) {
        long h = base ^ (0x9E3779B97F4A7C15L * (step + 1L));
        h ^= (long) salt * 0xD6E8FEB86659FD93L;
        h ^= (h >>> 32);
        return h;
    }

    /** A fresh, independent seed for a whole system's walk (distinct from the origin-chunk seed
     *  already consumed for its start position/parameters above), deterministic only in terms
     *  of (worldSeed, origin chunk, a tag distinguishing cave from ravine). */
    private static long systemSeed(long worldSeed, int ocx, int ocz, long tag) {
        long s = worldSeed ^ tag;
        s = s * 6364136223846793005L + (long) ocx * 1442695040888963407L;
        s = s * 6364136223846793005L + (long) ocz * 1442695040888963407L;
        return s;
    }

    private static void carveSystemsFromOrigin(Chunk target, long worldSeed, int ocx, int ocz, int[] chunkBudget) {
        Random rnd = originRandom(worldSeed, ocx, ocz);

        // Always draw in this exact fixed order - cave roll, then ravine roll - regardless of
        // which chunk is asking, so results never depend on scan order (Phase 12 determinism).
        if (rnd.nextFloat() < CAVE_START_PROBABILITY) {
            double startX = ocx * (double) Chunk.SIZE_X + rnd.nextInt(Chunk.SIZE_X);
            double startZ = ocz * (double) Chunk.SIZE_Z + rnd.nextInt(Chunk.SIZE_Z);
            double startY = MIN_CAVE_Y + rnd.nextInt(MAX_CAVE_Y - MIN_CAVE_Y);
            float yaw = rnd.nextFloat() * (float) (Math.PI * 2);
            float pitch = (rnd.nextFloat() - 0.5f) * 0.3f;
            int length = CAVE_MIN_LENGTH + rnd.nextInt(CAVE_MAX_LENGTH - CAVE_MIN_LENGTH);
            float radius = CAVE_MIN_RADIUS + rnd.nextFloat() * (CAVE_MAX_RADIUS - CAVE_MIN_RADIUS);
            long seed = systemSeed(worldSeed, ocx, ocz, 0x1DL);
            Job root = new Job(seed, startX, startY, startZ, yaw, pitch, length, radius, radius * 0.75f, 0, false);
            walkSystem(target, root, chunkBudget);
        }

        if (rnd.nextFloat() < RAVINE_START_PROBABILITY) {
            double startX = ocx * (double) Chunk.SIZE_X + rnd.nextInt(Chunk.SIZE_X);
            double startZ = ocz * (double) Chunk.SIZE_Z + rnd.nextInt(Chunk.SIZE_Z);
            double startY = MIN_RAVINE_Y + rnd.nextInt(MAX_RAVINE_Y - MIN_RAVINE_Y);
            float yaw = rnd.nextFloat() * (float) (Math.PI * 2);
            float pitch = (rnd.nextFloat() - 0.5f) * 0.15f;
            int length = RAVINE_MIN_LENGTH + rnd.nextInt(RAVINE_MAX_LENGTH - RAVINE_MIN_LENGTH);
            float width = RAVINE_MIN_WIDTH + rnd.nextFloat() * (RAVINE_MAX_WIDTH - RAVINE_MIN_WIDTH);
            long seed = systemSeed(worldSeed, ocx, ocz, 0x2ADL);
            Job root = new Job(seed, startX, startY, startZ, yaw, pitch, length, width, width * RAVINE_VERTICAL_SCALE, 0, true);
            walkSystem(target, root, chunkBudget);
        }
    }

    /** One tunnel/ravine walk job: a fully self-contained deterministic seed plus starting
     *  kinematic state. Branches are pushed as new Jobs with their own independently-derived
     *  seed rather than continuing to draw from a shared stream, so an iterative work queue can
     *  process them in any order without affecting the result (Phase 1.6/1.7 bounded branching). */
    private record Job(long seed, double x, double y, double z, float yaw, float pitch, int length,
                        float horizRadius, float vertRadius, int depth, boolean ravine) {
    }

    private static void walkSystem(Chunk target, Job root, int[] chunkBudget) {
        Deque<Job> worklist = new ArrayDeque<>();
        worklist.push(root);
        int totalSteps = 0;
        int branchesSpawned = 0;
        int maxSteps = root.ravine() ? MAX_CARVE_STEPS_PER_RAVINE : MAX_CARVE_STEPS_PER_SYSTEM;
        double targetCenterX = target.worldOriginX() + Chunk.SIZE_X / 2.0;
        double targetCenterZ = target.worldOriginZ() + Chunk.SIZE_Z / 2.0;

        while (!worklist.isEmpty() && totalSteps < maxSteps && chunkBudget[0] < MAX_TOTAL_STEPS_PER_CHUNK) {
            Job job = worklist.pop();
            Random rnd = new Random(job.seed());
            double x = job.x(), y = job.y(), z = job.z();
            float yaw = job.yaw(), pitch = job.pitch();
            float horizRadius = job.horizRadius(), vertRadius = job.vertRadius();
            float targetHorizRadius = horizRadius, targetVertRadius = vertRadius;
            int chamberStepsLeft = 0, chamberCooldown = 0;
            float chamberTargetHoriz = horizRadius, chamberTargetVert = vertRadius;

            float yawChange = job.ravine() ? RAVINE_YAW_CHANGE_STRENGTH : YAW_CHANGE_STRENGTH;
            float pitchChange = job.ravine() ? RAVINE_PITCH_CHANGE_STRENGTH : PITCH_CHANGE_STRENGTH;
            float maxPitch = job.ravine() ? RAVINE_MAX_PITCH : MAX_PITCH;

            for (int step = 0; step < job.length() && totalSteps < maxSteps && chunkBudget[0] < MAX_TOTAL_STEPS_PER_CHUNK;
                    step++, totalSteps++, chunkBudget[0]++) {
                yaw += (rnd.nextFloat() - rnd.nextFloat()) * yawChange;
                pitch = clamp(pitch * 0.9f + (rnd.nextFloat() - rnd.nextFloat()) * pitchChange, -maxPitch, maxPitch);

                double dx = Math.cos(yaw) * Math.cos(pitch);
                double dy = Math.sin(pitch);
                double dz = Math.sin(yaw) * Math.cos(pitch);
                x += dx * STEP_SIZE;
                y += dy * STEP_SIZE;
                z += dz * STEP_SIZE;

                if (y < 1 || y >= Chunk.SIZE_Y - 1) break; // never touch bedrock or the world ceiling

                // Target-relevance early exit: a pure optimization, never changes what gets
                // carved. Once even the most generous remaining-travel bound (this job's own
                // remaining steps, plus slack for a branch reversing straight back toward the
                // target) can no longer possibly bring any carve envelope into this chunk, further
                // stepping can only ever be irrelevant to it - stop paying for it. This is what
                // keeps a single chunk's generation cost bounded despite scanning many origins
                // (see the class docs and CaveCarver's own performance notes).
                int remainingSteps = job.length() - step - 1;
                double horizDistToTarget = Math.hypot(x - targetCenterX, z - targetCenterZ);
                double maxFurtherReach = remainingSteps * STEP_SIZE + BRANCH_REACH_SLACK_BLOCKS
                        + Chunk.SIZE_X; // chunk-diagonal-ish margin
                if (horizDistToTarget - maxFurtherReach > 0) break;

                if (!job.ravine() && step % RADIUS_RETARGET_INTERVAL == 0) {
                    targetHorizRadius = CAVE_MIN_RADIUS + rnd.nextFloat() * (CAVE_MAX_RADIUS - CAVE_MIN_RADIUS);
                    targetVertRadius = targetHorizRadius * (0.6f + rnd.nextFloat() * 0.3f);
                }
                horizRadius += (targetHorizRadius - horizRadius) * RADIUS_LERP_RATE;
                vertRadius += (targetVertRadius - vertRadius) * RADIUS_LERP_RATE;

                float carveHoriz = horizRadius, carveVert = vertRadius;
                if (!job.ravine()) {
                    if (chamberCooldown <= 0 && chamberStepsLeft <= 0
                            && rnd.nextFloat() < CHAMBER_PROBABILITY_PER_STEP) {
                        chamberStepsLeft = CHAMBER_DURATION_STEPS;
                        chamberCooldown = CHAMBER_COOLDOWN_STEPS;
                        chamberTargetHoriz = CHAMBER_RADIUS_MIN + rnd.nextFloat() * (CHAMBER_RADIUS_MAX - CHAMBER_RADIUS_MIN);
                        chamberTargetVert = chamberTargetHoriz * 0.85f;
                    }
                    if (chamberStepsLeft > 0) {
                        carveHoriz += (chamberTargetHoriz - carveHoriz) * 0.35f;
                        carveVert += (chamberTargetVert - carveVert) * 0.35f;
                        chamberStepsLeft--;
                    }
                    if (chamberCooldown > 0) chamberCooldown--;
                }

                carveEllipsoid(target, x, y, z, carveHoriz, carveVert);

                if (!job.ravine() && job.depth() < MAX_BRANCH_DEPTH && branchesSpawned < MAX_BRANCHES_PER_SYSTEM
                        && rnd.nextFloat() < BRANCH_PROBABILITY_PER_STEP) {
                    branchesSpawned++;
                    long childSeed = mixSeed(job.seed(), step, branchesSpawned * 7919);
                    Random childRnd = new Random(childSeed ^ 0x51ED_270B_2137_19ADL);
                    float childYaw = yaw + (childRnd.nextFloat() - 0.5f) * 1.4f;
                    float childPitch = clamp(pitch + (childRnd.nextFloat() - 0.5f) * 0.3f, -maxPitch, maxPitch);
                    int childLength = CAVE_MIN_LENGTH / 2 + childRnd.nextInt(Math.max(1, CAVE_MAX_LENGTH / 2));
                    float childRadius = Math.max(CAVE_MIN_RADIUS * 0.6f, horizRadius * (0.5f + childRnd.nextFloat() * 0.3f));
                    worklist.push(new Job(childSeed, x, y, z, childYaw, childPitch, childLength,
                            childRadius, childRadius * 0.8f, job.depth() + 1, false));
                }

                if (horizRadius < 0.4f) break; // tunnel has tapered down to nothing
            }
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static void carveEllipsoid(Chunk target, double worldX, double worldY, double worldZ,
                                        float horizRadius, float vertRadius) {
        if (horizRadius <= 0f || vertRadius <= 0f) return;
        int originX = target.worldOriginX();
        int originZ = target.worldOriginZ();

        int minWX = (int) Math.floor(worldX - horizRadius);
        int maxWX = (int) Math.ceil(worldX + horizRadius);
        int minWZ = (int) Math.floor(worldZ - horizRadius);
        int maxWZ = (int) Math.ceil(worldZ + horizRadius);

        // Fast reject: this carve envelope's AABB doesn't touch this chunk's XZ footprint at all.
        if (maxWX < originX || minWX >= originX + Chunk.SIZE_X) return;
        if (maxWZ < originZ || minWZ >= originZ + Chunk.SIZE_Z) return;

        int minY = Math.max(1, (int) Math.floor(worldY - vertRadius));
        int maxY = Math.min(Chunk.SIZE_Y - 2, (int) Math.ceil(worldY + vertRadius));
        if (minY > maxY) return;

        int lxStart = Math.max(0, minWX - originX);
        int lxEnd = Math.min(Chunk.SIZE_X - 1, maxWX - originX);
        int lzStart = Math.max(0, minWZ - originZ);
        int lzEnd = Math.min(Chunk.SIZE_Z - 1, maxWZ - originZ);

        double hr2 = (double) horizRadius * horizRadius;
        double vr2 = (double) vertRadius * vertRadius;

        for (int lx = lxStart; lx <= lxEnd; lx++) {
            double dx = (originX + lx + 0.5) - worldX;
            double nx = dx * dx / hr2;
            if (nx > 1.0) continue;
            for (int lz = lzStart; lz <= lzEnd; lz++) {
                double dz = (originZ + lz + 0.5) - worldZ;
                double nz = dz * dz / hr2;
                if (nx + nz > 1.0) continue;
                double horizFrac = nx + nz;
                for (int ly = minY; ly <= maxY; ly++) {
                    double dy = (ly + 0.5) - worldY;
                    double ny = dy * dy / vr2;
                    if (horizFrac + ny > 1.0) continue;
                    BlockType existing = target.getLocal(lx, ly, lz);
                    if (isCarveable(existing)) {
                        target.setLocal(lx, ly, lz, BlockType.AIR);
                    }
                }
            }
        }
    }
}
