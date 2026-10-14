package com.example.ui;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** The yellow splash text on the title screen, picked from Minecraft's own list ({@code texts/splashes.txt}). */
public final class Splashes {
    private Splashes() {
    }

    /** The non-empty lines of a splash file; empty when it is missing or unreadable. */
    public static List<String> load(File file) {
        List<String> out = new ArrayList<>();
        try {
            if (file.isFile()) {
                for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                    String s = line.strip();
                    if (!s.isEmpty() && s.chars().allMatch(c -> c >= 32 && c < 127)) out.add(s); // the font draws plain ASCII
                }
            }
        } catch (IOException | RuntimeException e) {
            out.clear();
        }
        return out;
    }

    /** One random splash, or an empty string when there are none. */
    public static String pick(List<String> all, Random random) {
        return all.isEmpty() ? "" : all.get(random.nextInt(all.size()));
    }
}
