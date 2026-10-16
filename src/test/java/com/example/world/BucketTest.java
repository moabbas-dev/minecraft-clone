package com.example.world;

import com.example.inventory.ItemStack;
import com.example.inventory.Recipes;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The bucket: its recipe, stack sizes, and the ray an empty bucket aims with. */
class BucketTest {
    /** The flat world starts at the bottom of the world: {@code BOTTOM + n} is the n-th layer above its bedrock (grass at 3, air from 4). */
    private static final int BOTTOM = com.example.world.Chunk.MIN_Y;

    private static World flat() {
        World world = new World(1L, new FlatWorldGenerator());
        world.generateChunk(world.getOrCreateChunk(0, 0));
        return world;
    }

    @Test
    void threeIronIngotsInAVMakeABucket() {
        BlockType[] grid = new BlockType[9];
        grid[0] = BlockType.IRON_INGOT;
        grid[2] = BlockType.IRON_INGOT;
        grid[4] = BlockType.IRON_INGOT;
        ItemStack made = Recipes.craft(grid, 3, 3);
        assertEquals(BlockType.BUCKET, made.type());
        assertEquals(1, made.count());
        BlockType[] wrong = new BlockType[9];
        wrong[0] = BlockType.IRON_INGOT;
        wrong[1] = BlockType.IRON_INGOT;
        wrong[2] = BlockType.IRON_INGOT;
        assertNotEquals(BlockType.BUCKET, Recipes.craft(wrong, 3, 3).type());
    }

    @Test
    void emptyBucketsStackToSixteenAndFullOnesDoNotStack() {
        assertEquals(16, ItemStack.maxStackFor(BlockType.BUCKET, 64));
        assertEquals(1, ItemStack.maxStackFor(BlockType.WATER_BUCKET, 64));
        assertEquals(1, ItemStack.maxStackFor(BlockType.LAVA_BUCKET, 64));
    }

    @Test
    void theNormalRaySeesThroughFluidsButTheBucketRayStopsAtASource() {
        World w = flat();
        w.setBlock(5, BOTTOM + 4, 5, BlockType.WATER);
        w.setBlock(5, BOTTOM + 5, 5, BlockType.WATER_FLOW_3);
        Vector3f from = new Vector3f(5.5f, BOTTOM + 8.5f, 5.5f), down = new Vector3f(0f, -1f, 0f);
        Raycast.Hit normal = Raycast.cast(w, from, down, 10f);
        assertEquals(BOTTOM + 3, normal.y(), "the ground below the water");
        Raycast.Hit bucket = Raycast.cast(w, from, down, 10f, true);
        assertEquals(BOTTOM + 4, bucket.y(), "the source, past the flowing water above it which does not stop the ray");
        assertSame(BlockType.WATER, bucket.type());
        w.setBlock(5, BOTTOM + 4, 5, BlockType.WATER_FLOW_2);
        assertEquals(BOTTOM + 3, Raycast.cast(w, from, down, 10f, true).y(), "a flowing block cannot be scooped");
    }

    @Test
    void pouringAndScoopingMovesWaterAroundLikeTheBucketDoes() {
        World w = flat();
        w.setBlock(8, BOTTOM + 4, 8, BlockType.WATER);
        w.fluids().settle(w, 400);
        assertTrue(w.getBlock(10, BOTTOM + 4, 8).isWater());
        w.setBlock(8, BOTTOM + 4, 8, BlockType.AIR); // scooped
        w.fluids().settle(w, 600);
        assertTrue(w.getBlock(10, BOTTOM + 4, 8).isAir(), "the flow dries up once its source is taken");
    }
}
