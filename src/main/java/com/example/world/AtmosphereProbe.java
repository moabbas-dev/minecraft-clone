package com.example.world;

/**
 * The camera's {@code EnvironmentAttributeProbe} (26.3) for the biome atmosphere attributes ({@link BiomeAttributes}): every client
 * tick ({@code Camera.tick}) it samples the biomes around the camera position; a frame reads {@code partialTickLerp(partialTick,
 * lastValue, newValue)}, i.e. the value of the previous tick blended toward this tick's. Before the first tick it answers the
 * overworld's dimension values ({@code ValueProbe} before a level is set gives the defaults; the engine starts from the dimension).
 */
public final class AtmosphereProbe {
    private BiomeAttributes.Values last = BiomeAttributes.Values.overworld();
    private BiomeAttributes.Values current = last;
    private boolean ticked;

    /** {@code EnvironmentAttributeProbe.tick(level, cameraPosition)}. */
    public void tick(World world, double x, double y, double z) {
        BiomeAttributes.Values value = world.atmosphereAt(x, y, z);
        last = ticked ? current : value;
        current = value;
        ticked = true;
    }

    /** {@code EnvironmentAttributeProbe.reset} (a new world). */
    public void reset() {
        last = current = BiomeAttributes.Values.overworld();
        ticked = false;
    }

    /** The values for a frame {@code partialTick} (0..1) of the way from the last tick to the next. */
    public BiomeAttributes.Values get(float partialTick) {
        return last.lerp(partialTick, current);
    }
}
