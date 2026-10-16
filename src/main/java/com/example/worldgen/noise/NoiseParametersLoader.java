package com.example.worldgen.noise;

import com.example.worldgen.json.Json;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads vanilla noise definitions ({@code data/<namespace>/worldgen/noise/<path>.json}) from the
 * locally restored Mojang data folder (see {@code scripts/restore-worldgen-data.py}) and decodes
 * them the way the 26.3 {@code NormalNoise.Parameters.CODEC} does:
 * <ul>
 *     <li>{@code base_amplitude}: optional, default 1.0, range [{@code 1.0E-5f} widened, 1000000];</li>
 *     <li>{@code base_octave}: required int in [-32, 32];</li>
 *     <li>{@code octave_count}: optional, default 1, int in [1, 32];</li>
 *     <li>{@code normalize}: optional, default enabled; {@code true}/{@code false} or {@code "legacy"};</li>
 *     <li>{@code amplitude_modifiers}: optional, default empty; 0..32 doubles each in [0, 1000000],
 *     and when present its size must equal {@code octave_count}.</li>
 * </ul>
 * Unknown keys are ignored, as by vanilla's record codec.
 */
public final class NoiseParametersLoader {

    private static final double MIN_BASE_AMPLITUDE = 9.999999747378752E-6;
    private static final double MAX_AMPLITUDE = 1000000.0;

    private final Path dataRoot;

    /** @param dataRoot the {@code data} directory (containing {@code minecraft/worldgen/noise}). */
    public NoiseParametersLoader(Path dataRoot) {
        this.dataRoot = dataRoot;
    }

    /** Loader for {@code ./data} relative to the working directory (the repository root). */
    public static NoiseParametersLoader fromWorkingDirectory() {
        return new NoiseParametersLoader(Path.of("data"));
    }

    /** The file an id resolves to; {@code "continentalness"} means {@code "minecraft:continentalness"}. */
    public Path pathOf(String id) {
        int colon = id.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : id.substring(0, colon);
        String path = colon < 0 ? id : id.substring(colon + 1);
        return dataRoot.resolve(namespace).resolve("worldgen").resolve("noise").resolve(path + ".json");
    }

    public NormalNoise.Parameters loadParameters(String id) {
        Path file = pathOf(id);
        String text;
        try {
            text = Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read noise " + id + " from " + file, e);
        }
        try {
            return decode(Json.parse(text));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Invalid noise " + id + ": " + e.getMessage(), e);
        }
    }

    public NormalNoise load(String id) {
        return NormalNoise.of(loadParameters(id));
    }

    /** Decodes one parsed noise JSON object. */
    public static NormalNoise.Parameters decode(Object json) {
        if (!(json instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Noise definition must be a JSON object");
        }
        double baseAmplitude = map.containsKey("base_amplitude")
                ? doubleInRange(map.get("base_amplitude"), "base_amplitude", MIN_BASE_AMPLITUDE, MAX_AMPLITUDE) : 1.0;
        if (!map.containsKey("base_octave")) {
            throw new IllegalArgumentException("No key base_octave");
        }
        int baseOctave = intInRange(map.get("base_octave"), "base_octave", -32, 32);
        int octaveCount = map.containsKey("octave_count") ? intInRange(map.get("octave_count"), "octave_count", 1, 32) : 1;
        NormalNoise.Normalization normalize = map.containsKey("normalize")
                ? normalization(map.get("normalize")) : NormalNoise.Normalization.ENABLED;
        List<Double> modifiers = new ArrayList<>();
        if (map.containsKey("amplitude_modifiers")) {
            if (!(map.get("amplitude_modifiers") instanceof List<?> list)) {
                throw new IllegalArgumentException("amplitude_modifiers must be a list");
            }
            if (list.size() > 32) {
                throw new IllegalArgumentException("amplitude_modifiers has more than 32 entries");
            }
            for (Object o : list) {
                modifiers.add(doubleInRange(o, "amplitude_modifiers entry", 0.0, MAX_AMPLITUDE));
            }
        }
        if (!modifiers.isEmpty() && modifiers.size() != octaveCount) {
            throw new IllegalArgumentException("Amplitude modifier count " + modifiers.size()
                    + " does not match octave count " + octaveCount);
        }
        return new NormalNoise.Parameters(baseAmplitude, baseOctave, octaveCount, normalize, modifiers);
    }

    private static double doubleInRange(Object value, String name, double min, double max) {
        if (!(value instanceof Number n)) {
            throw new IllegalArgumentException(name + " must be a number");
        }
        double d = n.doubleValue();
        if (!(d >= min && d <= max)) {
            throw new IllegalArgumentException(name + " " + d + " outside [" + min + ", " + max + "]");
        }
        return d;
    }

    private static int intInRange(Object value, String name, int min, int max) {
        if (!(value instanceof Long l)) {
            throw new IllegalArgumentException(name + " must be an integer");
        }
        if (l < min || l > max) {
            throw new IllegalArgumentException(name + " " + l + " outside [" + min + ", " + max + "]");
        }
        return (int) (long) l;
    }

    private static NormalNoise.Normalization normalization(Object value) {
        if (value instanceof Boolean b) {
            return b ? NormalNoise.Normalization.ENABLED : NormalNoise.Normalization.DISABLED;
        }
        if ("legacy".equals(value)) {
            return NormalNoise.Normalization.LEGACY;
        }
        throw new IllegalArgumentException("normalize must be true, false or \"legacy\"");
    }
}
