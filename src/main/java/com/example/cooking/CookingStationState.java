package com.example.cooking;

import com.example.inventory.ItemStack;
import lombok.Getter;

/**
 * The actual "block entity" behind one placed Furnace/Blast Furnace/Smoker - this project's
 * first block-entity-shaped state (no such system existed before this milestone; every other
 * block's entire state is its BlockType id). Owns its own input/fuel/output and burn/cook
 * progress, and simulates itself one call to {@link #tick()} at a time - see
 * {@link CookingStationManager} for who calls that, on what schedule, and completely independent
 * of whether any GUI is currently looking at this station (this class has no idea a GUI exists).
 *
 * Never branches on {@code type == FURNACE}/etc. anywhere in here - every station-specific
 * decision (which recipes, how long a cook takes, how fast fuel burns) is answered by asking
 * {@link CookingRecipeRegistry}/{@link CookingStationType} a question, not by an if/else chain.
 */
public class CookingStationState {
    @Getter
    private final int x, y, z;
    @Getter
    private final CookingStationType type;

    @Getter
    private ItemStack inputStack = ItemStack.EMPTY;
    @Getter
    private ItemStack fuelStack = ItemStack.EMPTY;
    @Getter
    private ItemStack outputStack = ItemStack.EMPTY;

    @Getter
    private int burnTimeRemainingTicks = 0;
    /** The current fuel item's own full (already station-scaled) burn duration - only meaningful
     *  while {@link #isBurning()}; used purely for the GUI's burn-indicator fraction. */
    @Getter
    private int totalBurnTimeTicks = 0;
    @Getter
    private int cookProgressTicks = 0;
    @Getter
    private float storedExperience = 0f;

    public CookingStationState(CookingStationType type) {
        this(0, 0, 0, type);
    }

