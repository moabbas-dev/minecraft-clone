package com.example.audio;

import com.example.physics.Player;
import com.example.world.BlockType;
import com.example.world.Weather;
import com.example.world.World;
import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3f;

import java.util.Random;

/**
 * The one class {@code Game} talks to for audio - owns {@link AudioEngine} and
 * {@link SoundRegistry}, and turns real game state (player movement, block break/place,
 * weather) into sound events. Kept deliberately outside {@code Game} itself so audio can be
 * unit-tested (the pure trigger-decision logic below) independently of the real OpenAL device
 * {@code Game} can't run headless anyway - same split TESTING.md already documents for
 * rendering vs. logic.
 */
@Slf4j
public class GameAudio {
    /** Approximate stride length in blocks between footsteps - not a verified vanilla constant
     *  (none was found/confirmed for this project's own scale), tuned by feel against this
     *  project's own WALK_SPEED. Distance-based (not a fixed timer) so it naturally speeds up
     *  with sprint and slows with sneaking, without special-casing either (Phase 8.2). */
    static final float STEP_DISTANCE = 1.15f;
    static final float SNEAK_STEP_DISTANCE = STEP_DISTANCE * 1.6f;
    static final float SNEAK_FOOTSTEP_GAIN = 0.5f;
    static final float PLACEMENT_PITCH_MULTIPLIER = 0.8f;

    private static final float CAVE_AMBIENT_MIN_INTERVAL = 12f;
    private static final float CAVE_AMBIENT_MAX_INTERVAL = 35f;
    /** SkyLight (0-15) at/under this at the player's own block is treated as "no clear path to
     *  open sky" - reused from LightEngine's already-computed BFS lighting, not a dedicated cave
     *  detector. Also fires in any other fully enclosed dark interior, and won't fire in a lit
     *  cave mouth - a documented approximation, not a verified "is in a cave" signal. */
    static final int CAVE_SKYLIGHT_THRESHOLD = 2;

    private final AudioEngine engine;
    private final SoundRegistry registry;
    private final Random random = new Random();

    private final Vector3f lastFootstepPos = new Vector3f();
    private boolean footstepPosInitialized = false;
    private float footstepDistance = 0f;
    private boolean prevOnGround = false;

    private float caveAmbientTimer = 0f;
    private float prevLightningFlash = 0f;

    public GameAudio() {
        this.engine = new AudioEngine();
        this.registry = new SoundRegistry();
    }

    public void init() {
        engine.init();
    }

    public boolean isAvailable() {
        return engine.isAvailable();
    }

    public void setMasterVolume(float v) {
        engine.setMasterVolume(v);
    }

    public void setCategoryVolume(AudioCategory category, float v) {
        engine.setCategoryVolume(category, v);
    }

    public void setMuted(boolean muted) {
        engine.setMuted(muted);
    }

    /** Called once per in-game frame. Syncs the listener, then drives footsteps, cave ambience,
     *  and weather (rain loop + thunder) from real state - see each private method's own docs
     *  for the exact trigger condition and why. */
    public void update(float dt, Player player, World world, Weather weather) {
        engine.setListener(player.eyePosition(), player.frontVector(), new Vector3f(0, 1, 0));
        updateFootsteps(dt, player, world);
        updateCaveAmbience(dt, player, world);
        updateWeather(weather);
    }

