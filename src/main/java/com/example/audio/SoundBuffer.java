package com.example.audio;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.libc.LibCStdlib;

import java.io.File;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;

import static org.lwjgl.openal.AL10.*;

/**
 * One decoded OGG file uploaded to an OpenAL buffer. Every sound this project uses is a short
 * effect/ambience clip (at most a few seconds - see sounds/ambient/cave, sounds/ambient/weather),
 * so this always fully decodes and uploads up front rather than streaming - streaming would only
 * matter for long music tracks, which this milestone doesn't have (Phase 3's own "determine
 * whether streaming is required based on the actual assets" - it isn't, here).
 *
 * Mirrors {@code Colormap.load}'s graceful-fallback pattern: a missing/corrupt/unsupported file
 * never crashes the game, {@link #load} just returns null and the caller treats that ID as
 * silently unavailable (see {@code SoundRegistry}).
 */
@Slf4j
public class SoundBuffer {
    @Getter
    private final int bufferId;

    private SoundBuffer(int bufferId) {
        this.bufferId = bufferId;
    }

    public static SoundBuffer load(File file) {
        if (!file.isFile()) {
            log.warn("Sound file not found: {}", file);
            return null;
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channelsBuf = stack.mallocInt(1);
            IntBuffer sampleRateBuf = stack.mallocInt(1);
            ShortBuffer pcm = STBVorbis.stb_vorbis_decode_filename(file.getAbsolutePath(), channelsBuf, sampleRateBuf);
            if (pcm == null) {
                log.warn("Failed to decode OGG file (unsupported/corrupt): {}", file);
                return null;
            }
            try {
                int channels = channelsBuf.get(0);
                int sampleRate = sampleRateBuf.get(0);
                int format = channels == 1 ? AL_FORMAT_MONO16 : AL_FORMAT_STEREO16;

                int bufferId = alGenBuffers();
                alBufferData(bufferId, format, pcm, sampleRate);
                return new SoundBuffer(bufferId);
            } finally {
                LibCStdlib.free(pcm);
            }
        } catch (Exception e) {
            log.warn("Error loading sound file {}: {}", file, e.getMessage());
            return null;
        }
    }

    public void destroy() {
        alDeleteBuffers(bufferId);
    }
}
