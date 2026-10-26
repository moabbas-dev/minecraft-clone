package com.example.worldgen.feature;

import com.example.worldgen.feature.level.BlockSurvival;
import com.example.worldgen.feature.stateprovider.BlockStateProviders;
import com.example.worldgen.level.StateRef;
import com.example.worldgen.terrain.BlockProperties;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consistency of the jar-read state tables: every state definition's property names match the default state registered in
 * {@link BlockProperties}, every default value is one of the property's values, every block with a ported survival rule is
 * registered and defined, and the state-holder operations reject what vanilla's {@code StateHolder} rejects.
 */
class BlockStateDefinitionsTest {

    @Test
    void definitionsMatchRegisteredDefaults() {
        int n = 0;
        for (Map.Entry<String, List<BlockStateDefinitions.Property>> e : BlockStateDefinitions.all().entrySet()) {
            StateRef def = BlockProperties.defaultState(e.getKey());
            TreeSet<String> names = new TreeSet<>();
            for (BlockStateDefinitions.Property p : e.getValue()) {
                names.add(p.name());
                assertTrue(p.values().contains(def.properties().get(p.name())), e.getKey() + " default " + p.name());
            }
            assertEquals(new TreeSet<>(def.properties().keySet()), names, e.getKey());
            n++;
        }
        assertEquals(89, n, "definitions");
    }

    @Test
    void survivalBlocksAreRegisteredAndDefined() {
        for (String id : BlockStateDefinitions.all().keySet()) {
            if (id.equals("minecraft:kelp") || id.equals("minecraft:kelp_plant") || id.startsWith("minecraft:cave_vines")
                    || id.startsWith("minecraft:big_dripleaf") || id.equals("minecraft:vine")) {
                continue; // placed by block_column / vines only; their canSurvive is never called by the ported features
            }
            assertTrue(BlockSurvival.isPorted(id), "survival rule of " + id);
        }
    }

    @Test
    void stateHolderOperations() {
        StateRef pickle = BlockProperties.SEA_PICKLE;
        assertEquals("4", BlockStateDefinitions.setValue(pickle, BlockStateDefinitions.PICKLES, "4").properties().get("pickles"));
        assertThrows(IllegalArgumentException.class, () -> BlockStateDefinitions.setValue(pickle, BlockStateDefinitions.PICKLES, "5"));
        assertThrows(IllegalArgumentException.class, () -> BlockStateDefinitions.setValue(pickle, BlockStateDefinitions.AGE_25, "1"));
        assertEquals(pickle, BlockStateDefinitions.trySetValue(pickle, BlockStateDefinitions.FACING, "up"));
        StateRef stem = StateRef.of("minecraft:big_dripleaf_stem", "facing", "east", "waterlogged", "true");
        StateRef copied = BlockStateDefinitions.withPropertiesOf(BlockProperties.BIG_DRIPLEAF, stem);
        assertEquals("east", copied.properties().get("facing"));
        assertEquals("true", copied.properties().get("waterlogged"));
        assertEquals("none", copied.properties().get("tilt"));
        assertEquals(10, BlockStateProviders.REGISTERED_TYPES.size());
    }
}
