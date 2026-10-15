package com.example.entity.mob;

import com.example.entity.Mob;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Baby pigs, cows and chickens: half-size hitbox, 20 minutes to grow up, no loot or experience, saved as remaining age. */
class BabyAnimalTest {
    private static final Vector3f AT = new Vector3f(0, 4, 0);

    @Test
    void aBabyHasHalfTheHitboxAndAnAdultIsUnchanged() {
        Pig adult = new Pig(AT, 0f);
        Pig baby = new Pig(AT, 0f);
        baby.makeBaby();
        assertEquals(Pig.WIDTH, adult.getWidth());
        assertEquals(Pig.WIDTH * 0.5f, baby.getWidth());
        assertEquals(Cow.HEIGHT * 0.5f, babyCow().getHeight());
        Chicken chick = new Chicken(AT, 0f);
        chick.makeBaby();
        assertEquals(Chicken.HEIGHT * 0.5f, chick.getHeight());
        assertTrue(baby.isBaby());
        assertFalse(adult.isBaby());
    }

    private static Cow babyCow() {
        Cow c = new Cow(AT, 0f);
        c.makeBaby();
        return c;
    }

    @Test
    void aBabyGrowsUpAfterTwentyMinutes() {
        Pig baby = new Pig(AT, 0f);
        baby.makeBaby();
        for (int i = 0; i < 1199; i++) baby.onTick(null, 1f);
        assertTrue(baby.isBaby());
        baby.onTick(null, 1f);
        assertFalse(baby.isBaby());
        assertEquals(Pig.HEIGHT, baby.getHeight());
    }

    @Test
    void babiesDropNothingAndGiveNoExperienceButAdultsDo() {
        Random random = new Random(1);
        Cow baby = babyCow();
        assertTrue(baby.getDrops(random).isEmpty());
        assertEquals(0, baby.xpReward(random));
        Cow adult = new Cow(AT, 0f);
        assertFalse(adult.getDrops(random).isEmpty());
        assertTrue(adult.xpReward(random) >= 1);
    }

    @Test
    void theRemainingAgeSurvivesASaveAndAnAdultKeepsItsEggTimer() {
        Pig baby = new Pig(AT, 0f);
        baby.makeBaby();
        baby.onTick(null, 100f);
        float saved = baby.extraState();
        assertEquals(-1100f, saved, 1e-3f);
        Pig loaded = new Pig(AT, 0f);
        loaded.restoreExtraState(saved);
        assertTrue(loaded.isBaby());

        Chicken hen = new Chicken(AT, 0f);
        float egg = hen.extraState();
        assertTrue(egg > 0f);
        Chicken loadedHen = new Chicken(AT, 0f);
        loadedHen.restoreExtraState(egg);
        assertEquals(egg, loadedHen.extraState(), 1e-4f);
        assertFalse(loadedHen.isBaby());
    }

    @Test
    void aChickLaysNoEggsUntilItHasGrownUp() {
        Chicken chick = new Chicken(AT, 0f);
        chick.makeBaby();
        for (int i = 0; i < 1100; i++) chick.onTick(null, 1f);
        assertNull(chick.takeLaidItem(), "a chick never lays");
        for (int i = 0; i < 101; i++) chick.onTick(null, 1f);
        assertFalse(chick.isBaby());
    }

    @Test
    void theControllerTypeIdsSpawnBabies() {
        Mob pig = MobTypes.create("pig_baby", AT, 0f, null);
        assertNotNull(pig);
        assertTrue(pig.isBaby());
        assertEquals("pig", pig.typeId());
        Mob zombie = MobTypes.create("zombie_baby", AT, 0f, () -> com.example.world.Difficulty.NORMAL);
        assertNotNull(zombie, "the baby zombie has its own model now");
        assertTrue(zombie.isBaby());
        assertEquals("zombie", zombie.typeId());
        assertNull(MobTypes.create("creeper_baby", AT, 0f, null), "the other hostile mobs have no baby");
    }
}
