package com.example.survival;

import com.example.inventory.PlayerArmor;
import com.example.physics.Player;
import com.example.world.BlockType;
import com.example.world.Difficulty;
import com.example.world.FoodProperties;
import com.example.world.FoodRegistry;
import lombok.Getter;
import org.joml.Vector3f;

/**
 * The one class {@code Game} drives each frame for the player's health/hunger simulation -
 * mirrors {@code GameAudio.update(dt, player, world, weather)}'s exact shape and its own
 * external-state-tracking idiom (a remembered last position, compared each call - see
 * {@code GameAudio.updateFootsteps}), rather than teaching {@code Player} anything about hunger.
 * Owns one {@link HealthComponent} and one {@link HungerComponent}; every exhaustion-adding rule
 * this milestone's request specifies (sprinting/jumping/swimming/block-breaking/damage/regen)
 * lives here as the one place translating real gameplay events into exhaustion amounts - the
 * components themselves stay reusable/ignorant of what caused a given number.
 */
public class PlayerSurvival {
    // Exhaustion rates - real vanilla's own published values (the request's own numbers).
    private static final float SPRINT_EXHAUSTION_PER_BLOCK = 0.1f;
    private static final float SWIM_EXHAUSTION_PER_BLOCK = 0.01f;
    private static final float JUMP_EXHAUSTION_PER_JUMP = 0.05f;
    private static final float SPRINT_JUMP_EXHAUSTION_PER_JUMP = 0.2f;
    private static final float BLOCK_BREAK_EXHAUSTION = 0.005f;
    private static final float DAMAGE_EXHAUSTION_PER_HIT = 0.1f;
    private static final float REGEN_EXHAUSTION_PER_HP = 6f;

    // Adapted timings (this project's own choice, not independently verified against vanilla's
    // real tick-random mechanics - documented per this file's own honesty convention).
    private static final float REGEN_INTERVAL_SECONDS = 4f; // 80 ticks
    private static final float FAST_REGEN_INTERVAL_SECONDS = 0.5f; // 10 ticks
    private static final float PEACEFUL_REGEN_SECONDS = 1f;
    public static final float MAX_AIR_TICKS = 300f;
    private static final float DROWNING_DAMAGE = 2f;
    /** Vanilla-adjacent (80 game ticks at its real 20 ticks/second), not independently verified. */
    private static final float STARVATION_INTERVAL_SECONDS = 4f;
    private static final float STARVATION_DAMAGE = 1f;
    /** Real vanilla's own fall-damage rule: 1 HP per block fallen beyond this. */
    private static final float FALL_DAMAGE_SAFE_DISTANCE = 3f;
    /** Vanilla Entity.checkBelowWorld: below (world minimum height - 64) every hurt cooldown deals 4 damage (LivingEntity.onBelowWorld). */
    public static final float VOID_Y = -64f;
    private static final float VOID_DAMAGE = 4f;

    @Getter
    private final HealthComponent health = new HealthComponent();
    @Getter
    private final HungerComponent hunger = new HungerComponent();
    @Getter
    private Difficulty difficulty;
    @Getter
    private final PlayerArmor armor = new PlayerArmor();
    /** Total XP earned so far - see {@link #addExperience}. This project has no leveling curve
     *  or XP-bar HUD (no XP system existed before the cooking milestone); this is the "clean
     *  integration point" that request explicitly asks for in place of building one, so cooking
     *  XP has somewhere real (and testable) to go. */
    @Getter
    private float experience = 0f;
    /** Total XP collected this life, shown as "Score" on the death screen (vanilla: includes XP already spent). */
    @Getter
    private float score = 0f;
    /** Breath in ticks (see {@link #tickAir}); full = {@link #MAX_AIR_TICKS}. */
    @Getter
    private float airTicks = MAX_AIR_TICKS;

    private final Vector3f lastPosition = new Vector3f();
    private boolean positionInitialized = false;
    /** NaN = not currently tracking an in-progress fall. */
    private float fallStartY = Float.NaN;
    private float regenTimer = 0f;
    private float starvationTimer = 0f;

    public PlayerSurvival(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }

    /** Fresh health/hunger/exhaustion and fall/regen/starvation timers, and re-anchors position
     *  tracking to wherever the player now is - called on new-world/load-world, the same moment
     *  {@code Game.resetGameplayState} resets everything else, so a freshly (re)spawned player
     *  never inherits a stale in-progress fall or a hunger state from a previous world. */
    public void reset(Vector3f position) {
        health.reset();
        hunger.reset();
        lastPosition.set(position);
        positionInitialized = true;
        fallStartY = Float.NaN;
        regenTimer = 0f;
        starvationTimer = 0f;
    }