    private void updateFootsteps(float dt, Player player, World world) {
        Vector3f pos = player.position;
        if (!footstepPosInitialized) {
            lastFootstepPos.set(pos);
            footstepPosInitialized = true;
            prevOnGround = player.isOnGround();
            return;
        }
        float dx = pos.x - lastFootstepPos.x;
        float dz = pos.z - lastFootstepPos.z;
        lastFootstepPos.set(pos);

        boolean onGround = player.isOnGround();
        boolean grounded = onGround && !player.isFlying() && !player.isSwimming();
        // Landing edge (was airborne last frame, grounded now): plays immediately regardless of
        // accumulated distance. Without this there's a real dead gap right at touchdown - not a
        // logic bug in the distance accumulator itself (it resumes counting correctly on its
        // own), but it reads as "the sound disappeared" the instant you land, since the very
        // first post-landing footstep otherwise waits for a full fresh stride's worth of ground
        // travel before making any sound at all.
        boolean justLanded = isLandingEdge(onGround, prevOnGround, player.isFlying(), player.isSwimming());
        prevOnGround = onGround;

        // No footsteps airborne, flying, or swimming (Phase 8's explicit "should not trigger"
        // list) - sneaking is allowed but uses a longer, quieter step (below), not suppressed.
        if (!grounded) {
            footstepDistance = 0f;
            return;
        }

        footstepDistance += Math.sqrt(dx * dx + dz * dz);
        float threshold = footstepThreshold(player.isSneaking());
        if (!justLanded && !shouldTriggerFootstep(footstepDistance, threshold)) return;
        footstepDistance = 0f;

        BlockType under = blockUnderFeet(world, pos);
        SoundMaterial material = SoundMaterial.materialFor(under);
        if (material == null) return; // e.g. standing over air/water at a chunk edge - no crash, just no sound
        String id = "step." + material.name().toLowerCase();
        float gain = player.isSneaking() ? SNEAK_FOOTSTEP_GAIN : 1f;
        engine.playOneShot(registry.definitionOf(id), registry.buffersFor(id), pos, 1f, gain);
    }

    /** Pure decision logic below, package-private so it's directly testable without an
     *  AudioEngine/OpenAL context - same reasoning as Player.isDirectlyAboveGround. */

    static boolean shouldTriggerFootstep(float accumulatedDistance, float threshold) {
        return accumulatedDistance >= threshold;
    }

    static boolean isLandingEdge(boolean onGround, boolean prevOnGround, boolean flying, boolean swimming) {
        return onGround && !prevOnGround && !flying && !swimming;
    }

    static float footstepThreshold(boolean sneaking) {
        return sneaking ? SNEAK_STEP_DISTANCE : STEP_DISTANCE;
    }

    static BlockType blockUnderFeet(World world, Vector3f feetPos) {
        return world.getBlock((int) Math.floor(feetPos.x), (int) Math.floor(feetPos.y - 0.1f), (int) Math.floor(feetPos.z));
    }

    /** True the instant flash goes from decaying back up to a fresh strike value - see
     *  Weather.update's lightningFlash=1f assignment. */
    static boolean isLightningStrikeThisFrame(float currentFlash, float previousFlash) {
        return currentFlash > previousFlash;
    }

    static boolean isUnderground(int skyLightAtPlayer) {
        return skyLightAtPlayer <= CAVE_SKYLIGHT_THRESHOLD;
    }

    /** Shared by both the mining-hit tick (called once per swing while actively breaking a
     *  block, from Game's existing swingTimer-repeat trigger) and the final destruction sound -
     *  real vanilla reuses the same "dig" family for both, this project's own asset dump only
     *  has one dig/-family set per material anyway (no separate "break" set - see the audit). */
    public void playDig(BlockType type, Vector3f worldPos) {
        SoundMaterial material = SoundMaterial.materialFor(type);
        if (material == null) return;
        String id = "dig." + material.name().toLowerCase();
        engine.playOneShot(registry.definitionOf(id), registry.buffersFor(id), worldPos);
    }

    /** Only ever called after a placement has actually succeeded (see Game.handleInteraction) -
     *  never for a rejected/no-op placement attempt. Reuses the step family at a lower pitch
     *  (real vanilla's own actual convention), not a separate placement asset set. */
    public void playPlace(BlockType type, Vector3f worldPos) {
        SoundMaterial material = SoundMaterial.materialFor(type);
        if (material == null) return;
        String id = "step." + material.name().toLowerCase();
        engine.playOneShot(registry.definitionOf(id), registry.buffersFor(id), worldPos, PLACEMENT_PITCH_MULTIPLIER, 1f);
    }

