package com.example.worldgen.noise;

import java.util.Locale;

/** Port of {@code net.minecraft.world.level.levelgen.synth.NoiseUtils} (26.3). */
public final class NoiseUtils {

    private NoiseUtils() {
    }

    /** {@code v + sin(PI * v) * strength / PI}. */
    public static double biasTowardsExtreme(double v, double strength) {
        return v + Math.sin(Math.PI * v) * strength / Math.PI;
    }

    /** Vanilla's debug line for one octave: offsets as floats and permutation entries 0 and 255. */
    public static void parityNoiseOctaveConfigString(StringBuilder sb, double xo, double yo, double zo, byte[] perms) {
        sb.append(String.format(Locale.ROOT, "xo=%.3f, yo=%.3f, zo=%.3f, p0=%d, p255=%d",
                (float) xo, (float) yo, (float) zo, perms[0], perms[255]));
    }
}
