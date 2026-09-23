package com.example.world;

/**
 * A queryable, declarative bundle of one {@link TreeSpecies}' own dimensional/shape data - the
 * reusable "tree profile" the biome round's spec asks for. Deliberately NOT a replacement for
 * each species' private shape-generator method (see {@link TreeSpecies}'s class docs): those
 * stay imperative because each shape family (conical, umbrella, circular, ...) genuinely needs
 * different geometry, and forcing all of them through one generic declarative shape language
 * would be a much larger, riskier rewrite of already-tested, already-visually-verified code for
 * no behavioral benefit. This record is what lets other code (tests, future debug tooling, a
 * future species) inspect a species' own numbers as one bundle instead of reading loose enum
 * fields one at a time.
 *
 * {@code maxBlockCount} is an empirically-measured, margin-padded upper bound (each species'
 * own field javadoc in {@link TreeSpecies} records the real measured maximum it was padded
 * from) - not a live-enforced cap. Enforcing it live would mean threading a mutable counter
 * through every one of the 9 existing shape methods, a much larger change for a bound that's
 * already comfortably true; {@code TreeProfileTest} instead asserts real placement across many
 * seeds never exceeds it, which is what "maximum total block count" needs to actually mean
 * here: a validated invariant, not invented enforcement.
 */
public record TreeProfile(
        BlockType log,
        BlockType leaves,
        int minTrunkHeight,
        int maxTrunkHeight,
        /** 1 for every species except DARK_OAK's real 2x2 footprint. */
        int trunkFootprint,
        int canopyRadius,
        /** Distinct vertical rows the canopy occupies - a documentation/introspection value,
         *  read directly off each species' own shape method, not separately enforced. */
        int canopyHeight,
        CanopyShape canopyShape,
        int minSpacing,
        int maxBlockCount) {
    // Deliberately no derived maxTotalHeight() here - TreeSpecies.maxTotalHeight() (unchanged,
    // maxTrunkHeight+3, the value TerrainGenerator's placement-safety check actually uses) stays
    // the one source of truth for that; a second method with a different canopyHeight-based
    // formula on this record would just create two numbers that could disagree.
}
