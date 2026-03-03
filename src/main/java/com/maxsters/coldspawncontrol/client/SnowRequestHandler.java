package com.maxsters.coldspawncontrol.client;

import com.maxsters.coldspawncontrol.init.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.maxsters.coldspawncontrol.ColdSpawnControl;

import com.maxsters.coldspawncontrol.client.particle.SnowClusterParticle;
import com.maxsters.coldspawncontrol.mixin.accessor.LevelAccessor;
import java.util.Collections;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID, value = Dist.CLIENT)
@SuppressWarnings("null")
public class SnowRequestHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Pre-computed direction vectors (every 22.5 degrees) - fewer directions, still
    // good coverage
    private static final double[][] DIRECTIONS = new double[16][2];
    private static final double[] Y_OFFSETS = { 0, 4, -4 };

    static {
        for (int i = 0; i < 16; i++) {
            double angle = Math.toRadians(i * 22.5);
            DIRECTIONS[i][0] = Math.sin(angle); // X component
            DIRECTIONS[i][1] = -Math.cos(angle); // Z component (negative for north = 0°)
        }
    }

    // Exposed state for debug command
    private static boolean lastIsAnchored = false;
    private static boolean lastInCaveAir = false;
    private static double lastAnchorDist = 0.0;
    private static double lastRadius = 0.0;

    // Cave detection cache — re-scan only when player moves significantly
    private static double cachedScanX = Double.NaN;
    private static double cachedScanZ = Double.NaN;
    private static boolean cachedFoundSky = false;
    private static double cachedBestX = 0;
    private static double cachedBestZ = 0;
    private static double cachedBestDist = 9999.0;
    /** Distance threshold before re-scanning cave detection (blocks²). */
    private static final double RESCAN_DIST_SQ = 9.0; // 3 blocks

    // Reusable MutableBlockPos to avoid per-particle allocations in findSpawnPos
    private static final BlockPos.MutableBlockPos SPAWN_CHECK_POS = new BlockPos.MutableBlockPos();

    public static boolean isAnchored() {
        return lastIsAnchored;
    }

    public static boolean isInCaveAir() {
        return lastInCaveAir;
    }

    public static double getAnchorDistance() {
        return lastAnchorDist;
    }

    public static double getRadius() {
        return lastRadius;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        // Fix "Snow Gone on Rejoin": Clear the static pool when leaving the world
        LOGGER.info(
                "SnowRequestHandler: Clearing particle pool on logout. Size: " + SnowClusterParticle.PARTICLES.size());
        SnowClusterParticle.PARTICLES.clear();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        Level level = mc.level;

        if (player == null || level == null)
            return;

        // Realistic Mode Check: If enabled, disable snow particles
        if (ClientRealisticModeState.isRealisticModeEnabled) {
            // Cleanup existing particles if any remain (e.g. from toggling mode)
            if (!SnowClusterParticle.PARTICLES.isEmpty()) {
                synchronized (SnowClusterParticle.PARTICLES) {
                    for (SnowClusterParticle p : SnowClusterParticle.PARTICLES) {
                        p.remove(); // Mark as removed for vanilla engine
                    }
                    SnowClusterParticle.PARTICLES.clear(); // Clear local tracking
                }
            }
            return;
        }

        if (mc.isPaused())
            return;

        // Use INTERNAL rain level via Accessor to bypass ClientRainVisualsMixin which
        // might be forcing 0
        float trueRainLevel = ((LevelAccessor) level).getRainLevelInternal();
        if (trueRainLevel <= 0.0f)
            return;

        Vec3 cameraPos = mc.gameRenderer.getMainCamera().getPosition();

        // Guard: Skip snow logic only if player's chunk isn't loaded at all.
        // We no longer bail out when isLightCorrect() is false, because that
        // was preventing ALL snow particles from rendering in loaded chunks
        // during brief lighting engine delays.
        BlockPos playerPos = new BlockPos(cameraPos);
        if (!level.getChunkSource().hasChunk(playerPos.getX() >> 4, playerPos.getZ() >> 4)) {
            return;
        }

        Vec3 playerVelocity = player.getDeltaMovement();

        // Calculate spawn center (biased by velocity)
        double speed = playerVelocity.horizontalDistance();
        double offsetScale = 0;
        if (speed > 0.01) {
            offsetScale = (speed / 0.28) * 3.33;
        }

        Vec3 forward = playerVelocity.normalize();
        // Handle NaN if stationary
        if (Double.isNaN(forward.x) || Double.isNaN(forward.z)) {
            // Use Look Angle, but FORCE PITCH TO 0 (Horizontal only).
            // This prevents "looking down" from treating snow above as "behind".
            forward = Vec3.directionFromRotation(0, player.getYRot());
        }

        // Cave/Enclosure Detection: Check if player cannot see sky
        // This is much more reliable than checking for CAVE_AIR blocks,
        // as caves often contain regular AIR and this catches any enclosed space.
        double centerSpawnX = cameraPos.x;
        double centerSpawnZ = cameraPos.z;

        boolean underRoof = !level.canSeeSky(playerPos);
        boolean isAnchored = false;

        if (underRoof) {
            // Check if we can reuse the cached cave scan result
            double dx = cameraPos.x - cachedScanX;
            double dz = cameraPos.z - cachedScanZ;
            boolean needRescan = Double.isNaN(cachedScanX) || (dx * dx + dz * dz) > RESCAN_DIST_SQ;

            if (needRescan) {
                // Re-scan for cave openings
                BlockPos.MutableBlockPos mut = new BlockPos.MutableBlockPos();
                cachedFoundSky = false;
                cachedBestDist = 9999.0;
                cachedBestX = cameraPos.x;
                cachedBestZ = cameraPos.z;
                cachedScanX = cameraPos.x;
                cachedScanZ = cameraPos.z;

                // Scan in 16 directions (every 22.5 degrees)
                // Check at 3 Y levels (player level, above, below)
                outerLoop: for (double[] dir : DIRECTIONS) {
                    for (double yOff : Y_OFFSETS) {
                        for (double d = 2.0; d <= 50.0; d += 1.5) {
                            double checkX = cameraPos.x + dir[0] * d;
                            double checkZ = cameraPos.z + dir[1] * d;
                            double checkY = cameraPos.y + yOff;
                            mut.set(checkX, checkY, checkZ);

                            if (!level.getChunkSource().hasChunk(mut.getX() >> 4, mut.getZ() >> 4))
                                continue;

                            if (level.canSeeSky(mut)) {
                                if (d < cachedBestDist) {
                                    cachedBestDist = d;
                                    cachedBestX = checkX;
                                    cachedBestZ = checkZ;
                                    cachedFoundSky = true;
                                }
                                // Aggressive early-exit: if sky found within 15 blocks,
                                // stop ALL scanning immediately
                                if (d < 15.0)
                                    break outerLoop;
                                break; // Otherwise stop this direction+Y combo
                            }
                        }
                    }
                }
            }

            if (cachedFoundSky) {
                centerSpawnX = cachedBestX;
                centerSpawnZ = cachedBestZ;
                isAnchored = true;
            } else {
                // deep cave, apply standard velocity bias just in case
                centerSpawnX += (forward.x * offsetScale);
                centerSpawnZ += (forward.z * offsetScale);
            }
        } else {
            // Standard Open Field behavior (or player structure)
            // Invalidate cave cache since we're in open sky
            cachedScanX = Double.NaN;
            centerSpawnX += (forward.x * offsetScale);
            centerSpawnZ += (forward.z * offsetScale);
        }

        // Calculate velocity-based biasing
        double vSpeed = Math.abs(playerVelocity.y);
        double biasRaw = (speed - vSpeed) * 10.0;
        double bias = Math.max(-5.0, Math.min(5.0, biasRaw));

        double baseRadius = 10.0;
        double radius = baseRadius + bias;

        if (isAnchored) {
            radius *= 2.0; // Double radius when anchored to exit
        }
        double heightModifier = -bias;

        int targetPoolSize = 4000;

        // Dynamic Culling Distance
        // If the anchor is projected far away (e.g. at cave entrance), we must NOT cull
        // particles there.
        // Base cull dist is radius.
        // If anchor is 25 blocks away, we need context.
        double anchorDistSq = (centerSpawnX - cameraPos.x) * (centerSpawnX - cameraPos.x) +
                (centerSpawnZ - cameraPos.z) * (centerSpawnZ - cameraPos.z);
        double anchorDist = Math.sqrt(anchorDistSq);

        // Update exposed state for debug command
        lastIsAnchored = isAnchored;
        lastInCaveAir = underRoof;
        lastAnchorDist = anchorDist;
        lastRadius = radius;

        // Allowed distance = Distance to Anchor + Radius + Buffer
        double maxCullDist = anchorDist + radius + 8.0;
        double maxCullDistSq = maxCullDist * maxCullDist;

        // Diagnostic Heartbeat
        int currentSize = SnowClusterParticle.PARTICLES.size();
        heartbeatTicks++;
        if (heartbeatTicks >= 100 && player.isCreative()) {
            heartbeatTicks = 0;
            LOGGER.info("Snow Heartbeat: Pool: {}, Radius: {}, AnchorDist: {}, MaxCull: {}", currentSize,
                    String.format("%.1f", radius), String.format("%.1f", anchorDist),
                    String.format("%.1f", maxCullDist));
        }

        // 1. Fill Pool
        if (currentSize < targetPoolSize) {
            int toAdd = Math.min(200, targetPoolSize - currentSize);
            for (int i = 0; i < toAdd; i++) {
                spawnNewParticle(level, cameraPos, centerSpawnX, centerSpawnZ, radius, heightModifier, underRoof);
            }
        }

        // 2. Recycle Pool
        synchronized (SnowClusterParticle.PARTICLES) {
            int size = SnowClusterParticle.PARTICLES.size();
            if (size > 0) {
                int toCheck = Math.min(size, 400);
                for (int i = 0; i < toCheck; i++) {
                    if (checkIndex >= SnowClusterParticle.PARTICLES.size()) {
                        checkIndex = 0;
                    }

                    try {
                        SnowClusterParticle p = SnowClusterParticle.PARTICLES.get(checkIndex);

                        if (p.getLevel() != level) {
                            p.remove();
                            int lastIndex = SnowClusterParticle.PARTICLES.size() - 1;
                            if (checkIndex != lastIndex)
                                Collections.swap(SnowClusterParticle.PARTICLES, checkIndex, lastIndex);
                            SnowClusterParticle.PARTICLES.remove(lastIndex);
                            continue;
                        }

                        boolean expired = p.getAge() >= p.getLifetime();
                        boolean tooLow = (p.getY() - cameraPos.y) < -20.0;

                        // Culling Logic
                        double distSq3D = (p.getX() - cameraPos.x) * (p.getX() - cameraPos.x) +
                                (p.getY() - cameraPos.y) * (p.getY() - cameraPos.y) + // Note: Y distance counts fully
                                                                                      // now
                                (p.getZ() - cameraPos.z) * (p.getZ() - cameraPos.z);

                        // Use our calculated dynamic cull distance
                        boolean tooFar = distSq3D > maxCullDistSq;

                        // "Behind" check (Dot Product)
                        // If behind, use tighter culling (base radius)
                        // But need to be careful with Cave Mode projection.
                        // Simplified: If projecting, disable "Behind" aggressive culling to prevent
                        // artifacts.
                        if (!underRoof) {
                            double toCamX = p.getX() - cameraPos.x;
                            double toCamY = p.getY() - cameraPos.y;
                            double toCamZ = p.getZ() - cameraPos.z;
                            double len = Math.sqrt(distSq3D);
                            if (len > 0.0001) {
                                double dot = (toCamX / len) * forward.x + (toCamY / len) * forward.y
                                        + (toCamZ / len) * forward.z;
                                if (dot < -0.2) { // Is Behind
                                    // If behind, cull aggressively (radius squared)
                                    if (distSq3D > radius * radius)
                                        tooFar = true;
                                }
                            }
                        }

                        if (expired || tooLow || tooFar) {
                            p.remove();
                            // Optimization: Swap-Remove for O(1) removal
                            // 1. Swap current with last
                            int lastIndex = SnowClusterParticle.PARTICLES.size() - 1;
                            if (checkIndex != lastIndex) {
                                Collections.swap(SnowClusterParticle.PARTICLES, checkIndex, lastIndex);
                            }
                            // 2. Remove last (constant time)
                            SnowClusterParticle.PARTICLES.remove(lastIndex);

                            // Don't increment index, because slot 'checkIndex' now holds the swapped-in
                            // particle
                        } else {
                            checkIndex++;
                        }
                    } catch (Exception e) {
                        try {
                            // O(1) Remove on error too
                            int lastIndex = SnowClusterParticle.PARTICLES.size() - 1;
                            if (checkIndex != lastIndex) {
                                Collections.swap(SnowClusterParticle.PARTICLES, checkIndex, lastIndex);
                            }
                            SnowClusterParticle.PARTICLES.remove(lastIndex);
                        } catch (Exception ignored) {
                        }
                    }
                }
            }
        }
    }

    private static int checkIndex = 0;

    private static int heartbeatTicks = 0;

    // Helper to find a valid spawn position
    private static Vec3 findSpawnPos(Level level, Vec3 cameraPos, double centerSpawnX, double centerSpawnZ,
            double radius, double heightModifier, boolean isCave) {
        double r = radius * Math.sqrt(level.random.nextDouble());
        double theta = level.random.nextDouble() * 2 * Math.PI;

        double dX = r * Math.cos(theta);
        double dZ = r * Math.sin(theta);

        double pX = centerSpawnX + dX;
        double pZ = centerSpawnZ + dZ;

        // NOTE: 2D deadzone check was removed because it incorrectly rejected snow
        // particles directly above the player (small horizontal distance, large
        // vertical
        // distance). The 3D deadzone check at the end of this method handles this
        // correctly.

        // Use Heightmap to find reliable surface Y
        int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                (int) Math.floor(pX), (int) Math.floor(pZ));

        // Cave Filter: If in cave mode, reject spawn positions on the "roof" (mountain
        // above).
        // Only allow spawns that are roughly level with the player or lower.
        // This forces particles to spawn in the visible "gap" outside the cave.
        if (isCave && surfaceY > cameraPos.y + 20.0) {
            return null;
        }

        // Spawn in a column around the player (Modified by velocity bias)
        // Negative heightModifier = Taller column (Vertical motion)
        // Positive heightModifier = Shorter column (Horizontal motion)
        double minY = cameraPos.y - (20.0 - heightModifier);
        double maxY = cameraPos.y + (25.0 + heightModifier);

        // Ensure minY is at least 0.5 blocks above the ground surface
        // (This prevents particles from immediately "landing" and looking frozen)
        if (minY < surfaceY + 0.5) {
            minY = surfaceY + 0.5;
        }

        // If minY became > maxY (e.g. at the bottom of a deep valley), clamp maxY
        if (maxY < minY + 5.0) {
            maxY = minY + 5.0;
        }

        double pY = minY + (level.random.nextDouble() * (maxY - minY));

        // Final safety Check: Sky access — reuse MutableBlockPos to avoid allocation
        SPAWN_CHECK_POS.set(pX, pY, pZ);
        if (!level.getChunkSource().hasChunk(SPAWN_CHECK_POS.getX() >> 4, SPAWN_CHECK_POS.getZ() >> 4)
                || !level.canSeeSky(SPAWN_CHECK_POS)) {
            return null;
        }

        // Deadzone Check: Don't spawn within 5 blocks of camera (full 3D check)
        double distSq = (pX - cameraPos.x) * (pX - cameraPos.x) +
                (pY - cameraPos.y) * (pY - cameraPos.y) +
                (pZ - cameraPos.z) * (pZ - cameraPos.z);
        if (distSq < 25.0) {
            return null;
        }

        return new Vec3(pX, pY, pZ);
    }

    private static void spawnNewParticle(Level level, Vec3 cameraPos, double centerX, double centerZ, double radius,
            double heightModifier, boolean isCave) {
        Vec3 pos = findSpawnPos(level, cameraPos, centerX, centerZ, radius, heightModifier, isCave);
        if (pos != null) {
            level.addParticle(ModParticles.SNOW_CLUSTER.get(), pos.x, pos.y, pos.z, 0.0, -0.0675, 0);
        }
    }
}
