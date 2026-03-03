package com.maxsters.coldspawncontrol.util;

import com.momosoftworks.coldsweat.util.world.WorldHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Shared temperature cache for server-side temperature lookups.
 * Uses game ticks for expiry instead of System.currentTimeMillis().
 * Only accessed from the server thread — no concurrency needed.
 *
 * Caches temperature per 16×16×16 block region (chunk section).
 * Temperature within a region is nearly identical, so one lookup
 * per region is sufficient.
 *
 * Uses a single HashMap with a lightweight record value instead of
 * dual HashMaps — halves hash computations and reduces memory.
 */
public final class TemperatureCache {

    private record CacheEntry(double temperature, long tick) {
    }

    private record CacheKey(net.minecraft.resources.ResourceLocation dimension, long regionPos) {
    }

    private static final HashMap<CacheKey, CacheEntry> CACHE = new HashMap<>();

    /** Cache entries expire after 2400 ticks (120 seconds at 20 TPS). */
    private static final long EXPIRY_TICKS = 2400L;

    /** Cleanup stale entries every 6000 ticks (~5 minutes). */
    private static final long CLEANUP_INTERVAL = 6000L;

    private static long lastCleanupTick = 0;

    private TemperatureCache() {
    }

    /**
     * Returns a region key for the 16×16×16 block section containing {@code pos}.
     * Including Y is critical because temperature varies significantly between
     * the surface and deep caves.
     */
    private static long regionPosOffset(BlockPos pos) {
        int rx = pos.getX() >> 4;
        int ry = pos.getY() >> 4;
        int rz = pos.getZ() >> 4;
        return BlockPos.asLong(rx, ry, rz);
    }

    private static CacheKey regionKey(ServerLevel level, BlockPos pos) {
        return new CacheKey(level.dimension().location(), regionPosOffset(pos));
    }

    /**
     * Returns the cached temperature for the region containing {@code pos},
     * or queries Cold Sweat and caches the result if missing/expired.
     */
    public static double get(ServerLevel level, BlockPos pos) {
        CacheKey key = regionKey(level, pos);
        long currentTick = level.getGameTime();

        CacheEntry entry = CACHE.get(key);
        if (entry != null && (currentTick - entry.tick()) < EXPIRY_TICKS) {
            return entry.temperature();
        }

        // Cache miss or expired — query the API
        double temp;
        try {
            temp = WorldHelper.getTemperatureAt(level, pos);
        } catch (RuntimeException e) {
            temp = 0.0; // Default to cold on error
        }

        CACHE.put(key, new CacheEntry(temp, currentTick));

        return temp;
    }

    /**
     * Call periodically (e.g. every server tick) to clean up stale entries.
     * Actual cleanup only runs every {@link #CLEANUP_INTERVAL} ticks.
     */
    public static void tick(long currentTick) {
        if (currentTick - lastCleanupTick < CLEANUP_INTERVAL) {
            return;
        }
        lastCleanupTick = currentTick;

        Iterator<Map.Entry<CacheKey, CacheEntry>> it = CACHE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<CacheKey, CacheEntry> entry = it.next();
            if (currentTick - entry.getValue().tick() >= EXPIRY_TICKS) {
                it.remove();
            }
        }
    }

    /**
     * Clear all cached data (e.g. on world unload).
     */
    public static void clear() {
        CACHE.clear();
        lastCleanupTick = 0;
    }
}
