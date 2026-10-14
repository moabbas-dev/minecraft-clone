package com.example.engine;

import java.awt.image.BufferedImage;
import java.util.Random;

/**
 * Original 16x16 block textures, generated in code: clustered-noise palettes (dark to light, so the colour comes in
 * soft blotches like pixel art rather than per-pixel static) plus structured details - cobble cells, plank boards,
 * brick joints, bark ridges, ore clusters. Nothing here is copied or extracted from Mojang's textures.
 *
 * {@link TextureAtlas} uses {@link #draw} as the fallback for any block tile without a texture file in
 * {@code textures/block/}. Tiles that this class does not handle (sky bodies, rain, mob skins...) return null.
 */
public final class BlockArt {
    private BlockArt() {
    }

    private static final int N = 16;

    private static int c(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int a(int r, int g, int b, int alpha) {
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }

    private static int shift(int col, int d) {
        int r = Math.max(0, Math.min(255, ((col >> 16) & 0xFF) + d));
        int g = Math.max(0, Math.min(255, ((col >> 8) & 0xFF) + d));
        int b = Math.max(0, Math.min(255, (col & 0xFF) + d));
        return (col & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** A 16x16 canvas with tileable (wrapping) clustered noise. */
    private static final class T {
        final int[] px = new int[N * N];
        final Random rnd;
        final double[][] coarse = new double[4][4];
        final double[][] fine = new double[8][8];

        T(long seed) {
            rnd = new Random(seed * 1_000_003L + 17);
            for (double[] row : coarse) for (int i = 0; i < row.length; i++) row[i] = rnd.nextDouble();
            for (double[] row : fine) for (int i = 0; i < row.length; i++) row[i] = rnd.nextDouble();
        }

        void set(int x, int y, int col) {
            if (x >= 0 && y >= 0 && x < N && y < N) px[y * N + x] = col;
        }

        int get(int x, int y) {
            return px[Math.floorMod(y, N) * N + Math.floorMod(x, N)];
        }

        void fill(int col) {
            java.util.Arrays.fill(px, col);
        }

        void rect(int x, int y, int w, int h, int col) {
            for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) set(x + i, y + j, col);
        }

        void hline(int x0, int x1, int y, int col) {
            for (int x = x0; x <= x1; x++) set(x, y, col);
        }

        void vline(int x, int y0, int y1, int col) {
            for (int y = y0; y <= y1; y++) set(x, y, col);
        }

        private static double smooth(double t) {
            return t * t * (3 - 2 * t);
        }

        private static double lattice(double[][] g, double fx, double fy) {
            int n = g.length;
            int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
            double tx = smooth(fx - x0), ty = smooth(fy - y0);
            double v00 = g[Math.floorMod(y0, n)][Math.floorMod(x0, n)], v10 = g[Math.floorMod(y0, n)][Math.floorMod(x0 + 1, n)];
            double v01 = g[Math.floorMod(y0 + 1, n)][Math.floorMod(x0, n)], v11 = g[Math.floorMod(y0 + 1, n)][Math.floorMod(x0 + 1, n)];
            return (v00 * (1 - tx) + v10 * tx) * (1 - ty) + (v01 * (1 - tx) + v11 * tx) * ty;
        }

        /** 0..1 noise: soft 4 px blotches, 2 px detail and a little white noise. */
        double noise(int x, int y) {
            double v = 0.5 * lattice(coarse, x / 4.0, y / 4.0) + 0.32 * lattice(fine, x / 2.0, y / 2.0) + 0.18 * rnd.nextDouble();
            return Math.max(0, Math.min(0.9999, v));
        }

        /** Paints every pixel from a palette ordered dark to light using the clustered noise. */
        void blotch(int[] pal) {
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    double v = (noise(x, y) - 0.2) / 0.6;
                    int i = (int) (Math.max(0, Math.min(0.9999, v)) * pal.length);
                    set(x, y, pal[i]);
                }
            }
        }

        void speckle(int col, int count) {
            for (int i = 0; i < count; i++) set(rnd.nextInt(N), rnd.nextInt(N), col);
        }

        /** Light top/left edge and dark bottom/right edge, {@code w} px wide. */
        void frame(int light, int dark, int w) {
            for (int i = 0; i < w; i++) {
                hline(i, N - 1 - i, i, light);
                vline(i, i, N - 1 - i, light);
                hline(i, N - 1 - i, N - 1 - i, dark);
                vline(N - 1 - i, i, N - 1 - i, dark);
            }
        }

        BufferedImage image() {
            BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, N, N, px, 0, N);
            return img;
        }
    }

    // ---- shared builders ----

    /** Voronoi cells (wrapping): fills each cell with one of {@code pal}, leaving {@code grout} between cells. */
    private static T cells(long seed, int count, int[] pal, int grout, double groutWidth) {
        T t = new T(seed);
        double[][] pts = new double[count][2];
        int[] colorOf = new int[count];
        for (int i = 0; i < count; i++) {
            pts[i][0] = t.rnd.nextDouble() * N;
            pts[i][1] = t.rnd.nextDouble() * N;
            colorOf[i] = pal[t.rnd.nextInt(pal.length)];
        }
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                double d1 = 1e9, d2 = 1e9;
                int best = 0;
                for (int i = 0; i < count; i++) {
                    double dx = Math.abs(x + 0.5 - pts[i][0]), dy = Math.abs(y + 0.5 - pts[i][1]);
                    dx = Math.min(dx, N - dx);
                    dy = Math.min(dy, N - dy);
                    double d = Math.sqrt(dx * dx + dy * dy);
                    if (d < d1) {
                        d2 = d1;
                        d1 = d;
                        best = i;
                    } else if (d < d2) {
                        d2 = d;
                    }
                }
                if (d2 - d1 < groutWidth) t.set(x, y, grout);
                else {
                    // light top-left of each cell, dark bottom-right, for a rounded-stone look
                    double dx = x + 0.5 - pts[best][0], dy = y + 0.5 - pts[best][1];
                    int col = colorOf[best];
                    t.set(x, y, dx + dy < -2.2 ? shift(col, 14) : dx + dy > 2.4 ? shift(col, -14) : col);
                }
            }
        }
        return t;
    }

    private static final int[] STONE = {c(104, 104, 106), c(116, 116, 118), c(127, 127, 129), c(138, 138, 140), c(148, 148, 150)};
    private static final int[] DIRT = {c(108, 76, 50), c(121, 85, 57), c(134, 96, 66), c(146, 106, 74), c(156, 116, 84)};
    private static final int[] GRASS = {c(72, 132, 40), c(84, 146, 46), c(96, 160, 54), c(108, 172, 62), c(122, 184, 74)};
    private static final int[] SAND = {c(200, 188, 142), c(212, 200, 152), c(222, 210, 162), c(230, 220, 174)};
    private static final int[] PLANK = {c(150, 112, 62), c(166, 126, 72), c(180, 140, 84), c(192, 152, 96)};

    private static T stoneBase(long seed) {
        T t = new T(seed);
        t.blotch(STONE);
        return t;
    }

    private static T oreOn(long seed, int dark, int mid, int light, int clusters) {
        T t = stoneBase(seed);
        for (int i = 0; i < clusters; i++) {
            int cx = 1 + t.rnd.nextInt(13), cy = 1 + t.rnd.nextInt(13);
            int[][] shape = {{0, 0}, {1, 0}, {0, 1}, {1, 1}, {2, 1}, {1, 2}, {2, 2}};
            int len = 4 + t.rnd.nextInt(3);
            for (int k = 0; k < len; k++) {
                int x = cx + shape[k][0], y = cy + shape[k][1];
                t.set(x, y, k == 0 ? light : k < 3 ? mid : dark);
            }
        }
        return t;
    }

    private static T logSide(long seed, int[] bark, int dash) {
        T t = new T(seed);
        for (int x = 0; x < N; x++) {
            int tone = (x * 7 + t.rnd.nextInt(3)) % bark.length;
            for (int y = 0; y < N; y++) {
                int idx = Math.max(0, Math.min(bark.length - 1, tone + (t.rnd.nextInt(5) == 0 ? 1 : 0) - (t.rnd.nextInt(6) == 0 ? 1 : 0)));
                t.set(x, y, bark[idx]);
            }
        }
        if (dash != 0) for (int i = 0; i < 7; i++) t.hline(t.rnd.nextInt(10), t.rnd.nextInt(10) + 2 + 3, t.rnd.nextInt(N), dash);
        return t;
    }

    private static T logTop(long seed, int ring, int wood, int heart, int bark) {
        T t = new T(seed);
        for (int y = 0; y < N; y++) {
            for (int x = 0; x < N; x++) {
                double d = Math.max(Math.abs(x - 7.5), Math.abs(y - 7.5));
                int col = d > 6.6 ? bark : ((int) (d * 1.15) % 2 == 0 ? wood : ring);
                if (d < 1.6) col = heart;
                t.set(x, y, col);
            }
        }
        return t;
    }

    private static T leaves(long seed, int[] palette) {
        // The renderer multiplies leaves by a biome tint, so the base is lifted to stay bright after tinting.
        int[] pal = palette.clone();
        if (((pal[0] >> 16) & 0xFF) < 200) for (int i = 0; i < pal.length; i++) pal[i] = shift(pal[i], 34);
        T t = new T(seed);
        t.blotch(pal);
        for (int i = 0; i < 20; i++) t.set(t.rnd.nextInt(N), t.rnd.nextInt(N), shift(pal[0], -16));
        for (int i = 0; i < 12; i++) t.set(t.rnd.nextInt(N), t.rnd.nextInt(N), shift(pal[pal.length - 1], 10));
        return t;
    }

    private static T planks(long seed, int[] pal, int seam) {
        T t = new T(seed);
        for (int board = 0; board < 4; board++) {
            int y0 = board * 4;
            int base = pal[(board * 2 + t.rnd.nextInt(2)) % pal.length];
            for (int y = y0; y < y0 + 3; y++) for (int x = 0; x < N; x++) t.set(x, y, t.rnd.nextInt(5) == 0 ? shift(base, -12) : base);
            t.hline(0, N - 1, y0 + 3, seam);
            int cut = 3 + (board * 5) % 10;
            t.vline(cut, y0, y0 + 2, seam);
            for (int i = 0; i < 4; i++) t.set(t.rnd.nextInt(N), y0 + t.rnd.nextInt(3), shift(base, -22)); // grain
            t.set((cut + 2) % N, y0 + 1, shift(base, 16));
        }
        return t;
    }

    private static T bricks(long seed, int[] pal, int mortar) {
        T t = new T(seed);
        t.fill(mortar);
        for (int row = 0; row < 4; row++) {
            int y0 = row * 4;
            int offset = row % 2 == 0 ? 0 : 4;
            for (int b = -1; b < 3; b++) {
                int x0 = offset + b * 8;
                int col = pal[t.rnd.nextInt(pal.length)];
                for (int y = y0; y < y0 + 3; y++) {
                    for (int x = x0; x < x0 + 7; x++) {
                        if (x < 0 || x >= N) continue;
                        t.set(x, y, y == y0 ? shift(col, 18) : y == y0 + 2 ? shift(col, -16) : col);
                    }
                }
            }
        }
        return t;
    }

    private static T blockPlate(long seed, int base, int light, int dark, int shine) {
        T t = new T(seed);
        t.fill(base);
        for (int i = 0; i < 24; i++) t.set(t.rnd.nextInt(N), t.rnd.nextInt(N), t.rnd.nextBoolean() ? shift(base, 8) : shift(base, -8));
        t.frame(light, dark, 1);
        t.hline(2, 6, 2, shine);
        t.set(2, 3, shine);
        t.hline(9, 12, 12, dark);
        return t;
    }

    private static BufferedImage grassSide(long seed) {
        T t = new T(seed);
        t.blotch(DIRT);
        for (int x = 0; x < N; x++) {
            int depth = 3 + (t.rnd.nextInt(3) == 0 ? 2 : t.rnd.nextInt(2));
            for (int y = 0; y < depth; y++) t.set(x, y, GRASS[Math.min(GRASS.length - 1, 1 + t.rnd.nextInt(3))]);
        }
        return t.image();
    }

    private static BufferedImage tntSide(long seed) {
        T t = new T(seed);
        t.fill(c(196, 52, 44));
        for (int x = 0; x < N; x += 4) t.vline(x, 0, 15, c(168, 36, 32));
        t.rect(0, 5, N, 6, c(236, 228, 204));
        t.hline(0, N - 1, 5, c(206, 196, 170));
        t.hline(0, N - 1, 10, c(206, 196, 170));
        int ink = c(40, 30, 28);
        // "TNT", 3 px wide letters
        for (int x = 2; x <= 4; x++) t.set(x, 6, ink);
        t.vline(3, 6, 9, ink);
        t.vline(6, 6, 9, ink);
        t.vline(8, 6, 9, ink);
        t.set(7, 7, ink);
        t.set(7, 8, ink);
        for (int x = 10; x <= 12; x++) t.set(x, 6, ink);
        t.vline(11, 6, 9, ink);
        return t.image();
    }

    private static BufferedImage bookshelf(long seed) {
        T t = planks(seed, PLANK, c(104, 74, 38));
        int[] spines = {c(150, 40, 40), c(48, 82, 150), c(196, 160, 48), c(60, 124, 70), c(122, 70, 138), c(170, 96, 50)};
        for (int shelf = 0; shelf < 2; shelf++) {
            int y0 = 1 + shelf * 8;
            t.rect(1, y0, 14, 6, c(40, 28, 20));
            int x = 1;
            while (x < 15) {
                int w = 1 + t.rnd.nextInt(2);
                int col = spines[t.rnd.nextInt(spines.length)];
                int h = 5 - t.rnd.nextInt(2);
                for (int i = 0; i < w && x + i < 15; i++) {
                    t.vline(x + i, y0 + 6 - h, y0 + 5, i == 0 ? shift(col, 22) : col);
                }
                x += w;
            }
        }
        t.hline(0, N - 1, 0, c(104, 74, 38));
        t.hline(0, N - 1, 7, c(176, 134, 78));
        t.hline(0, N - 1, 15, c(104, 74, 38));
        return t.image();
    }

    private static T chestFace(long seed) {
        T t = planks(seed, new int[]{c(138, 98, 50), c(150, 108, 56), c(160, 118, 62)}, c(88, 60, 28));
        t.hline(0, N - 1, 6, c(58, 40, 20));
        t.hline(0, N - 1, 7, c(58, 40, 20));
        t.vline(0, 0, 15, c(58, 40, 20));
        t.vline(N - 1, 0, 15, c(58, 40, 20));
        t.rect(7, 6, 2, 4, c(190, 190, 196));
        t.set(7, 9, c(120, 120, 128));
        t.set(8, 9, c(120, 120, 128));
        return t;
    }

    private static T craftingTop(long seed) {
        T t = planks(seed, PLANK, c(104, 74, 38));
        int line = c(76, 52, 26);
        t.frame(c(196, 156, 98), line, 1);
        for (int i = 4; i <= 11; i += 4) {
            t.hline(2, 13, i, line);
            t.vline(i, 2, 13, line);
        }
        return t;
    }

    private static T craftingSide(long seed) {
        T t = planks(seed, new int[]{c(132, 96, 52), c(144, 106, 58)}, c(92, 64, 32));
        t.rect(1, 1, 14, 3, c(94, 66, 34));
        t.rect(2, 5, 5, 6, c(64, 48, 36));
        t.rect(3, 6, 3, 4, c(150, 150, 156));
        t.rect(9, 5, 5, 6, c(64, 48, 36));
        t.vline(11, 5, 10, c(196, 176, 120));
        t.hline(9, 13, 6, c(196, 176, 120));
        return t;
    }

    private static T furnaceFront(long seed, int[] shell, int mouth, int glow, boolean lit) {
        T t = cells(seed, 9, shell, shift(shell[0], -30), 1.0);
        t.rect(4, 5, 8, 7, c(30, 30, 32));
        t.rect(5, 6, 6, 5, mouth);
        if (lit) {
            t.rect(6, 8, 4, 3, glow);
            t.set(7, 7, shift(glow, 40));
        }
        t.rect(4, 12, 8, 1, shift(shell[0], -34));
        return t;
    }

    private static T pumpkinSide(long seed) {
        T t = new T(seed);
        int[] pal = {c(196, 100, 22), c(214, 118, 28), c(228, 136, 36)};
        for (int x = 0; x < N; x++) {
            int col = pal[(int) Math.round(1 + Math.sin(x * Math.PI / 2.0) * 1.0) % 3];
            for (int y = 0; y < N; y++) t.set(x, y, t.rnd.nextInt(7) == 0 ? shift(col, -14) : col);
        }
        t.hline(0, N - 1, 0, c(170, 80, 16));
        t.hline(0, N - 1, 15, c(170, 80, 16));
        return t;
    }

    private static T snow(long seed) {
        T t = new T(seed);
        t.blotch(new int[]{c(222, 232, 240), c(232, 240, 246), c(241, 246, 250), c(250, 252, 253)});
        return t;
    }

    private static T ice(long seed) {
        T t = new T(seed);
        t.blotch(new int[]{a(150, 196, 232, 215), a(164, 208, 240, 215), a(178, 218, 246, 215), a(196, 230, 250, 215)});
        for (int i = 0; i < 3; i++) {
            int x = t.rnd.nextInt(10), y = t.rnd.nextInt(12);
            for (int k = 0; k < 5; k++) t.set(x + k, y + k / 2, a(235, 247, 255, 235));
        }
        return t;
    }

    private static T glass(long seed) {
        T t = new T(seed);
        t.rect(0, 0, N, N, a(208, 236, 244, 70));
        t.frame(a(236, 250, 255, 230), a(176, 214, 226, 200), 1);
        t.set(3, 3, a(255, 255, 255, 230));
        t.set(4, 3, a(255, 255, 255, 200));
        t.set(3, 4, a(255, 255, 255, 200));
        t.set(11, 12, a(255, 255, 255, 170));
        t.set(12, 11, a(255, 255, 255, 170));
        return t;
    }

    private static T water(long seed) {
        T t = new T(seed);
        t.blotch(new int[]{a(36, 78, 196, 178), a(46, 92, 208, 178), a(58, 106, 220, 178), a(76, 124, 232, 178)});
        return t;
    }

    private static T sandstone(long seed, boolean side) {
        T t = new T(seed);
        t.blotch(new int[]{c(204, 190, 142), c(216, 202, 152), c(226, 212, 164)});
        if (side) {
            t.hline(0, N - 1, 3, c(190, 174, 128));
            t.hline(0, N - 1, 4, c(232, 220, 174));
            t.hline(0, N - 1, 11, c(190, 174, 128));
            t.hline(0, N - 1, 12, c(232, 220, 174));
            t.rect(3, 6, 10, 3, c(196, 180, 132));
            t.hline(3, 12, 6, c(176, 160, 116));
        } else {
            t.frame(c(232, 220, 174), c(190, 174, 128), 1);
        }
        return t;
    }

    private static T glowstone(long seed) {
        return cells(seed, 11, new int[]{c(214, 154, 66), c(238, 186, 88), c(252, 214, 118), c(255, 236, 160)}, c(150, 96, 40), 0.8);
    }

    private static T netherrack(long seed) {
        T t = new T(seed);
        t.blotch(new int[]{c(78, 30, 30), c(96, 40, 38), c(112, 50, 46), c(128, 62, 56)});
        for (int i = 0; i < 10; i++) {
            int x = t.rnd.nextInt(N), y = t.rnd.nextInt(N);
            t.set(x, y, c(56, 20, 22));
            t.set(x + 1, y, c(66, 24, 26));
        }
        return t;
    }

    private static T bedrock(long seed) {
        return cells(seed, 10, new int[]{c(46, 46, 48), c(62, 62, 64), c(80, 80, 82), c(34, 34, 36)}, c(24, 24, 26), 0.9);
    }

    private static T obsidian(long seed) {
        T t = new T(seed);
        t.blotch(new int[]{c(14, 10, 24), c(22, 14, 40), c(34, 20, 62), c(52, 30, 92)});
        for (int i = 0; i < 6; i++) t.set(t.rnd.nextInt(N), t.rnd.nextInt(N), c(110, 70, 170));
        return t;
    }

    private static T terracotta(long seed, int base, int stripe) {
        T t = new T(seed);
        t.blotch(new int[]{shift(base, -10), base, shift(base, 8), shift(base, 14)});
        if (stripe != 0) for (int y = 3; y < N; y += 6) t.hline(0, N - 1, y, stripe);
        return t;
    }

    private static T mud(long seed) {
        T t = new T(seed);
        t.blotch(new int[]{c(52, 40, 38), c(62, 48, 44), c(74, 58, 52), c(86, 68, 60)});
        return t;
    }

    private static T roots(long seed, boolean top) {
        T t = new T(seed);
        t.blotch(new int[]{c(62, 42, 30), c(78, 52, 38), c(94, 64, 44)});
        if (!top) for (int x = 1; x < N; x += 4) t.vline(x, 0, 15, c(44, 30, 22));
        else t.rect(5, 5, 6, 6, c(110, 74, 50));
        return t;
    }

    private static T clay(long seed) {
        T t = new T(seed);
        t.blotch(new int[]{c(150, 156, 172), c(160, 166, 182), c(170, 176, 190), c(180, 186, 198)});
        return t;
    }

    private static T mossy(long seed) {
        T t = cells(seed, 9, new int[]{c(112, 112, 114), c(126, 126, 128), c(140, 140, 142)}, c(76, 76, 78), 1.0);
        for (int i = 0; i < 26; i++) {
            int x = t.rnd.nextInt(N), y = t.rnd.nextInt(N);
            t.set(x, y, t.rnd.nextBoolean() ? c(84, 130, 54) : c(104, 150, 66));
            t.set(x + 1, y, c(72, 116, 46));
        }
        return t;
    }

    private static T smoothStone(long seed) {
        T t = new T(seed);
        t.blotch(new int[]{c(150, 150, 152), c(158, 158, 160), c(166, 166, 168), c(174, 174, 176)});
        t.hline(0, N - 1, 15, c(124, 124, 126));
        t.hline(0, N - 1, 0, c(184, 184, 186));
        return t;
    }

    private static T gravel(long seed) {
        return cells(seed, 22, new int[]{c(122, 118, 114), c(140, 136, 132), c(104, 100, 98), c(156, 150, 144), c(132, 112, 96)}, c(80, 78, 76), 0.55);
    }

    private static T leafTone(long seed, String species) {
        return switch (species) {
            case "OAK" -> leaves(seed, new int[]{c(40, 98, 28), c(52, 118, 34), c(64, 134, 42), c(80, 152, 52)});
            case "BIRCH" -> leaves(seed, new int[]{c(104, 140, 66), c(122, 160, 78), c(138, 176, 92), c(156, 190, 108)});
            case "SPRUCE" -> leaves(seed, new int[]{c(30, 62, 40), c(40, 78, 50), c(50, 92, 60), c(64, 108, 72)});
            case "JUNGLE" -> leaves(seed, new int[]{c(28, 112, 22), c(40, 134, 28), c(54, 152, 36), c(72, 170, 48)});
            case "ACACIA" -> leaves(seed, new int[]{c(112, 116, 44), c(132, 136, 54), c(148, 152, 64), c(166, 168, 78)});
            case "MANGROVE" -> leaves(seed, new int[]{c(36, 100, 44), c(48, 118, 54), c(60, 134, 64), c(76, 150, 78)});
            case "DARK_OAK" -> leaves(seed, new int[]{c(24, 62, 18), c(32, 78, 24), c(42, 92, 30), c(54, 108, 38)});
            case "CHERRY" -> leaves(seed, new int[]{c(226, 150, 184), c(238, 170, 200), c(246, 188, 214), c(252, 208, 228)});
            default -> throw new IllegalArgumentException(species);
        };
    }

    /**
     * The texture for a block tile, or null when this class does not paint it.
     */
    public static BufferedImage draw(Tile tile) {
        T t = switch (tile) {
            case STONE -> stoneBase(1);
            case DIRT -> {
                T d = new T(2);
                d.blotch(DIRT);
                d.speckle(c(92, 62, 40), 6);
                d.speckle(c(168, 128, 96), 4);
                yield d;
            }
            case GRASS_TOP -> {
                T g = new T(3);
                g.blotch(GRASS);
                yield g;
            }
            case GRASS_SIDE -> {
                yield null;
            }
            case COBBLESTONE -> cells(5, 8, new int[]{c(112, 112, 114), c(126, 126, 128), c(140, 140, 142), c(100, 100, 102)}, c(70, 70, 72), 1.0);
            case PLANKS -> planks(6, PLANK, c(104, 74, 38));
            case BEDROCK -> bedrock(7);
            case WATER -> water(8);
            case SAND -> {
                T s = new T(9);
                s.blotch(SAND);
                s.speckle(c(236, 226, 184), 5);
                yield s;
            }
            case GRAVEL -> gravel(10);
            case GOLD_ORE -> oreOn(11, c(176, 128, 30), c(240, 196, 52), c(255, 238, 140), 5);
            case IRON_ORE -> oreOn(12, c(150, 108, 84), c(216, 170, 140), c(240, 208, 184), 5);
            case COAL_ORE -> oreOn(13, c(20, 20, 24), c(40, 40, 46), c(72, 72, 80), 6);
            case DIAMOND_ORE -> oreOn(14, c(44, 170, 170), c(92, 232, 222), c(214, 255, 250), 5);
            case COPPER_ORE -> oreOn(15, c(80, 130, 106), c(206, 122, 82), c(238, 168, 128), 5);
            case LOG_SIDE -> logSide(16, new int[]{c(70, 54, 32), c(86, 66, 40), c(102, 80, 50), c(116, 92, 58)}, 0);
            case LOG_TOP -> logTop(17, c(158, 122, 72), c(184, 148, 92), c(150, 112, 62), c(100, 78, 48));
            case LEAVES -> leafTone(18, "OAK");
            case GLASS -> glass(19);
            case SANDSTONE_TOP -> sandstone(20, false);
            case SANDSTONE_BOTTOM -> {
                T s = sandstone(120, false);
                s.frame(c(190, 174, 128), c(176, 160, 116), 1); // the underside is a plain slab, shaded evenly
                yield s;
            }
            case SANDSTONE_SIDE -> sandstone(21, true);
            case GOLD_BLOCK -> blockPlate(22, c(244, 208, 70), c(255, 244, 150), c(196, 140, 28), c(255, 252, 214));
            case IRON_BLOCK -> blockPlate(23, c(214, 214, 218), c(250, 250, 252), c(150, 150, 158), c(255, 255, 255));
            case DIAMOND_BLOCK -> blockPlate(24, c(96, 226, 216), c(200, 255, 250), c(40, 156, 170), c(240, 255, 254));
            case BRICKS -> bricks(25, new int[]{c(158, 76, 58), c(170, 84, 64), c(146, 68, 52), c(180, 94, 72)}, c(176, 168, 156));
            case TNT_TOP, TNT_BOTTOM -> {
                T s = new T(tile == Tile.TNT_TOP ? 26 : 126);
                s.fill(c(190, 54, 46));
                s.rect(6, 6, 4, 4, tile == Tile.TNT_TOP ? c(60, 40, 34) : c(110, 36, 30));
                s.frame(c(214, 84, 72), c(150, 34, 30), 1);
                yield s;
            }
            case BOOKSHELF -> null;
            case MOSSY_COBBLESTONE -> mossy(28);
            case OBSIDIAN -> obsidian(29);
            case CHEST -> chestFace(30);
            case CRAFTING_TABLE_TOP -> craftingTop(31);
            case CRAFTING_TABLE_SIDE -> craftingSide(32);
            case FURNACE_SIDE -> furnaceFront(33, new int[]{c(104, 104, 106), c(122, 122, 124), c(138, 138, 140)}, c(18, 18, 20), c(244, 150, 40), false);
            case FURNACE_TOP -> cells(34, 8, new int[]{c(112, 112, 114), c(126, 126, 128), c(140, 140, 142)}, c(78, 78, 80), 0.9);
            case ICE -> ice(35);
            case SNOW_BLOCK -> snow(36);
            case CLAY -> clay(37);
            case PUMPKIN_SIDE -> pumpkinSide(38);
            case PUMPKIN_TOP -> {
                T s = new T(39);
                s.blotch(new int[]{c(206, 108, 24), c(222, 128, 32), c(234, 146, 44)});
                s.rect(6, 6, 4, 4, c(96, 118, 40));
                s.rect(7, 7, 2, 2, c(60, 80, 24));
                yield s;
            }
            case NETHERRACK -> netherrack(40);
            case GLOWSTONE -> glowstone(41);
            case TERRACOTTA -> terracotta(42, c(152, 94, 66), c(136, 82, 56));
            case RED_SAND -> {
                T s = new T(43);
                s.blotch(new int[]{c(172, 92, 44), c(186, 102, 50), c(198, 112, 58), c(208, 124, 68)});
                s.speckle(c(150, 76, 36), 6);
                yield s;
            }
            case BIRCH_LOG_SIDE -> logSide(44, new int[]{c(200, 198, 184), c(216, 214, 200), c(228, 226, 212), c(236, 234, 222)}, c(48, 46, 44));
            case BIRCH_LOG_TOP -> logTop(45, c(190, 170, 120), c(212, 192, 140), c(176, 154, 104), c(214, 212, 198));
            case BIRCH_LEAVES -> leafTone(46, "BIRCH");
            case SPRUCE_LOG_SIDE -> logSide(47, new int[]{c(50, 36, 22), c(62, 44, 28), c(74, 54, 34), c(86, 62, 40)}, 0);
            case SPRUCE_LOG_TOP -> logTop(48, c(112, 82, 48), c(134, 100, 60), c(104, 74, 42), c(66, 48, 30));
            case SPRUCE_LEAVES -> leafTone(49, "SPRUCE");
            case JUNGLE_LOG_SIDE -> logSide(50, new int[]{c(78, 62, 34), c(94, 76, 42), c(108, 88, 50), c(120, 98, 58)}, c(62, 78, 30));
            case JUNGLE_LOG_TOP -> logTop(51, c(158, 120, 70), c(182, 144, 88), c(150, 112, 62), c(92, 74, 42));
            case JUNGLE_LEAVES -> leafTone(52, "JUNGLE");
            case ACACIA_LOG_SIDE -> logSide(53, new int[]{c(96, 88, 80), c(110, 102, 94), c(124, 116, 106), c(138, 128, 118)}, 0);
            case ACACIA_LOG_TOP -> logTop(54, c(186, 108, 62), c(206, 128, 74), c(172, 96, 54), c(104, 96, 86));
            case ACACIA_LEAVES -> leafTone(55, "ACACIA");
            case MANGROVE_LOG_SIDE -> logSide(56, new int[]{c(84, 44, 38), c(98, 54, 44), c(112, 64, 52), c(124, 74, 60)}, 0);
            case MANGROVE_LOG_TOP -> logTop(57, c(156, 74, 66), c(176, 90, 78), c(142, 64, 56), c(94, 52, 44));
            case MANGROVE_LEAVES -> leafTone(58, "MANGROVE");
            case DARK_OAK_LOG_SIDE -> logSide(59, new int[]{c(40, 30, 20), c(50, 38, 24), c(60, 46, 28), c(70, 54, 34)}, 0);
            case DARK_OAK_LOG_TOP -> logTop(60, c(80, 58, 34), c(98, 72, 42), c(72, 50, 28), c(52, 40, 26));
            case DARK_OAK_LEAVES -> leafTone(61, "DARK_OAK");
            case CHERRY_LOG_SIDE -> logSide(62, new int[]{c(74, 44, 52), c(90, 54, 62), c(104, 64, 72), c(118, 76, 84)}, 0);
            case CHERRY_LOG_TOP -> logTop(63, c(212, 160, 150), c(230, 180, 170), c(198, 144, 134), c(100, 62, 70));
            case CHERRY_LEAVES -> leafTone(64, "CHERRY");
            case MUD -> mud(65);
            case MANGROVE_ROOTS_SIDE -> roots(66, false);
            case MANGROVE_ROOTS_TOP -> roots(67, true);
            case BLAST_FURNACE_TOP -> cells(68, 8, new int[]{c(78, 84, 92), c(92, 98, 106), c(106, 112, 120)}, c(50, 54, 60), 0.9);
            case BLAST_FURNACE_SIDE -> furnaceFront(69, new int[]{c(86, 92, 100), c(100, 106, 114), c(114, 120, 128)}, c(20, 20, 24), c(240, 120, 30), false);
            case SMOKER_TOP -> cells(70, 8, new int[]{c(108, 86, 66), c(124, 98, 74), c(140, 112, 84)}, c(70, 54, 40), 0.9);
            case SMOKER_SIDE -> furnaceFront(71, new int[]{c(120, 94, 70), c(136, 108, 80), c(150, 120, 90)}, c(24, 20, 18), c(240, 140, 40), false);
            case SMOOTH_STONE -> smoothStone(72);
            default -> null;
        };
        if (t != null) return t.image();
        return switch (tile) {
            case GRASS_SIDE -> grassSide(4);
            case BOOKSHELF -> bookshelf(27);
            case TNT_SIDE -> tntSide(26);
            default -> null;
        };
    }
}
