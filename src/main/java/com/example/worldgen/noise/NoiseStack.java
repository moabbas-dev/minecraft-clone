package com.example.worldgen.noise;

import com.example.worldgen.Interval;
import com.example.worldgen.density.DensityBuffer;
import com.example.worldgen.density.DensityVolume;

import java.util.ArrayList;
import java.util.List;

/**
 * Port of {@code net.minecraft.world.level.levelgen.synth.NoiseStack} (26.3): a weighted sum of
 * noise layers, {@code sum += amplitude * noise.get(x * frequency, y * frequency, z * frequency)}
 * accumulated in float in layer order. In 26.3 this replaces the octave loop of the old
 * multi-octave {@code PerlinNoise}. Vanilla's builder returns private subclasses
 * ({@code NoiseStack$Perlin}, {@code NoiseStack$SmearedPerlin}) when every layer is a plain or
 * smeared Perlin octave; their {@code get} bodies are identical to this one (they only let the
 * JIT devirtualise), so a single class is used here.
 */
public class NoiseStack implements Noise {

    protected final Layer[] layers;
    private final Interval range;

    private NoiseStack(Layer[] layers) {
        this.layers = layers;
        Interval total = Interval.ofExact(0.0F);
        for (Layer layer : layers) {
            Interval scaled = Interval.mul(layer.noise.range(), Interval.ofExact(layer.amplitude));
            total = Interval.add(total, scaled);
        }
        this.range = total;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Interval range() {
        return range;
    }

    @Override
    public float get(double x, double y, double z) {
        float sum = 0.0F;
        for (Layer layer : layers) {
            double f = layer.frequency;
            sum += layer.amplitude * layer.noise.get(x * f, y * f, z * f);
        }
        return sum;
    }

    @Override
    public float get(double x, double z) {
        float sum = 0.0F;
        for (Layer layer : layers) {
            double f = layer.frequency;
            sum += layer.amplitude * layer.noise.get(x * f, z * f);
        }
        return sum;
    }

    /** {@code NoiseStack.addToVolume}: each layer adds into the buffer at its frequency and amplitude. */
    @Override
    public void addToVolume(DensityBuffer buffer, DensityVolume volume, double xzScale, double yScale, float amplitude) {
        for (Layer layer : layers) {
            double frequency = layer.frequency;
            layer.noise.addToVolume(buffer, volume, xzScale * frequency, yScale * frequency, amplitude * layer.amplitude);
        }
    }

    public Noise getLayer(int index) {
        return layers[index].noise;
    }

    /** Number of layers (vanilla reads {@code layers.length} directly). */
    public int layerCount() {
        return layers.length;
    }

    /** {@code NoiseStack.Layer}: one noise with its input frequency and output amplitude. */
    public record Layer(Noise noise, double frequency, float amplitude) {
    }

    /** {@code NoiseStack.Builder}. */
    public static class Builder {

        private final List<Layer> layers = new ArrayList<>();

        private Builder() {
        }

        public Builder add(Noise noise, double frequency, float amplitude) {
            layers.add(new Layer(noise, frequency, amplitude));
            return this;
        }

        /** Appends every layer of {@code stack} with frequency and amplitude multiplied in. */
        public Builder addStack(NoiseStack stack, double frequency, float amplitude) {
            for (Layer layer : stack.layers) {
                layers.add(new Layer(layer.noise, layer.frequency * frequency, layer.amplitude * amplitude));
            }
            return this;
        }

        public NoiseStack build() {
            return new NoiseStack(layers.toArray(Layer[]::new));
        }
    }
}
