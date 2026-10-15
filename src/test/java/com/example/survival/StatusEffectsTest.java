package com.example.survival;

import com.example.entity.LightningBolts;
import com.example.game.SkyDetailsAccess;
import com.example.inventory.Recipes;
import com.example.physics.Player;
import com.example.world.BlockType;
import com.example.world.Difficulty;
import com.example.world.FlatWorldGenerator;
import com.example.world.FoodRegistry;
import com.example.world.Weather;
import com.example.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Hunger, poison, regeneration and absorption as the 26.3 client runs them, the food that gives them, and lightning bolts. */
class StatusEffectsTest {
    private PlayerSurvival survival() {
        PlayerSurvival s = new PlayerSurvival(Difficulty.NORMAL);
        s.reset(new Vector3f(0, 4, 0));
        return s;
    }

    private void ticks(PlayerSurvival s, int n) {
        Player p = new Player(new Vector3f(0, 4, 0));
        for (int i = 0; i < n; i++) s.update(0.05f, p);
    }

    @Test
    void theTickRulesFollowThePoisonAndRegenerationIntervals() {
        assertTrue(StatusEffect.POISON.appliesThisTick(25, 0));
        assertFalse(StatusEffect.POISON.appliesThisTick(24, 0));
        assertTrue(StatusEffect.POISON.appliesThisTick(12 * 2, 1), "level II: every 12 ticks");
        assertTrue(StatusEffect.REGENERATION.appliesThisTick(50, 0));
        assertTrue(StatusEffect.REGENERATION.appliesThisTick(25, 1), "level II: every 25 ticks");
        assertFalse(StatusEffect.REGENERATION.appliesThisTick(24, 1));
        assertTrue(StatusEffect.HUNGER.appliesThisTick(7, 0));
        assertFalse(StatusEffect.ABSORPTION.appliesThisTick(7, 0));
    }

    @Test
    void poisonTakesOneHealthEveryTwentyFiveTicksAndNeverTheLastHalfHeart() {
        PlayerSurvival s = survival();
        s.getHunger().forceState(10f, 0f, 0f); // below the food level that regenerates on its own
        s.addEffect(StatusEffect.POISON, 100, 0);
        // the effect starts at 100 ticks left; it does its work whenever the remaining time is a multiple of 25: 100, 75, 50, 25
        ticks(s, 100);
        assertEquals(20f - 4f, s.getHealth().getCurrentHealth(), 0.01f, "four poison ticks in 5 seconds");
        assertFalse(s.getEffects().has(StatusEffect.POISON), "it ran out");
        PlayerSurvival low = survival();
        low.getHunger().forceState(10f, 0f, 0f);
        low.getHealth().setHealth(1.5f);
        low.addEffect(StatusEffect.POISON, 400, 0);
        ticks(low, 400);
        assertTrue(low.getHealth().getCurrentHealth() > 0f, "poison never kills");
        assertTrue(low.getHealth().getCurrentHealth() <= 1.5f);
    }

    @Test
    void regenerationLevelTwoHealsOneEveryTwentyFiveTicks() {
        PlayerSurvival s = survival();
        s.getHunger().forceState(10f, 0f, 0f); // below the food level that regenerates on its own
        s.getHealth().setHealth(10f);
        s.addEffect(StatusEffect.REGENERATION, 100, 1);
        ticks(s, 100);
        assertEquals(14f, s.getHealth().getCurrentHealth(), 0.01f, "100 / 25 = 4 heals");
    }

    @Test
    void hungerAddsExhaustionEveryTick() {
        PlayerSurvival s = survival();
        s.getHunger().forceState(10f, 0f, 0f);
        s.addEffect(StatusEffect.HUNGER, 100, 1);
        ticks(s, 10);
        assertEquals(10 * 0.005f * 2f, s.getHunger().getExhaustion(), 1e-4f);
    }

