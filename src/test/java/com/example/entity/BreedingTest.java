package com.example.entity;

import com.example.entity.ai.BreedGoal;
import com.example.entity.mob.Chicken;
import com.example.entity.mob.Cow;
import com.example.entity.mob.PassiveMob;
import com.example.entity.mob.Pig;
import com.example.world.BlockType;
import com.example.world.FlatWorldGenerator;
import com.example.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Breeding as the 26.3 client does it: the foods, 30 s of love, the 60-tick walk to a partner, the baby and the 5 minute wait. */
class BreedingTest {
    private static World flatWorld() {
        World world = new World(1L, new FlatWorldGenerator());
        for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) world.generateChunk(world.getOrCreateChunk(cx, cz));
        return world;
    }

    @Test
    void eachSpeciesLikesItsOwnFood() {
        Pig pig = new Pig(new Vector3f(), 0f);
        Cow cow = new Cow(new Vector3f(), 0f);
        Chicken chicken = new Chicken(new Vector3f(), 0f);
        assertTrue(pig.isBreedingItem(BlockType.CARROT) && pig.isBreedingItem(BlockType.POTATO) && pig.isBreedingItem(BlockType.BEETROOT));
        assertFalse(pig.isBreedingItem(BlockType.WHEAT));
        assertTrue(cow.isBreedingItem(BlockType.WHEAT));
        assertFalse(cow.isBreedingItem(BlockType.CARROT));
        assertTrue(chicken.isBreedingItem(BlockType.WHEAT_SEEDS) && chicken.isBreedingItem(BlockType.BEETROOT_SEEDS));
        assertFalse(chicken.isBreedingItem(BlockType.WHEAT));
    }

    @Test
    void feedingPutsAnAdultInLoveForThirtySecondsOnceAndAFoodOfAnotherKindDoesNothing() {
        Pig pig = new Pig(new Vector3f(), 0f);
        assertFalse(pig.feed(BlockType.WHEAT));
        assertFalse(pig.isInLove());
        assertTrue(pig.feed(BlockType.CARROT));
        assertTrue(pig.isInLove());
        assertEquals(30f, pig.loveSecondsLeft(), 1e-4f);
        assertFalse(pig.feed(BlockType.CARROT), "already in love: the food is not taken");
    }

    @Test
    void aBabyOnlyGrowsUpTenPercentOfItsRemainingTimeSooner() {
        Pig baby = new Pig(new Vector3f(), 0f);
        baby.makeBaby();
        assertTrue(baby.feed(BlockType.CARROT));
        assertFalse(baby.isInLove());
        assertEquals(PassiveMob.BABY_SECONDS - 120f, -baby.extraState(), 1e-3f);
    }

    @Test
    void twoPigsInLoveWalkTogetherAndHaveABabyThenWaitFiveMinutes() {
        World world = flatWorld();
        MobManager mobs = new MobManager();
        Pig a = new Pig(new Vector3f(0.5f, 5f, 0.5f), 0f);
        Pig b = new Pig(new Vector3f(5.5f, 5f, 0.5f), 0f);
        mobs.spawn(a);
        mobs.spawn(b);
        a.feed(BlockType.CARROT);
        b.feed(BlockType.POTATO);
        ItemEntityManager items = new ItemEntityManager(64);
        for (int i = 0; i < 600 && mobs.size() < 3; i++) {
            mobs.tick(world, List.of(), new Vector3f(2f, 5f, 0.5f), items, new Random(3), 0.05f);
        }
        assertEquals(3, mobs.size(), "a baby was born");
        PassiveMob baby = (PassiveMob) mobs.mobs().get(2);
        assertTrue(baby.isBaby());
        assertEquals("pig", baby.typeId());
        assertFalse(a.isInLove());
        assertFalse(b.isInLove());
        assertEquals(PassiveMob.BREED_COOLDOWN_SECONDS, a.cooldownSecondsLeft(), 1f);
        assertFalse(a.feed(BlockType.CARROT), "the parents have to wait 6000 ticks");
    }

    @Test
    void loveGoesOutIfNoPartnerComes() {
        World world = flatWorld();
        Pig lonely = new Pig(new Vector3f(0.5f, 5f, 0.5f), 0f);
        lonely.feed(BlockType.CARROT);
        lonely.setNeighbors(List.of(lonely));
        for (int i = 0; i < 700; i++) lonely.tick(world, List.of(), 0.05f);
        assertFalse(lonely.isInLove(), "after 600 ticks");
    }

    @Test
    void theGoalIgnoresBabiesAnotherKindAndPartnersOutOfRange() {
        World world = flatWorld();
        Pig a = new Pig(new Vector3f(0.5f, 5f, 0.5f), 0f);
        a.feed(BlockType.CARROT);
        Pig babyInLove = new Pig(new Vector3f(2.5f, 5f, 0.5f), 0f);
        babyInLove.makeBaby();
        Cow cow = new Cow(new Vector3f(3.5f, 5f, 0.5f), 0f);
        cow.feed(BlockType.WHEAT);
        Pig far = new Pig(new Vector3f(12.5f, 5f, 0.5f), 0f);
        far.feed(BlockType.CARROT);
        a.setNeighbors(List.of(a, babyInLove, cow, far));
        assertFalse(new BreedGoal().canStart(a, world, List.of()));
        Pig near = new Pig(new Vector3f(6.5f, 5f, 0.5f), 0f);
        near.feed(BlockType.CARROT);
        a.setNeighbors(List.of(a, near));
        assertTrue(new BreedGoal().canStart(a, world, List.of()));
    }
}
