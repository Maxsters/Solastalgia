package com.maxsters.coldspawncontrol.registry;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Server-wide persistent tracker for the "Torn Field Note" ominous book.
 * Once any player on the server has acquired the book, all further
 * generation (loot tables + chunk spawning) is halted for the world.
 *
 * Stored in the Overworld's data storage as "solastalgia_book_tracker".
 */
public class OminousBookTracker extends SavedData {

    private static final String DATA_NAME = "solastalgia_book_tracker";

    private boolean bookAcquired = false;

    public OminousBookTracker() {
    }

    public static OminousBookTracker load(CompoundTag tag) {
        OminousBookTracker tracker = new OminousBookTracker();
        tracker.bookAcquired = tag.getBoolean("bookAcquired");
        return tracker;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("bookAcquired", bookAcquired);
        return tag;
    }

    /**
     * Returns true if any player has already acquired the book on this server.
     */
    public boolean isBookAcquired() {
        return bookAcquired;
    }

    /**
     * Marks the book as acquired server-wide. Stops all future generation.
     */
    public void markBookAcquired() {
        if (!bookAcquired) {
            bookAcquired = true;
            setDirty();
            ColdSpawnControl.LOGGER.info("Torn Field Note acquired! Halting further book generation.");
        }
    }

    /**
     * Resets the acquired state (used by debug command).
     */
    public void resetBookAcquired() {
        bookAcquired = false;
        setDirty();
    }

    /**
     * Gets the tracker from the Overworld's data storage.
     */
    @SuppressWarnings("null")
    public static OminousBookTracker get(ServerLevel level) {
        // Always use the Overworld's data storage so it's shared across dimensions
        ServerLevel overworld = level.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null)
            overworld = level;
        return overworld.getDataStorage().computeIfAbsent(
                OminousBookTracker::load,
                OminousBookTracker::new,
                DATA_NAME);
    }
}
