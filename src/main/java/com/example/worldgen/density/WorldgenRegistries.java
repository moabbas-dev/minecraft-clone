package com.example.worldgen.density;

import com.example.worldgen.json.Json;
import com.example.worldgen.noise.NoiseParametersLoader;
import com.example.worldgen.noise.NormalNoise;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The two data-driven registries the density functions read, loaded on demand from the restored vanilla data
 * folder ({@code data/<namespace>/worldgen/density_function/<path>.json} and {@code .../noise/<path>.json}): the
 * {@code minecraft:worldgen/density_function} registry (entries decoded with vanilla's {@code DIRECT_CODEC} by
 * {@link DensityFunctionParser}) and the {@code minecraft:worldgen/noise} registry ({@link NoiseParametersLoader}).
 * Entries are {@link Holder.Reference}s, unique per id, bound once their file is decoded; an id without a file is
 * an error, as an unknown registry key is in vanilla. Ids without a namespace get {@code minecraft:}.
 */
public final class WorldgenRegistries {

    private final Path dataRoot;
    private final NoiseParametersLoader noiseLoader;
    private final Map<String, Holder.Reference<DensityFunction>> densityFunctions = new HashMap<>();
    private final Map<String, Holder.Reference<NormalNoise>> noises = new HashMap<>();
    private final DensityFunctionParser parser = new DensityFunctionParser(this);

    /** @param dataRoot the {@code data} directory (containing {@code minecraft/worldgen}). */
    public WorldgenRegistries(Path dataRoot) {
        this.dataRoot = dataRoot;
        this.noiseLoader = new NoiseParametersLoader(dataRoot);
    }

    public static WorldgenRegistries fromWorkingDirectory() {
        return new WorldgenRegistries(Path.of("data"));
    }

    public Path dataRoot() {
        return dataRoot;
    }

    public DensityFunctionParser parser() {
        return parser;
    }

    /** {@code namespace:path} form of an id ({@code Identifier.parse} default namespace {@code minecraft}). */
    public static String normalize(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    Path file(String id, String registryFolder) {
        String full = normalize(id);
        int colon = full.indexOf(':');
        return dataRoot.resolve(full.substring(0, colon)).resolve("worldgen").resolve(registryFolder)
                .resolve(full.substring(colon + 1) + ".json");
    }

    /** The registered density function {@code id}; decodes its file the first time. */
    public synchronized Holder.Reference<DensityFunction> densityFunction(String id) {
        String key = normalize(id);
        Holder.Reference<DensityFunction> holder = densityFunctions.get(key);
        if (holder != null) {
            return holder;
        }
        Path path = file(key, "density_function");
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Unknown registry key in minecraft:worldgen/density_function: " + key);
        }
        holder = new Holder.Reference<>(key);
        densityFunctions.put(key, holder);
        try {
            holder.bindValue(parser.parseDirect(Json.parseWithNumberLiterals(read(path))));
        } catch (RuntimeException e) {
            densityFunctions.remove(key);
            throw new IllegalArgumentException("Invalid density function " + key + ": " + e.getMessage(), e);
        }
        return holder;
    }

    /** The registered noise {@code id}; decodes its file the first time. */
    public synchronized Holder.Reference<NormalNoise> noise(String id) {
        String key = normalize(id);
        Holder.Reference<NormalNoise> holder = noises.get(key);
        if (holder != null) {
            return holder;
        }
        if (!Files.isRegularFile(file(key, "noise"))) {
            throw new IllegalArgumentException("Unknown registry key in minecraft:worldgen/noise: " + key);
        }
        holder = new Holder.Reference<>(key);
        holder.bindValue(noiseLoader.load(key));
        noises.put(key, holder);
        return holder;
    }

    /** Every {@code minecraft:} id that has a file in the given registry folder ({@code density_function}, ...). */
    public List<String> listIds(String registryFolder) {
        Path base = dataRoot.resolve("minecraft").resolve("worldgen").resolve(registryFolder);
        try (Stream<Path> files = Files.walk(base)) {
            return files.filter(p -> p.toString().endsWith(".json")).map(p -> {
                String rel = base.relativize(p).toString().replace('\\', '/');
                return "minecraft:" + rel.substring(0, rel.length() - ".json".length());
            }).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads a UTF-8 data file. */
    public static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + path, e);
        }
    }
}
