package com.maxsters.coldspawncontrol.registry;

import java.util.Random;

/**
 * Curated list of names for the amnesia nametag shuffle feature.
 * 
 * Names are a mix of:
 * - Generic pet/human names
 * - Name from the journal narrative (Mira)
 * - Cryptic but non-cliche names (no "???" or redacted text)
 */
public final class AmnesiaNameList {

    private AmnesiaNameList() {
    }

    private static final Random RANDOM = new Random();

    /**
     * 100 names for nametagged entities.
     * Mix of generic, journal-referenced, and cryptic names.
     */
    private static final String[] NAMES = {
            // Journal name (from the narrative)
            "Mira",

            // Generic pet names
            "Buddy", "Max", "Charlie", "Cooper", "Rocky", "Bear", "Duke", "Tucker",
            "Luna", "Bella", "Sadie", "Molly", "Maggie", "Sophie", "Chloe", "Daisy",
            "Bailey", "Ginger", "Pepper", "Scout", "Rusty", "Murphy", "Jack", "Toby",
            "Sam", "Oscar", "Leo", "Milo", "Bruno", "Finn", "Zeus", "Henry",

            // Generic human names
            "Alex", "Jordan", "Taylor", "Casey", "Riley", "Morgan", "Quinn", "Drew",
            "Kai", "Avery", "Ash", "Sage", "River", "Sky", "Phoenix", "Reed",
            "Blake", "Cameron", "Devon", "Ellis", "Finley", "Harper", "Hayden", "Jesse",

            // Cryptic but grounded names (no supernatural implications)
            "The Old One", "Wanderer", "Keeper", "Stranger", "Traveler", "Survivor",
            "Found One", "Silent", "Watcher", "Listener", "Seeker", "Helper",
            "Companion", "Friend", "Partner", "Guide", "Follower", "Stray",
            "Lost One", "Forgotten", "Remembered", "Familiar", "Unknown", "Unnamed",

            // Simple descriptive names
            "Grey", "Brown", "Patches", "Spots", "Stripes", "Fluffy", "Small One",
            "Big One", "Quick", "Quiet", "Loud", "Gentle", "Brave", "Shy",

            // Names that suggest past ownership
            "Someone's", "Theirs", "Ours", "Mine", "Yours",
            "First", "Second", "Third", "Last"
    };

    /**
     * Gets a random name from the list.
     */
    public static String getRandomName() {
        return NAMES[RANDOM.nextInt(NAMES.length)];
    }

    /**
     * Gets a specific name by index (for deterministic testing).
     */
    public static String getName(int index) {
        return NAMES[index % NAMES.length];
    }

    /**
     * Gets the total number of available names.
     */
    public static int getNameCount() {
        return NAMES.length;
    }
}
