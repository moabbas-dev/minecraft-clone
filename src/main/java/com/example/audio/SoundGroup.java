package com.example.audio;

/**
 * One sound "manifest entry" (Phase 4's manifest concept, expressed as this project's own
 * static-table-in-code convention - see {@code SoundRegistry} - rather than an external JSON
 * file, since nothing else in this codebase parses config from JSON and this doesn't need to be
 * the first thing that does).
 */
public record SoundGroup(String id, String[] fileNames, AudioCategory category, float baseVolume,
                          float pitchMin, float pitchMax, boolean positional, float maxDistance) {
    public SoundGroup {
        if (fileNames == null || fileNames.length == 0) {
            throw new IllegalArgumentException("SoundGroup '" + id + "' must have at least one file");
        }
        if (baseVolume < 0f || baseVolume > 1f) {
            throw new IllegalArgumentException("SoundGroup '" + id + "' baseVolume out of [0,1]: " + baseVolume);
        }
        if (pitchMin <= 0f || pitchMax < pitchMin) {
            throw new IllegalArgumentException("SoundGroup '" + id + "' invalid pitch range: [" + pitchMin + "," + pitchMax + "]");
        }
    }
}
