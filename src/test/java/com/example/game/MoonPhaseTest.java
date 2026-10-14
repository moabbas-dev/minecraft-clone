package com.example.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MoonPhaseTest {
    @Test
    void theFirstNightIsAFullMoon() {
        assertEquals(0, Game.moonPhase(0, 0.8f)); // the evening of day 0
    }

    @Test
    void thePhaseChangesAtSunriseNotAtMidnight() {
        assertEquals(0, Game.moonPhase(0, 0.99f));
        assertEquals(0, Game.moonPhase(1, 0.10f), "after midnight it is still the same night");
        assertEquals(1, Game.moonPhase(1, 0.30f), "the next day's phase starts at sunrise");
    }

    @Test
    void thePhaseCyclesEveryEightDays() {
        for (int day = 0; day < 20; day++) assertEquals(day % 8, Game.moonPhase(day, 0.8f));
    }

    @Test
    void aWorldStartedBeforeTheFirstSunriseStillGivesAValidPhase() {
        assertEquals(7, Game.moonPhase(0, 0.1f));
    }
}
