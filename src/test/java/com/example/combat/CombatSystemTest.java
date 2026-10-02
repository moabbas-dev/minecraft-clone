package com.example.combat;

import com.example.inventory.Inventory;
import com.example.inventory.InventoryMenu;
import com.example.inventory.ItemStack;
import com.example.inventory.PlayerArmor;
import com.example.inventory.Recipes;
import com.example.survival.DamageSource;
import com.example.survival.HealthComponent;
import com.example.survival.PlayerSurvival;
import com.example.world.BlockType;
import com.example.world.Difficulty;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class CombatSystemTest {
    // ---- weapon stats (1.9+ table) ----

    @Test
    void damageAndSpeedMatchTheJava19Table() {
        assertEquals(4f, WeaponStats.damage(BlockType.WOODEN_SWORD));
        assertEquals(5f, WeaponStats.damage(BlockType.STONE_SWORD));
        assertEquals(6f, WeaponStats.damage(BlockType.IRON_SWORD));
        assertEquals(7f, WeaponStats.damage(BlockType.WOODEN_AXE));
        assertEquals(9f, WeaponStats.damage(BlockType.IRON_AXE));
        assertEquals(2.5f, WeaponStats.damage(BlockType.WOODEN_SHOVEL));
        assertEquals(4f, WeaponStats.damage(BlockType.IRON_PICKAXE));
        assertEquals(1f, WeaponStats.damage(null), "bare hand");
        assertEquals(1f, WeaponStats.damage(BlockType.DIRT), "a block is just a hand");
        assertEquals(1.6f, WeaponStats.attackSpeed(BlockType.IRON_SWORD));
        assertEquals(0.8f, WeaponStats.attackSpeed(BlockType.STONE_AXE));
        assertEquals(0.9f, WeaponStats.attackSpeed(BlockType.IRON_AXE));
        assertEquals(12.5f, WeaponStats.periodTicks(BlockType.IRON_SWORD), 1e-4f);
        assertEquals(5f, WeaponStats.periodTicks(null), 1e-4f);
    }

    @Test
    void chargeFollowsTheVanillaFormula() {
        float period = WeaponStats.periodTicks(BlockType.IRON_SWORD); // 12.5
        assertEquals(0.5f / 12.5f, CombatMath.charge(0, period), 1e-5f);
        assertEquals(1f, CombatMath.charge(12, period), 1e-5f, "t + 0.5 >= T at tick 12");
        assertEquals(1f, CombatMath.charge(100, period));
        assertEquals(0.2f, CombatMath.damageMultiplier(0f), 1e-6f);
        assertEquals(1f, CombatMath.damageMultiplier(1f), 1e-6f);
        // q = 0.9 -> 0.2 + 0.8 * 0.81 = 0.848 (the special-attack threshold)
        assertEquals(0.848f, CombatMath.damageMultiplier(0.9f), 1e-5f);
    }

    @Test
    void aFullyChargedIronSwordDoesSixAndAMashedOneDoesAlmostNothing() {
        assertEquals(6f, CombatMath.attackDamage(BlockType.IRON_SWORD, 1f, false), 1e-5f);
        assertEquals(6f * 0.2f * (1f + 0.8f * 0f) , CombatMath.attackDamage(BlockType.IRON_SWORD, 0f, false), 1e-5f);
        assertEquals(9f, CombatMath.attackDamage(BlockType.IRON_SWORD, 1f, true), 1e-5f, "crit is 1.5x");
    }

    @Test
    void criticalNeedsAFallInTheAirAtHighChargeAndNoSprintHit() {
        assertTrue(CombatMath.isCritical(1f, false, -3f, false, false, false));
        assertFalse(CombatMath.isCritical(1f, true, -3f, false, false, false), "grounded");
        assertFalse(CombatMath.isCritical(1f, false, 2f, false, false, false), "rising");
        assertFalse(CombatMath.isCritical(0.5f, false, -3f, false, false, false), "weak swing");
        assertFalse(CombatMath.isCritical(1f, false, -3f, true, false, false), "in water");
        assertFalse(CombatMath.isCritical(1f, false, -3f, false, true, false), "flying");
        assertFalse(CombatMath.isCritical(1f, false, -3f, false, false, true), "sprint hit excludes crit");
    }

    @Test
    void sweepNeedsASwordOnTheGroundAtHighChargeWithNoCritOrSprint() {
        assertTrue(CombatMath.isSweep(BlockType.IRON_SWORD, 1f, true, false, false));
        assertFalse(CombatMath.isSweep(BlockType.IRON_AXE, 1f, true, false, false));
        assertFalse(CombatMath.isSweep(BlockType.IRON_SWORD, 0.5f, true, false, false));
        assertFalse(CombatMath.isSweep(BlockType.IRON_SWORD, 1f, false, false, false));
        assertFalse(CombatMath.isSweep(BlockType.IRON_SWORD, 1f, true, true, false));
        assertFalse(CombatMath.isSweep(BlockType.IRON_SWORD, 1f, true, false, true));
    }

    // ---- knockback ----

    @Test
    void knockbackHalvesVelocityAndPushesAwayFromTheAttacker() {
        // victim at rest, attacker toward +x -> victim pushed toward -x with strength 0.4, popped up 0.4 if grounded
        float[] v = CombatMath.knockback(0f, 0f, 0f, true, 5f, 0f, 0.4f);
        assertEquals(-0.4f, v[0], 1e-6f);
        assertEquals(0.4f, v[1], 1e-6f);
        assertEquals(0f, v[2], 1e-6f);
        float[] air = CombatMath.knockback(0f, -0.3f, 0f, false, 0f, 3f, 0.4f);
        assertEquals(-0.3f, air[1], 1e-6f, "airborne victims get no extra vertical pop");
        assertEquals(-0.4f, air[2], 1e-6f);
        float[] moving = CombatMath.knockback(0.2f, 0f, 0f, true, 1f, 0f, 0.4f);
        assertEquals(0.1f - 0.4f, moving[0], 1e-6f, "existing velocity is halved first");
    }

    @Test
    void sprintingAddsHalfAKnockbackLevel() {
        assertEquals(0.4f, CombatMath.knockbackStrength(false));
        assertEquals(0.9f, CombatMath.knockbackStrength(true), 1e-6f);
    }

    // ---- armor ----

    @Test
    void fullIronArmorTakes1Point38OfAThreePointHit() {
        // 15 points: max(3, 15 - 3/2) = 13.5 -> 54% off -> 1.38
        assertEquals(1.38f, ArmorRules.reduce(3f, 15f, 0f), 1e-4f);
        assertEquals(6f, ArmorRules.reduce(10f, 15f, 0f), 1e-4f);
        assertEquals(3f, ArmorRules.reduce(10f, 20f, 8f), 1e-4f, "full diamond");
        assertEquals(3.936f, ArmorRules.reduce(4f, 2f, 0f), 1e-4f, "zombie natural armor vs a wooden sword");
        assertEquals(5f, ArmorRules.reduce(5f, 0f, 0f), "no armor, no change");
    }

    @Test
    void armorNeverRemovesMoreThanEightyPercent() {
        for (float d : new float[]{0.1f, 1f, 5f, 50f}) {
            assertTrue(ArmorRules.reduce(d, 20f, 0f) >= d * 0.2f - 1e-4f, "damage " + d);
        }
        assertEquals(0.22f, ArmorRules.reduce(1f, 20f, 0f), 1e-4f, "20 points vs a 1 HP scratch: 19.5/25 = 78% off");
    }

    @Test
    void armorPieceTableMatchesTheResearch() {
        assertEquals(6, ArmorStats.of(BlockType.IRON_CHESTPLATE).defense());
        assertEquals(EquipmentSlot.FEET, ArmorStats.of(BlockType.GOLD_BOOTS).slot());
        assertEquals(3, ArmorStats.of(BlockType.LEATHER_CHESTPLATE).defense());
        assertNull(ArmorStats.of(BlockType.IRON_INGOT));
        PlayerArmor a = new PlayerArmor();
        for (BlockType t : new BlockType[]{BlockType.IRON_HELMET, BlockType.IRON_CHESTPLATE, BlockType.IRON_LEGGINGS, BlockType.IRON_BOOTS})
            a.equip(new ItemStack(t, 1));
        assertEquals(15, a.totalDefense());
    }

    @Test
    void durabilityConstantsFollowPieceTimesMaterial() {
        assertEquals(240, Durability.max(BlockType.IRON_CHESTPLATE));
        assertEquals(165, Durability.max(BlockType.IRON_HELMET));
        assertEquals(55, Durability.max(BlockType.LEATHER_HELMET));
        assertEquals(112, Durability.max(BlockType.GOLD_CHESTPLATE));
        assertEquals(250, Durability.max(BlockType.IRON_SWORD));
        assertEquals(59, Durability.max(BlockType.WOODEN_AXE));
        assertEquals(0, Durability.max(BlockType.APPLE));
    }

    @Test
    void actionCostsMatchTheResearch() {
        assertEquals(1, Durability.attackCost(BlockType.IRON_SWORD));
        assertEquals(2, Durability.attackCost(BlockType.IRON_AXE));
        assertEquals(0, Durability.attackCost(null));
        assertEquals(2, Durability.blockBreakCost(BlockType.IRON_SWORD, 1.5f));
        assertEquals(1, Durability.blockBreakCost(BlockType.IRON_PICKAXE, 1.5f));
        assertEquals(0, Durability.blockBreakCost(BlockType.IRON_PICKAXE, 0f), "instant blocks cost nothing");
        assertEquals(1, Durability.armorWear(3f));
        assertEquals(1, Durability.armorWear(7f));
        assertEquals(2, Durability.armorWear(8f));
    }

    @Test
    void armorReducesPlayerDamageWearsEveryPieceAndBypassesFall() {
        PlayerSurvival s = new PlayerSurvival(Difficulty.NORMAL);
        s.getArmor().equip(new ItemStack(BlockType.IRON_HELMET, 1));
        s.getArmor().equip(new ItemStack(BlockType.IRON_CHESTPLATE, 1));
        s.getArmor().equip(new ItemStack(BlockType.IRON_LEGGINGS, 1));
        s.getArmor().equip(new ItemStack(BlockType.IRON_BOOTS, 1));
        assertTrue(s.damage(3f, DamageSource.MOB));
        assertEquals(20f - 1.38f, s.getHealth().getCurrentHealth(), 1e-3f);
        assertEquals(165 - 1, s.getArmor().get(EquipmentSlot.HEAD).durabilityLeft());
        s.getHealth().tick(1f);
        float before = s.getHealth().getCurrentHealth();
        assertTrue(s.damage(5f, DamageSource.FALL));
        assertEquals(before - 5f, s.getHealth().getCurrentHealth(), 1e-3f, "fall damage ignores armor");
        assertEquals(165 - 1, s.getArmor().get(EquipmentSlot.HEAD).durabilityLeft(), "and does not wear it");
    }

    @Test
    void aPieceBreaksWhenItsDurabilityRunsOut() {
        PlayerArmor a = new PlayerArmor();
        a.equip(new ItemStack(BlockType.GOLD_BOOTS, 1, Durability.max(BlockType.GOLD_BOOTS) - 1));
        a.wear(4f);
        assertTrue(a.get(EquipmentSlot.FEET).isEmpty());
    }

    @Test
    void armorPersistsAndOnlyLoadsIntoItsOwnSlot() throws Exception {
        PlayerArmor a = new PlayerArmor();
        a.equip(new ItemStack(BlockType.IRON_CHESTPLATE, 1, 40));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        a.write(new DataOutputStream(bytes));
        PlayerArmor b = new PlayerArmor();
        b.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(240 - 40, b.get(EquipmentSlot.CHEST).durabilityLeft());
        assertTrue(b.get(EquipmentSlot.HEAD).isEmpty());
    }

    // ---- hurt window (vanilla: only the excess lands) ----

    @Test
    void aBiggerHitDuringTheHurtWindowAppliesOnlyTheExcess() {
        HealthComponent h = new HealthComponent();
        assertTrue(h.damage(7f, DamageSource.MOB));
        assertFalse(h.damage(5f, DamageSource.MOB), "weaker hit swallowed");
        assertFalse(h.damage(7f, DamageSource.MOB), "equal hit swallowed");
        assertTrue(h.damage(12f, DamageSource.MOB));
        assertEquals(20f - 12f, h.getCurrentHealth(), 1e-5f, "7 + excess 5 = 12 total");
        h.tick(1f);
        assertTrue(h.damage(1f, DamageSource.MOB), "a fresh window opens after 0.5 s");
    }

    // ---- durable items in the inventory ----

    @Test
    void damageableItemsNeverStackAndKeepTheirWear() {
        Inventory inv = new Inventory();
        assertEquals(2, inv.add(BlockType.IRON_SWORD, 2));
        assertEquals(1, inv.get(0).count());
        assertEquals(1, inv.get(1).count());
        inv.set(2, new ItemStack(BlockType.IRON_PICKAXE, 1, 30));
        assertEquals(30, inv.get(2).damage());
        assertEquals(1, inv.add(BlockType.IRON_PICKAXE, 1, 0));
        assertEquals(30, inv.get(2).damage(), "a fresh pickaxe never merges into a worn one");
    }

    @Test
    void toolWearSurvivesTheInventoryRoundTrip() throws Exception {
        Inventory inv = new Inventory();
        inv.set(0, new ItemStack(BlockType.IRON_SWORD, 1, 120));
        inv.set(1, new ItemStack(BlockType.COAL, 33));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inv.write(new DataOutputStream(bytes));
        Inventory back = Inventory.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
        assertEquals(120, back.get(0).damage());
        assertEquals(33, back.get(1).count());
        assertEquals(0, back.get(1).damage());
    }

    @Test
    void anItemBreaksAtZeroDurability() {
        ItemStack s = new ItemStack(BlockType.WOODEN_SWORD, 1, 58);
        assertFalse(s.damaged(0).isEmpty());
        assertTrue(s.damaged(1).isEmpty());
        assertEquals(1, s.durabilityLeft());
    }

    @Test
    void armorSlotsInTheInventoryMenuAcceptOnlyMatchingPieces() {
        Inventory inv = new Inventory();
        PlayerArmor armor = new PlayerArmor();
        InventoryMenu menu = new InventoryMenu(inv, armor, 2);
        inv.set(0, new ItemStack(BlockType.IRON_HELMET, 1));
        inv.set(1, new ItemStack(BlockType.IRON_BOOTS, 1));
        menu.click(0, false); // pick up the helmet
        menu.click(InventoryMenu.ARMOR_BASE + EquipmentSlot.CHEST.ordinal(), false);
        assertTrue(armor.get(EquipmentSlot.CHEST).isEmpty(), "a helmet does not fit the chest slot");
        menu.click(InventoryMenu.ARMOR_BASE + EquipmentSlot.HEAD.ordinal(), false);
        assertEquals(BlockType.IRON_HELMET, armor.get(EquipmentSlot.HEAD).type());
        assertTrue(menu.getCursor().isEmpty());
        menu.click(InventoryMenu.ARMOR_BASE + EquipmentSlot.HEAD.ordinal(), false); // take it off again
        assertEquals(BlockType.IRON_HELMET, menu.getCursor().type());
        assertTrue(armor.get(EquipmentSlot.HEAD).isEmpty());
    }

    // ---- recipes ----

    @Test
    void swordAndArmorRecipesMatchTheVanillaShapes() {
        BlockType P = BlockType.OAK_PLANKS, S = BlockType.STICK, I = BlockType.IRON_INGOT, L = BlockType.LEATHER;
        assertEquals(BlockType.WOODEN_SWORD, Recipes.craft(grid(3, 3, 1, P, 4, P, 7, S), 3, 3).type());
        assertEquals(BlockType.IRON_SWORD, Recipes.craft(grid(3, 3, 0, I, 3, I, 6, S), 3, 3).type());
        assertEquals(BlockType.IRON_HELMET, Recipes.craft(grid(3, 3, 0, I, 1, I, 2, I, 3, I, 5, I), 3, 3).type());
        assertEquals(BlockType.IRON_BOOTS, Recipes.craft(grid(3, 3, 3, I, 5, I, 6, I, 8, I), 3, 3).type());
        assertEquals(BlockType.LEATHER_CHESTPLATE,
                Recipes.craft(grid(3, 3, 0, L, 2, L, 3, L, 4, L, 5, L, 6, L, 7, L, 8, L), 3, 3).type());
        assertEquals(BlockType.GOLD_LEGGINGS,
                Recipes.craft(grid(3, 3, 0, BlockType.GOLD_INGOT, 1, BlockType.GOLD_INGOT, 2, BlockType.GOLD_INGOT,
                        3, BlockType.GOLD_INGOT, 5, BlockType.GOLD_INGOT, 6, BlockType.GOLD_INGOT, 8, BlockType.GOLD_INGOT), 3, 3).type());
        assertTrue(Recipes.craft(grid(3, 3, 0, I, 1, I, 2, I, 3, I, 4, I, 5, I), 3, 3).isEmpty(), "a full block of ingots is not a helmet");
    }

    // ---- diamond tier (docs/research/diamond-tier.md) ----

    @Test
    void diamondToolsAndArmorMatchTheResearch() {
        assertEquals(7f, WeaponStats.damage(BlockType.DIAMOND_SWORD));
        assertEquals(9f, WeaponStats.damage(BlockType.DIAMOND_AXE));
        assertEquals(1.0f, WeaponStats.attackSpeed(BlockType.DIAMOND_AXE));
        assertEquals(5f, WeaponStats.damage(BlockType.DIAMOND_PICKAXE));
        assertEquals(5.5f, WeaponStats.damage(BlockType.DIAMOND_SHOVEL));
        assertTrue(WeaponStats.isSword(BlockType.DIAMOND_SWORD));
        assertEquals(1561, Durability.max(BlockType.DIAMOND_PICKAXE));
        assertEquals(363, Durability.max(BlockType.DIAMOND_HELMET));
        assertEquals(528, Durability.max(BlockType.DIAMOND_CHESTPLATE));
        assertEquals(495, Durability.max(BlockType.DIAMOND_LEGGINGS));
        assertEquals(429, Durability.max(BlockType.DIAMOND_BOOTS));
        PlayerArmor a = new PlayerArmor();
        for (BlockType t : new BlockType[]{BlockType.DIAMOND_HELMET, BlockType.DIAMOND_CHESTPLATE, BlockType.DIAMOND_LEGGINGS, BlockType.DIAMOND_BOOTS})
            a.equip(new ItemStack(t, 1));
        assertEquals(20, a.totalDefense());
        assertEquals(8f, a.totalToughness());
        assertEquals(3f, ArmorRules.reduce(10f, a.totalDefense(), a.totalToughness()), 1e-4f);
    }

    @Test
    void diamondRecipesCraftAndTheToolMinesFasterThanIron() {
        BlockType D = BlockType.DIAMOND, S = BlockType.STICK;
        assertEquals(BlockType.DIAMOND_SWORD, Recipes.craft(grid(3, 3, 0, D, 3, D, 6, S), 3, 3).type());
        assertEquals(BlockType.DIAMOND_PICKAXE, Recipes.craft(grid(3, 3, 0, D, 1, D, 2, D, 4, S, 7, S), 3, 3).type());
        assertEquals(BlockType.DIAMOND_BOOTS, Recipes.craft(grid(3, 3, 3, D, 5, D, 6, D, 8, D), 3, 3).type());
        assertTrue(com.example.world.ToolEffectiveness.tierOf(BlockType.DIAMOND_PICKAXE).speedMultiplier
                > com.example.world.ToolEffectiveness.tierOf(BlockType.IRON_PICKAXE).speedMultiplier);
    }

    private static BlockType[] grid(int w, int h, Object... cells) {
        BlockType[] g = new BlockType[w * h];
        for (int i = 0; i < cells.length; i += 2) g[(Integer) cells[i]] = (BlockType) cells[i + 1];
        return g;
    }
}
