package com.example.audio;

import lombok.extern.slf4j.Slf4j;
import org.joml.Vector3f;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;

/**
 * Owns the OpenAL device/context lifecycle, a small fixed pool of one-shot sources, one
 * dedicated looping source (rain - the only genuinely continuous ambient sound this milestone
 * has; everything else, including cave ambience, is periodic one-shots - see GameAudio), the
 * listener, and category/master volume. Initialized once from {@code Game.init()}, destroyed
 * once from {@code Game.cleanup()} - never per-sound (Phase 0/13's explicit constraint).
 *
 * If no audio device exists or OpenAL fails to initialize, {@link #available} stays false and
 * every play/update call below becomes a silent no-op - the game must run identically otherwise
 * (Phase 2's "fail gracefully... do not crash because one optional sound is missing").
 */
@Slf4j
public class AudioEngine {
    private static final int SOURCE_POOL_SIZE = 16;
    /** OpenAL gain is only meaningful in roughly [0,1] for this project's purposes - not a hard
     *  backend limit, just this project's own sane ceiling (Phase 2 "do not use a volume value
     *  outside the valid range expected by the backend" combined with "avoid unexpected high
     *  volume" from Phase 15's safe-defaults requirement). */
    private static final float MAX_GAIN = 1f;

    private long device = 0;
    private long context = 0;
    private boolean available = false;

    private final int[] sourcePool = new int[SOURCE_POOL_SIZE];
    private int nextSourceIndex = 0;
    /** Several overlapping loop layers (see {@link #startRainLoop}) - one short clip looping on
     *  its own leaves an audible gap/dip at every seam (encoder padding + the clip's own quiet
     *  tail), which sounded intermittent. */
    private static final int RAIN_LAYERS = 3;
    private final int[] rainSources = new int[RAIN_LAYERS];
    private boolean rainLoopActive = false;

    private final Random random = new Random();

    private float masterVolume = 1f;
    private boolean muted = false;
    private final Map<AudioCategory, Float> categoryVolume = new EnumMap<>(AudioCategory.class);

    public AudioEngine() {
        for (AudioCategory c : AudioCategory.values()) categoryVolume.put(c, 1f);
    }

    public boolean isAvailable() {
        return available;
    }

    public void init() {
        try {
            device = alcOpenDevice((java.nio.ByteBuffer) null);
            if (device == 0) {
                log.warn("No OpenAL-capable audio device found - continuing without sound");
                return;
            }
            ALCCapabilities deviceCaps = ALC.createCapabilities(device);
            context = alcCreateContext(device, (IntBuffer) null);
            if (context == 0) {
                log.warn("Failed to create OpenAL context - continuing without sound");
                alcCloseDevice(device);
                device = 0;
                return;
            }
            alcMakeContextCurrent(context);
            AL.createCapabilities(deviceCaps);

            for (int i = 0; i < SOURCE_POOL_SIZE; i++) sourcePool[i] = alGenSources();
            for (int i = 0; i < RAIN_LAYERS; i++) {
                rainSources[i] = alGenSources();
                alSourcei(rainSources[i], AL_LOOPING, AL_TRUE);
                alSourcei(rainSources[i], AL_SOURCE_RELATIVE, AL_TRUE);
            }

            available = true;
            log.info("Audio initialized: {} one-shot sources + 1 rain loop source", SOURCE_POOL_SIZE);
        } catch (Throwable e) {
            // Broad catch deliberately: a missing native OpenAL library throws
            // UnsatisfiedLinkError (not Exception), which must also degrade gracefully rather
            // than crash the whole game over an optional sound backend.
            log.warn("Audio initialization failed - continuing without sound: {}", e.toString());
            available = false;
        }
    }

