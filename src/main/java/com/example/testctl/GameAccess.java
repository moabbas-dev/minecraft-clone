package com.example.testctl;

import java.io.IOException;

/**
 * The exact surface Game exposes to the test controller. Deliberately narrow and explicit -
 * Commands only gets to do what these methods allow, rather than holding a reference to Game
 * itself, which keeps the controller from becoming a general remote-code-execution surface
 * (see the request's "avoid arbitrary code execution/filesystem access" constraints).
 */
public interface GameAccess {
    /** yawDegrees/pitchDegrees may be null to leave that axis untouched. */
    void teleport(float x, float y, float z, Float yawDegrees, Float pitchDegrees);

    /** fraction in [0,1): 0=midnight, 0.25=sunrise, 0.5=noon, 0.75=sunset. */
    void setTime(float dayTimeFraction);

    /** state is one of "CLEAR", "RAIN", "THUNDER". */
    void setWeather(String state);

    /** Starts a fresh world for testing, overriding the normal random-seed path with a fixed one. */
    void newTestWorld(long seed);

    /** Captures the current frame to test-output/screenshots/&lt;name&gt;.png. Returns the file path. */
    String captureScreenshot(String name) throws IOException;

    /** Structured plain-text dump of player/biome/color/weather/time/streaming state. */
    String stateSnapshot();

    /** Requests a graceful shutdown, same path as closing the window. */
    void shutdown();
}
