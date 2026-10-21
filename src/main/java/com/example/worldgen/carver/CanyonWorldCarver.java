package com.example.worldgen.carver;

import com.example.worldgen.ChunkPos;
import com.example.worldgen.Mth;
import com.example.worldgen.provider.FloatProvider;
import com.example.worldgen.provider.HeightProvider;
import com.example.worldgen.provider.WorldGenerationContext;
import com.example.worldgen.random.RandomSource;

/**
 * Port of {@code net.minecraft.world.level.levelgen.carver.CanyonWorldCarver} (26.3), a record holding its own
 * config (JSON keys: {@code probability} floatRange 0..1, {@code y} HeightProvider, {@code vertical_rotation}
 * FloatProvider, {@code shape} {@link Shape}). {@code carve}, {@code doCarve}, {@code initWidthFactors},
 * {@code updateVerticalRadius} and {@code shouldSkip} are transcribed from the bytecode (constants 6.2831855f, 1.5,
 * 3.1415927f, 0.7f, 0.05f, 0.8f, 0.5f, 2.0f, 4.0f, 0.75f, 6.0).
 */
public record CanyonWorldCarver(float probability, HeightProvider y, FloatProvider verticalRotation, Shape shape)
        implements WorldCarver {

    /**
     * {@code CanyonWorldCarver$Shape}: JSON {@code distance_factor}, {@code thickness}, {@code width_smoothness}
     * ({@code ExtraCodecs.POSITIVE_INT}), {@code horizontal_radius_factor}, {@code vertical_radius_default_factor}
     * (float), {@code vertical_radius_center_factor} (float), {@code y_scale}; all required.
     */
    public record Shape(FloatProvider distanceFactor, FloatProvider thickness, int widthSmoothness,
                        FloatProvider horizontalRadiusFactor, float verticalRadiusDefaultFactor,
                        float verticalRadiusCenterFactor, FloatProvider yScale) {
    }

    @Override
    public boolean isStartChunk(RandomSource random) {
        return random.nextFloat() <= probability;
    }

    @Override
    public boolean carve(WorldGenerationContext context, RandomSource random, ChunkPos chunkPos, ChunkPos sourcePos,
                         CarverOutput output) {
        int maxDistance = (getRange() * 2 - 1) * 16;
        double x = sourcePos.getBlockX(random.nextInt(16));
        int startY = y.sample(random, context);
        double z = sourcePos.getBlockZ(random.nextInt(16));
        float yaw = random.nextFloat() * 6.2831855F;
        float pitch = verticalRotation.sample(random);
        double yScale = shape.yScale().sample(random);
        float thickness = shape.thickness().sample(random);
        int distance = (int) ((float) maxDistance * shape.distanceFactor().sample(random));
        doCarve(context, chunkPos, random.nextLong(), x, startY, z, thickness, yaw, pitch, 0, distance, yScale, output);
        return true;
    }

    private void doCarve(WorldGenerationContext context, ChunkPos chunkPos, long seed, double x, double y, double z,
                         float thickness, float yaw, float pitch, int step, int distance, double yScale,
                         CarverOutput output) {
        RandomSource random = RandomSource.createThreadLocalInstance(seed);
        float[] widthFactors = initWidthFactors(context, random);
        float yawChange = 0.0F;
        float pitchChange = 0.0F;
        for (int i = step; i < distance; i++) {
            double horizontalRadius = 1.5 + (double) (Mth.sin((double) ((float) i * 3.1415927F / (float) distance)) * thickness);
            double verticalRadius = horizontalRadius * yScale;
            horizontalRadius *= (double) shape.horizontalRadiusFactor().sample(random);
            verticalRadius = updateVerticalRadius(random, verticalRadius, (float) distance, (float) i);
            float cosPitch = Mth.cos((double) pitch);
            float sinPitch = Mth.sin((double) pitch);
            x += (double) (Mth.cos((double) yaw) * cosPitch);
            y += (double) sinPitch;
            z += (double) (Mth.sin((double) yaw) * cosPitch);
            pitch *= 0.7F;
            pitch += pitchChange * 0.05F;
            yaw += yawChange * 0.05F;
            pitchChange *= 0.8F;
            yawChange *= 0.5F;
            pitchChange += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 2.0F;
            yawChange += (random.nextFloat() - random.nextFloat()) * random.nextFloat() * 4.0F;
            if (random.nextInt(4) != 0) {
                if (!WorldCarver.canReach(chunkPos, x, z, i, distance, thickness)) {
                    return;
                }
                WorldCarver.carveEllipsoid(chunkPos, x, y, z, horizontalRadius, verticalRadius, output,
                        (relX, relY, relZ, blockY) -> shouldSkip(context, widthFactors, relX, relY, relZ, blockY));
            }
        }
    }

    /** One factor per generation layer: re-rolled ({@code 1 + nextFloat * nextFloat}) at layer 0 and with chance 1/widthSmoothness; stored squared. */
    private float[] initWidthFactors(WorldGenerationContext context, RandomSource random) {
        int depth = context.getGenDepth();
        float[] factors = new float[depth];
        float factor = 1.0F;
        for (int i = 0; i < depth; i++) {
            if (i == 0 || random.nextInt(shape.widthSmoothness()) == 0) {
                factor = 1.0F + random.nextFloat() * random.nextFloat();
            }
            factors[i] = factor * factor;
        }
        return factors;
    }

    private double updateVerticalRadius(RandomSource random, double verticalRadius, float distance, float step) {
        float centerness = 1.0F - Mth.abs(0.5F - step / distance) * 2.0F;
        float factor = shape.verticalRadiusDefaultFactor() + shape.verticalRadiusCenterFactor() * centerness;
        return (double) factor * verticalRadius * (double) Mth.randomBetween(random, 0.75F, 1.0F);
    }

    private boolean shouldSkip(WorldGenerationContext context, float[] widthFactors, double relX, double relY,
                               double relZ, int blockY) {
        int layer = blockY - context.getMinGenY();
        return (relX * relX + relZ * relZ) * (double) widthFactors[layer - 1] + relY * relY / 6.0 >= 1.0;
    }
}
