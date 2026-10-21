package com.example.worldgen.carver;

import com.example.worldgen.ChunkPos;
import com.example.worldgen.Mth;
import com.example.worldgen.provider.FloatProvider;
import com.example.worldgen.provider.HeightProvider;
import com.example.worldgen.provider.IntProvider;
import com.example.worldgen.provider.WorldGenerationContext;
import com.example.worldgen.random.RandomSource;

/**
 * Port of {@code net.minecraft.world.level.levelgen.carver.CaveWorldCarver} (26.3), a record holding its own config
 * (JSON keys: {@code probability} floatRange 0..1, {@code y} HeightProvider, {@code count} non-negative IntProvider,
 * {@code thickness} FloatProvider with min >= 0, {@code weird_thickness_bias} optional false,
 * {@code room_vertical_radius_multiplier}, {@code horizontal_radius_multiplier}, {@code vertical_radius_multiplier},
 * {@code start_vertical_radius_multiplier} optional {@code ConstantFloat.of(1.0f)}, {@code floor_level} FloatProvider
 * within [-1, 1]). {@code carve}, {@code getThickness}, {@code createRoom}, {@code createTunnel} and
 * {@code shouldSkip} are transcribed from the bytecode (constants 6.0f, 6.2831855f, 0.5f/4.0f, 3.0f, 1.5,
 * 1.5707963705062866, 3.1415927f, 0.92f/0.7f, 0.1f, 0.9f, 0.75f, 2.0f, 4.0f, 1.5707964f).
 */
public record CaveWorldCarver(float probability, HeightProvider y, IntProvider count, FloatProvider thickness,
                              boolean weirdThicknessBias, FloatProvider roomVerticalRadiusMultiplier,
                              FloatProvider horizontalRadiusMultiplier, FloatProvider verticalRadiusMultiplier,
                              FloatProvider startVerticalRadiusMultiplier, FloatProvider floorLevel) implements WorldCarver {

    @Override
    public boolean isStartChunk(RandomSource random) {
        return random.nextFloat() <= probability;
    }

    @Override
    public boolean carve(WorldGenerationContext context, RandomSource random, ChunkPos chunkPos, ChunkPos sourcePos,
                         CarverOutput output) {
        int maxDistance = (getRange() * 2 - 1) << 4;
        int caves = count.sample(random);
        for (int i = 0; i < caves; i++) {
            double x = sourcePos.getBlockX(random.nextInt(16));
            double cy = y.sample(random, context);
            double z = sourcePos.getBlockZ(random.nextInt(16));
            double horizontalMultiplier = horizontalRadiusMultiplier.sample(random);
            double verticalMultiplier = verticalRadiusMultiplier.sample(random);
            double startVerticalMultiplier = startVerticalRadiusMultiplier.sample(random);
            double floor = floorLevel.sample(random);
            WorldCarver.CarveSkipChecker skipChecker = (relX, relY, relZ, blockY) -> shouldSkip(relX, relY, relZ, floor);
            int tunnels = 1;
            if (random.nextInt(4) == 0) {
                double roomVertical = roomVerticalRadiusMultiplier.sample(random);
                float roomRadius = 1.0F + random.nextFloat() * 6.0F;
                createRoom(chunkPos, x, cy, z, roomRadius, roomVertical, output, skipChecker);
                tunnels += random.nextInt(4);
            }
            for (int t = 0; t < tunnels; t++) {
                float yaw = random.nextFloat() * 6.2831855F;
                float pitch = (random.nextFloat() - 0.5F) / 4.0F;
                float tunnelThickness = getThickness(random);
                int distance = maxDistance - random.nextInt(maxDistance / 4);
                createTunnel(chunkPos, random.nextLong(), x, cy, z, horizontalMultiplier, verticalMultiplier,
                        tunnelThickness, yaw, pitch, 0, distance, startVerticalMultiplier, output, skipChecker);
            }
        }
        return true;
    }

    private float getThickness(RandomSource random) {
        float result = thickness.sample(random);
        if (weirdThicknessBias && random.nextInt(10) == 0) {
            result *= random.nextFloat() * random.nextFloat() * 3.0F + 1.0F;
        }
        return result;
    }

    private void createRoom(ChunkPos chunkPos, double x, double y, double z, float radius, double verticalMultiplier,
                            CarverOutput output, WorldCarver.CarveSkipChecker skipChecker) {
        double horizontalRadius = 1.5 + (double) (Mth.sin(1.5707963705062866) * radius);
        double verticalRadius = horizontalRadius * verticalMultiplier;
        WorldCarver.carveEllipsoid(chunkPos, x + 1.0, y, z, horizontalRadius, verticalRadius, output, skipChecker);
    }

    private void createTunnel(ChunkPos chunkPos, long seed, double x, double y, double z, double horizontalMultiplier,
                              double verticalMultiplier, float thickness, float yaw, float pitch, int step, int distance,
                              double yScale, CarverOutput output, WorldCarver.CarveSkipChecker skipChecker) {
        RandomSource random = RandomSource.createThreadLocalInstance(seed);
        int splitPoint = random.nextInt(distance / 2) + distance / 4;
        boolean steep = random.nextInt(6) == 0;
        float yawChange = 0.0F;
        float pitchChange = 0.0F;
        for (int i = step; i < distance; i++) {
            double horizontalRadius = 1.5 + (double) (Mth.sin((double) (3.1415927F * (float) i / (float) distance)) * thickness);
            double verticalRadius = horizontalRadius * yScale;
            float cosPitch = Mth.cos((double) pitch);
            x += (double) (Mth.cos((double) yaw) * cosPitch);
            y += (double) Mth.sin((double) pitch);
            z += (double) (Mth.sin((double) yaw) * cosPitch);
            pitch *= steep ? 0.92F : 0.7F;
            pitch += pitchChange * 0.1F;
            yaw += yawChange * 0.1F;
            pitchChange *= 0.9F;
            yawChange *= 0.75F;
            pitchChange += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0F;
            yawChange += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0F;
            if (i == splitPoint && thickness > 1.0F) {
                createTunnel(chunkPos, random.nextLong(), x, y, z, horizontalMultiplier, verticalMultiplier,
                        random.nextFloat() * 0.5F + 0.5F, yaw - 1.5707964F, pitch / 3.0F, i, distance, 1.0, output, skipChecker);
                createTunnel(chunkPos, random.nextLong(), x, y, z, horizontalMultiplier, verticalMultiplier,
                        random.nextFloat() * 0.5F + 0.5F, yaw + 1.5707964F, pitch / 3.0F, i, distance, 1.0, output, skipChecker);
                return;
            }
            if (random.nextInt(4) != 0) {
                if (!WorldCarver.canReach(chunkPos, x, z, i, distance, thickness)) {
                    return;
                }
                WorldCarver.carveEllipsoid(chunkPos, x, y, z, horizontalRadius * horizontalMultiplier,
                        verticalRadius * verticalMultiplier, output, skipChecker);
            }
        }
    }

    /** {@code shouldSkip(relX, relY, relZ, floorLevel)}: {@code relY <= floor} or {@code relX^2 + relY^2 + relZ^2 >= 1}. */
    private static boolean shouldSkip(double relX, double relY, double relZ, double floorLevel) {
        if (relY <= floorLevel) {
            return true;
        }
        return relX * relX + relY * relY + relZ * relZ >= 1.0;
    }
}