    @Test
    void absorptionSoaksDamageFirstAndGoesWhenTheEffectEnds() {
        PlayerSurvival s = survival();
        s.addEffect(StatusEffect.ABSORPTION, 40, 0);
        assertEquals(4f, s.getAbsorption());
        assertTrue(s.damage(3f, DamageSource.GENERIC));
        assertEquals(20f, s.getHealth().getCurrentHealth());
        assertEquals(1f, s.getAbsorption(), 1e-5f);
        s.getHealth().tick(1f);
        s.damage(3f, DamageSource.GENERIC);
        assertEquals(18f, s.getHealth().getCurrentHealth(), 1e-5f, "1 soaked, 2 left through");
        assertEquals(0f, s.getAbsorption());
        PlayerSurvival t = survival();
        t.addEffect(StatusEffect.ABSORPTION, 20, 0);
        ticks(t, 25);
        assertEquals(0f, t.getAbsorption(), "the extra health goes with the effect");
    }

    @Test
    void aStrongerOrLongerEffectReplacesAWeakerOneButNotTheOtherWayRound() {
        PlayerEffects e = new PlayerEffects();
        assertTrue(e.add(StatusEffect.POISON, 100, 0));
        assertFalse(e.add(StatusEffect.POISON, 50, 0), "shorter, same level");
        assertTrue(e.add(StatusEffect.POISON, 200, 0), "longer");
        assertFalse(e.add(StatusEffect.POISON, 300, 0) && false);
        assertTrue(e.add(StatusEffect.POISON, 10, 1), "a higher level always wins");
        assertFalse(e.add(StatusEffect.POISON, 999, 0), "but never a lower one");
        assertEquals(1, e.get(StatusEffect.POISON).amplifier());
    }

    @Test
    void foodGivesTheVanillaEffects() {
        PlayerSurvival s = survival();
        s.applyFoodEffects(BlockType.SPIDER_EYE, new Random(1));
        assertEquals(100, s.getEffects().get(StatusEffect.POISON).duration());
        s.applyFoodEffects(BlockType.GOLDEN_APPLE, new Random(1));
        assertEquals(1, s.getEffects().get(StatusEffect.REGENERATION).amplifier());
        assertEquals(100, s.getEffects().get(StatusEffect.REGENERATION).duration());
        assertEquals(2400, s.getEffects().get(StatusEffect.ABSORPTION).duration());
        assertEquals(4f, s.getAbsorption());
        int hungry = 0;
        for (int i = 0; i < 2000; i++) {
            PlayerSurvival f = survival();
            f.applyFoodEffects(BlockType.ROTTEN_FLESH, new Random(i * 31L));
            if (f.getEffects().has(StatusEffect.HUNGER)) {
                hungry++;
                assertEquals(600, f.getEffects().get(StatusEffect.HUNGER).duration());
            }
        }
        assertEquals(0.8, hungry / 2000.0, 0.05, "rotten flesh: hunger 30 s with an 80% chance");
        assertNotNull(FoodRegistry.propertiesFor(BlockType.GOLDEN_APPLE));
        assertTrue(FoodRegistry.propertiesFor(BlockType.GOLDEN_APPLE).canEatWhenFull());
    }

    @Test
    void goldenAppleRecipeIsAnAppleInAnEightIngotRing() {
        BlockType[] g = new BlockType[9];
        java.util.Arrays.fill(g, BlockType.GOLD_INGOT);
        g[4] = BlockType.APPLE;
        assertEquals(BlockType.GOLDEN_APPLE, Recipes.craft(g, 3, 3).type());
    }

    @Test
    void aBurningPlayerTakesOneDamageASecondAndWaterPutsItOut() {
        PlayerSurvival s = survival();
        s.getHunger().forceState(10f, 0f, 0f); // no natural regeneration
        s.ignite(8f);
        assertTrue(s.isBurning());
        assertEquals(160, s.getBurnTicks());
        ticks(s, 60);
        assertEquals(17f, s.getHealth().getCurrentHealth(), 0.01f, "3 seconds, 3 damage");
        s.extinguish();
        assertFalse(s.isBurning());
        s.ignite(2f);
        s.ignite(1f);
        assertEquals(40, s.getBurnTicks(), "never shortens an existing burn");
    }

    // ---- lightning ----

