package com.example.game;

import com.example.inventory.Inventory;
import com.example.inventory.Recipes;
import com.example.world.BlockDrops;
import com.example.world.BlockSupport;
import com.example.world.BlockType;
import com.example.world.FlatWorldGenerator;
import com.example.world.LightEngine;
import com.example.world.RandomTicks;
import com.example.world.Raycast;
import com.example.world.SaplingGrowth;
import com.example.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Torches (floor and wall, light 14, support rules, recipe), farmland (support, random ticks), and glass being solid. */
class TorchAndFarmlandTest {
    private static final int G = FlatWorldGenerator.GRASS_HEIGHT;
    private World world;
    private Inventory inventory;
    private final List<BlockType> dropped = new ArrayList<>();

    @BeforeEach
    void setUp() {
        world = new World(1L, new FlatWorldGenerator());
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) world.generateChunk(world.getOrCreateChunk(cx, cz));
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) LightEngine.initChunk(world.getChunk(cx, cz), world);
        inventory = new Inventory();
        inventory.add(BlockType.TORCH, 8);
        world.setDetachListener((x, y, z, t) -> dropped.add(t));
    }

    private BlockPlacer.Result place(int hx, int hy, int hz, int px, int py, int pz) {
        Raycast.Hit hit = new Raycast.Hit(hx, hy, hz, px, py, pz, world.getBlock(hx, hy, hz));
        return BlockPlacer.tryPlace(world, inventory, hit, (x, y, z) -> false, new SaplingGrowth(1L));
    }

    @Test
    void aTorchOnTheTopOfABlockIsAFloorTorchLightingItsSurroundingsAtFourteen() {
        assertTrue(place(5, G, 5, 5, G + 1, 5).placed());
        assertEquals(BlockType.TORCH, world.getBlock(5, G + 1, 5));
        assertEquals(7, inventory.count(BlockType.TORCH));
        assertEquals(14, world.getBlockLight(5, G + 1, 5));
        assertEquals(13, world.getBlockLight(6, G + 1, 5));
        assertEquals(0, world.getBlockLight(5 + 15, G + 1, 5));
    }

    @Test
    void aTorchOnTheSideOfABlockIsAWallTorchFacingAwayFromIt() {
        world.setBlock(5, G + 1, 5, BlockType.STONE);
        assertTrue(place(5, G + 1, 5, 6, G + 1, 5).placed());
        assertEquals(BlockType.WALL_TORCH_EAST, world.getBlock(6, G + 1, 5));
        assertTrue(place(5, G + 1, 5, 5, G + 1, 4).placed());
        assertEquals(BlockType.WALL_TORCH_NORTH, world.getBlock(5, G + 1, 4));
        assertTrue(place(5, G + 1, 5, 4, G + 1, 5).placed());
        assertEquals(BlockType.WALL_TORCH_WEST, world.getBlock(4, G + 1, 5));
        assertTrue(place(5, G + 1, 5, 5, G + 1, 6).placed());
        assertEquals(BlockType.WALL_TORCH_SOUTH, world.getBlock(5, G + 1, 6));
    }

    @Test
    void aTorchCannotHangFromTheUndersideOrStandOnLeavesWaterOrAnotherTorch() {
        world.setBlock(5, G + 3, 5, BlockType.STONE);
        assertFalse(place(5, G + 3, 5, 5, G + 2, 5).placed(), "not on the underside of a block");
        world.setBlock(8, G + 1, 8, BlockType.OAK_LEAVES);
        assertFalse(place(8, G + 1, 8, 8, G + 2, 8).placed(), "leaves are not sturdy");
        assertTrue(place(5, G, 5, 5, G + 1, 5).placed());
        assertFalse(place(5, G + 1, 5, 5, G + 2, 5).placed(), "a torch is not a support");
        assertEquals(7, inventory.count(BlockType.TORCH));
    }

    @Test
    void aTorchPopsOffAsAnItemWhenItsSupportIsBroken() {
        place(5, G, 5, 5, G + 1, 5);
        world.setBlock(5, G, 5, BlockType.AIR);
        assertEquals(BlockType.AIR, world.getBlock(5, G + 1, 5));
        assertEquals(List.of(BlockType.TORCH), dropped);
        assertEquals(0, world.getBlockLight(5, G + 1, 5), "and its light goes with it");
    }

    @Test
    void aWallTorchPopsOffWhenTheBlockBehindItGoes() {
        world.setBlock(5, G + 1, 5, BlockType.STONE);
        place(5, G + 1, 5, 6, G + 1, 5);
        world.setBlock(5, G + 1, 5, BlockType.AIR);
        assertEquals(BlockType.AIR, world.getBlock(6, G + 1, 5));
        assertEquals(List.of(BlockType.WALL_TORCH_EAST), dropped);
    }

    @Test
    void everyTorchDropsTheTorchItem() {
        for (BlockType t : new BlockType[]{BlockType.TORCH, BlockType.WALL_TORCH_EAST, BlockType.WALL_TORCH_WEST, BlockType.WALL_TORCH_NORTH, BlockType.WALL_TORCH_SOUTH}) {
            assertEquals(BlockType.TORCH, BlockDrops.resolve(t, new Random(1)).get(0).item(), t.name());
        }
        assertEquals(BlockType.DIRT, BlockDrops.resolve(BlockType.FARMLAND, new Random(1)).get(0).item());
    }

    @Test
    void fourTorchesFromCoalOrCharcoalOverAStick() {
        BlockType[] g = new BlockType[9];
        g[0] = BlockType.COAL;
        g[3] = BlockType.STICK;
        assertEquals(BlockType.TORCH, Recipes.craft(g, 3, 3).type());
        assertEquals(4, Recipes.craft(g, 3, 3).count());
        g[0] = BlockType.CHARCOAL;
        assertEquals(BlockType.TORCH, Recipes.craft(g, 3, 3).type());
    }

    @Test
    void farmlandTurnsToDirtWhenASolidBlockIsPlacedOnIt() {
        world.setBlock(5, G, 5, BlockType.FARMLAND);
        world.setBlock(5, G + 1, 5, BlockType.TORCH); // not solid: it stays
        assertEquals(BlockType.FARMLAND, world.getBlock(5, G, 5));
        world.setBlock(5, G + 1, 5, BlockType.STONE);
        assertEquals(BlockType.DIRT, world.getBlock(5, G, 5));
    }

    @Test
    void farmlandIsMoistNearWaterDriesWithoutItAndEventuallyBecomesDirt() {
        world.setBlock(5, G, 5, BlockType.FARMLAND);
        world.setBlock(9, G + 1, 5, BlockType.WATER); // 4 blocks away, one above
        RandomTicks.Rain dry = (x, y, z) -> false;
        Random r = new Random(2);
        RandomTicks.farmlandTick(world, r, 5, G, 5, BlockType.FARMLAND, dry);
        assertEquals(BlockType.FARMLAND_MOIST, world.getBlock(5, G, 5));
        world.setBlock(9, G + 1, 5, BlockType.AIR);
        RandomTicks.farmlandTick(world, r, 5, G, 5, BlockType.FARMLAND_MOIST, dry);
        assertEquals(BlockType.FARMLAND, world.getBlock(5, G, 5), "moist dries one step");
        int ticks = 0;
        while (world.getBlock(5, G, 5) == BlockType.FARMLAND && ticks++ < 1000) {
            RandomTicks.farmlandTick(world, r, 5, G, 5, BlockType.FARMLAND, dry);
        }
        assertEquals(BlockType.DIRT, world.getBlock(5, G, 5));
        // rain wets it too
        world.setBlock(5, G, 5, BlockType.FARMLAND);
        RandomTicks.farmlandTick(world, r, 5, G, 5, BlockType.FARMLAND, (x, y, z) -> true);
        assertEquals(BlockType.FARMLAND_MOIST, world.getBlock(5, G, 5));
    }

    @Test
    void randomTicksReachFarmlandWithinRange() {
        world.setBlock(5, G, 5, BlockType.FARMLAND_MOIST);
        Random r = new Random(3);
        for (int i = 0; i < 20000 && world.getBlock(5, G, 5) == BlockType.FARMLAND_MOIST; i++) {
            RandomTicks.tick(world, r, 0, 0, 1, (x, y, z) -> false);
        }
        assertNotEquals(BlockType.FARMLAND_MOIST, world.getBlock(5, G, 5), "a tick dried the dry-standing farmland");
    }

    @Test
    void glassIsSolidSoYouCannotWalkThroughItAndStillLetsLightPass() {
        assertTrue(BlockType.GLASS.isSolidCollision());
        assertTrue(BlockSupport.isSturdy(BlockType.GLASS), "a torch can sit on glass");
        assertEquals(0, com.example.world.LightProperties.dampening(BlockType.GLASS));
    }

    @Test
    void onlyWaterAndIceAreBlendedInTheWorldMesh() {
        assertTrue(com.example.world.ChunkMeshBuilder.isTranslucentForTest(BlockType.WATER));
        assertTrue(com.example.world.ChunkMeshBuilder.isTranslucentForTest(BlockType.ICE));
        assertFalse(com.example.world.ChunkMeshBuilder.isTranslucentForTest(BlockType.GLASS));
        assertFalse(com.example.world.ChunkMeshBuilder.isTranslucentForTest(BlockType.TORCH));
        assertFalse(com.example.world.ChunkMeshBuilder.isTranslucentForTest(BlockType.OAK_SAPLING_STAGE_0));
    }
}
