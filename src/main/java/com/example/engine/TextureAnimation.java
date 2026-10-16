package com.example.engine;

import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An animated texture as the 26.3 client plays it (SpriteContents.AnimationState, the {@code .png.mcmeta} animation format described on
 * minecraft.wiki's Resource pack page): the image is a vertical strip of square frames (as wide as the image), the metadata gives the
 * order and the length of each frame in game ticks ({@code frametime}, default 1; a frame can carry its own {@code time}), and with
 * {@code interpolate} the picture is blended toward the next frame while the current one lasts. Without a {@code frames} list every
 * frame is shown once, top to bottom. {@link #tick()} is called once per game tick (20 a second).
 */
public final class TextureAnimation {
    private final BufferedImage strip;
    private final int frameSize;
    private final int frameCount;
    private final int[] indices, times;
    private final boolean interpolate;
    private int position, subFrame;
    private boolean changed = true;

    private TextureAnimation(BufferedImage strip, int frameSize, int[] indices, int[] times, boolean interpolate) {
        this.strip = strip;
        this.frameSize = frameSize;
        this.frameCount = strip.getHeight() / frameSize;
        this.indices = indices;
        this.times = times;
        this.interpolate = interpolate;
    }

    private static final Pattern FRAMETIME = Pattern.compile("\"frametime\"\\s*:\\s*(\\d+)");
    private static final Pattern INTERPOLATE = Pattern.compile("\"interpolate\"\\s*:\\s*true");
    private static final Pattern FRAMES = Pattern.compile("\"frames\"\\s*:\\s*\\[([^\\]]*)\\]");
    private static final Pattern FRAME_ENTRY = Pattern.compile("\\{[^}]*\\}|\\d+");
    private static final Pattern INDEX = Pattern.compile("\"index\"\\s*:\\s*(\\d+)");
    private static final Pattern TIME = Pattern.compile("\"time\"\\s*:\\s*(\\d+)");

    /** The animation described by {@code mcmeta} for a strip of frames, or null when the metadata has no {@code animation} section. */
    public static TextureAnimation parse(BufferedImage strip, String mcmeta) {
        if (mcmeta == null || !mcmeta.contains("\"animation\"")) return null;
        int frameSize = Math.min(strip.getWidth(), strip.getHeight());
        int frameCount = Math.max(1, strip.getHeight() / frameSize);
        Matcher ft = FRAMETIME.matcher(mcmeta);
        int frameTime = ft.find() ? Math.max(1, Integer.parseInt(ft.group(1))) : 1;
        boolean interpolate = INTERPOLATE.matcher(mcmeta).find();
        List<int[]> sequence = new ArrayList<>();
        Matcher fr = FRAMES.matcher(mcmeta);
        if (fr.find()) {
            Matcher entry = FRAME_ENTRY.matcher(fr.group(1));
            while (entry.find()) {
                String e = entry.group();
                if (e.startsWith("{")) {
                    Matcher index = INDEX.matcher(e), time = TIME.matcher(e);
                    if (!index.find()) continue;
                    sequence.add(new int[]{Integer.parseInt(index.group(1)), time.find() ? Math.max(1, Integer.parseInt(time.group(1))) : frameTime});
                } else {
                    sequence.add(new int[]{Integer.parseInt(e), frameTime});
                }
            }
        }
        if (sequence.isEmpty()) for (int i = 0; i < frameCount; i++) sequence.add(new int[]{i, frameTime});
        int[] indices = new int[sequence.size()], times = new int[sequence.size()];
        for (int i = 0; i < indices.length; i++) {
            indices[i] = Math.min(sequence.get(i)[0], frameCount - 1);
            times[i] = sequence.get(i)[1];
        }
        return new TextureAnimation(strip, frameSize, indices, times, interpolate);
    }

    public int frameSize() {
        return frameSize;
    }

    public int frameCount() {
        return frameCount;
    }

    /** The index (from the top of the strip) of the frame shown now. */
    public int currentFrame() {
        return indices[position];
    }

    /** One game tick: the current frame has been shown for one more tick; after its time the next one in the sequence takes over. */
    public void tick() {
        subFrame++;
        if (subFrame >= times[position]) {
            position = (position + 1) % indices.length;
            subFrame = 0;
            changed = true;
        } else if (interpolate) {
            changed = true;
        }
    }

    /** True once after the picture has changed (always while interpolating), so the caller re-uploads only when needed. */
    public boolean takeChanged() {
        boolean c = changed;
        changed = false;
        return c;
    }

    /** Ticks of the whole cycle (the sum of the frame times). */
    public int cycleTicks() {
        int sum = 0;
        for (int t : times) sum += t;
        return sum;
    }

    /** The {@code size} x {@code size} texels at ({@code rx}, {@code ry}) of the current frame (blended with the next when interpolating), ARGB. */
    public int[] region(int rx, int ry, int size) {
        int[] now = frameRegion(indices[position], rx, ry, size);
        if (!interpolate) return now;
        int[] next = frameRegion(indices[(position + 1) % indices.length], rx, ry, size);
        float mix = subFrame / (float) times[position];
        int[] out = new int[now.length];
        for (int i = 0; i < out.length; i++) out[i] = lerp(now[i], next[i], mix);
        return out;
    }

    private int[] frameRegion(int frame, int rx, int ry, int size) {
        int[] out = new int[size * size];
        strip.getRGB(rx, frame * frameSize + ry, size, size, out, 0, size);
        return out;
    }

    private static int lerp(int a, int b, float t) {
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int ca = (a >>> shift) & 0xFF, cb = (b >>> shift) & 0xFF;
            out |= Math.round(ca + (cb - ca) * t) << shift;
        }
        return out;
    }
}
