package com.example.worldgen;

import com.example.worldgen.noise.BlendedNoise;
import com.example.worldgen.noise.Noise;
import com.example.worldgen.noise.NormalNoise;
import com.example.worldgen.noise.PerlinNoise;
import com.example.worldgen.noise.SimplexNoise;
import com.example.worldgen.noise.SmearedPerlinNoise;
import com.example.worldgen.random.LegacyRandomSource;
import com.example.worldgen.random.PositionalRandomFactory;
import com.example.worldgen.random.RandomSource;
import com.example.worldgen.random.RandomSupport;
import com.example.worldgen.random.SingleThreadedRandomSource;
import com.example.worldgen.random.WorldgenRandom;
import com.example.worldgen.random.XoroshiroRandomSource;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Compares the port bit-for-bit against golden vectors produced by running the REAL Minecraft
 * Java 26.3 classes (client jar, with only codec/registry static initialisers stubbed out) through
 * an oracle program that performs exactly the computations of {@link #generate()} below. The
 * expected values are in {@link GoldenVectors26_3}; floats and doubles are compared through their
 * raw bit patterns.
 */
class GoldenVectorsTest {

    private final StringBuilder out = new StringBuilder();

    private static final long[] SEEDS = {0L, 1L, -1L, 12345L, 8682522807148012L, Long.MIN_VALUE, 0x5DEECE66DL, -4172144997902289642L};
    private static final double[][] PTS = {
            {0, 0, 0}, {0.5, 0.25, 0.75}, {-13.37, 64.0, 1024.5}, {123456.789, -45.6, -98765.4321},
            {1.0e-3, 2.0e-3, -3.0e-3}, {33554432.5, 70.0, -33554431.25}, {-0.0001, 255.9999, 256.0001}, {31.9, -31.9, 7.77}
    };

    @Test
    void portMatchesVanilla26_3() {
        generate();
        Map<String, String> expected = toMap(GoldenVectors26_3.LINES);
        Map<String, String> actual = toMap(out.toString().split("\n"));
        assertEquals(expected.size(), actual.size(), "vector count");
        StringBuilder mismatches = new StringBuilder();
        int bad = 0;
        for (Map.Entry<String, String> e : expected.entrySet()) {
            String got = actual.get(e.getKey());
            if (!e.getValue().equals(got)) {
                if (bad++ < 25) {
                    mismatches.append(e.getKey()).append("\n  vanilla: ").append(e.getValue()).append("\n  port:    ").append(got).append('\n');
                }
            }
        }
        if (bad > 0) {
            fail(bad + " of " + expected.size() + " vectors differ from vanilla 26.3:\n" + mismatches);
        }
    }

    private static Map<String, String> toMap(String[] lines) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            int space = line.indexOf(' ');
            map.put(line.substring(0, space), line.substring(space + 1));
        }
        return map;
    }

    private void p(String k, long v) {
        out.append(k).append(' ').append(v).append('\n');
    }

    private void pi(String k, int v) {
        out.append(k).append(' ').append(v).append('\n');
    }

    private void pf(String k, float v) {
        out.append(k).append(' ').append(Float.floatToRawIntBits(v)).append(" //").append(v).append('\n');
    }

    private void generate() {
        for (long s : SEEDS) {
            p("mix." + s, RandomSupport.mixStafford13(s));
            RandomSupport.Seed128bit u = RandomSupport.upgradeSeedTo128bit(s);
            p("up.lo." + s, u.seedLo());
            p("up.hi." + s, u.seedHi());
        }
        String[] names = {"", "minecraft:terrain", "octave_-9", "minecraft:continentalness", "ÿé€😀"};
        for (int i = 0; i < names.length; i++) {
            RandomSupport.Seed128bit h = RandomSupport.seedFromHashOf(names[i]);
            p("md5.lo." + i, h.seedLo());
            p("md5.hi." + i, h.seedHi());
        }
        int[][] pos = {{0, 0, 0}, {1, 2, 3}, {-1, -64, -1}, {30000000, 320, -30000000}, {Integer.MAX_VALUE, Integer.MIN_VALUE, 12345}};
        for (int[] q : pos) {
            p("getSeed." + q[0] + "." + q[1] + "." + q[2], Mth.getSeed(q[0], q[1], q[2]));
        }
        for (long s : SEEDS) {
            dumpRandom("leg." + s, new LegacyRandomSource(s));
            dumpRandom("xor." + s, new XoroshiroRandomSource(s));
            dumpRandom("st." + s, new SingleThreadedRandomSource(s));
            XoroshiroRandomSource x = new XoroshiroRandomSource(s);
            RandomSource f = x.fork();
            p("xor.fork." + s, f.nextLong());
            PositionalRandomFactory pf = new XoroshiroRandomSource(s).forkPositional();
            p("xor.at." + s, pf.at(17, -42, 99999).nextLong());
            p("xor.hash." + s, pf.fromHashOf("minecraft:terrain").nextLong());
            p("xor.fromSeed." + s, pf.fromSeed(987654321L).nextLong());
            PositionalRandomFactory lf = new LegacyRandomSource(s).forkPositional();
            p("leg.at." + s, lf.at(17, -42, 99999).nextLong());
            p("leg.hash." + s, lf.fromHashOf("minecraft:terrain").nextLong());
            p("leg.fromSeed." + s, lf.fromSeed(987654321L).nextLong());
            WorldgenRandom w = new WorldgenRandom(new XoroshiroRandomSource(s));
            p("wg.deco." + s, w.setDecorationSeed(s, 160, -320));
            p("wg.deco.next." + s, w.nextLong());
            w.setFeatureSeed(s ^ 77L, 3, 9);
            pi("wg.feat.next." + s, w.nextInt(1000));
            pi("wg.count." + s, w.getCount());
            WorldgenRandom wl = new WorldgenRandom(new LegacyRandomSource(s));
            wl.setLargeFeatureSeed(s, 5, -7);
            p("wgl.large." + s, wl.nextLong());
            wl.setLargeFeatureWithSalt(s, 5, -7, 14357617);
            pi("wgl.salt." + s, wl.nextInt(16));
            p("slime." + s, WorldgenRandom.seedSlimeChunk(3, -4, s, 987234911L).nextInt(10));
        }
        for (long s : SEEDS) {
            PerlinNoise pn = new PerlinNoise(new XoroshiroRandomSource(s));
            SmearedPerlinNoise sp = new SmearedPerlinNoise(new LegacyRandomSource(s), 0.7);
            SimplexNoise sx = new SimplexNoise(new LegacyRandomSource(s));
            SimplexNoise sx0 = new SimplexNoise(new LegacyRandomSource(s), true);
            for (int i = 0; i < PTS.length; i++) {
                double[] q = PTS[i];
                pf("perlin." + s + "." + i, pn.get(q[0], q[1], q[2]));
                pf("perlin2." + s + "." + i, pn.get(q[0], q[2]));
                pf("smeared." + s + "." + i, sp.get(q[0], q[1], q[2]));
                pf("simplex3." + s + "." + i, sx.get(q[0], q[1], q[2]));
                pf("simplex2." + s + "." + i, sx.get(q[0], q[1]));
                pf("simplex0." + s + "." + i, sx0.get(q[0], q[1], q[2]));
            }
            float[] der = new float[3];
            float v = pn.noiseWithDerivative(PTS[2][0], PTS[2][1], PTS[2][2], der);
            pf("deriv.v." + s, v);
            pf("deriv.0." + s, der[0]);
            pf("deriv.1." + s, der[1]);
            pf("deriv.2." + s, der[2]);
        }
        Object[][] specs = {
                {"continentalness", 0.8880832896205223, -9, new double[]{1.0, 1.0, 2.0, 2.0, 2.0, 1.0, 1.0, 1.0, 1.0}},
                {"temperature", 1.2453007926713473, -10, new double[]{1.5, 0.0, 1.0, 0.0, 0.0, 0.0}},
                {"offset", 0.9381732587751005, -3, new double[]{1.0, 1.0, 1.0, 0.0}},
                {"surface", 0.9381732587751008, -6, new double[]{1.0, 1.0, 1.0}},
                {"spaghetti_3d_1", 0.955388882960065, -7, new double[]{1.0}},
        };
        for (Object[] spec : specs) {
            NormalNoise nn = build((Double) spec[1], (Integer) spec[2], (double[]) spec[3]);
            pf("nn.range." + spec[0], nn.range().max());
            for (long s : SEEDS) {
                Noise noise = nn.create(new XoroshiroRandomSource(s).forkPositional().fromHashOf("minecraft:" + spec[0]));
                Noise leg = nn.createForLegacyNetherBiome(new LegacyRandomSource(s));
                for (int i = 0; i < PTS.length; i++) {
                    double[] q = PTS[i];
                    pf("nn." + spec[0] + "." + s + "." + i, noise.get(q[0], q[1], q[2]));
                    pf("nnleg." + spec[0] + "." + s + "." + i, leg.get(q[0], q[1], q[2]));
                }
            }
        }
        BlendedNoise bn = new BlendedNoise(0.25, 0.125, 80.0, 160.0, 8.0);
        pf("bn.range.min", bn.range().min());
        pf("bn.range.max", bn.range().max());
        for (long s : SEEDS) {
            BlendedNoise.Sampler sampler = bn.compileSampler(new XoroshiroRandomSource(s).forkPositional().fromHashOf(BlendedNoise.NOISE_SEED));
            int[][] bp = {{0, 0, 0}, {5, 64, -9}, {-1000, -60, 2000}, {123456, 300, -654321}, {7, 7, 7}};
            for (int[] q : bp) {
                pf("bn." + s + "." + q[0] + "." + q[1] + "." + q[2], sampler.sampleValue(q[0], q[1], q[2]));
            }
        }
    }

    private static NormalNoise build(double base, int octave, double[] mods) {
        NormalNoise.Builder b = NormalNoise.builder().setBaseAmplitude(base).setBaseOctave(octave).setOctaveCount(mods.length);
        for (int i = 0; i < mods.length; i++) {
            if (mods[i] != 1.0) {
                b.setAmplitudeModifier(i, mods[i]);
            }
        }
        return b.build();
    }

    private void dumpRandom(String k, RandomSource r) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(r.nextInt()).append(',');
        }
        for (int b : new int[]{1, 2, 7, 16, 100, 1 << 30, Integer.MAX_VALUE}) {
            sb.append(r.nextInt(b)).append(',');
        }
        sb.append(r.nextInt(-5, 9)).append(',').append(r.nextIntBetweenInclusive(3, 4)).append(',');
        for (int i = 0; i < 3; i++) {
            sb.append(r.nextLong()).append(',');
        }
        for (int i = 0; i < 3; i++) {
            sb.append(r.nextBoolean()).append(',');
        }
        for (int i = 0; i < 3; i++) {
            sb.append(Float.floatToRawIntBits(r.nextFloat())).append(',');
        }
        for (int i = 0; i < 3; i++) {
            sb.append(Double.doubleToRawLongBits(r.nextDouble())).append(',');
        }
        for (int i = 0; i < 3; i++) {
            sb.append(Double.doubleToRawLongBits(r.nextGaussian())).append(',');
        }
        sb.append(Double.doubleToRawLongBits(r.triangle(1.5, 4.0))).append(',');
        sb.append(Float.floatToRawIntBits(r.triangle(1.5F, 4.0F))).append(',');
        r.consumeCount(5);
        sb.append(r.nextLong());
        out.append(k).append(' ').append(sb).append('\n');
    }
}
