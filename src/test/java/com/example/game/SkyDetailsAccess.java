package com.example.game;

/** Lets tests in other packages reach the package-private sky maths of {@link SkyDetails}. */
public final class SkyDetailsAccess {
    private SkyDetailsAccess() {
    }

    /** The game's day fraction (0 = midnight) for a vanilla tick of day (0 = sunrise). */
    public static float dayTimeOfTick(float tick) {
        return (((tick + 6000f) % 24000f) + 24000f) % 24000f / 24000f;
    }

    public static int skyDarken(float tick, float rain, float thunder) {
        return SkyDetails.skyDarken(tick, rain, thunder);
    }
}
