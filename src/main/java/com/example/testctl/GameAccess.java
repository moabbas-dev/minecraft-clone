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

    /** Starts a fresh superflat world (grass at y=3), for lining trees up side by side. */
    void newFlatTestWorld();

    /** Loads an already-saved world by its on-disk folder name (see {@code state}'s own
     *  {@code world.folder} line) through the real load path - the only way this controller can
     *  exercise a save/load round trip live, since the normal load flow is a menu screen with no
     *  synthetic input support. @throws IllegalStateException if no such folder was ever saved. */
    void loadTestWorld(String folderName);

    /** Generates one tree of the named {@code TreeSpecies} with its trunk base at (x, y, z) using
     *  the real placement code and a fixed seed, then refreshes the meshes. The tree must fit in
     *  the chunk containing (x, z) exactly like generated trees do (place it near a chunk centre).
     *  @throws IllegalArgumentException for an unknown species. */
    void spawnTree(String species, int x, int y, int z, long seed);

    /** Sets a single world block by BlockType enum name (case-insensitive) - the one narrow,
     *  named-enum-only write surface exposed here (no arbitrary code/expression evaluation).
     *  If the placed block is a sapling stage block, it's registered with growth tracking the
     *  same way a real player planting it or a chunk reload discovering it would be, so this
     *  can exercise the full growth path live, not just rendering.
     *  @throws IllegalArgumentException if blockTypeName isn't a real BlockType. */
    void setBlock(int x, int y, int z, String blockTypeName);

    /** Selects a hotbar slot by index into {@code BlockType.PLACEABLE}, same effect as pressing
     *  the matching number key. @throws IllegalArgumentException if index is out of range. */
    void selectSlot(int index);

    /** Adds items (by BlockType enum name, case-insensitive) to the inventory through its normal
     *  add API and returns how many actually fit. @throws IllegalArgumentException for an unknown
     *  or non-item type or a negative count. */
    int giveItem(String blockTypeName, int count);

    /** Holds (or releases) the attack button through the same path real mouse input takes. */
    void holdAttack(boolean held);

    /** Queues one use/place click, consumed by the next interaction update. */
    void useItem();

    /** Rotates the player to face a world point. @throws IllegalArgumentException if it is the eye position. */
    void lookAt(double x, double y, double z);

    /** Opens the inventory/crafting screen with a 2x2 or 3x3 crafting grid.
     *  @throws IllegalArgumentException for any other size; @throws IllegalStateException when not in-game. */
    void openInventoryScreen(int gridSize);

    /** A left or right click on a menu slot id (0-35 player slots, 100+i crafting grid, 1000 result). */
    void inventoryClick(int slot, boolean rightButton);

    /** Closes the screen, returning grid/cursor contents to the inventory (overflow drops in the world). */
    void closeInventoryScreen();

    /** Captures the current frame to test-output/screenshots/&lt;name&gt;.png. Returns the file path. */
    String captureScreenshot(String name) throws IOException;

    /** Applies damage through the real {@code PlayerSurvival.damage} pipeline (respects the hurt
     *  cooldown - two calls close together may see the second one blocked, same as real combat
     *  would). {@code source} is a {@code DamageSource} enum name (case-insensitive).
     *  @throws IllegalArgumentException for an unknown source or a non-positive amount;
     *  @throws IllegalStateException when not in-game. */
    void damage(float amount, String source);

    /** Heals through the real {@code HealthComponent.heal} (clamped to max, no-op if dead).
     *  @throws IllegalStateException when not in-game. */
    void heal(float amount);

    /** Full heal, clears death - the "future respawn" hook this milestone is asked to leave a
     *  clear place for. @throws IllegalStateException when not in-game. */
    void respawn();

    /** {@code level} is a {@code Difficulty} enum name (case-insensitive); also updates the
     *  world's own settings so a later save persists it. @throws IllegalArgumentException for an
     *  unknown level; @throws IllegalStateException when not in-game. */
    void setDifficulty(String level);

    /** Directly sets hunger state (food/saturation/exhaustion), clamped the same way eating or
     *  exhaustion normally would - for setting up starvation/regen/sprint-disable scenarios
     *  without waiting out real exhaustion accumulation. @throws IllegalStateException when not
     *  in-game. */
    void setHunger(float foodLevel, float saturation, float exhaustion);

    /** Structured plain-text dump of player/biome/color/weather/time/streaming state. */
    String stateSnapshot();

    /** Requests a graceful shutdown, same path as closing the window. */
    void shutdown();
}
