package com.example.worldgen.noise;

import com.example.worldgen.Interval;
import com.example.worldgen.Mth;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.random.RandomSource;

/**
 * Port of {@code net.minecraft.world.level.levelgen.synth.PerlinNoise} (26.3): ONE octave of
 * improved Perlin noise (in 26.3 this class replaced the old {@code ImprovedNoise}; multi-octave
 * sums are {@link NoiseStack}). Coordinates are wrapped and offset in double, the fractional
 * parts are narrowed to float, and gradients, smoothstep and trilinear interpolation run in
 * float, as in the 26.3 bytecode. Range: {@code [-2, 2]}.
 */
public class PerlinNoise extends GradientNoise {

    public static final Interval RANGE = Interval.ofSymmetric(2.0F);
    public static final double STANDARD_DEVIATION = 0.2702247831245211;

    public PerlinNoise(RandomSource random) {
        super(random);
    }

    @Override
    public Interval range() {
        return RANGE;
    }

    /** 2D sample: {@code get(wrap(x), 0, wrap(z))} (dispatches to the subclass's 3D form). */
    @Override
    public float get(double x, double z) {
        return get(wrap(x), 0.0, wrap(z));
    }

    @Override
    public float get(double x, double y, double z) {
        double px = wrap(x) + offsetX;
        double py = wrap(y) + offsetY;
        double pz = wrap(z) + offsetZ;
        int ix = Mth.floor(px);
        int iy = Mth.floor(py);
        int iz = Mth.floor(pz);
        float fx = (float) (px - (double) ix);
        float fy = (float) (py - (double) iy);
        float fz = (float) (pz - (double) iz);
        return sampleAndLerp(ix, iy, iz, fx, fy, fz, fy);
    }

    /**
     * Sample plus analytic gradient: adds d/dx, d/dy, d/dz of this octave into
     * {@code derivativeOut[0..2]} and returns the value.
     */
    public float noiseWithDerivative(double x, double y, double z, float[] derivativeOut) {
        double px = wrap(x) + offsetX;
        double py = wrap(y) + offsetY;
        double pz = wrap(z) + offsetZ;
        int ix = Mth.floor(px);
        int iy = Mth.floor(py);
        int iz = Mth.floor(pz);
        float fx = (float) (px - (double) ix);
        float fy = (float) (py - (double) iy);
        float fz = (float) (pz - (double) iz);
        return sampleWithDerivative(ix, iy, iz, fx, fy, fz, derivativeOut);
    }

    /**
     * Gradient dot products at the 8 cell corners, smoothstep weights, trilinear lerp.
     * {@code fySmooth} is the y fraction used for the interpolation weight (it differs from the
     * gradient y fraction {@code fy} only in {@link SmearedPerlinNoise}).
     */
    protected float sampleAndLerp(int ix, int iy, int iz, float fx, float fy, float fz, float fySmooth) {
        int a = permute(ix);
        int b = permute(ix + 1);
        int aa = permute(a + iy);
        int ab = permute(a + iy + 1);
        int ba = permute(b + iy);
        int bb = permute(b + iy + 1);
        float d000 = gradDot(permute(aa + iz), fx, fy, fz);
        float d100 = gradDot(permute(ba + iz), fx - 1.0F, fy, fz);
        float d010 = gradDot(permute(ab + iz), fx, fy - 1.0F, fz);
        float d110 = gradDot(permute(bb + iz), fx - 1.0F, fy - 1.0F, fz);
        float d001 = gradDot(permute(aa + iz + 1), fx, fy, fz - 1.0F);
        float d101 = gradDot(permute(ba + iz + 1), fx - 1.0F, fy, fz - 1.0F);
        float d011 = gradDot(permute(ab + iz + 1), fx, fy - 1.0F, fz - 1.0F);
        float d111 = gradDot(permute(bb + iz + 1), fx - 1.0F, fy - 1.0F, fz - 1.0F);
        float sx = Mth.smoothstep(fx);
        float sy = Mth.smoothstep(fySmooth);
        float sz = Mth.smoothstep(fz);
        return Mth.lerp3(sx, sy, sz, d000, d100, d010, d110, d001, d101, d011, d111);
    }

