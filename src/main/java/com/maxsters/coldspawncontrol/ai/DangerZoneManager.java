package com.maxsters.coldspawncontrol.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;

import java.util.Map;

public class DangerZoneManager {
    // Map of Dimension -> Map of Region Key -> Map of Danger Zones (BlockPos ->
    // Expiration)
    // Region keys are 32x32 block areas for spatial hashing
    private static final Map<ResourceKey<Level>, Map<Long, Map<BlockPos, Long>>> DANGER_ZONES_BY_REGION = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int DANGER_RADIUS = 10;
    private static final int DANGER_RADIUS_SQR = DANGER_RADIUS * DANGER_RADIUS;
    private static final long EXPIRATION_TIME_MS = 10 * 60 * 1000; // 10 minutes

    private static final int REGION_BITS = 5; // 32 = 2^5

    private static long getRegionKey(BlockPos pos) {
        int regionX = pos.getX() >> REGION_BITS;
        int regionZ = pos.getZ() >> REGION_BITS;
        return (long) regionX << 32 | (regionZ & 0xFFFFFFFFL);
    }

    public static void addDangerZone(Level level, BlockPos pos) {
        ResourceKey<Level> dim = level.dimension();
        long expiration = System.currentTimeMillis() + EXPIRATION_TIME_MS;
        long regionKey = getRegionKey(pos);

        DANGER_ZONES_BY_REGION
                .computeIfAbsent(dim, k -> new java.util.concurrent.ConcurrentHashMap<>())
                .computeIfAbsent(regionKey, k -> new java.util.concurrent.ConcurrentHashMap<>())
                .put(pos.immutable(), expiration);
    }

    public static boolean isDangerous(Level level, BlockPos pos) {
        ResourceKey<Level> dim = level.dimension();
        Map<Long, Map<BlockPos, Long>> regionMap = DANGER_ZONES_BY_REGION.get(dim);

        if (regionMap == null || regionMap.isEmpty()) {
            return false;
        }

        long now = System.currentTimeMillis();

        // Check center region and adjacent regions (for danger zones near region
        // boundaries)
        int centerX = pos.getX() >> REGION_BITS;
        int centerZ = pos.getZ() >> REGION_BITS;

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                long regionKey = (long) (centerX + dx) << 32 | ((centerZ + dz) & 0xFFFFFFFFL);
                Map<BlockPos, Long> zones = regionMap.get(regionKey);

                if (zones == null || zones.isEmpty())
                    continue;

                java.util.Iterator<java.util.Map.Entry<BlockPos, Long>> iterator = zones.entrySet().iterator();
                while (iterator.hasNext()) {
                    java.util.Map.Entry<BlockPos, Long> entry = iterator.next();
                    if (now > entry.getValue()) {
                        iterator.remove(); // Expired
                        continue;
                    }

                    if (entry.getKey().distSqr(pos) <= DANGER_RADIUS_SQR) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public static void clear() {
        DANGER_ZONES_BY_REGION.clear();
    }
}
