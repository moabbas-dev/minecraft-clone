package com.example.worldgen.chunk;

import com.example.worldgen.density.DensitySamplerSet;
import com.example.worldgen.density.SamplerContext;
import com.example.worldgen.level.GenChunk.HeightmapType;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.levelgen.SpawnTargetPoint;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.terrain.BlockProperties;
import com.example.worldgen.terrain.TerrainChunk;

import java.util.List;
import java.util.Set;

/**
 * The initial spawn of a new 26.3 world, read from the client jar:
 * <ol>
 *     <li>{@link #origin}: {@code NoiseBasedChunkGenerator.getOrigin(randomState)} = {@code ChunkPos.containing(
 *     NoiseSpawnFinder.findSpawnPosition(settings.spawnTarget(), randomState.samplersWithContext(SamplerContext.builder()
 *     .enableCaches().build())))};</li>
 *     <li>{@link #worldSpawn}: {@code MinecraftServer.setInitialSpawn}: the spawn is first {@code origin.getWorldPosition()
 *     .offset(8, getSpawnHeight, 8)} ({@code ChunkGenerator.getSpawnHeight} is 64 and not overridden by
 *     {@code NoiseBasedChunkGenerator}; 64 is not below the world, so the {@code WORLD_SURFACE} fallback is not taken), then
 *     the first chunk of an 11 x 11 spiral around the origin where {@code PlayerSpawnFinder.getSpawnPosInChunk} finds a
 *     position;</li>
 *     <li>{@link #playerSpawn}: {@code PlayerSpawnFinder.findSpawn} for a survival/creative player: radius
 *     {@code max(0, gamerule respawn_radius)} (default 10), the default world border never limits it; {@code (2r + 1)^2}
 *     candidates (at most 1024) visited as {@code (offset + coprime * i) % count} with {@code coprime = count <= 16 ?
 *     count - 1 : 17} and {@code offset = RandomSource.createThreadLocalInstance().nextInt(count)}; the first candidate
 *     column whose {@code getLevelRespawnPos} is free for the player ({@code noCollisionNoLiquid}) wins, else
 *     {@code fixupSpawnHeight} of the world spawn.</li>
 * </ol>
 * The chunks are the terrain-only chunks of this port (no features yet), read through their final heightmaps
 * ({@code MOTION_BLOCKING}, {@code WORLD_SURFACE}, {@code OCEAN_FLOOR}) and block states.
 */
public final class OverworldSpawn {

    /** {@code ChunkGenerator.getSpawnHeight(LevelHeightAccessor)}: {@code bipush 64}. */
    public static final int SPAWN_HEIGHT = 64;
    /** {@code GameRules.RESPAWN_RADIUS} ({@code respawn_radius}) default: {@code registerInteger("respawn_radius", PLAYER, 10, 0)}. */
    public static final int RESPAWN_RADIUS = 10;
    /** {@code NoiseSpawnFinder.MAX_RADIUS}. */
    private static final long MAX_RADIUS = 2048L;
    /** {@code PlayerSpawnFinder.ABSOLUTE_MAX_ATTEMPTS}. */
    private static final int ABSOLUTE_MAX_ATTEMPTS = 1024;

    /**
     * Terrain blocks whose collision shape (with {@code CollisionContext.empty()}) is the full block, so
     * {@code Block.isFaceFull(shape, UP)} holds: plain {@code Block}s and the falling/snowy/pillar/ice blocks of the
     * material rules (default {@code getCollisionShape} = the full shape).
     */
    private static final Set<String> FULL_BLOCKS = Set.of("minecraft:stone", "minecraft:deepslate", "minecraft:dirt",
            "minecraft:grass_block", "minecraft:mycelium", "minecraft:podzol", "minecraft:coarse_dirt", "minecraft:bedrock",
            "minecraft:calcite", "minecraft:cinnabar", "minecraft:copper_ore", "minecraft:deepslate_iron_ore", "minecraft:granite",
            "minecraft:gravel", "minecraft:ice", "minecraft:packed_ice", "minecraft:raw_copper_block", "minecraft:raw_iron_block",
            "minecraft:red_sand", "minecraft:red_sandstone", "minecraft:sand", "minecraft:sandstone", "minecraft:snow_block",
            "minecraft:sulfur", "minecraft:tuff", "minecraft:terracotta", "minecraft:white_terracotta",
            "minecraft:orange_terracotta", "minecraft:yellow_terracotta", "minecraft:brown_terracotta", "minecraft:red_terracotta",
            "minecraft:light_gray_terracotta");
    /** {@code MudBlock.SHAPE = Block.column(16, 0, 14)}: it collides, but its top face is not full. */
    private static final String MUD = "minecraft:mud";
    /** {@code PowderSnowBlock.getCollisionShape}: {@code Shapes.empty()} without an entity in the context. */
    private static final String POWDER_SNOW = "minecraft:powder_snow";

