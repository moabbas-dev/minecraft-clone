package com.example.entity;

import com.example.entity.mob.Chicken;
import com.example.entity.mob.Cow;
import com.example.entity.mob.MobTypes;
import com.example.entity.mob.Pig;
import com.example.world.BlockDrops;
import com.example.world.BlockType;
import com.example.world.WorldIO;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class PassiveMobTest {
    private static int total(List<BlockDrops.Resolved> drops, BlockType t) {
        int n = 0;
        for (BlockDrops.Resolved d : drops) if (d.item() == t) n += d.count();
        return n;
    }

    @Test
    void statsMatchTheResearch() {
        Vector3f p = new Vector3f();
        assertEquals(10f, new Pig(p, 0).getHealth().getMaxHealth());
        assertEquals(10f, new Cow(p, 0).getHealth().getMaxHealth());
        assertEquals(4f, new Chicken(p, 0).getHealth().getMaxHealth());
        assertEquals(1.4f, new Cow(p, 0).getHeight());
        assertEquals(0.4f, new Chicken(p, 0).getWidth());
        assertTrue(new Pig(p, 0).isPersistent());
    }

    @Test
    void dropsStayInTheirVanillaRanges() {
        Random r = new Random(7);
        for (int i = 0; i < 500; i++) {
            int pork = total(new Pig(new Vector3f(), 0).getDrops(r), BlockType.RAW_PORKCHOP);
            assertTrue(pork >= 1 && pork <= 3);
            List<BlockDrops.Resolved> cow = new Cow(new Vector3f(), 0).getDrops(r);
            assertTrue(total(cow, BlockType.RAW_BEEF) >= 1 && total(cow, BlockType.RAW_BEEF) <= 3);
            assertTrue(total(cow, BlockType.LEATHER) <= 2);
            List<BlockDrops.Resolved> hen = new Chicken(new Vector3f(), 0).getDrops(r);
            assertEquals(1, total(hen, BlockType.RAW_CHICKEN));
            assertTrue(total(hen, BlockType.FEATHER) <= 2);
        }
    }

    @Test
    void chickensLayEggsOnTheirTimerAndKeepItAcrossSaves() {
        Chicken c = new Chicken(new Vector3f(), 0);
        c.restoreExtraState(0.05f);
        assertNull(c.takeLaidItem());
        com.example.world.World world = new com.example.world.World(1L, new com.example.world.FlatWorldGenerator());
        world.generateChunk(world.getOrCreateChunk(0, 0));
        c.tick(world, List.of(), 0.1f);
        assertEquals(BlockType.EGG, c.takeLaidItem());
        assertNull(c.takeLaidItem());
        assertTrue(c.extraState() >= 300f && c.extraState() <= 600f, "a fresh 5-10 minute timer starts");
    }

    @Test
    void mobTypesRoundTripThroughTheirIds() {
        for (String id : new String[]{"pig", "cow", "chicken", "zombie"}) {
            assertEquals(id, MobTypes.create(id, new Vector3f(), 0f, () -> null).typeId());
        }
        assertNull(MobTypes.create("wither", new Vector3f(), 0f, () -> null));
    }

    @Test
    void mobsAndArmorPersist(@TempDir File dir) {
        WorldIO.saveMobs(dir, List.of(new WorldIO.MobSnapshot("cow", 1f, 2f, 3f, 0.5f, 7f, 0f, 0.1f, 0f, -0.2f)));
        List<WorldIO.MobSnapshot> back = WorldIO.loadMobs(dir);
        assertEquals(1, back.size());
        assertEquals("cow", back.get(0).type());
        assertEquals(7f, back.get(0).health());
        assertEquals(-0.2f, back.get(0).vz());
        assertTrue(WorldIO.loadMobs(new File(dir, "missing")).isEmpty());

        com.example.inventory.PlayerArmor a = new com.example.inventory.PlayerArmor();
        a.equip(new com.example.inventory.ItemStack(BlockType.IRON_HELMET, 1, 12));
        WorldIO.saveArmor(dir, a);
        com.example.inventory.PlayerArmor b = new com.example.inventory.PlayerArmor();
        WorldIO.loadArmor(dir, b);
        assertEquals(BlockType.IRON_HELMET, b.get(com.example.combat.EquipmentSlot.HEAD).type());
    }

    @Test
    void speciesWeightsFavourPigsAndChickensOverCows() {
        int[] counts = new int[3];
        Random r = new Random(3);
        for (int i = 0; i < 14000; i++) counts[PassiveSpawner.pickSpecies(r)]++;
        assertTrue(counts[1] < counts[0] && counts[1] < counts[2]);
        assertEquals(5000, counts[0], 400);
    }
}