    public CookingStationState(int x, int y, int z, CookingStationType type) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = type;
    }

    public boolean isBurning() {
        return burnTimeRemainingTicks > 0;
    }

    /** 0..1, how far through the burning fuel unit this station is - 0 while unlit. */
    public float burnFraction() {
        return totalBurnTimeTicks <= 0 ? 0f : (float) burnTimeRemainingTicks / totalBurnTimeTicks;
    }

    /** 0..1, how far through cooking the current input item this station is. */
    public float cookFraction() {
        int needed = requiredCookTicks();
        return needed <= 0 ? 0f : (float) cookProgressTicks / needed;
    }

    public void setInputStack(ItemStack stack) {
        inputStack = stack;
    }

    public void setFuelStack(ItemStack stack) {
        fuelStack = stack;
    }

    private int requiredCookTicks() {
        CookingRecipe recipe = CookingRecipeRegistry.recipeFor(inputStack.type(), type);
        return recipe == null ? type.cookTimeTicks : recipe.cookTimeTicksFor(type);
    }

    /** Whether a completed cook can currently happen: a real recipe exists for the current input
     *  at this station, and the output slot either is empty or already holds that exact recipe's
     *  output with room for one more (see the class docs on "never overwrite incompatible
     *  output" / "never let the output slot silently exceed the stack limit"). */
    private boolean canProcess() {
        if (inputStack.isEmpty()) return false;
        CookingRecipe recipe = CookingRecipeRegistry.recipeFor(inputStack.type(), type);
        if (recipe == null) return false;
        if (!outputStack.isEmpty() && outputStack.type() != recipe.output()) return false;
        int futureCount = outputStack.isEmpty() ? recipe.outputCount() : outputStack.count() + recipe.outputCount();
        return futureCount <= com.example.inventory.Inventory.DEFAULT_MAX_STACK;
    }

    /**
     * Advances the simulation by exactly one tick (see {@link CookingStationManager} for the
     * fixed-step accumulator that guarantees this is called at a constant real-time rate,
     * independent of render FPS). Order matters and mirrors real vanilla: a burnt-out fire only
     * relights if there's actually something valid to cook right now (never burns fuel for
     * nothing - see the class docs' "output safety" note), but once lit, it burns down
     * regardless of the input becoming invalid mid-burn.
     */
    public void tick() {
        if (burnTimeRemainingTicks <= 0 && canProcess() && !fuelStack.isEmpty() && FuelRegistry.isFuel(fuelStack.type())) {
            int baseTicks = FuelRegistry.furnaceBurnTicks(fuelStack.type());
            totalBurnTimeTicks = Math.max(1, (int) Math.round(baseTicks * type.fuelTickScale()));
            burnTimeRemainingTicks = totalBurnTimeTicks;
            fuelStack = fuelStack.withCount(fuelStack.count() - 1); // ItemStack's own ctor normalizes 0 -> EMPTY
        }

        if (burnTimeRemainingTicks > 0) {
            burnTimeRemainingTicks--;
            if (canProcess()) {
                cookProgressTicks++;
                if (cookProgressTicks >= requiredCookTicks()) {
                    completeCook();
                    cookProgressTicks = 0;
                }
            }
            // Burning but nothing valid to cook right now: the fuel still burns down (it was
            // already committed), progress simply doesn't advance - matches real vanilla.
        } else {
            cookProgressTicks = 0; // the fire went out - progress resets, same as vanilla
        }
    }

    private void completeCook() {
        CookingRecipe recipe = CookingRecipeRegistry.recipeFor(inputStack.type(), type);
        if (recipe == null) return; // defensive - canProcess() already guarantees this true when called
        outputStack = outputStack.isEmpty()
                ? new ItemStack(recipe.output(), recipe.outputCount())
                : outputStack.withCount(outputStack.count() + recipe.outputCount());
        inputStack = inputStack.withCount(inputStack.count() - 1); // -> EMPTY once it hits 0
        storedExperience += recipe.experience();
    }

    /**
     * Removes {@code count} items from the output slot and returns the XP that comes with them -
     * proportional to how much of the currently-stored XP this take represents, so partial
     * collection never loses or invents fractional XP (see the class docs / PlayerSurvival's own
     * "store fractional XP safely" requirement). Taking everything grants everything remaining
     * and clears any float dust.
     */
    public float takeOutput(int count) {
        if (count <= 0 || outputStack.isEmpty() || count > outputStack.count()) return 0f;
        boolean takingAll = count == outputStack.count();
        float granted = takingAll ? storedExperience : storedExperience * count / outputStack.count();
        storedExperience -= granted;
        outputStack = outputStack.withCount(outputStack.count() - count);
        if (outputStack.isEmpty()) storedExperience = 0f;
        return granted;
    }

    /** Directly replaces the output stack - used only by the menu's "put items back" no-op path
     *  and by save/load restoration; the player can never place a new item INTO this slot via
     *  the GUI (see CookingMenu - output only ever shrinks through takeOutput). */
    void restoreOutputStack(ItemStack stack) {
        outputStack = stack;
    }

    /** Restores exact saved progress/XP - only ever called right after construction with values
     *  a save file already wrote from a valid in-game state (see CookingStationManager/WorldIO). */
    void restoreProgress(int burnTimeRemainingTicks, int totalBurnTimeTicks, int cookProgressTicks, float storedExperience) {
        this.burnTimeRemainingTicks = Math.max(0, burnTimeRemainingTicks);
        this.totalBurnTimeTicks = Math.max(0, totalBurnTimeTicks);
        this.cookProgressTicks = Math.max(0, cookProgressTicks);
        this.storedExperience = Math.max(0f, storedExperience);
    }

    /** Every non-empty stack currently held (input, fuel, output, in that order) - used when the
     *  block breaks (its contents must never simply vanish) and by tests. */
    public java.util.List<ItemStack> contents() {
        java.util.List<ItemStack> list = new java.util.ArrayList<>(3);
        if (!inputStack.isEmpty()) list.add(inputStack);
        if (!fuelStack.isEmpty()) list.add(fuelStack);
        if (!outputStack.isEmpty()) list.add(outputStack);
        return list;
    }
}
