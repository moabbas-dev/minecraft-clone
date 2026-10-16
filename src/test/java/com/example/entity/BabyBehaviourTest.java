package com.example.entity;

import com.example.entity.mob.Cow;
import com.example.entity.mob.Pig;
import com.example.world.FlatWorldGenerator;
import com.example.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BabyBehaviourTest {
    /** The flat world starts at the bottom of the world: {@code BOTTOM + n} is the n-th layer above its bedrock (grass at 3, air from 4). */
    private static final int BOTTOM = com.example.world.Chunk.MIN_Y;

    private static World flatWorld() {
        World world = new World(1L, new FlatWorldGenerator());
        for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) world.generateChunk(world.getOrCreateChunk(cx, cz));
        return world;
    }

    @Test
    void aBabyWalksTowardsItsParentBeyondThreeBlocks() {
        World world = flatWorld();
        Pig adult = new Pig(new Vector3f(6.5f, BOTTOM + 5f, 0.5f), 0f);
        Pig baby = new Pig(new Vector3f(0.5f, BOTTOM + 5f, 0.5f), 0f);
        baby.makeBaby();
        List<Mob> all = List.of(adult, baby);
        baby.setNeighbors(all);
        float before = baby.position.distance(adult.position);
        for (int i = 0; i < 200; i++) baby.tick(world, List.of(), 0.05f);
        assertTrue(baby.position.distance(adult.position) < before - 1.5f,
                "the baby closed in on its parent: " + baby.position.distance(adult.position));
    }

    @Test
    void anAdultDoesNotFollowAnotherAdult() {
        World world = flatWorld();
        Pig a = new Pig(new Vector3f(0.5f, BOTTOM + 5f, 0.5f), 0f);
        Pig b = new Pig(new Vector3f(6.5f, BOTTOM + 5f, 0.5f), 0f);
        a.setNeighbors(List.of(a, b));
        assertFalse(new com.example.entity.ai.FollowParentGoal().canStart(a, world, List.of()));
    }

    @Test
    void aBabyIgnoresAParentOfAnotherKindOrOutOfRange() {
        World world = flatWorld();
        Pig baby = new Pig(new Vector3f(0.5f, BOTTOM + 5f, 0.5f), 0f);
        baby.makeBaby();
        Cow cow = new Cow(new Vector3f(5.5f, BOTTOM + 5f, 0.5f), 0f);
        Pig far = new Pig(new Vector3f(12.5f, BOTTOM + 5f, 0.5f), 0f);
        baby.setNeighbors(List.of(baby, cow, far));
        assertFalse(new com.example.entity.ai.FollowParentGoal().canStart(baby, world, List.of()));
    }

    @Test
    void aBabyAlreadyWithinThreeBlocksStaysPut() {
        World world = flatWorld();
        Pig baby = new Pig(new Vector3f(0.5f, BOTTOM + 5f, 0.5f), 0f);
        baby.makeBaby();
        Pig adult = new Pig(new Vector3f(2.5f, BOTTOM + 5f, 0.5f), 0f);
        baby.setNeighbors(List.of(baby, adult));
        assertFalse(new com.example.entity.ai.FollowParentGoal().canStart(baby, world, List.of()));
    }

    @Test
    void babySpeechIsOneAndAHalfTimesAsHighButFootstepsAreNot() {
        Pig baby = new Pig(new Vector3f(), 0f);
        baby.makeBaby();
        baby.emitSound("say");
        baby.emitSound("step");
        List<Mob.SoundEvent> sounds = baby.drainSounds();
        assertEquals(1.5f, sounds.get(0).pitch(), 0f);
        assertEquals(1f, sounds.get(1).pitch(), 0f);
        Pig adult = new Pig(new Vector3f(), 0f);
        adult.emitSound("say");
        assertEquals(1f, adult.drainSounds().get(0).pitch(), 0f);
    }
}
