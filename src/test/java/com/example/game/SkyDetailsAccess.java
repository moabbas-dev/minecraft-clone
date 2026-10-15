package com.example.game;

/** Lets tests in other packages reach the package-private sky maths of {@link SkyDetails}. */
public final class SkyDetailsAccess {
    private SkyDetailsAccess() {
    }

    public static int skyDarken(float tick, float rain, float thunder) {
        return SkyDetails.skyDarken(tick, rain, thunder);
    }
}
