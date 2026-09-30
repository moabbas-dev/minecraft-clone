package com.example.cooking;

import com.example.world.BlockType;

import java.util.Set;

/**
 * One input-to-output conversion, data only - no station-specific behavior lives here or in any
 * station class; {@link CookingStationState} only ever asks {@link CookingRecipeRegistry} "what
 * (if anything) does this input become at this station", never branches on which station it is.
 *
 * @param input                    the item consumed, exactly 1 per completed cook
 * @param output                   the item produced
 * @param outputCount              how many of {@code output} one cook yields (always 1 for every
 *                                 recipe this project has today; kept as a real field, not
 *                                 hardcoded to 1, since nothing about the architecture assumes it)
 * @param experience               XP stored per completed cook (see CookingStationState/
 *                                 PlayerSurvival for how it's actually granted - not every tick)
 * @param stations                 which station(s) accept this recipe - see {@link #supportsStation}
 * @param cookTimeOverrideTicks    null for almost every recipe (the station's own cookTimeTicks
 *                                 applies) - the extensibility hook the request calls for, kept
 *                                 unused rather than invented a use for it
 */
public record CookingRecipe(BlockType input, BlockType output, int outputCount, float experience,
                             Set<CookingStationType> stations, Integer cookTimeOverrideTicks) {
    public CookingRecipe(BlockType input, BlockType output, int outputCount, float experience,
                          Set<CookingStationType> stations) {
        this(input, output, outputCount, experience, stations, null);
    }

    public boolean supportsStation(CookingStationType station) {
        return stations.contains(station);
    }

    /** The station's own cookTimeTicks, unless this specific recipe overrides it. */
    public int cookTimeTicksFor(CookingStationType station) {
        return cookTimeOverrideTicks != null ? cookTimeOverrideTicks : station.cookTimeTicks;
    }
}