    public void setListener(Vector3f position, Vector3f front, Vector3f up) {
        if (!available) return;
        alListener3f(AL_POSITION, position.x, position.y, position.z);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer orientation = stack.mallocFloat(6);
            orientation.put(front.x).put(front.y).put(front.z).put(up.x).put(up.y).put(up.z).flip();
            alListenerfv(AL_ORIENTATION, orientation);
        }
    }

    public void setMasterVolume(float v) {
        masterVolume = clamp01(v);
    }

    public void setCategoryVolume(AudioCategory category, float v) {
        categoryVolume.put(category, clamp01(v));
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    /** effectiveGain = masterVolume * categoryVolume * soundBaseVolume, clamped - see
     *  SoundGroup.baseVolume and Phase 5's documented formula. */
    private float effectiveGain(AudioCategory category, float baseVolume) {
        float g = masterVolume * categoryVolume.getOrDefault(category, 1f) * baseVolume;
        return Math.max(0f, Math.min(MAX_GAIN, g));
    }

    /** Plays one random variation from {@code group} once. {@code worldPosition} null means a
     *  non-positional sound relative to the listener (UI-style; also used here for thunder,
     *  which has no meaningful strike location - see GameAudio). */
    public void playOneShot(SoundGroup group, SoundBuffer[] variations, Vector3f worldPosition) {
        playOneShot(group, variations, worldPosition, 1f, 1f);
    }

    /** Same as {@link #playOneShot(SoundGroup, SoundBuffer[], Vector3f)}, but with extra
     *  multipliers applied on top of the group's own randomized pitch/base volume - used for
     *  e.g. placement's lower "thud" pitch and sneaking's quieter footsteps (see GameAudio),
     *  matching Phase 5's effectiveGain = master * category * sound * event formula (eventVolume
     *  is this gainMultiplier). */
    public void playOneShot(SoundGroup group, SoundBuffer[] variations, Vector3f worldPosition,
                             float pitchMultiplier, float gainMultiplier) {
        if (!available || muted || group == null || variations.length == 0) return;
        SoundBuffer chosen = variations[random.nextInt(variations.length)];
        int source = allocateSource();
        if (source == 0) return;

        if (log.isDebugEnabled()) {
            log.debug("play {} at {}", group.id(), group.positional() && worldPosition != null ? worldPosition : "listener");
        }
        alSourcei(source, AL_BUFFER, chosen.getBufferId());
        alSourcef(source, AL_GAIN, effectiveGain(group.category(), group.baseVolume() * clamp01(gainMultiplier)));
        float pitch = (group.pitchMin() + random.nextFloat() * (group.pitchMax() - group.pitchMin())) * pitchMultiplier;
        alSourcef(source, AL_PITCH, Math.max(0.1f, pitch));

        if (group.positional() && worldPosition != null) {
            alSourcei(source, AL_SOURCE_RELATIVE, AL_FALSE);
            alSource3f(source, AL_POSITION, worldPosition.x, worldPosition.y, worldPosition.z);
            alSourcef(source, AL_REFERENCE_DISTANCE, 2f);
            alSourcef(source, AL_MAX_DISTANCE, Math.max(4f, group.maxDistance()));
            alSourcef(source, AL_ROLLOFF_FACTOR, 1f);
        } else {
            alSourcei(source, AL_SOURCE_RELATIVE, AL_TRUE);
            alSource3f(source, AL_POSITION, 0f, 0f, 0f);
        }
        alSourcePlay(source);
    }

    /** Round-robin over the fixed pool; a source still AL_PLAYING gets stopped and reused rather
     *  than growing the pool - Phase 13's "limit simultaneous sound effects... avoid creating
     *  unlimited sources". With only short one-shot SFX and a 16-source pool this is not
     *  expected to audibly steal anything in normal play; not claimed as measured. */
    private int allocateSource() {
        for (int i = 0; i < SOURCE_POOL_SIZE; i++) {
            int idx = (nextSourceIndex + i) % SOURCE_POOL_SIZE;
            if (alGetSourcei(sourcePool[idx], AL_SOURCE_STATE) != AL_PLAYING) {
                nextSourceIndex = (idx + 1) % SOURCE_POOL_SIZE;
                return sourcePool[idx];
            }
        }
        int idx = nextSourceIndex;
        nextSourceIndex = (nextSourceIndex + 1) % SOURCE_POOL_SIZE;
        alSourceStop(sourcePool[idx]);
        return sourcePool[idx];
    }

    /** Starts one looping layer per entry (up to RAIN_LAYERS), each begun at an evenly spaced
     *  offset into its clip, so the layers' loop seams never line up and the sum is a steady bed. */
    public void startRainLoop(SoundBuffer[] buffers) {
        if (!available || buffers == null || buffers.length == 0 || rainLoopActive) return;
        for (int i = 0; i < RAIN_LAYERS; i++) {
            SoundBuffer b = buffers[i % buffers.length];
            int id = b.getBufferId();
            alSourcei(rainSources[i], AL_BUFFER, id);
            float seconds = bufferSeconds(id);
            alSourcePlay(rainSources[i]);
            if (seconds > 0.5f) alSourcef(rainSources[i], org.lwjgl.openal.AL11.AL_SEC_OFFSET, seconds * i / RAIN_LAYERS);
        }
        rainLoopActive = true;
        log.debug("rain loop started");
    }

    private static float bufferSeconds(int bufferId) {
        int freq = alGetBufferi(bufferId, AL_FREQUENCY);
        int bytes = alGetBufferi(bufferId, AL_SIZE);
        int bits = alGetBufferi(bufferId, AL_BITS);
        int ch = alGetBufferi(bufferId, AL_CHANNELS);
        if (freq <= 0 || bits <= 0 || ch <= 0) return 0f;
        return bytes / (float) (freq * ch * (bits / 8));
    }

    public void stopRainLoop() {
        if (!available || !rainLoopActive) return;
        for (int src : rainSources) alSourceStop(src);
        rainLoopActive = false;
        log.debug("rain loop stopped");
    }

    public void setRainVolume(float baseVolume) {
        if (!available) return;
        float gain = effectiveGain(AudioCategory.WEATHER, baseVolume) * 0.7f; // 3 layers sum louder than one
        for (int src : rainSources) alSourcef(src, AL_GAIN, gain);
    }

    public boolean isRainLoopActive() {
        return rainLoopActive;
    }

    public void cleanup() {
        if (!available) {
            if (device != 0) {
                alcMakeContextCurrent(0);
                if (context != 0) alcDestroyContext(context);
                alcCloseDevice(device);
            }
            device = 0;
            context = 0;
            return;
        }
        for (int s : sourcePool) {
            alSourceStop(s);
            alDeleteSources(s);
        }
        for (int src : rainSources) {
            alSourceStop(src);
            alDeleteSources(src);
        }
        alcMakeContextCurrent(0);
        alcDestroyContext(context);
        alcCloseDevice(device);
        available = false;
        device = 0;
        context = 0;
        log.info("Audio shut down");
    }
}