    /** The chunks the search reads (generated on demand by the caller). */
    @FunctionalInterface
    public interface Chunks {
        TerrainChunk get(int chunkX, int chunkZ);
    }

    /** A block position. */
    public record BlockPos(int x, int y, int z) {
    }

    private OverworldSpawn() {
    }

    // ------------------------------------------------------------------ NoiseSpawnFinder

    private record Result(int x, int z, long fitness) {
    }

    /** {@code getOrigin}: the chunk of {@code NoiseSpawnFinder.findSpawnPosition}. */
    public static int[] origin(OverworldGenerator generator) {
        List<SpawnTargetPoint> targets = generator.settings().spawnTarget();
        if (targets.isEmpty()) {
            throw new IllegalStateException("noise settings without spawn_target: ChunkGenerator.getOrigin is not ported");
        }
        DensitySamplerSet samplers = generator.randomState().samplersWithContext(SamplerContext.builder().enableCaches().build());
        Result result = fitness(samplers, targets, 0, 0);
        result = radialSearch(samplers, targets, result, 2048.0F, 512.0F);
        result = radialSearch(samplers, targets, result, 512.0F, 32.0F);
        return new int[]{result.x() >> 4, result.z() >> 4};
    }

    /** {@code radialSearch(samplers, targets, maxRadius, step)}: the centre is the best result when the search starts. */
    private static Result radialSearch(DensitySamplerSet samplers, List<SpawnTargetPoint> targets, Result best, float maxRadius, float step) {
        float angle = 0.0F;
        float radius = step;
        int centerX = best.x();
        int centerZ = best.z();
        while (radius <= maxRadius) {
            int x = centerX + (int) (Math.sin((double) angle) * (double) radius);
            int z = centerZ + (int) (Math.cos((double) angle) * (double) radius);
            Result candidate = fitness(samplers, targets, x, z);
            if (candidate.fitness() < best.fitness()) {
                best = candidate;
            }
            angle += step / radius;
            if ((double) angle > 6.283185307179586) {
                angle = 0.0F;
                radius += step;
            }
        }
        return best;
    }

    /** {@code getSpawnPositionAndFitness}: sampled at the quart corner, distance from the world origin added. */
    private static Result fitness(DensitySamplerSet samplers, List<SpawnTargetPoint> targets, int x, int z) {
        int sampleX = (x >> 2) << 2;
        int sampleZ = (z >> 2) << 2;
        long fitness = Long.MAX_VALUE;
        for (SpawnTargetPoint target : targets) {
            fitness = Math.min(fitness, target.sampleFitness(samplers, sampleX, 0, sampleZ));
        }
        long distance = (long) x * (long) x + (long) z * (long) z;
        return new Result(x, z, fitness * (MAX_RADIUS * MAX_RADIUS) + distance);
    }

    // ------------------------------------------------------------------ MinecraftServer.setInitialSpawn

    /** The world spawn ({@code LevelData} respawn position) of a new world. */
    public static BlockPos worldSpawn(OverworldGenerator generator, Chunks chunks) {
        int[] origin = origin(generator);
        BlockPos spawn = new BlockPos(origin[0] * 16 + 8, SPAWN_HEIGHT, origin[1] * 16 + 8);
        int x = 0;
        int z = 0;
        int dx = 0;
        int dz = -1;
        for (int i = 0; i < 11 * 11; i++) {
            if (x >= -5 && x <= 5 && z >= -5 && z <= 5) {
                BlockPos found = spawnPosInChunk(generator, chunks, origin[0] + x, origin[1] + z);
                if (found != null) {
                    spawn = found;
                    break;
                }
            }
            if (x == z || x < 0 && x == -z || x > 0 && x == 1 - z) {
                int swap = dx;
                dx = -dz;
                dz = swap;
            }
            x += dx;
            z += dz;
        }
        return spawn;
    }

    /** {@code PlayerSpawnFinder.getSpawnPosInChunk}: x outer, z inner over the chunk's 256 columns. */
    public static BlockPos spawnPosInChunk(OverworldGenerator generator, Chunks chunks, int chunkX, int chunkZ) {
        for (int x = chunkX * 16; x <= chunkX * 16 + 15; x++) {
            for (int z = chunkZ * 16; z <= chunkZ * 16 + 15; z++) {
                BlockPos pos = levelRespawnPos(generator, chunks, x, z);
                if (pos != null) return pos;
            }
        }
        return null;
    }

