package com.example.worldgen.noise;

import com.example.worldgen.Interval;
import com.example.worldgen.Mth;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityVolume;
import com.example.worldgen.random.RandomSource;

/**
 * Port of {@code net.minecraft.world.level.levelgen.synth.SmearedPerlinNoise} (26.3): a Perlin
 * octave whose gradient y fraction is quantised ("smeared") to steps of {@code fudgeYScale}
 * (the legacy {@code yScale/yMax} behaviour of old {@code ImprovedNoise}, used by
 * {@link BlendedNoise}). The {@code y} argument of {@link #get(double, double, double)} doubles
 * as the "yMax" clamp exactly as in the 26.3 bytecode; the interpolation weight still uses the
 * unsmeared y fraction. Epsilon: {@code SHIFT_UP_EPSILON = 1.0E-7f}, widened to double.
 */
public class SmearedPerlinNoise extends PerlinNoise {

    private static final float SHIFT_UP_EPSILON = 1.0E-7F;

    private final double fudgeYScale;

    public SmearedPerlinNoise(RandomSource random, double fudgeYScale) {
        super(random);
        this.fudgeYScale = fudgeYScale;
    }

    /** {@code ofSymmetric((float) (|fudgeYScale| + 2))}. */
    public static Interval range(double fudgeYScale) {
        return Interval.ofSymmetric((float) (Math.abs(fudgeYScale) + 2.0));
    }

    @Override
    public Interval range() {
        return range(fudgeYScale);
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
        double fy = py - (double) iy;
        float fz = (float) (pz - (double) iz);
        float smearedFy = (float) (fy - computeFudgeY(y, fy));
        return sampleAndLerp(ix, iy, iz, fx, smearedFy, fz, (float) fy);
    }

    private double computeFudgeY(double yMax, double fy) {
        double d = yMax >= 0.0 && yMax < fy ? yMax : fy;
        return (double) Mth.floor(d / fudgeYScale + (double) SHIFT_UP_EPSILON) * fudgeYScale;
    }

    /**
     * {@code SmearedPerlinNoise.addToVolume}: the batch form of {@link PerlinNoise#addToVolume} with the smeared y
     * fraction ({@code relativeY - computeFudgeY(originalY, relativeY)}) in the corner values and the unsmeared one
     * in the y weight, as in the 26.3 bytecode.
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
                    double originalY = (double) volume.blockY(indexY) * yScale;
                    double y = wrap(originalY) + offsetY;
                    int floorY = Mth.floor(y);
                    double relativeY = y - (double) floorY;
                    float alphaY = Mth.smoothstep((float) relativeY);
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
                    float fudgedRelativeY = (float) (relativeY - computeFudgeY(originalY, relativeY));
                    buffer.addTo(index, amplitude * Mth.lerp3(alphaX, alphaY, alphaZ,
                            d000xz + g000y * fudgedRelativeY, d100xz + g100y * fudgedRelativeY,
                            d010xz + g010y * (fudgedRelativeY - 1.0F), d110xz + g110y * (fudgedRelativeY - 1.0F),
                            d001xz + g001y * fudgedRelativeY, d101xz + g101y * fudgedRelativeY,
                            d011xz + g011y * (fudgedRelativeY - 1.0F), d111xz + g111y * (fudgedRelativeY - 1.0F)));
                    ++index;
                }
            }
        }
    }
}