    /**
     * {@code PerlinNoise.addToVolume}: the batch form. For each (z, x) column it walks y, recomputing the eight corner
     * gradients only when the y cell changes; corner values are {@code dotXz + gradY * relativeY} (y fraction
     * {@code relativeY - 1} for the upper corners), then {@code lerp3} with smoothstep weights; the result times
     * {@code amplitude} is added to the buffer. The float rounding differs from {@link #get(double, double, double)}
     * (separate xz dot product), exactly as in vanilla.
     */
    @Override
    public void addToVolume(DensityBuffer buffer, DensityVolume volume, double xzScale, double yScale, float amplitude) {
        float d000xz = 0.0F;
        float d100xz = 0.0F;
        float d010xz = 0.0F;
        float d110xz = 0.0F;
        float d001xz = 0.0F;
        float d101xz = 0.0F;
        float d011xz = 0.0F;
        float d111xz = 0.0F;
        float g000y = 0.0F;
        float g100y = 0.0F;
        float g010y = 0.0F;
        float g110y = 0.0F;
        float g001y = 0.0F;
        float g101y = 0.0F;
        float g011y = 0.0F;
        float g111y = 0.0F;
        int index = 0;
        for (int indexZ = 0; indexZ < volume.sizeZ(); ++indexZ) {
            double z = wrap((double) volume.blockZ(indexZ) * xzScale) + offsetZ;
            int floorZ = Mth.floor(z);
            float relativeZ = (float) (z - (double) floorZ);
            float alphaZ = Mth.smoothstep(relativeZ);
            for (int indexX = 0; indexX < volume.sizeX(); ++indexX) {
                double x = wrap((double) volume.blockX(indexX) * xzScale) + offsetX;
                int floorX = Mth.floor(x);
                float relativeX = (float) (x - (double) floorX);
                int x0 = permute(floorX);
                int x1 = permute(floorX + 1);
                float alphaX = Mth.smoothstep(relativeX);
                int lastFloorY = Integer.MIN_VALUE;
                for (int indexY = 0; indexY < volume.sizeY(); ++indexY) {
                    double y = wrap((double) volume.blockY(indexY) * yScale) + offsetY;
                    int floorY = Mth.floor(y);
                    float relativeY = (float) (y - (double) floorY);
                    float alphaY = Mth.smoothstep(relativeY);
                    if (lastFloorY != floorY) {
                        int xy00 = permute(x0 + floorY);
                        int xy01 = permute(x0 + floorY + 1);
                        int xy10 = permute(x1 + floorY);
                        int xy11 = permute(x1 + floorY + 1);
                        Gradient g000 = permuteToGrad(xy00 + floorZ);
                        d000xz = g000.dotXz(relativeX, relativeZ);
                        g000y = (float) g000.y();
                        Gradient g100 = permuteToGrad(xy10 + floorZ);
                        d100xz = g100.dotXz(relativeX - 1.0F, relativeZ);
                        g100y = (float) g100.y();
                        Gradient g010 = permuteToGrad(xy01 + floorZ);
                        d010xz = g010.dotXz(relativeX, relativeZ);
                        g010y = (float) g010.y();
                        Gradient g110 = permuteToGrad(xy11 + floorZ);
                        d110xz = g110.dotXz(relativeX - 1.0F, relativeZ);
                        g110y = (float) g110.y();
                        Gradient g001 = permuteToGrad(xy00 + floorZ + 1);
                        d001xz = g001.dotXz(relativeX, relativeZ - 1.0F);
                        g001y = (float) g001.y();
                        Gradient g101 = permuteToGrad(xy10 + floorZ + 1);
                        d101xz = g101.dotXz(relativeX - 1.0F, relativeZ - 1.0F);
                        g101y = (float) g101.y();
                        Gradient g011 = permuteToGrad(xy01 + floorZ + 1);
                        d011xz = g011.dotXz(relativeX, relativeZ - 1.0F);
                        g011y = (float) g011.y();
                        Gradient g111 = permuteToGrad(xy11 + floorZ + 1);
                        d111xz = g111.dotXz(relativeX - 1.0F, relativeZ - 1.0F);
                        g111y = (float) g111.y();
                        lastFloorY = floorY;
                    }
                    buffer.addTo(index, amplitude * Mth.lerp3(alphaX, alphaY, alphaZ,
                            d000xz + g000y * relativeY, d100xz + g100y * relativeY,
                            d010xz + g010y * (relativeY - 1.0F), d110xz + g110y * (relativeY - 1.0F),
                            d001xz + g001y * relativeY, d101xz + g101y * relativeY,
                            d011xz + g011y * (relativeY - 1.0F), d111xz + g111y * (relativeY - 1.0F)));
                    ++index;
                }
            }
        }
    }

