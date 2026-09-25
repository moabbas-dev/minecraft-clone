package com.example.audio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Only exercises SoundRegistry's pure metadata (definitionOf) - never buffersFor(), which
 * decodes real files and uploads to OpenAL and so needs a real audio device/context this suite
 * deliberately never creates (see Phase 16.4: separate pure logic from runtime AL validation,
 * same split TESTING.md already uses for GL/window-dependent code).
 */
class SoundRegistryTest {

    @Test
    void everyMaterialHasADigAndStepDefinitionExceptGlassWhichOnlyHasDig() {
        SoundRegistry registry = new SoundRegistry();
        for (SoundMaterial m : new SoundMaterial[]{SoundMaterial.STONE, SoundMaterial.GRAVEL,
                SoundMaterial.GRASS, SoundMaterial.SAND, SoundMaterial.SNOW, SoundMaterial.WOOD}) {
            String id = m.name().toLowerCase();
            assertNotNull(registry.definitionOf("dig." + id), "missing dig." + id);
            assertNotNull(registry.definitionOf("step." + id), "missing step." + id);
        }
        assertNotNull(registry.definitionOf("dig.glass"));
        assertNull(registry.definitionOf("step.glass"), "glass is never solid-collision-walkable, so no step sound is needed");
    }

    @Test
    void ambientAndWeatherDefinitionsExist() {
        SoundRegistry registry = new SoundRegistry();
        assertNotNull(registry.definitionOf("ambient.cave"));
        assertNotNull(registry.definitionOf("ambient.rain"));
        assertNotNull(registry.definitionOf("weather.thunder"));
    }

    @Test
    void unknownIdReturnsNullDefinitionAndEmptyBuffers() {
        SoundRegistry registry = new SoundRegistry();
        assertNull(registry.definitionOf("not.a.real.id"));
        assertEquals(0, registry.buffersFor("not.a.real.id").length,
                "an unknown ID must return an empty buffer array, never null and never throw");
    }

    @Test
    void digSoundsArePositionalAndBlocksCategory() {
        SoundRegistry registry = new SoundRegistry();
        SoundGroup g = registry.definitionOf("dig.stone");
        assertTrue(g.positional());
        assertEquals(AudioCategory.BLOCKS, g.category());
    }

    @Test
    void thunderAndRainAreNonPositionalWeatherCategory() {
        SoundRegistry registry = new SoundRegistry();
        assertFalse(registry.definitionOf("weather.thunder").positional());
        assertFalse(registry.definitionOf("ambient.rain").positional());
        assertEquals(AudioCategory.WEATHER, registry.definitionOf("weather.thunder").category());
        assertEquals(AudioCategory.WEATHER, registry.definitionOf("ambient.rain").category());
    }
}
