package com.example.world;

/**
 * Descriptive tag for each {@link TreeSpecies}' canopy silhouette - purely metadata (see
 * {@link TreeProfile}), not itself a rendering or placement mechanism. Exists so species can be
 * grouped/queried by shape family (tests, future debug tooling) without inspecting each
 * species' private shape-generator method.
 */
public enum CanopyShape {
    /** Square-ish, roughly spherical layered canopy - OAK. */
    ROUND_LAYERED,
    /** Vertically stretched oval, narrow, bare trunk below - BIRCH. */
    TALL_OVAL,
    /** Tapers from a wide base to a point - SPRUCE. */
    CONICAL,
    /** Large overlapping leaf masses on log branches - JUNGLE. */
    BRANCH_CLUSTERS,
    /** Bent/forked trunk carrying broad, shallow, offset leaf platforms - ACACIA. */
    FORKED_UMBRELLA,
    /** Low, wide, lumpy lobes with one drooping side - SWAMP_OAK. */
    IRREGULAR_LOW,
    /** Wide layers spanning a thick multi-block trunk footprint - DARK_OAK. */
    THICK_BROAD,
    /** Several rounded lobes around short branches, soft gaps and hanging edges - CHERRY. */
    LOBED_ROUND,
    /** Irregular, branch-driven sprawl with uneven lobes and sparse hanging strands - MANGROVE. */
    BRANCH_SPRAWL
}
