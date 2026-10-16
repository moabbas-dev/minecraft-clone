package com.example.worldgen.biome;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Loads golden files produced by running the real 26.3 {@code OverworldBiomeBuilder}, {@code Climate}
 * and {@code MultiNoiseBiomeSourceParameterList$Preset$1} classes (unmodified, from the client jar) with
 * small stand-ins for DataFixerUpper/codec types.
 *
 * <p>Parameter files: one entry per line, {@code tmin tmax hmin hmax cmin cmax emin emax dmin dmax wmin wmax
 * offset biome}. Search files: {@code t h c e d w indexResult bruteForceResult}, queries made in this order
 * against one freshly built list on one thread ({@code findValue} then {@code findValueBruteForce}).
 */
final class GoldenData {
    private GoldenData() {
    }

    static List<String[]> read(String name) throws IOException {
        InputStream raw = GoldenData.class.getResourceAsStream(name);
        if (raw == null) {
            throw new IOException("Missing golden resource " + name);
        }
        List<String[]> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(new GZIPInputStream(raw), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    rows.add(line.trim().split(" "));
                }
            }
        }
        return rows;
    }

    static String format(Climate.Entry<String> entry) {
        Climate.ParameterPoint p = entry.parameters();
        return p.temperature().min() + " " + p.temperature().max() + " " + p.humidity().min() + " "
            + p.humidity().max() + " " + p.continentalness().min() + " " + p.continentalness().max() + " "
            + p.erosion().min() + " " + p.erosion().max() + " " + p.depth().min() + " " + p.depth().max() + " "
            + p.weirdness().min() + " " + p.weirdness().max() + " " + p.offset() + " " + entry.value();
    }

    static Climate.TargetPoint target(String[] row) {
        return new Climate.TargetPoint(Long.parseLong(row[0]), Long.parseLong(row[1]), Long.parseLong(row[2]),
            Long.parseLong(row[3]), Long.parseLong(row[4]), Long.parseLong(row[5]));
    }
}
