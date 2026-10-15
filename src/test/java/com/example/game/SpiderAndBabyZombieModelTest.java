package com.example.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The spider's walking legs (SpiderModel.setupAnim) and the dedicated baby zombie model. */
class SpiderAndBabyZombieModelTest {
    private static final float[] TINT = {1f, 1f, 1f};

    private static float[] spider(MobModelBuilder.Pose pose) {
        float[] out = new float[MobModelBuilder.FLOATS_PER_MOB];
        int n = MobModelBuilder.emitSpider(out, 0, 0f, 0f, 0f, 0f, 0f, pose, 1f, 0f, TINT);
        float[] r = new float[n];
        System.arraycopy(out, 0, r, 0, n);
        return r;
    }

    private static MobModelBuilder.Pose walking(float pos, float speed) {
        return new MobModelBuilder.Pose(pos, speed, 0f, 0f, 0f, false, 0f, 0f);
    }

    @Test
    void aStandingSpiderIsTheSameWhateverTheWalkPosition() {
        assertArrayEquals(spider(walking(0f, 0f)), spider(walking(7.3f, 0f)));
    }

    @Test
    void aWalkingSpiderMovesItsLegsAndThePositionChangesWhichOnes() {
        float[] rest = spider(walking(0f, 0f));
        float[] a = spider(walking(1f, 1f));
        float[] b = spider(walking(2f, 1f));
        assertFalse(java.util.Arrays.equals(rest, a));
        assertFalse(java.util.Arrays.equals(a, b));
    }

    @Test
    void theHeadTurnsWithTheLookDirectionButTheBodyDoesNot() {
        float[] straight = spider(walking(0f, 0f));
        float[] turned = spider(new MobModelBuilder.Pose(0f, 0f, 0.6f, 0.2f, 0f, false, 0f, 0f));
        int box = MobModelBuilder.FLOATS_PER_MOB / MobModelBuilder.BOXES; // one box worth of floats
        assertEquals(straight.length, turned.length);
        boolean headMoved = false, bodyMoved = false;
        for (int i = 0; i < straight.length; i++) {
            if (straight[i] != turned[i]) {
                if (i < box) headMoved = true;
                else bodyMoved = true;
            }
        }
        assertTrue(headMoved, "the head box is the first one written");
        assertFalse(bodyMoved, "neck, abdomen and legs keep still");
    }

    @Test
    void theBabyZombieIsItsOwnSmallModelNotAScaledAdult() {
        float[] out = new float[MobModelBuilder.FLOATS_PER_MOB];
        int n = MobModelBuilder.emitBabyZombie(out, 0, 0f, 0f, 0f, 0f, 0f, MobModelBuilder.Pose.REST, 1f, 0f, TINT);
        int perBox = ItemModelBuilder.BOX_VERTICES * ItemModelBuilder.FLOATS_PER_VERTEX;
        assertEquals(7 * perBox, n, "head, outer head layer, body, two arms and two legs");
        float top = 0f;
        for (int i = 1; i < n; i += ItemModelBuilder.FLOATS_PER_VERTEX) top = Math.max(top, out[i]);
        // The head is 6 px wide and its top is at 24 - 15.25 + 6.25 = 15 px: well under the adult's 32 px, and about 0.94 blocks.
        assertEquals(15f / 16f, top, 0.02f);
    }
}
