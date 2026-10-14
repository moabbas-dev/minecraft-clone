package com.example.engine;

import java.awt.image.BufferedImage;

/**
 * Original 16x16 pixel-art icons for every flat item (tools, armor, food, ingots, drops...). Nothing here is copied
 * or extracted from Mojang's textures: each icon is built from simple shapes (lines, ellipses, polygons) in a small
 * palette, then given the classic item look - a light rim on the top/left edges, a dark rim on the bottom/right and a
 * 1 px dark outline around the silhouette.
 *
 * {@link TextureAtlas} uses {@link #draw} as the fallback for any item without a texture file in
 * {@code textures/item/}.
 */
public final class ItemIcons {
    private ItemIcons() {
    }

    private static final int N = 16;

    // ---- colour helpers ----

    private static int c(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int ch(int argb, int shift) {
        return (argb >> shift) & 0xFF;
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int lighten(int col, int d) {
        return c(clamp(ch(col, 16) + d), clamp(ch(col, 8) + d), clamp(ch(col, 0) + d));
    }

    private static int darken(int col, int d) {
        return lighten(col, -d);
    }

    // ---- a tiny drawing surface ----

    private static final class Canvas {
        final int[] px = new int[N * N];

        void set(int x, int y, int col) {
            if (x >= 0 && y >= 0 && x < N && y < N) px[y * N + x] = col;
        }

        int get(int x, int y) {
            return x < 0 || y < 0 || x >= N || y >= N ? 0 : px[y * N + x];
        }

        boolean on(int x, int y) {
            return (get(x, y) >>> 24) != 0;
        }

        void rect(int x, int y, int w, int h, int col) {
            for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) set(x + i, y + j, col);
        }

        void line(int x0, int y0, int x1, int y1, int col) {
            int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
            while (true) {
                set(x0, y0, col);
                if (x0 == x1 && y0 == y1) break;
                int e2 = 2 * err;
                if (e2 >= dy) {
                    err += dy;
                    x0 += sx;
                }
                if (e2 <= dx) {
                    err += dx;
                    y0 += sy;
                }
            }
        }

        /** A line {@code thick} pixels wide (extra pixels to the right and below). */
        void thickLine(int x0, int y0, int x1, int y1, int col) {
            line(x0, y0, x1, y1, col);
            line(x0 + 1, y0, x1 + 1, y1, col);
        }

        void ellipse(double cx, double cy, double rx, double ry, int col) {
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    double dx = (x + 0.5 - cx) / rx, dy = (y + 0.5 - cy) / ry;
                    if (dx * dx + dy * dy <= 1.0) set(x, y, col);
                }
            }
        }