    /** Restores exact saved health/hunger/saturation/exhaustion (see {@link HungerComponent#forceState}) -
     *  only ever called right after construction, with values a save file already wrote from a
     *  previously-valid in-game state (see WorldIO). {@code currentHealth <= 0} intentionally
     *  loads as dead rather than being special-cased back to alive - a save should never have
     *  been written mid-death anyway (Game freezes further play once dead), but if one somehow
     *  was, resurrecting silently on load would be more surprising than honoring it. */
    public void loadState(float currentHealth, float foodLevel, float saturation, float exhaustion) {
        health.setHealth(currentHealth);
        hunger.forceState(foodLevel, saturation, exhaustion);
    }

    /** Advances every timer/rule this class owns by one frame's worth of real time. Does nothing
     *  to hunger/regen/starvation/fall tracking while dead (frozen, matching "prevent additional
     *  normal damage" / a clear pre-respawn state) but still ticks the hurt cooldown down, which
     *  is harmless once dead and keeps {@link HealthComponent#tick} unconditional like every
     *  other tick-based system in this project. */
    public void update(float dt, Player player) {
        health.tick(dt);
        if (!positionInitialized) {
            lastPosition.set(player.position);
            positionInitialized = true;
            return;
        }
        if (health.isDead()) {
            lastPosition.set(player.position);
            return;
        }

        float dx = player.position.x - lastPosition.x;
        float dz = player.position.z - lastPosition.z;
        lastPosition.set(player.position);
        float horizontalDistance = (float) Math.sqrt(dx * dx + dz * dz);

        if (!player.isFlying()) {
            if (player.isSwimming()) {
                hunger.addExhaustion(horizontalDistance * SWIM_EXHAUSTION_PER_BLOCK);
            } else if (player.isSprinting()) {
                hunger.addExhaustion(horizontalDistance * SPRINT_EXHAUSTION_PER_BLOCK);
            }
            if (player.isJustJumped()) {
                hunger.addExhaustion(player.isSprinting() ? SPRINT_JUMP_EXHAUSTION_PER_JUMP : JUMP_EXHAUSTION_PER_JUMP);
            }
        }

        updateFallTracking(player);
        tickAir(dt, player);
        if (player.position.y < VOID_Y) damage(VOID_DAMAGE, DamageSource.VOID);
        if (difficulty == Difficulty.PEACEFUL) {
            tickPeaceful(dt);
        } else {
            tickRegeneration(dt);
            tickStarvation(dt);
        }
    }

    /** Vanilla breath: 300 ticks of air; submerged it drains 1 per tick, at -20 the player takes 2 HP and air
     *  resets to 0 (so 2 HP per second while drowning); out of water it refills 4 per tick. */
    private void tickAir(float dt, Player player) {
        if (player.isHeadSubmerged() && !player.isFlying()) {
            airTicks -= dt * 20f;
            if (airTicks <= -20f) {
                airTicks = 0f;
                damage(DROWNING_DAMAGE, DamageSource.DROWNING);
            }
        } else {
            airTicks = Math.min(MAX_AIR_TICKS, Math.max(0f, airTicks) + dt * 20f * 4f);
        }
    }

    /** Vanilla Peaceful: health regenerates on its own and the hunger bar never drains. */
    private void tickPeaceful(float dt) {
        hunger.forceState(HungerComponent.MAX_FOOD, hunger.getSaturation(), 0f);
        if (health.getCurrentHealth() >= health.getMaxHealth()) {
            regenTimer = 0f;
            return;
        }
        regenTimer += dt;
        if (regenTimer >= PEACEFUL_REGEN_SECONDS) {
            regenTimer -= PEACEFUL_REGEN_SECONDS;
            health.heal(1f);
        }
    }

    /** Records the highest point reached during an in-progress (non-flying, non-swimming) fall,
     *  and applies fall damage the moment the player lands - real vanilla's own rule (1 HP per
     *  block beyond a 3-block safe distance). Flying or swimming cancels any tracked fall (both
     *  are vanilla-safe ways to descend). */
    private void updateFallTracking(Player player) {
        boolean grounded = player.isOnGround() && !player.isFlying() && !player.isSwimming();
        if (grounded) {
            if (!Float.isNaN(fallStartY)) {
                float fallDistance = fallStartY - player.position.y;
                fallStartY = Float.NaN;
                if (fallDistance > FALL_DAMAGE_SAFE_DISTANCE) {
                    damage((float) Math.ceil(fallDistance - FALL_DAMAGE_SAFE_DISTANCE), DamageSource.FALL); // vanilla: ceil(distance - 3)
                }
            }
        } else if (player.isFlying() || player.isSwimming()) {
            fallStartY = Float.NaN;
        } else {
            fallStartY = Float.isNaN(fallStartY) ? player.position.y : Math.max(fallStartY, player.position.y);
        }
    }

