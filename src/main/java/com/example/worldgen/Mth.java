package com.example.worldgen;

/**
 * The subset of {@code net.minecraft.util.Mth} (Minecraft Java 26.3) that the world generation
 * port needs. Every method is a one-to-one transcription of the 26.3 bytecode (read with
 * {@code javap -c} from the 26.3 client jar), keeping the exact operation order and the
 * float-versus-double split, because worldgen output depends on bit-exact arithmetic.
 */
public final class Mth {

    private Mth() {
    }

    /** {@code Mth.floor(float)}: {@code (int) Math.floor((double) v)}. */
    public static int floor(float v) {
        return (int) Math.floor(v);
    }

    /** {@code Mth.floor(double)}: {@code (int) Math.floor(v)} (d2i saturates). */
    public static int floor(double v) {
        return (int) Math.floor(v);
    }

    /** {@code Mth.lfloor(double)}: {@code (long) Math.floor(v)}. */
    public static long lfloor(double v) {
        return (long) Math.floor(v);
    }

    /** {@code Mth.clamp(int,int,int)}: {@code Math.min(Math.max(v, min), max)}. */
    public static int clamp(int v, int min, int max) {
        return Math.min(Math.max(v, min), max);
    }

    /** {@code Mth.clamp(long,long,long)}: {@code Math.min(Math.max(v, min), max)}. */
    public static long clamp(long v, long min, long max) {
        return Math.min(Math.max(v, min), max);
    }

    /** {@code Mth.clamp(float,float,float)}: below {@code min} returns {@code min}, else {@code Math.min(v, max)}. */
    public static float clamp(float v, float min, float max) {
        if (v < min) {
            return min;
        }
        return Math.min(v, max);
    }

    /** {@code Mth.clamp(double,double,double)}: below {@code min} returns {@code min}, else {@code Math.min(v, max)}. */
    public static double clamp(double v, double min, double max) {
        if (v < min) {
            return min;
        }
        return Math.min(v, max);
    }

    /** {@code Mth.clampedLerp(double,double,double)}: {@code t < 0 -> a}, {@code t > 1 -> b}, else {@code lerp}. */
    public static double clampedLerp(double t, double a, double b) {
        if (t < 0.0) {
            return a;
        }
        if (t > 1.0) {
            return b;
        }
        return lerp(t, a, b);
    }

    /** {@code Mth.clampedLerp(float,float,float)}. */
    public static float clampedLerp(float t, float a, float b) {
        if (t < 0.0F) {
            return a;
        }
        if (t > 1.0F) {
            return b;
        }
        return lerp(t, a, b);
    }

    /** {@code Mth.wrapDegrees(int)}: {@code v % 360} folded into [-180, 180). */
    public static int wrapDegrees(int v) {
        int r = v % 360;
        if (r >= 180) {
            r -= 360;
        }
        if (r < -180) {
            r += 360;
        }
        return r;
    }

    /** {@code Mth.wrapDegrees(float)}. */
    public static float wrapDegrees(float v) {
        float r = v % 360.0F;
        if (r >= 180.0F) {
            r -= 360.0F;
        }
        if (r < -180.0F) {
            r += 360.0F;
        }
        return r;
    }

    /** {@code Mth.wrapDegrees(double)}. */
    public static double wrapDegrees(double v) {
        double r = v % 360.0;
        if (r >= 180.0) {
            r -= 360.0;
        }
        if (r < -180.0) {
            r += 360.0;
        }
        return r;
    }

    /** {@code Mth.frac(double)}: {@code v - (double) lfloor(v)}. */
    public static double frac(double v) {
        return v - (double) lfloor(v);
    }

    /**
     * {@code Mth.getSeed(int,int,int)}: the positional hash used by every
     * {@code PositionalRandomFactory.at}. Note the int multiply of {@code x} (it overflows in int),
     * the long multiply of {@code z}, and the arithmetic shift at the end.
     */
    public static long getSeed(int x, int y, int z) {
        long seed = (long) (x * 3129871) ^ (long) z * 116129781L ^ (long) y;
        seed = seed * seed * 42317861L + seed * 11L;
        return seed >> 16;
    }