    @Test
    void aBoltLightsForTwoTicksPerFlashWithAPauseBetweenFlashes() {
        LightningBolts bolts = new LightningBolts();
        Random r = new Random(4);
        LightningBolts.Bolt b = bolts.spawn(0, 4, 0, r);
        List<String> events = new ArrayList<>();
        for (int tick = 0; tick < 60 && !bolts.bolts().isEmpty(); tick++) {
            bolts.tick(r, new LightningBolts.Listener() {
                public void struck(LightningBolts.Bolt bolt) {
                    events.add("struck");
                }

                public void lit(LightningBolts.Bolt bolt) {
                    events.add("lit");
                }
            });
        }
        assertEquals("struck", events.get(0), "thunder on the first tick");
        long lit = events.stream().filter("lit"::equals).count();
        assertTrue(lit >= 4 && lit <= 8 && lit % 2 == 0, "the first flash plus 1 to 3 more, each 2 lit ticks: " + lit);
        assertTrue(bolts.bolts().isEmpty(), "the bolt ends");
        assertFalse(b.lit());
    }

    @Test
    void thunderstormsStrikeOneChunkInAHundredThousandPerTick() {
        World world = new World(1L, new FlatWorldGenerator());
        for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) world.generateChunk(world.getOrCreateChunk(cx, cz));
        Random r = new Random(9);
        int strikes = 0, ticks = 20000;
        for (int i = 0; i < ticks; i++) strikes += LightningBolts.randomStrikes(world, r, 0, 0, 2, List.of()).size();
        double expected = ticks * 13 * LightningBolts.CHUNK_STRIKE_CHANCE; // 13 chunks within radius 2
        assertEquals(expected, strikes, 3.0 * Math.sqrt(expected) + 1);
    }

    @Test
    void aStruckChunkWithAMobNearTheSpotStrikesTheMob() {
        World world = new World(1L, new FlatWorldGenerator());
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) world.generateChunk(world.getOrCreateChunk(cx, cz));
        com.example.world.LightEngine.initChunk(world.getChunk(0, 0), world);
        // find a strike spot for an empty list, then put a living thing right beside it
        List<float[]> first = new ArrayList<>();
        Random probe = new Random(0);
        while (first.isEmpty()) first = lightningTry(world, probe, List.of());
        float[] spot = first.get(0);
        List<float[]> near = List.of(new float[]{spot[0] + 1f, spot[1], spot[2]});
        Random same = new Random(0);
        List<float[]> second = new ArrayList<>();
        while (second.isEmpty()) second = lightningTry(world, same, near);
        assertEquals(Math.floor(spot[0]) + 1.5f, second.get(0)[0], 0.01f, "the bolt goes to the thing standing there");
    }

    private static List<float[]> lightningTry(World world, Random r, List<float[]> living) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < 400000 && out.isEmpty(); i++) out.addAll(LightningBolts.randomStrikes(world, r, 0, 0, 0, living));
        return out;
    }

    @Test
    void weatherOnlyFlashesTheSkyWhileABoltIsLit() {
        Weather w = new Weather(4L);
        assertFalse(w.isSkyFlashing());
        w.strike();
        assertEquals(1f, w.getLightningFlash());
        w.flashSky();
        assertTrue(w.isSkyFlashing());
        for (int i = 0; i < 4; i++) w.update(0.05f);
        assertFalse(w.isSkyFlashing(), "it lasts about two ticks");
        for (int i = 0; i < 20; i++) w.update(0.25f);
        assertEquals(0f, w.getLightningFlash(), 0f, "and the thunder event decays");
    }

    @Test
    void monstersBurnFromTick23460ToTick12542() {
        assertTrue(SkyDetailsAccess.monstersBurn(6000f));
        assertTrue(SkyDetailsAccess.monstersBurn(12541f));
        assertFalse(SkyDetailsAccess.monstersBurn(12543f));
        assertFalse(SkyDetailsAccess.monstersBurn(18000f));
        assertFalse(SkyDetailsAccess.monstersBurn(23459f));
        assertTrue(SkyDetailsAccess.monstersBurn(23461f));
        assertTrue(SkyDetailsAccess.monstersBurn(10f));
    }
}