    /** Vanilla natural regeneration: food 20 with saturation > 0 heals {@code min(saturation, 6) / 6} HP every 10 ticks
     *  (costing 6 exhaustion per HP healed); otherwise food >= 18 heals 1 HP every 80 ticks for 6 exhaustion. */
    private void tickRegeneration(float dt) {
        if (!hunger.regenerationAllowed() || health.getCurrentHealth() >= health.getMaxHealth()) {
            regenTimer = 0f;
            return;
        }
        boolean fast = hunger.getFoodLevel() >= HungerComponent.MAX_FOOD && hunger.getSaturation() > 0f;
        float interval = fast ? FAST_REGEN_INTERVAL_SECONDS : REGEN_INTERVAL_SECONDS;
        regenTimer += dt;
        if (regenTimer >= interval) {
            regenTimer -= interval;
            float heal = fast ? Math.min(hunger.getSaturation(), 6f) / 6f : 1f;
            health.heal(heal);
            hunger.addExhaustion(REGEN_EXHAUSTION_PER_HP * heal);
        }
    }

    private void tickStarvation(float dt) {
        if (!hunger.isStarving()) {
            starvationTimer = 0f;
            return;
        }
        starvationTimer += dt;
        if (starvationTimer >= STARVATION_INTERVAL_SECONDS) {
            starvationTimer -= STARVATION_INTERVAL_SECONDS;
            float floor = switch (difficulty) {
                case EASY -> 10f;
                case NORMAL -> 1f;
                case HARD -> 0f;
                case PEACEFUL -> health.getMaxHealth();
            };
            if (health.getCurrentHealth() > floor) {
                damage(STARVATION_DAMAGE, DamageSource.STARVATION);
                if (health.getCurrentHealth() < floor) health.setHealth(floor);
            }
        }
    }

    /** The one damage entry point {@code Game} (and, later, mobs attacking the player) calls -
     *  wraps {@link HealthComponent#damage} with this player's own "receiving damage costs
     *  exhaustion" rule, which {@link HealthComponent} itself deliberately doesn't know about
     *  (a mob's own health has no hunger to feed). */
    public boolean damage(float amount, DamageSource source) {
        boolean reducible = !source.bypassesArmor();
        float taken = reducible
                ? com.example.combat.ArmorRules.reduce(amount, armor.totalDefense(), armor.totalToughness())
                : amount;
        boolean applied = health.damage(taken, source);
        if (applied) {
            hunger.addExhaustion(DAMAGE_EXHAUSTION_PER_HIT);
            if (reducible && armor.totalDefense() > 0) armor.wear(amount);
        }
        return applied;
    }

    /** Call once per block actually broken (see {@code Game.onBlockBroken}). A no-op while dead. */
    public void onBlockBroken() {
        if (health.isAlive()) hunger.addExhaustion(BLOCK_BREAK_EXHAUSTION);
    }

    /** Eats one unit of {@code foodType}. False (nothing changes) if it isn't a real food item or
     *  {@link HungerComponent#eat} itself refuses (already full, non-overeat food) - the caller
     *  (Game) only removes the inventory item when this returns true. */
    public boolean eat(BlockType foodType) {
        FoodProperties food = FoodRegistry.propertiesFor(foodType);
        return food != null && hunger.eat(food);
    }

    /** Adds XP (e.g. from taking cooked/smelted output - see {@code CookingMenu}) - a plain
     *  running float total, safe for the fractional amounts a partial output take produces. */
    public void addExperience(float amount) {
        if (amount > 0f) {
            experience += amount;
            score += amount;
        }
    }

    /** Experience level shown above the XP bar (vanilla formula, see {@link Experience}). */
    public int getLevel() {
        return Experience.levelFor(experience);
    }

    /** 0..1 fill of the XP bar within the current level. */
    public float getLevelProgress() {
        return Experience.progress(experience);
    }

    /** Restores saved XP (world load); the score is not persisted across sessions of the same life. */
    public void loadExperience(float xp, float score) {
        this.experience = Math.max(0f, xp);
        this.score = Math.max(0f, score);
    }

    /** Vanilla death: the XP dropped is {@code min(7 x level, 100)} and the player's XP resets to zero. */
    public float takeDeathExperience() {
        float dropped = Experience.deathDrop(getLevel());
        experience = 0f;
        return dropped;
    }
}
