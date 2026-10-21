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

    /** {@code Mth.sqrt(float)}: {@code (float) Math.sqrt((double) x)}. */
    public static float sqrt(float x) {
        return (float) Math.sqrt(x);
    }

    /** {@code Mth.cube(float)}: {@code x * x * x}. */
    public static float cube(float x) {
        return x * x * x;
    }

    /** {@code Mth.lengthSquared(float, float)}: {@code x * x + y * y}. */
    public static float lengthSquared(float x, float y) {
        return x * x + y * y;
    }

    /** {@code Mth.length(float, float)}: {@code (float) Math.sqrt((double) lengthSquared(x, y))}. */
    public static float length(float x, float y) {
        return (float) Math.sqrt(lengthSquared(x, y));
    }

    /** {@code Mth.lengthSquared(float, float, float)}: {@code x * x + y * y + z * z}. */
    public static float lengthSquared(float x, float y, float z) {
        return x * x + y * y + z * z;
    }

    /** {@code Mth.length(float, float, float)}: {@code sqrt(lengthSquared(x, y, z))}. */
    public static float length(float x, float y, float z) {
        return sqrt(lengthSquared(x, y, z));
    }

    /** {@code Mth.floorDiv(int, int)}: {@code Math.floorDiv(a, b)}. */
    public static int floorDiv(int a, int b) {
        return Math.floorDiv(a, b);
    }

    /** {@code Mth.positiveCeilDiv(int, int)}: {@code -Math.floorDiv(-input, divisor)}. */
    public static int positiveCeilDiv(int input, int divisor) {
        return -Math.floorDiv(-input, divisor);
    }

    /** {@code Mth.roundToward(int, int)}: {@code positiveCeilDiv(input, multiple) * multiple}. */
    public static int roundToward(int input, int multiple) {
        return positiveCeilDiv(input, multiple) * multiple;
    }

    /**
     * {@code Mth.binarySearch(int, int, IntPredicate)}: the first index in {@code [from, to)} for which the
     * (monotonic) condition holds, or {@code to}.
     */
    public static int binarySearch(int from, int to, java.util.function.IntPredicate condition) {
        int len = to - from;
        while (len > 0) {
            int half = len / 2;
            int middle = from + half;
            if (condition.test(middle)) {
                len = half;
            } else {
                from = middle + 1;
                len -= half + 1;
            }
        }
        return from;
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

    /** {@code Mth.SIN_SCALE}: 65536 / (2 pi) as the 26.3 constant pool stores it. */
    private static final double SIN_SCALE = 10430.378350470453;

    /** {@code Mth.SIN}: 65536 entries, {@code SIN[i] = (float) Math.sin(i / SIN_SCALE)} (static initialiser lambda). */
    private static final float[] SIN = new float[65536];

    static {
        for (int i = 0; i < SIN.length; i++) {
            SIN[i] = (float) Math.sin((double) i / SIN_SCALE);
        }
    }

    /** {@code Mth.sin(double)}: table lookup {@code SIN[(int) ((long) (v * SIN_SCALE) & 65535)]}. */
    public static float sin(double v) {
        return SIN[(int) ((long) (v * SIN_SCALE) & 65535L)];
    }

    /** {@code Mth.cos(double)}: table lookup {@code SIN[(int) ((long) (v * SIN_SCALE + 16384.0) & 65535)]}. */
    public static float cos(double v) {
        return SIN[(int) ((long) (v * SIN_SCALE + 16384.0) & 65535L)];
    }

    /** {@code Mth.abs(float)}: {@code Math.abs(v)}. */
    public static float abs(float v) {
        return Math.abs(v);
    }

    /** {@code Mth.randomBetweenInclusive(RandomSource,int,int)}: {@code random.nextInt(max - min + 1) + min}. */
    public static int randomBetweenInclusive(com.example.worldgen.random.RandomSource random, int min, int max) {
        return random.nextInt(max - min + 1) + min;
    }

    /** {@code Mth.randomBetween(RandomSource,float,float)}: {@code random.nextFloat() * (max - min) + min}. */
    public static float randomBetween(com.example.worldgen.random.RandomSource random, float min, float max) {
        return random.nextFloat() * (max - min) + min;
    }

    /** {@code Mth.nextInt(RandomSource,int,int)}: {@code min >= max ? min : random.nextInt(max - min + 1) + min}. */
    public static int nextInt(com.example.worldgen.random.RandomSource random, int min, int max) {
        if (min >= max) {
            return min;
        }
        return random.nextInt(max - min + 1) + min;
    }

    /** {@code Mth.normal(RandomSource,float,float)}: {@code mean + (float) random.nextGaussian() * deviation}. */
    public static float normal(com.example.worldgen.random.RandomSource random, float mean, float deviation) {
        return mean + (float) random.nextGaussian() * deviation;
    }
}
