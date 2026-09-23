package com.example.world;

/**
 * Descriptive tag for each {@link TreeSpecies}' canopy silhouette - purely metadata (see
 * {@link TreeProfile}), not itself a rendering or placement mechanism. Exists so species can be
 * grouped/queried by shape family (tests, future debug tooling) without inspecting each
 * species' private shape-generator method.
 */
public enum CanopyShape {
    /** Square-ish, roughly spherical layered canopy - OAK, BIRCH. */
    ROUND_LAYERED,
    /** Tapers from a wide base to a point - SPRUCE. */
    CONICAL,
    /** Large, mostly-flat broad layers - JUNGLE. */
    BROAD_FLAT,
    /** Thin canopy sitting only at the very top of a short trunk - ACACIA. */
    UMBRELLA_TOP,
    /** Low, wide, randomly-notched outline - SWAMP_OAK, MANGROVE. */
    IRREGULAR_LOW,
    /** Wide layers spanning a thick multi-block trunk footprint - DARK_OAK. */
    THICK_BROAD,
    /** Genuinely circular-distance (not square-trimmed) layers - CHERRY. */
    CIRCULAR
}