    /** {@code Mth.inverseLerp(double,double,double)}: {@code (v - min) / (max - min)}. */
    public static double inverseLerp(double v, double min, double max) {
        return (v - min) / (max - min);
    }

    /** {@code Mth.inverseLerp(float,float,float)}. */
    public static float inverseLerp(float v, float min, float max) {
        return (v - min) / (max - min);
    }

    /** {@code Mth.lerp(float,float,float)}: {@code a + t * (b - a)}. */
    public static float lerp(float t, float a, float b) {
        return a + t * (b - a);
    }

    /** {@code Mth.lerp(double,double,double)}: {@code a + t * (b - a)}. */
    public static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /** {@code Mth.lerp2(float...)}: lerp along y of two x-lerps. */
    public static float lerp2(float tx, float ty, float v00, float v10, float v01, float v11) {
        return lerp(ty, lerp(tx, v00, v10), lerp(tx, v01, v11));
    }

    /** {@code Mth.lerp2(double...)}. */
    public static double lerp2(double tx, double ty, double v00, double v10, double v01, double v11) {
        return lerp(ty, lerp(tx, v00, v10), lerp(tx, v01, v11));
    }

    /** {@code Mth.lerp3(float...)}: lerp along z of two {@link #lerp2} planes. */
    public static float lerp3(float tx, float ty, float tz,
                              float v000, float v100, float v010, float v110,
                              float v001, float v101, float v011, float v111) {
        return lerp(tz, lerp2(tx, ty, v000, v100, v010, v110), lerp2(tx, ty, v001, v101, v011, v111));
    }

    /** {@code Mth.lerp3(double...)}. */
    public static double lerp3(double tx, double ty, double tz,
                               double v000, double v100, double v010, double v110,
                               double v001, double v101, double v011, double v111) {
        return lerp(tz, lerp2(tx, ty, v000, v100, v010, v110), lerp2(tx, ty, v001, v101, v011, v111));
    }

    /** {@code Mth.smoothstep(float)}: {@code t*t*t * (t * (t*6 - 15) + 10)} in float. */
    public static float smoothstep(float t) {
        return t * t * t * (t * (t * 6.0F - 15.0F) + 10.0F);
    }

    /** {@code Mth.smoothstepDerivative(float)}: {@code 30*t*t*(t-1)*(t-1)} in float, left to right. */
    public static float smoothstepDerivative(float t) {
        return 30.0F * t * t * (t - 1.0F) * (t - 1.0F);
    }

    /** {@code Mth.square(double)}. */
    public static double square(double v) {
        return v * v;
    }

    /** {@code Mth.square(float)}. */
    public static float square(float v) {
        return v * v;
    }

    /** {@code Mth.square(int)}. */
    public static int square(int v) {
        return v * v;
    }

    /** {@code Mth.square(long)}. */
    public static long square(long v) {
        return v * v;
    }

    /** {@code Mth.clampedMap(double...)}: {@code clampedLerp(inverseLerp(v, a, b), c, d)}. */
    public static double clampedMap(double v, double fromMin, double fromMax, double toMin, double toMax) {
        return clampedLerp(inverseLerp(v, fromMin, fromMax), toMin, toMax);
    }

    /** {@code Mth.clampedMap(float...)}. */
    public static float clampedMap(float v, float fromMin, float fromMax, float toMin, float toMax) {
        return clampedLerp(inverseLerp(v, fromMin, fromMax), toMin, toMax);
    }

    /** {@code Mth.map(double...)}: {@code lerp(inverseLerp(v, a, b), c, d)}. */
    public static double map(double v, double fromMin, double fromMax, double toMin, double toMax) {
        return lerp(inverseLerp(v, fromMin, fromMax), toMin, toMax);
    }

    /** {@code Mth.map(float...)}. */
    public static float map(float v, float fromMin, float fromMax, float toMin, float toMax) {
        return lerp(inverseLerp(v, fromMin, fromMax), toMin, toMax);
    }
}
