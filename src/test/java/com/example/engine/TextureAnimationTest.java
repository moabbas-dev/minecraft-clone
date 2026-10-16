package com.example.engine;

import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** The animation format of the .png.mcmeta files (frames, frametime, per-frame time, interpolate) and the real timings of the 26.3 textures. */
class TextureAnimationTest {
    /** A strip of {@code frames} squares of {@code size}, frame i filled with the grey level i * 8 (alpha 255). */
    private static BufferedImage strip(int size, int frames) {
        BufferedImage img = new BufferedImage(size, size * frames, BufferedImage.TYPE_INT_ARGB);
        for (int f = 0; f < frames; f++) for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int g = f * 8;
            img.setRGB(x, f * size + y, 0xFF000000 | (g << 16) | (g << 8) | g);
        }
        return img;
    }

    private static int grey(TextureAnimation a) {
        return a.region(0, 0, 1)[0] & 0xFF;
    }

    @Test
    void anEmptyAnimationShowsEveryFrameForOneTickInOrder() {
        TextureAnimation a = TextureAnimation.parse(strip(16, 4), "{ \"animation\": {} }");
        assertEquals(16, a.frameSize());
        assertEquals(4, a.frameCount());
        assertEquals(4, a.cycleTicks());
        for (int tick = 0; tick < 9; tick++) {
            assertEquals((tick % 4) * 8, grey(a), "tick " + tick);
            a.tick();
        }
    }

    @Test
    void frametimeHoldsEachFrameForThatManyTicks() {
        TextureAnimation a = TextureAnimation.parse(strip(16, 3), "{ \"animation\": { \"frametime\": 2 } }");
        assertEquals(6, a.cycleTicks());
        int[] seen = new int[8];
        for (int i = 0; i < 8; i++) {
            seen[i] = grey(a);
            a.tick();
        }
        assertArrayEquals(new int[]{0, 0, 8, 8, 16, 16, 0, 0}, seen);
    }

    @Test
    void aFramesListSetsTheOrderAndSingleFramesCanCarryTheirOwnTime() {
        TextureAnimation a = TextureAnimation.parse(strip(16, 3),
                "{ \"animation\": { \"frametime\": 2, \"frames\": [ 2, { \"index\": 0, \"time\": 5 }, 1 ] } }");
        assertEquals(2 + 5 + 2, a.cycleTicks());
        int[] seen = new int[10];
        for (int i = 0; i < 10; i++) {
            seen[i] = grey(a);
            a.tick();
        }
        assertArrayEquals(new int[]{16, 16, 0, 0, 0, 0, 0, 8, 8, 16}, seen);
    }

    @Test
    void interpolationBlendsTowardTheNextFrameAndTheTexturesChangeEveryTick() {
        TextureAnimation a = TextureAnimation.parse(strip(16, 2), "{ \"animation\": { \"frametime\": 4, \"interpolate\": true } }");
        assertEquals(0, grey(a));
        a.tick();
        assertEquals(2, grey(a), "a quarter of the way from grey 0 to grey 8");
        a.tick();
        a.tick();
        assertEquals(6, grey(a));
        assertTrue(a.takeChanged());
    }

    @Test
    void aTextureWithoutAnAnimationSectionIsNotAnimated() {
        assertNull(TextureAnimation.parse(strip(16, 2), "{ \"texture\": { \"blur\": true } }"));
        assertNull(TextureAnimation.parse(strip(16, 2), null));
    }

    @Test
    void theFlowingSpritesAre32PixelFramesAndTheRegionIsReadFromTheRightPlace() {
        BufferedImage img = new BufferedImage(32, 64, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(8, 8, 0xFF112233); // the middle region's top-left texel of frame 0
        img.setRGB(8, 32 + 8, 0xFF445566); // the same texel of frame 1
        TextureAnimation a = TextureAnimation.parse(img, "{ \"animation\": {} }");
        assertEquals(32, a.frameSize());
        assertEquals(2, a.frameCount());
        assertEquals(0xFF112233, a.region(8, 8, 16)[0]);
        a.tick();
        assertEquals(0xFF445566, a.region(8, 8, 16)[0]);
    }

    @Test
    void theRealWaterAndLavaTexturesRunAtTheirVanillaSpeeds() throws Exception {
        File water = new File("textures/block/water_still.png"), lava = new File("textures/block/lava_still.png");
        assumeTrue(water.isFile() && new File(water.getPath() + ".mcmeta").isFile() && lava.isFile() && new File(lava.getPath() + ".mcmeta").isFile(),
                "the user's texture files are not restored here");
        TextureAnimation w = TextureAnimation.parse(TextureIo.read(water), Files.readString(new File(water.getPath() + ".mcmeta").toPath()));
        assertEquals(32, w.frameCount());
        assertEquals(64, w.cycleTicks(), "32 frames of 2 ticks");
        TextureAnimation l = TextureAnimation.parse(TextureIo.read(lava), Files.readString(new File(lava.getPath() + ".mcmeta").toPath()));
        assertEquals(20, l.frameCount());
        assertEquals(76, l.cycleTicks(), "38 listed frames (0 to 19 and back down to 1) of 2 ticks");
        int[] order = new int[40];
        for (int i = 0; i < 40; i++) {
            order[i] = l.currentFrame();
            l.tick();
            l.tick();
        }
        assertEquals(19, order[19]);
        assertEquals(18, order[20], "it plays the frames back down after frame 19");
        assertEquals(1, order[37]);
        assertEquals(0, order[38], "and starts again");
    }
}