    /** Plays a registered sound by id (positional when its group is and a position is given). Unknown ids and
     *  missing files are silent no-ops, like every other sound here. */
    public void play(String id, Vector3f worldPos) {
        engine.playOneShot(registry.definitionOf(id), registry.buffersFor(id), worldPos);
    }

    /** Same, with the sound's pitch multiplied (a baby animal's voice is 1.5 times as high). */
    public void play(String id, Vector3f worldPos, float pitchMultiplier) {
        engine.playOneShot(registry.definitionOf(id), registry.buffersFor(id), worldPos, pitchMultiplier, 1f);
    }

    /** Item collected into the inventory. Non-positional (it happens at the player). The caller
     *  invokes this at most once per update no matter how many stacks transferred, so a pile
     *  being collected in one frame is one sound, not a burst. */
    public void playPickup() {
        engine.playOneShot(registry.definitionOf("item.pickup"), registry.buffersFor("item.pickup"), null);
    }

    private void updateCaveAmbience(float dt, Player player, World world) {
        caveAmbientTimer -= dt;
        if (caveAmbientTimer > 0f) return;
        caveAmbientTimer = CAVE_AMBIENT_MIN_INTERVAL + random.nextFloat() * (CAVE_AMBIENT_MAX_INTERVAL - CAVE_AMBIENT_MIN_INTERVAL);

        int bx = (int) Math.floor(player.position.x);
        int by = (int) Math.floor(player.position.y);
        int bz = (int) Math.floor(player.position.z);
        if (!isUnderground(world.getSkyLight(bx, by, bz))) return; // outdoors/lit - skip this cycle

        // A small random offset near the player, not a real detected cave feature - gives the
        // cue some spatial character without claiming to know where the "cave" actually is.
        Vector3f pos = new Vector3f(player.position)
                .add((random.nextFloat() - 0.5f) * 12f, (random.nextFloat() - 0.5f) * 6f, (random.nextFloat() - 0.5f) * 12f);
        engine.playOneShot(registry.definitionOf("ambient.cave"), registry.buffersFor("ambient.cave"), pos);
    }

    private void updateWeather(Weather weather) {
        if (weather == null) return;

        if (weather.isRaining()) {
            if (!engine.isRainLoopActive()) {
                SoundBuffer[] variations = registry.buffersFor("ambient.rain");
                if (variations.length > 0) {
                    // Three distinct clips in a random order, layered by the engine.
                    SoundBuffer[] picks = variations.clone();
                    for (int i = picks.length - 1; i > 0; i--) {
                        int j = random.nextInt(i + 1);
                        SoundBuffer t = picks[i];
                        picks[i] = picks[j];
                        picks[j] = t;
                    }
                    engine.startRainLoop(picks);
                }
            }
            SoundGroup rainDef = registry.definitionOf("ambient.rain");
            engine.setRainVolume(rainDef.baseVolume() * weather.getStormIntensity());
        } else if (engine.isRainLoopActive()) {
            engine.stopRainLoop();
        }

        // lightningFlash is set to exactly 1f the instant a strike occurs, then only ever decays
        // (see Weather.update) - so a frame where it's HIGHER than the previous frame's value
        // means a strike just happened this frame. Pure observation of Weather's existing public
        // getter; Weather.java itself is untouched.
        float flash = weather.getLightningFlash();
        if (isLightningStrikeThisFrame(flash, prevLightningFlash)) {
            // Weather's model has no strike world-position, so thunder is non-positional
            // (documented Phase 12 fallback), not a fabricated location.
            engine.playOneShot(registry.definitionOf("weather.thunder"), registry.buffersFor("weather.thunder"), null);
        }
        prevLightningFlash = flash;
    }

    public void cleanup() {
        engine.cleanup();
        registry.destroy();
    }
}
