package com.example.world.unit;

import com.example.world.BlockType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BlockTypeTest {

    @Test
    void airIsNeverOpaqueSolidOrTransparent() {
        assertFalse(BlockType.AIR.isOpaqueCube());
        assertFalse(BlockType.AIR.isSolidCollision());
        assertTrue(BlockType.AIR.isAir());
    }

    @Test
    void everyBlockIsExactlyOpaqueXorTransparentRenderExceptAir() {
        // isOpaqueCube() is derived as "not AIR and not transparentRender" - this test locks
        // that invariant for every current and future BlockType, not a hardcoded subset.
        for (BlockType b : BlockType.values()) {
            if (b == BlockType.AIR) continue;
            assertNotEquals(b.isOpaqueCube(), b.isTransparentRender(),
                    b + " must be exactly one of opaque or transparent-render, never both or neither");
        }
    }

    @Test
    void byIdRoundTripsForEveryDefinedBlock() {
        for (BlockType b : BlockType.values()) {
            assertEquals(b, BlockType.byId(b.id), "byId(" + b.id + ") should return " + b);
        }
    }

    @Test
    void byIdFallsBackToAirForUnknownOrOutOfRangeIds() {
        assertEquals(BlockType.AIR, BlockType.byId(-1));
        assertEquals(BlockType.AIR, BlockType.byId(255));
        assertEquals(BlockType.AIR, BlockType.byId(999));
        // id 6, 8, etc. are intentionally unused gaps left over from the 1.8 numeric id palette.
        assertEquals(BlockType.AIR, BlockType.byId(6));
    }

    @Test
    void unbreakableBlocksReportNegativeBreakTime() {
        assertEquals(-1f, BlockType.BEDROCK.handBreakSeconds());
        assertEquals(-1f, BlockType.WATER.handBreakSeconds());
    }

    @Test
    void breakableBlocksReportNonNegativeBreakTime() {
        for (BlockType b : BlockType.values()) {
            if (b.hardness < 0) continue;
            assertTrue(b.handBreakSeconds() >= 0f, b + " should have a non-negative break time");
        }
    }

    @Test
    void placeableBlocksAreAllRealNonAirBlocks() {
        for (BlockType b : BlockType.PLACEABLE) {
            assertNotEquals(BlockType.AIR, b);
        }
    }

    @Test
    void toolsAndStickAreNeverPlaceableAndReportUnbreakable() {
        List<BlockType> placeable = Arrays.asList(BlockType.PLACEABLE);
        for (BlockType tool : new BlockType[]{BlockType.STICK, BlockType.WOODEN_PICKAXE, BlockType.STONE_PICKAXE,
                BlockType.IRON_PICKAXE, BlockType.WOODEN_AXE, BlockType.STONE_AXE, BlockType.IRON_AXE,
                BlockType.WOODEN_SHOVEL, BlockType.STONE_SHOVEL, BlockType.IRON_SHOVEL}) {
            assertFalse(placeable.contains(tool), tool + " is a tool/ingredient, never a placeable block");
            assertEquals(-1f, tool.handBreakSeconds(), tool + " is never a world block, so it's never breakable");
        }
    }
}
