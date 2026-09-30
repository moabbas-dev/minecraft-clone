package com.example.cooking;

/**
 * The 3 processing stations, and the one thing that actually differs between them at the engine
 * level: how many simulated ticks one item takes to finish (the request's own values - 200 for
 * Furnace, 100 for the other two). Everything else that makes them behave differently - which
 * recipes they accept, how fast their fuel burns - comes from {@link CookingRecipeRegistry} and
 * {@link FuelRegistry} querying this station's category, never a per-station if/else chain.
 */
public enum CookingStationType {
    FURNACE("Furnace", 200),
    BLAST_FURNACE("Blast Furnace", 100),
    SMOKER("Smoker", 100);

    public final String displayName;
    /** Simulated ticks (see CookingStationManager's fixed-step accumulator) to finish one item. */
    public final int cookTimeTicks;

    CookingStationType(String displayName, int cookTimeTicks) {
        this.displayName = displayName;
        this.cookTimeTicks = cookTimeTicks;
    }

    /**
     * How much of a fuel's furnace-relative burn duration (see {@link FuelRegistry}, which
     * always defines burn time in normal-Furnace ticks) this station actually gets to burn -
     * exactly {@code cookTimeTicks / FURNACE.cookTimeTicks}. Multiplying a fuel's base ticks by
     * this is what keeps "items per fuel unit" identical across every station (a Blast
     * Furnace/Smoker burns through the SAME fuel in half the real ticks, matching its own halved
     * per-item cook time, rather than getting to squeeze twice as many items out of it) - see
     * this class's own docs and CookingStationState's tick() for where this is actually applied.
     */
    public double fuelTickScale() {
        return (double) cookTimeTicks / FURNACE.cookTimeTicks;
    }
}