    /**
     * {@code PlayerSpawnFinder.getLevelRespawnPos} (no ceiling): top = {@code MOTION_BLOCKING} height; a column whose
     * {@code WORLD_SURFACE} is at most the top and above {@code OCEAN_FLOOR} (water at the surface) is refused; then from
     * {@code top + 1} down, a fluid stops the search and the first block with a full top face gives the position above it.
     */
    public static BlockPos levelRespawnPos(OverworldGenerator generator, Chunks chunks, int x, int z) {
        TerrainChunk chunk = chunks.get(x >> 4, z >> 4);
        int top = chunk.getHeight(HeightmapType.MOTION_BLOCKING, x, z);
        if (top < generator.minY()) return null;
        int surface = chunk.getHeight(HeightmapType.WORLD_SURFACE, x, z);
        if (surface <= top && surface > chunk.getHeight(HeightmapType.OCEAN_FLOOR, x, z)) return null;
        for (int y = top + 1; y >= generator.minY(); y--) {
            StateRef state = chunk.getState(x, y, z);
            if (BlockProperties.hasFluid(state)) break;
            if (fullTopFace(state)) return new BlockPos(x, y + 1, z);
        }
        return null;
    }

    private static boolean fullTopFace(StateRef state) {
        if (state.isAir()) return false;
        if (FULL_BLOCKS.contains(state.id())) return true;
        if (state.is(MUD) || state.is(POWDER_SNOW)) return false;
        throw new IllegalStateException("collision shape of " + state + " is not verified for the spawn search");
    }

    // ------------------------------------------------------------------ PlayerSpawnFinder.findSpawn

    /**
     * Where a survival or creative player appears in a new world: {@code Vec3.atBottomCenterOf} of the chosen block,
     * as {@code {x, y, z}}.
     */
    public static double[] playerSpawn(OverworldGenerator generator, Chunks chunks, BlockPos worldSpawn, int respawnRadius, RandomSource random) {
        int radius = Math.max(0, respawnRadius);
        long side = (long) radius * 2L + 1L;
        int candidateCount = (int) Math.min((long) ABSOLUTE_MAX_ATTEMPTS, side * side);
        int coprime = candidateCount <= 16 ? candidateCount - 1 : 17;
        int offset = random.nextInt(candidateCount);
        for (int i = 0; i < candidateCount; i++) {
            int index = (offset + coprime * i) % candidateCount;
            int dx = index % (radius * 2 + 1);
            int dz = index / (radius * 2 + 1);
            int x = worldSpawn.x() + dx - radius;
            int z = worldSpawn.z() + dz - radius;
            BlockPos pos = levelRespawnPos(generator, chunks, x, z);
            if (pos != null && noCollisionNoLiquid(generator, chunks, pos)) {
                return new double[]{pos.x() + 0.5, pos.y(), pos.z() + 0.5};
            }
        }
        return fixupSpawnHeight(generator, chunks, worldSpawn);
    }

    /** {@code fixupSpawnHeight}: up while blocked (below the top), then down while free (above the bottom), then one up. */
    private static double[] fixupSpawnHeight(OverworldGenerator generator, Chunks chunks, BlockPos start) {
        int maxY = generator.minY() + generator.height() - 1;
        int y = start.y();
        while (!noCollisionNoLiquid(generator, chunks, new BlockPos(start.x(), y, start.z())) && y < maxY) y++;
        y--;
        while (noCollisionNoLiquid(generator, chunks, new BlockPos(start.x(), y, start.z())) && y > generator.minY()) y--;
        y++;
        return new double[]{start.x() + 0.5, y, start.z() + 0.5};
    }

    /**
     * {@code noCollisionNoLiquid}: the player's box ({@code EntityType.PLAYER} 0.6 x 1.8 at the bottom centre of the
     * block) meets neither a collision shape nor a fluid. The box lies inside the column and spans the cells {@code y}
     * and {@code y + 1} (a block below only touches it); a cell blocks when its collision shape is not empty or it holds
     * a fluid. Outside the build height there is nothing.
     */
    private static boolean noCollisionNoLiquid(OverworldGenerator generator, Chunks chunks, BlockPos pos) {
        TerrainChunk chunk = chunks.get(pos.x() >> 4, pos.z() >> 4);
        for (int y = pos.y(); y <= pos.y() + 1; y++) {
            StateRef state = chunk.getState(pos.x(), y, pos.z());
            if (state.isAir()) continue;
            if (BlockProperties.hasFluid(state)) return false;
            if (state.is(POWDER_SNOW)) continue;
            if (FULL_BLOCKS.contains(state.id()) || state.is(MUD)) return false;
            throw new IllegalStateException("collision shape of " + state + " is not verified for the spawn search");
        }
        return true;
    }
}
