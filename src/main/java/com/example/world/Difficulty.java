package com.example.world;

/**
 * The one difficulty-driven rule this project currently has: how low starvation can bring a
 * player's health (see {@code com.example.survival.PlayerSurvival}). Deliberately minimal - no
 * mob-spawn-rate/hostile-damage-multiplier system exists yet, so this isn't a general difficulty
 * framework, just the smallest abstraction the request's starvation-floor rule actually needs.
 * No world-creation UI sets this yet; every world defaults to {@link #NORMAL} (see
 * {@link WorldSettings}'s convenience constructor) until one exists.
 */
public enum Difficulty {
    EASY, NORMAL, HARD,
    /** Appended last so saved worlds (which store the ordinal) keep their difficulty. */
    PEACEFUL;

    /** Vanilla's order on the Options button: Peaceful, Easy, Normal, Hard. */
    public Difficulty next() {
        return switch (this) {
            case PEACEFUL -> EASY;
            case EASY -> NORMAL;
            case NORMAL -> HARD;
            case HARD -> PEACEFUL;
        };
    }

    public String label() {
        String s = name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