    private float sampleWithDerivative(int ix, int iy, int iz, float fx, float fy, float fz, float[] out) {
        int a = permute(ix);
        int b = permute(ix + 1);
        int aa = permute(a + iy);
        int ab = permute(a + iy + 1);
        int ba = permute(b + iy);
        int bb = permute(b + iy + 1);
        Gradient g000 = permuteToGrad(aa + iz);
        Gradient g100 = permuteToGrad(ba + iz);
        Gradient g010 = permuteToGrad(ab + iz);
        Gradient g110 = permuteToGrad(bb + iz);
        Gradient g001 = permuteToGrad(aa + iz + 1);
        Gradient g101 = permuteToGrad(ba + iz + 1);
        Gradient g011 = permuteToGrad(ab + iz + 1);
        Gradient g111 = permuteToGrad(bb + iz + 1);
        float d000 = g000.dot(fx, fy, fz);
        float d100 = g100.dot(fx - 1.0F, fy, fz);
        float d010 = g010.dot(fx, fy - 1.0F, fz);
        float d110 = g110.dot(fx - 1.0F, fy - 1.0F, fz);
        float d001 = g001.dot(fx, fy, fz - 1.0F);
        float d101 = g101.dot(fx - 1.0F, fy, fz - 1.0F);
        float d011 = g011.dot(fx, fy - 1.0F, fz - 1.0F);
        float d111 = g111.dot(fx - 1.0F, fy - 1.0F, fz - 1.0F);
        float sx = Mth.smoothstep(fx);
        float sy = Mth.smoothstep(fy);
        float sz = Mth.smoothstep(fz);
        float gx = Mth.lerp3(sx, sy, sz, (float) g000.x(), (float) g100.x(), (float) g010.x(), (float) g110.x(),
                (float) g001.x(), (float) g101.x(), (float) g011.x(), (float) g111.x());
        float gy = Mth.lerp3(sx, sy, sz, (float) g000.y(), (float) g100.y(), (float) g010.y(), (float) g110.y(),
                (float) g001.y(), (float) g101.y(), (float) g011.y(), (float) g111.y());
        float gz = Mth.lerp3(sx, sy, sz, (float) g000.z(), (float) g100.z(), (float) g010.z(), (float) g110.z(),
                (float) g001.z(), (float) g101.z(), (float) g011.z(), (float) g111.z());
        float dx = Mth.lerp2(sy, sz, d100 - d000, d110 - d010, d101 - d001, d111 - d011);
        float dy = Mth.lerp2(sz, sx, d010 - d000, d011 - d001, d110 - d100, d111 - d101);
        float dz = Mth.lerp2(sx, sy, d001 - d000, d101 - d100, d011 - d010, d111 - d110);
        float wx = Mth.smoothstepDerivative(fx);
        float wy = Mth.smoothstepDerivative(fy);
        float wz = Mth.smoothstepDerivative(fz);
        float derX = gx + wx * dx;
        float derY = gy + wy * dy;
        float derZ = gz + wz * dz;
        out[0] += derX;
        out[1] += derY;
        out[2] += derZ;
        return Mth.lerp3(sx, sy, sz, d000, d100, d010, d110, d001, d101, d011, d111);
    }

    /** Vanilla's debug description of this octave. */
    public void parityConfigString(StringBuilder sb) {
        NoiseUtils.parityNoiseOctaveConfigString(sb, offsetX, offsetY, offsetZ, perms);
    }
}