        /** Fills a polygon given as x,y pairs (pixel-corner coordinates) by testing pixel centres. */
        void poly(int[] pts, int col) {
            int n = pts.length / 2;
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    double px = x + 0.5, py = y + 0.5;
                    boolean inside = false;
                    for (int i = 0, j = n - 1; i < n; j = i++) {
                        double xi = pts[2 * i], yi = pts[2 * i + 1], xj = pts[2 * j], yj = pts[2 * j + 1];
                        if (((yi > py) != (yj > py)) && (px < (xj - xi) * (py - yi) / (yj - yi) + xi)) inside = !inside;
                    }
                    if (inside) set(x, y, col);
                }
            }
        }

        /** Light rim on edges facing up/left, dark rim on edges facing down/right. */
        void bevel(int light, int dark) {
            int[] src = px.clone();
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    if ((src[y * N + x] >>> 24) == 0) continue;
                    boolean lit = !on(x - 1, y, src) || !on(x, y - 1, src);
                    boolean shadow = !on(x + 1, y, src) || !on(x, y + 1, src);
                    if (lit && !shadow) px[y * N + x] = lighten(src[y * N + x], light);
                    else if (shadow && !lit) px[y * N + x] = darken(src[y * N + x], dark);
                }
            }
        }

        private boolean on(int x, int y, int[] src) {
            return x >= 0 && y >= 0 && x < N && y < N && (src[y * N + x] >>> 24) != 0;
        }

        /** A 1 px outline on every transparent pixel that touches the shape (up/down/left/right). */
        void outline(int col) {
            int[] src = px.clone();
            for (int y = 0; y < N; y++) {
                for (int x = 0; x < N; x++) {
                    if ((src[y * N + x] >>> 24) != 0) continue;
                    if (on(x - 1, y, src) || on(x + 1, y, src) || on(x, y - 1, src) || on(x, y + 1, src)) px[y * N + x] = col;
                }
            }
        }

        BufferedImage image() {
            BufferedImage img = new BufferedImage(N, N, BufferedImage.TYPE_INT_ARGB);
            img.setRGB(0, 0, N, N, px, 0, N);
            return img;
        }
    }

    private static final int OUTLINE = c(28, 20, 16);
    private static final int HANDLE = c(166, 122, 74);
    private static final int HANDLE_DARK = c(112, 78, 44);

    private static int[] p(int... pts) {
        return pts;
    }

    // ---- materials ----

    /** Head tones (light, mid, dark) per tool tier. */
    private static int[] tier(String name) {
        return switch (name) {
            case "WOODEN" -> new int[]{c(196, 158, 102), c(160, 120, 70), c(118, 82, 46)};
            case "STONE" -> new int[]{c(176, 176, 176), c(134, 134, 136), c(96, 96, 100)};
            case "IRON" -> new int[]{c(246, 246, 246), c(208, 208, 212), c(152, 152, 160)};
            case "GOLD" -> new int[]{c(255, 244, 130), c(240, 200, 56), c(192, 140, 24)};
            case "DIAMOND" -> new int[]{c(190, 255, 248), c(84, 226, 216), c(34, 150, 166)};
            default -> throw new IllegalArgumentException(name);
        };
    }

    // ---- tools ----

    private static void handle(Canvas g, int x0, int y0, int x1, int y1) {
        g.line(x0, y0, x1, y1, HANDLE);
        g.line(x0 + 1, y0, x1 + 1, y1, HANDLE_DARK);
    }

    private static BufferedImage pickaxe(int[] t) {
        Canvas g = new Canvas();
        handle(g, 2, 14, 11, 5);
        // The head is a crescent around the top of the handle: pixels on the outer radius are lit, the inner ones shaded.
        double cx = 5.0, cy = 11.5, r = 7.6;
        for (double deg = -102; deg <= -2; deg += 1.0) {
            double u = (deg + 102) / 100.0;
            double thick = 1.2 + 1.5 * Math.sin(Math.PI * u);
            for (double rr = r - thick / 2; rr <= r + thick / 2; rr += 0.25) {
                int x = (int) Math.floor(cx + rr * Math.cos(Math.toRadians(deg)));
                int y = (int) Math.floor(cy + rr * Math.sin(Math.toRadians(deg)));
                g.set(x, y, rr > r + thick * 0.15 ? t[0] : rr < r - thick * 0.15 ? t[2] : t[1]);
            }
        }
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage axe(int[] t) {
        Canvas g = new Canvas();
        handle(g, 2, 14, 10, 6);
        g.poly(p(3, 5, 6, 2, 11, 1, 13, 3, 12, 7, 9, 8, 7, 6), t[1]);
        g.poly(p(6, 2, 11, 1, 13, 3, 9, 4), t[0]);
        g.poly(p(9, 8, 12, 7, 13, 3, 11, 5), t[2]);
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage shovel(int[] t) {
        Canvas g = new Canvas();
        handle(g, 2, 14, 9, 7);
        g.poly(p(10, 1, 14, 5, 11, 8, 7, 4), t[1]);
        g.poly(p(10, 1, 14, 5, 12, 5, 9, 3), t[0]);
        g.poly(p(11, 8, 14, 5, 12, 5, 9, 6), t[2]);
        g.set(8, 6, HANDLE_DARK);
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage sword(int[] t) {
        Canvas g = new Canvas();
        for (int k = 0; k <= 8; k++) {
            g.set(14 - k, 1 + k, t[0]);
            g.set(13 - k, 1 + k, t[1]);
        }
        g.set(15, 0, t[0]);
        for (int k = 0; k <= 8; k++) g.set(12 - k, 1 + k, t[2]);
        // cross-guard along the other diagonal, then the grip
        for (int k = -3; k <= 3; k++) {
            g.set(5 + k, 10 + k, c(120, 96, 72));
            g.set(6 + k, 10 + k, c(88, 66, 48));
        }
        g.line(4, 11, 1, 14, HANDLE);
        g.line(5, 11, 2, 14, HANDLE_DARK);
        g.set(0, 15, c(214, 176, 64));
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage stick() {
        Canvas g = new Canvas();
        g.line(2, 14, 13, 3, HANDLE);
        g.line(3, 14, 14, 3, HANDLE_DARK);
        g.set(13, 2, lighten(HANDLE, 30));
        g.outline(OUTLINE);
        return g.image();
    }

    // ---- armor ----

    /** Base, light, dark for each armor material. */
    private static int[] armorTone(String name) {
        return switch (name) {
            case "LEATHER" -> new int[]{c(168, 102, 58), c(204, 140, 88), c(112, 66, 36)};
            case "IRON" -> new int[]{c(212, 212, 216), c(246, 246, 248), c(150, 150, 158)};
            case "GOLD" -> new int[]{c(244, 206, 64), c(255, 242, 140), c(190, 134, 22)};
            case "DIAMOND" -> new int[]{c(80, 224, 214), c(190, 255, 248), c(32, 148, 164)};
            default -> throw new IllegalArgumentException(name);
        };
    }

    private static void armorFinish(Canvas g, int[] t, boolean leather) {
        g.bevel(34, 38);
        if (leather) { // stitched hem
            for (int x = 0; x < N; x++) for (int y = 0; y < N; y++) if (g.on(x, y) && (x + y) % 4 == 0 && (!g.on(x, y + 1) || !g.on(x, y - 1))) g.set(x, y, t[1]);
        }
        g.outline(OUTLINE);
    }

    private static BufferedImage helmet(int[] t, boolean leather) {
        Canvas g = new Canvas();
        g.poly(p(3, 13, 3, 7, 5, 4, 8, 3, 11, 4, 13, 7, 13, 13, 10, 13, 10, 10, 6, 10, 6, 13), t[0]);
        g.rect(4, 6, 8, 2, t[1]);
        g.rect(4, 13 - 1, 2, 1, t[2]);
        armorFinish(g, t, leather);
        return g.image();
    }

    private static BufferedImage chestplate(int[] t, boolean leather) {
        Canvas g = new Canvas();
        g.poly(p(1, 4, 5, 2, 7, 4, 9, 4, 11, 2, 15, 4, 15, 8, 12, 9, 12, 14, 4, 14, 4, 9, 1, 8), t[0]);
        g.rect(5, 8, 6, 1, t[1]);
        g.rect(4, 12, 8, 2, t[2]);
        armorFinish(g, t, leather);
        return g.image();
    }

    private static BufferedImage leggings(int[] t, boolean leather) {
        Canvas g = new Canvas();
        g.rect(3, 2, 10, 4, t[0]);
        g.rect(3, 6, 4, 8, t[0]);
        g.rect(9, 6, 4, 8, t[0]);
        g.rect(3, 4, 10, 1, t[1]);
        g.rect(7, 6, 2, 3, t[2]);
        armorFinish(g, t, leather);
        return g.image();
    }

    private static BufferedImage boots(int[] t, boolean leather) {
        Canvas g = new Canvas();
        g.rect(2, 3, 4, 8, t[0]);
        g.rect(2, 11, 6, 3, t[0]);
        g.rect(10, 3, 4, 8, t[0]);
        g.rect(10, 11, 5, 3, t[0]);
        g.rect(2, 4, 4, 1, t[1]);
        g.rect(10, 4, 4, 1, t[1]);
        armorFinish(g, t, leather);
        return g.image();
    }

    // ---- materials and drops ----

    private static BufferedImage ingot(int base) {
        Canvas g = new Canvas();
        g.poly(p(2, 10, 6, 6, 15, 6, 11, 10), lighten(base, 42));
        g.rect(2, 10, 9, 4, base);
        g.poly(p(11, 10, 15, 6, 15, 10, 11, 14), darken(base, 46));
        g.line(3, 10, 10, 10, lighten(base, 70));
        g.set(7, 7, lighten(base, 80));
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage nugget(int base) {
        Canvas g = new Canvas();
        g.ellipse(7.5, 9, 3.6, 3.0, base);
        g.set(6, 8, lighten(base, 70));
        g.set(10, 10, darken(base, 50));
        g.bevel(24, 36);
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage coal(int base, int facet, boolean grain) {
        Canvas g = new Canvas();
        g.poly(p(3, 7, 5, 4, 10, 3, 13, 6, 13, 11, 10, 13, 5, 13, 3, 10), base);
        g.poly(p(5, 4, 10, 3, 9, 6, 6, 7), facet);
        g.poly(p(3, 7, 6, 7, 5, 11, 3, 10), lighten(base, 14));
        if (grain) {
            g.line(7, 9, 11, 9, darken(base, 14));
            g.line(6, 11, 10, 11, darken(base, 14));
        } else {
            g.set(11, 7, lighten(facet, 40));
            g.set(10, 10, lighten(facet, 20));
        }
        g.bevel(16, 22);
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage brick() {
        Canvas g = new Canvas();
        int base = c(178, 84, 62);
        g.poly(p(2, 10, 6, 6, 15, 6, 11, 10), lighten(base, 36));
        g.rect(2, 10, 9, 4, base);
        g.poly(p(11, 10, 15, 6, 15, 10, 11, 14), darken(base, 44));
        g.set(4, 11, darken(base, 30));
        g.set(8, 12, darken(base, 30));
        g.set(6, 8, lighten(base, 60));
        g.set(10, 8, lighten(base, 50));
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage rawOre(int base, int spot, int hi) {
        Canvas g = new Canvas();
        g.poly(p(3, 7, 5, 4, 9, 3, 12, 5, 13, 9, 11, 12, 6, 13, 3, 11), base);
        int[][] spots = {{5, 7}, {9, 5}, {11, 8}, {7, 10}, {10, 11}, {6, 5}};
        for (int[] s : spots) {
            g.set(s[0], s[1], spot);
            g.set(s[0] + 1, s[1], spot);
        }
        g.set(6, 6, hi);
        g.set(9, 4, hi);
        g.bevel(22, 34);
        g.outline(OUTLINE);
        return g.image();
    }

    // ---- food ----

    private static BufferedImage apple() {
        Canvas g = new Canvas();
        int red = c(204, 36, 36);
        g.ellipse(6.2, 9.6, 3.7, 4.3, red);
        g.ellipse(9.8, 9.6, 3.7, 4.3, red);
        g.ellipse(8.0, 11.0, 4.6, 3.6, red);
        g.set(8, 6, 0); // the dip at the top
        g.bevel(10, 40);
        g.set(5, 8, c(255, 150, 140));
        g.set(5, 9, c(255, 120, 110));
        g.set(6, 8, c(240, 100, 96));
        g.line(8, 6, 9, 3, c(98, 64, 32));
        g.poly(p(9, 3, 13, 2, 12, 5), c(78, 150, 50));
        g.set(10, 3, c(130, 200, 90));
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage bread() {
        Canvas g = new Canvas();
        g.ellipse(8, 9, 6.6, 4.2, c(214, 164, 92));
        g.ellipse(8, 8.2, 6.4, 3.4, c(184, 126, 62));
        g.line(5, 6, 6, 9, c(236, 196, 132));
        g.line(8, 5, 9, 9, c(236, 196, 132));
        g.line(11, 6, 12, 9, c(236, 196, 132));
        g.bevel(14, 34);
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage steak(int base, int mark, boolean raw) {
        Canvas g = new Canvas();
        g.poly(p(2, 8, 4, 4, 9, 3, 13, 5, 14, 9, 11, 13, 6, 13, 3, 11), base);
        if (raw) {
            g.line(5, 6, 8, 8, mark);
            g.line(9, 7, 12, 9, mark);
            g.set(7, 11, mark);
        } else {
            g.line(5, 5, 9, 9, mark);
            g.line(8, 4, 12, 8, mark);
            g.line(4, 8, 8, 12, mark);
        }
        g.bevel(22, 38);
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage chop(int base, int mark, int boneTone) {
        Canvas g = new Canvas();
        g.poly(p(2, 7, 4, 4, 10, 3, 14, 6, 13, 11, 8, 13, 4, 12), base);
        g.ellipse(11, 7.5, 1.6, 1.6, boneTone);
        g.set(11, 7, lighten(boneTone, 30));
        g.line(5, 6, 8, 6, mark);
        g.line(4, 9, 9, 10, mark);
        g.bevel(20, 36);
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage drumstick(int meat, int mark) {
        Canvas g = new Canvas();
        g.ellipse(10.5, 6, 4.4, 3.8, meat);
        g.line(8, 8, 4, 12, c(238, 234, 214));
        g.line(9, 8, 5, 12, c(196, 192, 172));
        g.ellipse(3.2, 12.6, 1.7, 1.7, c(238, 234, 214));
        g.set(13, 5, lighten(meat, 36));
        g.set(12, 4, lighten(meat, 36));
        g.set(9, 8, mark);
        g.set(11, 9, mark);
        g.bevel(14, 36);
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage fish(int body, int belly, int fin) {
        Canvas g = new Canvas();
        g.ellipse(7, 8, 5.4, 3.3, body);
        g.poly(p(11, 8, 15, 4, 15, 12), fin);
        g.poly(p(5, 5, 8, 2, 10, 5), fin);
        g.rect(3, 9, 8, 1, belly);
        g.set(4, 7, c(255, 255, 255));
        g.set(4, 8, c(20, 20, 20));
        g.bevel(12, 30);
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage potato(int base, int spot, boolean baked) {
        Canvas g = new Canvas();
        g.ellipse(8, 9, 5.4, 4.2, base);
        if (baked) {
            g.line(5, 7, 10, 6, c(252, 226, 130));
            g.line(6, 8, 9, 7, c(255, 244, 190));
        }
        g.set(6, 11, spot);
        g.set(11, 10, spot);
        g.set(9, 12, spot);
        g.set(5, 9, spot);
        g.bevel(18, 36);
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage kelp(int base, boolean dried) {
        Canvas g = new Canvas();
        if (dried) {
            g.poly(p(3, 3, 12, 2, 13, 13, 4, 14), base);
            for (int y = 4; y < 13; y += 3) g.line(4, y, 12, y, lighten(base, 20));
        } else {
            for (int y = 1; y < 15; y++) {
                int x = 7 + (int) Math.round(2.2 * Math.sin(y * 0.75));
                g.set(x, y, base);
                g.set(x + 1, y, lighten(base, 26));
                g.set(x + 2, y, darken(base, 26));
            }
        }
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage rottenFlesh() {
        Canvas g = new Canvas();
        int base = c(122, 100, 72);
        g.poly(p(3, 6, 6, 3, 11, 4, 13, 8, 12, 12, 7, 13, 4, 11), base);
        g.poly(p(6, 3, 11, 4, 9, 7, 6, 7), c(98, 124, 62));
        g.set(10, 10, c(98, 124, 62));
        g.set(5, 10, c(98, 124, 62));
        g.set(8, 9, c(74, 52, 40));
        g.set(11, 7, c(74, 52, 40));
        g.set(5, 8, c(168, 110, 100));
        g.bevel(18, 32);
        g.outline(OUTLINE);
        return g.image();
    }

    // ---- drops ----

    private static BufferedImage leather() {
        Canvas g = new Canvas();
        int base = c(160, 98, 56);
        g.poly(p(2, 5, 5, 3, 11, 3, 14, 5, 13, 10, 14, 13, 10, 14, 6, 14, 2, 13, 3, 9), base);
        g.line(5, 6, 10, 6, lighten(base, 36));
        g.line(5, 10, 10, 10, darken(base, 30));
        g.set(7, 8, lighten(base, 26));
        g.set(9, 12, darken(base, 30));
        g.bevel(18, 34);
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage feather() {
        Canvas g = new Canvas();
        g.poly(p(2, 14, 4, 7, 10, 3, 14, 1, 13, 6, 9, 11, 4, 14), c(238, 238, 244));
        g.poly(p(2, 14, 4, 7, 7, 8, 5, 12), c(210, 212, 222));
        g.line(3, 14, 14, 2, c(170, 172, 186));
        g.bevel(8, 24);
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage egg() {
        Canvas g = new Canvas();
        g.ellipse(8, 9, 4.2, 5.2, c(232, 214, 176));
        g.set(6, 6, c(252, 244, 220));
        g.set(6, 7, c(252, 244, 220));
        g.set(10, 10, c(190, 160, 120));
        g.set(8, 12, c(190, 160, 120));
        g.bevel(16, 34);
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage diamond() {
        Canvas g = new Canvas();
        g.poly(p(8, 2, 13, 5, 13, 8, 8, 14, 3, 8, 3, 5), c(88, 224, 214));
        g.poly(p(8, 2, 3, 5, 8, 6), c(196, 255, 250));
        g.poly(p(8, 6, 13, 5, 13, 8, 8, 14), c(36, 156, 172));
        g.poly(p(3, 5, 8, 6, 8, 14, 3, 8), c(110, 236, 226));
        g.set(5, 5, c(255, 255, 255));
        g.set(6, 4, c(255, 255, 255));
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage flint() {
        Canvas g = new Canvas();
        g.poly(p(3, 11, 6, 4, 11, 3, 13, 8, 9, 13, 5, 13), c(66, 68, 76));
        g.poly(p(6, 4, 11, 3, 10, 6, 7, 8), c(112, 114, 124));
        g.set(11, 4, c(170, 172, 182));
        g.set(7, 10, c(44, 46, 52));
        g.bevel(12, 20);
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage ball(int base, int hi, int shade) {
        Canvas g = new Canvas();
        g.ellipse(8, 8, 5, 5, base);
        g.ellipse(9.2, 9.4, 3.8, 3.8, shade);
        g.ellipse(7.2, 7.0, 3.4, 3.4, base);
        g.set(5, 5, hi);
        g.set(6, 4, hi);
        g.set(5, 6, hi);
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage glowDust() {
        Canvas g = new Canvas();
        int[][] pts = {{4, 9}, {7, 8}, {10, 9}, {6, 11}, {9, 12}, {12, 11}, {8, 6}, {5, 6}};
        for (int[] q : pts) {
            g.rect(q[0], q[1], 2, 2, c(255, 224, 110));
            g.set(q[0], q[1], c(255, 248, 200));
            g.set(q[0] + 1, q[1] + 1, c(214, 160, 44));
        }
        g.outline(c(58, 34, 8));
        return g.image();
    }

    private static BufferedImage book() {
        Canvas g = new Canvas();
        g.rect(3, 2, 10, 12, c(150, 88, 46));
        g.rect(3, 2, 2, 12, c(108, 60, 30));
        g.rect(5, 12, 8, 2, c(240, 236, 220));
        g.rect(7, 5, 4, 3, c(214, 170, 62));
        g.line(5, 3, 12, 3, c(184, 118, 66));
        g.line(12, 3, 12, 11, darken(c(150, 88, 46), 30));
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage gunpowder() {
        Canvas g = new Canvas();
        g.ellipse(8, 10, 6, 3.6, c(88, 88, 94));
        g.ellipse(6.6, 8, 3.2, 2.6, c(112, 112, 120));
        g.ellipse(10.2, 8.6, 3.0, 2.4, c(70, 70, 76));
        g.set(6, 7, c(168, 168, 178));
        g.set(10, 8, c(150, 150, 160));
        g.set(8, 11, c(150, 150, 160));
        g.set(5, 10, c(40, 40, 46));
        g.set(11, 11, c(40, 40, 46));
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage bone() {
        Canvas g = new Canvas();
        int b = c(238, 234, 214);
        g.line(4, 12, 12, 4, b);
        g.line(5, 12, 13, 4, c(200, 196, 176));
        g.ellipse(3.2, 12.8, 2.1, 2.1, b);
        g.ellipse(12.8, 3.2, 2.1, 2.1, b);
        g.set(12, 2, b);
        g.set(2, 13, c(200, 196, 176));
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage arrow() {
        Canvas g = new Canvas();
        g.line(3, 13, 12, 4, c(160, 118, 72));
        g.line(4, 13, 13, 4, c(118, 84, 48));
        g.poly(p(15, 0, 10, 2, 13, 5), c(168, 170, 178));
        g.poly(p(15, 0, 13, 5, 14, 3), c(110, 112, 122));
        g.poly(p(0, 10, 4, 9, 6, 12, 2, 13), c(240, 240, 246));
        g.poly(p(3, 15, 4, 12, 7, 13, 6, 15), c(210, 212, 220));
        g.outline(OUTLINE);
        return g.image();
    }

    private static BufferedImage string() {
        Canvas g = new Canvas();
        int w = c(242, 242, 242);
        for (int i = 0; i < 3; i++) {
            for (int x = 1; x < 15; x++) {
                int y = 4 + i * 3 + (int) Math.round(1.6 * Math.sin(x * 0.7 + i * 1.7));
                g.set(x, y, w);
            }
        }
        g.outline(c(56, 56, 66));
        return g.image();
    }

    private static BufferedImage spiderEye() {
        Canvas g = new Canvas();
        g.ellipse(8, 8, 5, 5, c(164, 36, 52));
        g.ellipse(9, 9, 3.2, 3.2, c(120, 20, 38));
        g.ellipse(8, 8, 1.6, 1.6, c(40, 6, 16));
        g.set(5, 5, c(236, 130, 140));
        g.set(6, 4, c(236, 130, 140));
        g.set(5, 6, c(212, 90, 100));
        g.bevel(12, 30);
        g.outline(OUTLINE);
        return g.image();
    }

    /**
     * The icon for an item tile, or null when the tile is not a flat item (blocks, mobs, sky bodies...).
     */
    public static BufferedImage draw(Tile tile) {
        return switch (tile) {
            case STICK -> stick();
            case WOODEN_PICKAXE -> pickaxe(tier("WOODEN"));
            case STONE_PICKAXE -> pickaxe(tier("STONE"));
            case IRON_PICKAXE -> pickaxe(tier("IRON"));
            case DIAMOND_PICKAXE -> pickaxe(tier("DIAMOND"));
            case WOODEN_AXE -> axe(tier("WOODEN"));
            case STONE_AXE -> axe(tier("STONE"));
            case IRON_AXE -> axe(tier("IRON"));
            case DIAMOND_AXE -> axe(tier("DIAMOND"));
            case WOODEN_SHOVEL -> shovel(tier("WOODEN"));
            case STONE_SHOVEL -> shovel(tier("STONE"));
            case IRON_SHOVEL -> shovel(tier("IRON"));
            case DIAMOND_SHOVEL -> shovel(tier("DIAMOND"));
            case WOODEN_SWORD -> sword(tier("WOODEN"));
            case STONE_SWORD -> sword(tier("STONE"));
            case IRON_SWORD -> sword(tier("IRON"));
            case DIAMOND_SWORD -> sword(tier("DIAMOND"));

            case LEATHER_HELMET -> helmet(armorTone("LEATHER"), true);
            case LEATHER_CHESTPLATE -> chestplate(armorTone("LEATHER"), true);
            case LEATHER_LEGGINGS -> leggings(armorTone("LEATHER"), true);
            case LEATHER_BOOTS -> boots(armorTone("LEATHER"), true);
            case IRON_HELMET -> helmet(armorTone("IRON"), false);
            case IRON_CHESTPLATE -> chestplate(armorTone("IRON"), false);
            case IRON_LEGGINGS -> leggings(armorTone("IRON"), false);
            case IRON_BOOTS -> boots(armorTone("IRON"), false);
            case GOLD_HELMET -> helmet(armorTone("GOLD"), false);
            case GOLD_CHESTPLATE -> chestplate(armorTone("GOLD"), false);
            case GOLD_LEGGINGS -> leggings(armorTone("GOLD"), false);
            case GOLD_BOOTS -> boots(armorTone("GOLD"), false);
            case DIAMOND_HELMET -> helmet(armorTone("DIAMOND"), false);
            case DIAMOND_CHESTPLATE -> chestplate(armorTone("DIAMOND"), false);
            case DIAMOND_LEGGINGS -> leggings(armorTone("DIAMOND"), false);
            case DIAMOND_BOOTS -> boots(armorTone("DIAMOND"), false);

            case COAL -> coal(c(34, 34, 40), c(78, 78, 90), false);
            case CHARCOAL -> coal(c(62, 48, 40), c(102, 78, 62), true);
            case IRON_INGOT -> ingot(c(208, 208, 212));
            case GOLD_INGOT -> ingot(c(234, 192, 44));
            case COPPER_INGOT -> ingot(c(200, 112, 72));
            case IRON_NUGGET -> nugget(c(222, 222, 226));
            case BRICK -> brick();
            case RAW_IRON -> rawOre(c(168, 136, 112), c(120, 88, 70), c(226, 200, 180));
            case RAW_GOLD -> rawOre(c(226, 188, 70), c(172, 124, 30), c(255, 240, 160));
            case RAW_COPPER -> rawOre(c(196, 118, 80), c(120, 150, 110), c(240, 170, 130));

            case APPLE -> apple();
            case BREAD -> bread();
            case COOKED_BEEF -> steak(c(150, 84, 52), c(88, 44, 28), false);
            case RAW_BEEF -> steak(c(198, 68, 70), c(244, 204, 204), true);
            case RAW_PORKCHOP -> chop(c(236, 150, 150), c(252, 206, 200), c(238, 234, 214));
            case COOKED_PORKCHOP -> chop(c(170, 106, 70), c(110, 62, 38), c(238, 234, 214));
            case RAW_CHICKEN -> drumstick(c(236, 196, 168), c(250, 224, 204));
            case COOKED_CHICKEN -> drumstick(c(196, 128, 62), c(130, 78, 34));
            case RAW_MUTTON -> chop(c(186, 84, 104), c(230, 170, 176), c(238, 234, 214));
            case COOKED_MUTTON -> chop(c(148, 94, 68), c(96, 56, 36), c(238, 234, 214));
            case RAW_RABBIT -> drumstick(c(204, 130, 118), c(240, 190, 176));
            case COOKED_RABBIT -> drumstick(c(170, 108, 68), c(110, 66, 36));
            case COD -> fish(c(186, 150, 100), c(228, 208, 170), c(150, 116, 70));
            case COOKED_COD -> fish(c(176, 126, 76), c(222, 184, 130), c(130, 90, 50));
            case SALMON -> fish(c(214, 100, 86), c(246, 176, 160), c(170, 72, 62));
            case COOKED_SALMON -> fish(c(190, 112, 70), c(236, 168, 120), c(140, 78, 44));
            case POTATO -> potato(c(206, 170, 100), c(150, 114, 58), false);
            case BAKED_POTATO -> potato(c(184, 122, 56), c(120, 76, 30), true);
            case KELP -> kelp(c(50, 128, 56), false);
            case DRIED_KELP -> kelp(c(62, 84, 40), true);
            case ROTTEN_FLESH -> rottenFlesh();

            case LEATHER -> leather();
            case FEATHER -> feather();
            case EGG -> egg();
            case DIAMOND -> diamond();
            case FLINT -> flint();
            case CLAY_BALL -> ball(c(162, 168, 186), c(222, 228, 240), c(120, 126, 146));
            case SNOWBALL -> ball(c(246, 250, 252), c(255, 255, 255), c(190, 214, 236));
            case GLOWSTONE_DUST -> glowDust();
            case BOOK -> book();
            case GUNPOWDER -> gunpowder();
            case BONE -> bone();
            case ARROW -> arrow();
            case STRING -> string();
            case SPIDER_EYE -> spiderEye();
            default -> null;
        };
    }
}
