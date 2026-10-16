package com.example.game;

import com.example.inventory.Inventory;
import com.example.inventory.ItemStack;
import com.example.world.BlockType;
import com.example.world.Chunk;
import com.example.world.FlatWorldGenerator;
import com.example.world.Raycast;
import com.example.world.SaplingGrowth;
import com.example.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BlockPlacerTest {
    private World world;
    private Inventory inventory;
    private SaplingGrowth saplings;

    @BeforeEach
    void setUp() {
        world = new World(1L, new FlatWorldGenerator());
        world.generateChunk(world.getOrCreateChunk(0, 0));
        inventory = new Inventory();
        saplings = new SaplingGrowth(1L);
    }

    /** A hit on the grass block at (5,3,5), placing into the air cell right above it. */
    private Raycast.Hit topOfGround() {
        return new Raycast.Hit(5, FlatWorldGenerator.GRASS_HEIGHT, 5, 5, FlatWorldGenerator.GRASS_HEIGHT + 1, 5,
                world.getBlock(5, FlatWorldGenerator.GRASS_HEIGHT, 5));
    }

    private BlockPlacer.Result place(Raycast.Hit hit, BlockPlacer.PlayerOverlap overlap) {
        return BlockPlacer.tryPlace(world, inventory, hit, overlap, saplings);
    }

    @Test
    void successfulPlacementWritesTheBlockAndConsumesExactlyOneItem() {
        inventory.add(BlockType.DIRT, 5);
        BlockPlacer.Result r = place(topOfGround(), (x, y, z) -> false);
        assertTrue(r.placed());
        assertEquals(BlockType.DIRT, r.type());
        assertEquals(BlockType.DIRT, world.getBlock(5, FlatWorldGenerator.GRASS_HEIGHT + 1, 5));
        assertEquals(4, inventory.count(BlockType.DIRT));
    }

    @Test
    void placingTheLastItemEmptiesTheSlotCleanly() {
        inventory.add(BlockType.DIRT, 1);
        assertTrue(place(topOfGround(), (x, y, z) -> false).placed());
        assertEquals(ItemStack.EMPTY, inventory.get(0));
        assertTrue(inventory.selected().isEmpty(), "selection stays valid on the now-empty slot");
    }

    @Test
    void anEmptySelectedSlotCannotPlaceAndChangesNothing() {
        BlockPlacer.Result r = place(topOfGround(), (x, y, z) -> false);
        assertEquals(BlockPlacer.Outcome.NO_ITEM, r.outcome());
        assertEquals(BlockType.AIR, world.getBlock(5, FlatWorldGenerator.GRASS_HEIGHT + 1, 5));
    }

    @Test
    void placementBlockedByThePlayerFailsAndDoesNotConsumeAnything() {
        inventory.add(BlockType.DIRT, 5);
        BlockPlacer.Result r = place(topOfGround(), (x, y, z) -> true);
        assertEquals(BlockPlacer.Outcome.INVALID_TARGET, r.outcome());
        assertFalse(r.placed());
        assertEquals(5, inventory.count(BlockType.DIRT), "a failed placement must not consume an item");
        assertEquals(BlockType.AIR, world.getBlock(5, FlatWorldGenerator.GRASS_HEIGHT + 1, 5));
    }

    @Test
    void placingAboveTheWorldHeightFailsInsteadOfSilentlyEatingTheItem() {
        inventory.add(BlockType.DIRT, 5);
        Raycast.Hit tooHigh = new Raycast.Hit(5, Chunk.MAX_Y, 5, 5, Chunk.MAX_Y + 1, 5, BlockType.STONE);
        assertEquals(BlockPlacer.Outcome.INVALID_TARGET, place(tooHigh, (x, y, z) -> false).outcome());
        assertEquals(5, inventory.count(BlockType.DIRT));
    }

    @Test
    void aSaplingOnlyPlantsDirectlyOnTopOfValidGround() {
        inventory.add(BlockType.OAK_SAPLING_STAGE_0, 3);
        int g = FlatWorldGenerator.GRASS_HEIGHT;

        // Against a side face: hit at (5,g,5) but placing beside it - not directly above ground.
        Raycast.Hit side = new Raycast.Hit(5, g, 5, 6, g, 5, BlockType.GRASS_BLOCK);
        assertEquals(BlockPlacer.Outcome.INVALID_TARGET, place(side, (x, y, z) -> false).outcome());
        assertEquals(3, inventory.count(BlockType.OAK_SAPLING_STAGE_0));

        // On top of grass: valid, consumes one and registers growth tracking.
        assertTrue(place(topOfGround(), (x, y, z) -> false).placed());
        assertEquals(2, inventory.count(BlockType.OAK_SAPLING_STAGE_0));
        assertEquals(BlockType.OAK_SAPLING_STAGE_0, world.getBlock(5, g + 1, 5));
        assertEquals(1, saplings.activeCount());
    }

    @Test
    void aSaplingCannotBePlantedOnInvalidGround() {
        inventory.add(BlockType.OAK_SAPLING_STAGE_0, 1);
        int g = FlatWorldGenerator.GRASS_HEIGHT;
        world.setBlock(5, g, 5, BlockType.STONE);
        Raycast.Hit onStone = new Raycast.Hit(5, g, 5, 5, g + 1, 5, BlockType.STONE);
        assertEquals(BlockPlacer.Outcome.INVALID_TARGET, place(onStone, (x, y, z) -> false).outcome());
        assertEquals(1, inventory.count(BlockType.OAK_SAPLING_STAGE_0));
    }

    @Test
    void anItemThatIsNotPlaceableIsRejectedWithoutConsumption() {
        // Bypass Inventory.add's own checks by writing the stack directly - simulates a bad save.
        inventory.set(0, new ItemStack(BlockType.BEDROCK, 2));
        BlockPlacer.Result r = place(topOfGround(), (x, y, z) -> false);
        assertEquals(BlockPlacer.Outcome.NOT_PLACEABLE, r.outcome());
        assertEquals(2, inventory.get(0).count());
    }

    @Test
    void placementConsumesFromTheSelectedSlotOnly() {
        inventory.add(BlockType.DIRT, 4);
        inventory.add(BlockType.STONE, 4);
        inventory.selectSlot(1);
        assertTrue(place(topOfGround(), (x, y, z) -> false).placed());
        assertEquals(BlockType.STONE, world.getBlock(5, FlatWorldGenerator.GRASS_HEIGHT + 1, 5));
        assertEquals(4, inventory.count(BlockType.DIRT));
        assertEquals(3, inventory.count(BlockType.STONE));
    }
}
