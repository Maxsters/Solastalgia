package com.maxsters.coldspawncontrol.registry;

import java.util.List;
import java.util.Random;

/**
 * Configuration for forget sounds with their durations.
 * To add a new forget sound:
 * 1. Add .ogg file to
 * resources/assets/solastalgia/sounds/forgetting_sounds/
 * 2. Add entry to sounds.json under the "forget" sound event
 * 3. Add entry to SOUNDS list below with duration in seconds
 */
public final class ForgetSoundConfig {

    private static final Random RANDOM = new Random();

    /**
     * List of available forget sounds with their durations in seconds.
     * Each entry represents a sound variant that can play randomly.
     */
    private static final List<SoundEntry> SOUNDS = List.of(
            new SoundEntry("forget1", 26) // FORGET1.ogg = 26 seconds
    // Add more sounds here as needed:
    // new SoundEntry("forget2", 30),
    // new SoundEntry("forget3", 20),
    );

    /**
     * Get a random sound entry from the available sounds.
     * If only one sound exists, returns that sound.
     * 
     * @return A randomly selected SoundEntry
     */
    public static SoundEntry getRandomSound() {
        if (SOUNDS.size() == 1) {
            return SOUNDS.get(0);
        }
        return SOUNDS.get(RANDOM.nextInt(SOUNDS.size()));
    }

    /**
     * Calculate the effect duration in ticks based on sound duration.
     * Formula: (soundDurationSeconds - 5) * 20 ticks
     * Minimum duration is 100 ticks (5 seconds).
     * 
     * @param soundEntry The sound entry to calculate duration for
     * @return Duration in ticks
     */
    public static int getEffectDurationTicks(SoundEntry soundEntry) {
        int durationSeconds = Math.max(5, soundEntry.durationSeconds() - 5);
        return durationSeconds * 20;
    }

    /**
     * Represents a forget sound with its duration.
     * 
     * @param name            Sound name (matches the file name without extension,
     *                        lowercase)
     * @param durationSeconds Total duration of the sound in seconds
     */
    public record SoundEntry(String name, int durationSeconds) {
    }

    private ForgetSoundConfig() {
    }
}
