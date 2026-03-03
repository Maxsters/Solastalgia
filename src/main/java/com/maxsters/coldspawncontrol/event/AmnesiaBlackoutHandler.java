package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.network.AmnesiaDisorientPacket;
import com.maxsters.coldspawncontrol.network.AmnesiaNetworkHandler;
import com.maxsters.coldspawncontrol.network.Networking;
import com.maxsters.coldspawncontrol.registry.AmnesiaAdvancementConfig;
import com.maxsters.coldspawncontrol.slm.JournalEntryGenerator;
import com.maxsters.coldspawncontrol.registry.AmnesiaNameList;
import com.maxsters.coldspawncontrol.registry.ForgetSoundConfig;
import com.maxsters.coldspawncontrol.registry.ModSoundEvents;
import com.momosoftworks.coldsweat.api.util.Temperature;
import com.momosoftworks.coldsweat.api.util.Temperature.Trait;
import com.momosoftworks.coldsweat.util.world.WorldHelper;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.ServerStatsCounter;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Amnesia blackout mechanic - randomly teleports player to simulate memory
 * loss.
 * 
 * Trigger: Every minute after 30min cooldown:
 * - 0% if player moved <10 blocks
 * - 5% if player moved 235+ blocks
 * - 1% otherwise
 * 
 * Effects: teleportation, tool wear, inventory shuffle, hunger/damage.
 */
@SuppressWarnings("null")
@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public class AmnesiaBlackoutHandler {

    private static final Random RANDOM = new Random();

    // Flag to suppress chat announcements during reset
    public static boolean isResetting = false;

    // Timing
    private static final long COOLDOWN_TICKS = 30 * 60 * 20; // 30 minutes
    private static final int CHECK_INTERVAL_TICKS = 20 * 60; // 1 minute

    // Movement thresholds (per minute)
    private static final double HIGH_MOVEMENT_THRESHOLD = 235.0;
    private static final double MIN_MOVEMENT_THRESHOLD = 10.0;

    // Chance percentages
    private static final double HIGH_MOVEMENT_CHANCE = 0.05; // 5%
    private static final double LOW_MOVEMENT_CHANCE = 0.01; // 1%

    // Teleport range
    private static final int MIN_TELEPORT_DISTANCE = 100;
    private static final int MAX_TELEPORT_DISTANCE = 1000;

    // Confusion messages for actionbar
    private static final String[] CONFUSION_MESSAGES = {
            "§7§oWhat... where am I?§r",
            "§7§oHow did I get here?§r",
            "§7§oI can't remember...§r",
            "§7§oEverything is blurry...§r"
    };

    // Track active confusion messages per player: UUID -> (endTime, messageIndex)
    private static final Map<UUID, long[]> ACTIVE_CONFUSION = new HashMap<>();

    // Persistent data keys
    private static final String LAST_BLACKOUT_KEY = "maxsters_last_blackout";
    private static final String LAST_POS_X_KEY = "maxsters_last_pos_x";
    private static final String LAST_POS_Z_KEY = "maxsters_last_pos_z";

    // Global synchronized timing (shared across all players in multiplayer)
    private static long lastGlobalCheck = 0;
    private static long lastGlobalBlackout = 0;

    // Async chunk loading settings
    private static final int CHUNKS_PER_TICK = 2; // How many chunks to request per tick
    private static final int MAX_LOADING_TICKS = 60; // 3 seconds max wait time
    private static final int CANDIDATES_TO_GENERATE = 20; // Reduced from 50

    // Track pending blackouts for async processing
    private static final Map<UUID, PendingBlackout> PENDING_BLACKOUTS = new HashMap<>();

    /**
     * Tracks a blackout in progress while chunks are being loaded async.
     */
    private static class PendingBlackout {
        final ServerLevel targetLevel;
        final List<BlockPos> candidatePositions;
        final int forcedDistance;
        final long startTick;
        final ForgetSoundConfig.SoundEntry soundEntry;
        final int effectDurationTicks;
        int currentIndex = 0;
        BlockPos validDestination = null;

        PendingBlackout(ServerLevel targetLevel, List<BlockPos> candidates, int forcedDistance,
                long startTick, ForgetSoundConfig.SoundEntry soundEntry, int effectDurationTicks) {
            this.targetLevel = targetLevel;
            this.candidatePositions = candidates;
            this.forcedDistance = forcedDistance;
            this.startTick = startTick;
            this.soundEntry = soundEntry;
            this.effectDurationTicks = effectDurationTicks;
        }
    }

    /**
     * Schedules a blackout to occur after the SLM generation delay.
     * Triggers SLM generation immediately.
     *
     * @param distance Forced teleport distance, or -1 for random.
     */
    /**
     * Schedules a blackout to occur after the SLM generation delay.
     * Triggers SLM generation immediately.
     *
     * @param distance Forced teleport distance, or -1 for random.
     */
    public static void scheduleBlackout(ServerPlayer player, int distance) {
        scheduleBlackout(player, distance, false);
    }

    public static void scheduleBlackout(ServerPlayer player, int distance, boolean debug) {
        ServerLevel level = (ServerLevel) player.level;
        ColdSpawnControl.LOGGER.info("Scheduling blackout for {} (waiting for SLM generation...)",
                player.getName().getString());

        // Start SLM generation and wait for it to complete
        JournalEntryGenerator.startGeneration(player, level, debug).thenAccept(success -> {
            // This runs on a background thread, so we must schedule the blackout on the
            // main server thread
            player.getServer().execute(() -> {
                if (player.isRemoved())
                    return; // Player disconnected

                ColdSpawnControl.LOGGER.info("SLM generation finished (success={}). Triggering blackout now.", success);

                if (distance > 0) {
                    triggerBlackoutWithDistance(player, (ServerLevel) player.level, level.getGameTime(), distance);
                } else {
                    triggerBlackout(player, (ServerLevel) player.level, level.getGameTime());
                }
            });
        });
    }

    /**
     * Synchronizes a blackout across a group of players, waiting for all of their
     * SLM models
     * to resolve, up to a maximum timeout of 30 seconds.
     */
    public static void scheduleGlobalBlackout(List<ServerPlayer> players, int distance, boolean debug) {
        if (players.isEmpty())
            return;

        ServerLevel level = (ServerLevel) players.get(0).level;
        ColdSpawnControl.LOGGER.info("Scheduling globally synchronized blackout for {} players...", players.size());

        List<java.util.concurrent.CompletableFuture<Boolean>> futures = new ArrayList<>();
        for (ServerPlayer player : players) {
            futures.add(JournalEntryGenerator.startGeneration(player, (ServerLevel) player.level, debug));
        }

        java.util.concurrent.CompletableFuture<Void> allFinished = java.util.concurrent.CompletableFuture.allOf(
                futures.toArray(new java.util.concurrent.CompletableFuture[0]));

        java.util.concurrent.CompletableFuture<Void> timeout = new java.util.concurrent.CompletableFuture<>();
        java.util.Timer timer = new java.util.Timer("BlackoutTimeoutTimer", true);
        timer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                timeout.complete(null);
            }
        }, 30000); // 30 seconds timeout limit

        java.util.concurrent.CompletableFuture.anyOf(allFinished, timeout).thenRun(() -> {
            timer.cancel(); // kill timer if natural resolution reached first

            // push execution back onto primary server loop safely against async threads
            level.getServer().execute(() -> {
                for (ServerPlayer player : players) {
                    if (player.isRemoved())
                        continue;

                    ColdSpawnControl.LOGGER.info("Triggering synchronized blackout for {}...",
                            player.getName().getString());
                    if (distance > 0) {
                        triggerBlackoutWithDistance(player, (ServerLevel) player.level, level.getGameTime(), distance);
                    } else {
                        triggerBlackout(player, (ServerLevel) player.level, level.getGameTime());
                    }
                }
            });
        });
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER)
            return;

        ServerPlayer player = (ServerPlayer) event.player;
        ServerLevel level = (ServerLevel) player.level;

        long gameTime = level.getGameTime();

        // Process any pending blackouts for this player (async chunk loading)
        processPendingBlackout(player, level, gameTime);

        // Handle confusion message display every tick (outside the check interval)
        handleConfusionMessages(player, level);

        // Only check in Overworld for timing (but blackouts can happen in any
        // dimension)
        if (!Level.OVERWORLD.equals(level.dimension()))
            return;

        // Synchronized global check - runs once per minute for ALL players
        if (gameTime - lastGlobalCheck >= CHECK_INTERVAL_TICKS) {
            lastGlobalCheck = gameTime;

            // Check global cooldown (30 min since last blackout for any player)
            if (gameTime - lastGlobalBlackout >= COOLDOWN_TICKS) {
                // Roll ONCE for this cycle - applies to all players
                double maxMovementChance = 0.0;

                // Find the highest chance among all online players
                for (ServerPlayer p : level.getServer().getPlayerList().getPlayers()) {
                    CompoundTag pData = p.getPersistentData();
                    double lastX = pData.getDouble(LAST_POS_X_KEY);
                    double lastZ = pData.getDouble(LAST_POS_Z_KEY);
                    double distance = Math.sqrt(Math.pow(p.getX() - lastX, 2) + Math.pow(p.getZ() - lastZ, 2));

                    // Update position tracking
                    pData.putDouble(LAST_POS_X_KEY, p.getX());
                    pData.putDouble(LAST_POS_Z_KEY, p.getZ());

                    // Calculate chance for this player
                    double playerChance = 0.0;
                    if (distance >= HIGH_MOVEMENT_THRESHOLD) {
                        playerChance = HIGH_MOVEMENT_CHANCE;
                    } else if (distance >= MIN_MOVEMENT_THRESHOLD) {
                        playerChance = LOW_MOVEMENT_CHANCE;
                    }
                    maxMovementChance = Math.max(maxMovementChance, playerChance);
                }

                // Single synchronized roll for all players
                if (maxMovementChance > 0 && RANDOM.nextDouble() < maxMovementChance) {
                    lastGlobalBlackout = gameTime;

                    // Start SLM journal generation and schedule blackout synchronized for all
                    // online players
                    scheduleGlobalBlackout(level.getServer().getPlayerList().getPlayers(), -1, false);
                }
            }
        }
    }

    /**
     * Handles displaying confusion messages every tick for active blackouts.
     */
    private static void handleConfusionMessages(ServerPlayer player, ServerLevel level) {
        UUID uuid = player.getUUID();
        long[] confusionData = ACTIVE_CONFUSION.get(uuid);

        if (confusionData == null)
            return;

        long endTime = confusionData[0];
        long startTime = confusionData[1];
        long gameTime = level.getGameTime();

        if (gameTime >= endTime) {
            ACTIVE_CONFUSION.remove(uuid);
            return;
        }

        // Calculate which message to show (rotate every 50 ticks = 2.5 seconds)
        // Guard against negative elapsed time (can happen on newly created worlds)
        long elapsed = Math.max(0, gameTime - startTime);
        int messageIndex = Math.abs((int) ((elapsed / 50) % CONFUSION_MESSAGES.length));

        // Display message every tick to prevent fading
        if (gameTime % 10 == 0) { // Refresh every 0.5 seconds
            player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(CONFUSION_MESSAGES[messageIndex]), true);
        }
    }

    /**
     * Processes pending blackouts for a player - loads chunks progressively
     * to avoid blocking the main thread.
     */
    private static void processPendingBlackout(ServerPlayer player, ServerLevel level, long gameTime) {
        UUID uuid = player.getUUID();
        PendingBlackout pending = PENDING_BLACKOUTS.get(uuid);

        if (pending == null)
            return;

        // Check for timeout - if we've been trying too long, abort or use fallback
        long elapsed = gameTime - pending.startTick;
        if (elapsed > MAX_LOADING_TICKS) {
            ColdSpawnControl.LOGGER.warn("Blackout chunk loading timed out after {} ticks", elapsed);
            PENDING_BLACKOUTS.remove(uuid);
            // Fallback: teleport to nearest already-loaded chunk position
            BlockPos fallback = findFallbackDestination(player, pending.targetLevel, pending.forcedDistance);
            if (fallback != null) {
                completeBlackout(player, pending.targetLevel, fallback, pending.soundEntry,
                        pending.effectDurationTicks, gameTime);
            }
            return;
        }

        // Already found a valid destination? Complete the blackout
        if (pending.validDestination != null) {
            PENDING_BLACKOUTS.remove(uuid);
            completeBlackout(player, pending.targetLevel, pending.validDestination,
                    pending.soundEntry, pending.effectDurationTicks, gameTime);
            return;
        }

        // Process CHUNKS_PER_TICK candidates per tick
        for (int i = 0; i < CHUNKS_PER_TICK && pending.currentIndex < pending.candidatePositions.size(); i++) {
            BlockPos candidate = pending.candidatePositions.get(pending.currentIndex);
            pending.currentIndex++;

            // Request chunk loading (non-blocking if chunk isn't loaded)
            int chunkX = candidate.getX() >> 4;
            int chunkZ = candidate.getZ() >> 4;

            // Check if chunk is already loaded - if so, we can validate immediately
            if (pending.targetLevel.hasChunk(chunkX, chunkZ)) {
                BlockPos safe = findSafeYFast(pending.targetLevel, candidate.getX(), candidate.getZ(),
                        player.getYRot());
                if (safe != null && isLocationSafeFast(pending.targetLevel, safe)) {
                    pending.validDestination = safe;
                    return; // Will teleport on next tick
                }
            } else {
                // Request async chunk load - will be available on future ticks
                pending.targetLevel.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
            }
        }

        // If we've exhausted all candidates without finding a valid spot
        if (pending.currentIndex >= pending.candidatePositions.size()) {
            ColdSpawnControl.LOGGER.warn("Exhausted all {} candidates without finding valid spot",
                    pending.candidatePositions.size());
            PENDING_BLACKOUTS.remove(uuid);
            // Fallback
            BlockPos fallback = findFallbackDestination(player, pending.targetLevel, pending.forcedDistance);
            if (fallback != null) {
                completeBlackout(player, pending.targetLevel, fallback, pending.soundEntry,
                        pending.effectDurationTicks, gameTime);
            }
        }
    }

    /**
     * Generates candidate positions upfront for async evaluation.
     * Returns a list of BlockPos at ground level to check.
     */
    private static List<BlockPos> generateCandidatePositions(ServerLevel level, BlockPos origin, int forcedDistance,
            int count) {
        List<BlockPos> candidates = new ArrayList<>(count);
        String dim = level.dimension().location().getPath();

        int loopGuard = 0;
        int maxLoop = count * 10;
        for (int i = 0; i < count && loopGuard < maxLoop; loopGuard++) {
            int distance;
            if (forcedDistance > 0) {
                // Add small variance to forced distance (+/- 10%)
                distance = forcedDistance + RANDOM.nextInt(forcedDistance / 10 + 1) - (forcedDistance / 20);
            } else {
                distance = MIN_TELEPORT_DISTANCE + RANDOM.nextInt(MAX_TELEPORT_DISTANCE - MIN_TELEPORT_DISTANCE);
            }

            double angle = RANDOM.nextDouble() * 2 * Math.PI;
            int x = origin.getX() + (int) (Math.cos(angle) * distance);
            int z = origin.getZ() + (int) (Math.sin(angle) * distance);

            // In the End, avoid the void gap between the main island and outer islands
            // (~150 to ~950 block radius)
            if (dim.equals("the_end")) {
                double distFromCenterSqr = (x * x) + (z * z);
                if (distFromCenterSqr > 150 * 150 && distFromCenterSqr < 950 * 950) {
                    continue; // Skip and reroll
                }
            }

            // Use Y=40 as starting point (underground caves are preferred)
            candidates.add(new BlockPos(x, 40, z));
            i++; // Successfully found a candidate
        }

        return candidates;
    }

    /**
     * Fast Y-level finder that doesn't load extra chunks.
     * Only checks if terrain is already loaded.
     */
    private static BlockPos findSafeYFast(ServerLevel level, int x, int z, float yRot) {
        String dim = level.dimension().location().getPath();

        if (dim.equals("the_nether")) {
            // Nether has roofs and floors across all Y levels
            for (int y = 50; y <= 110; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (isValidSpawnFast(level, pos, yRot)) {
                    return pos;
                }
            }
            for (int y = 30; y < 50; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (isValidSpawnFast(level, pos, yRot)) {
                    return pos;
                }
            }
        } else if (dim.equals("the_end")) {
            // End islands hover around Y=55 to Y=70
            for (int y = 50; y <= 80; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (isValidSpawnFast(level, pos, yRot)) {
                    return pos;
                }
            }
        } else {
            // Overworld
            // Try underground first (Y 30-50)
            for (int y = 50; y >= 20; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                if (isValidSpawnFast(level, pos, yRot)) {
                    return pos;
                }
            }

            // Try surface
            for (int y = 60; y <= 150; y++) {
                BlockPos pos = new BlockPos(x, y, z);
                if (isValidSpawnFast(level, pos, yRot)) {
                    return pos;
                }
            }
        }

        return null;
    }

    /**
     * Fast spawn validation that doesn't trigger chunk loads for adjacent blocks.
     */
    private static boolean isValidSpawnFast(ServerLevel level, BlockPos pos, float yRot) {
        // All blocks must be in loaded chunks
        if (!level.isLoaded(pos) || !level.isLoaded(pos.above()) || !level.isLoaded(pos.below())) {
            return false;
        }

        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        BlockState floor = level.getBlockState(pos.below());

        if (!feet.isAir() || !head.isAir() || !floor.getMaterial().isSolid() ||
                floor.is(Blocks.LAVA) || floor.is(Blocks.WATER) ||
                floor.is(Blocks.POWDER_SNOW) || feet.is(Blocks.POWDER_SNOW)) {
            return false;
        }

        // Ledge Check: Ensure there are at least 4 solid blocks below the player's path
        // out to 8 blocks in front
        int solidCount = 0;
        float rad = yRot * ((float) Math.PI / 180.0F);
        double vx = -Math.sin(rad);
        double vz = Math.cos(rad);

        for (int i = 1; i <= 8; i++) {
            int fx = pos.getX() + (int) Math.round(vx * i);
            int fz = pos.getZ() + (int) Math.round(vz * i);
            BlockPos forwardFloor = new BlockPos(fx, pos.getY() - 1, fz);

            if (level.isLoaded(forwardFloor)) {
                BlockState forwardState = level.getBlockState(forwardFloor);
                if (forwardState.getMaterial().isSolid() &&
                        !forwardState.is(Blocks.LAVA) &&
                        !forwardState.is(Blocks.WATER) &&
                        !forwardState.is(Blocks.POWDER_SNOW)) {
                    solidCount++;
                }
            }
        }

        return solidCount >= 4;
    }

    /**
     * Fast location safety check - reduced radius, no spawner scan.
     */
    private static boolean isLocationSafeFast(ServerLevel level, BlockPos pos) {
        // Check for hostile mobs within 16 blocks (reduced from 24)
        var nearbyEntities = level.getEntitiesOfClass(
                net.minecraft.world.entity.monster.Monster.class,
                new net.minecraft.world.phys.AABB(pos).inflate(16));

        return nearbyEntities.isEmpty();
        // Note: Spawner check removed for performance
    }

    /**
     * Fallback destination finder for when async loading times out.
     * Searches only in already-loaded chunks.
     */
    private static BlockPos findFallbackDestination(ServerPlayer player, ServerLevel level, int forcedDistance) {
        BlockPos origin = player.blockPosition();

        // Try 10 random positions in a smaller radius where chunks might be loaded
        for (int attempts = 0; attempts < 10; attempts++) {
            int distance = Math.min(forcedDistance > 0 ? forcedDistance : 200, 200); // Cap at 200 for fallback
            double angle = RANDOM.nextDouble() * 2 * Math.PI;
            int x = origin.getX() + (int) (Math.cos(angle) * distance);
            int z = origin.getZ() + (int) (Math.sin(angle) * distance);

            int chunkX = x >> 4;
            int chunkZ = z >> 4;

            if (level.hasChunk(chunkX, chunkZ)) {
                BlockPos safe = findSafeYFast(level, x, z, player.getYRot());
                if (safe != null) {
                    return safe;
                }
            }
        }

        return null;
    }

    /**
     * Completes the blackout teleport after chunks are loaded.
     */
    private static void completeBlackout(ServerPlayer player, ServerLevel targetLevel, BlockPos destination,
            ForgetSoundConfig.SoundEntry soundEntry, int effectDurationTicks,
            long gameTime) {
        int teleportDistance = (int) Math.sqrt(player.blockPosition().distSqr(destination));

        ColdSpawnControl.LOGGER.info("Completing blackout: {} blocks to {}",
                teleportDistance, destination);

        // Ensure destination chunk is fully loaded before teleport
        int chunkX = destination.getX() >> 4;
        int chunkZ = destination.getZ() >> 4;
        targetLevel.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, true);

        // Apply darkness effect based on sound duration
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, effectDurationTicks, 0, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, effectDurationTicks, 0, false, false));

        // Teleport the player
        player.teleportTo(targetLevel, destination.getX() + 0.5, destination.getY(), destination.getZ() + 0.5,
                player.getYRot(), player.getXRot());
        player.fallDistance = 0;

        // Play forget sound at player's NEW location
        targetLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                ModSoundEvents.FORGET.get(), SoundSource.AMBIENT, 1.0f, 1.0f);

        // Start confusion text loop
        startConfusionTextLoop(player, targetLevel, effectDurationTicks);

        // Apply effects
        simulateToolUsage(player);
        applyInventoryEffects(player);
        applyHungerEffects(player);
        adjustTemperature(player, targetLevel, destination, teleportDistance);

        // New amnesia effects
        randomizeFakeDay(player);
        randomizePlayerStatistics(player);
        sendDisorientationEffects(player);
        resetToIronAge(player);

        // Apply any pending SLM-generated journal entry
        JournalEntryGenerator.applyPendingEntry(player);

        shuffleNearbyNamedEntities(player, targetLevel);
        injectChestContents(player, targetLevel, destination);
        toggleNearbyDoors(targetLevel, destination);

        // Write SLM-generated journal entry (if available)
        // This call is now redundant as it's moved above. Keeping the comment for
        // context if needed.

        ColdSpawnControl.LOGGER.info("Blackout complete - teleported {} blocks to {}",
                teleportDistance, destination);
    }

    /**
     * Triggers the amnesia blackout effect.
     * This is public so it can be called by the /forget command.
     */
    public static void triggerBlackout(ServerPlayer player, ServerLevel level, long gameTime) {
        triggerBlackoutWithDistance(player, level, gameTime, -1); // -1 = random
    }

    /**
     * Triggers blackout with a specific distance (or random if -1).
     * Now uses async chunk loading to prevent tick freezes.
     */
    public static void triggerBlackoutWithDistance(ServerPlayer player, ServerLevel level, long gameTime,
            int forcedDistance) {
        ColdSpawnControl.LOGGER.info("Triggering amnesia blackout for player {} (async mode)",
                player.getName().getString());

        // Prevent duplicate pending blackouts
        if (PENDING_BLACKOUTS.containsKey(player.getUUID())) {
            ColdSpawnControl.LOGGER.warn("Player already has pending blackout, skipping");
            return;
        }

        // Record blackout time
        player.getPersistentData().putLong(LAST_BLACKOUT_KEY, gameTime);

        // Teleport within current dimension only
        ServerLevel targetLevel = level;

        // Select random forget sound and calculate duration
        ForgetSoundConfig.SoundEntry soundEntry = ForgetSoundConfig.getRandomSound();
        int effectDurationTicks = ForgetSoundConfig.getEffectDurationTicks(soundEntry);

        ColdSpawnControl.LOGGER.info("Playing forget sound: {} (duration: {}s, effect: {} ticks)",
                soundEntry.name(), soundEntry.durationSeconds(), effectDurationTicks);

        // Generate candidate positions upfront (fast, no chunk loading)
        BlockPos origin = player.blockPosition();
        List<BlockPos> candidates = generateCandidatePositions(targetLevel, origin, forcedDistance,
                CANDIDATES_TO_GENERATE);

        // Create pending blackout for async processing
        PendingBlackout pending = new PendingBlackout(targetLevel, candidates, forcedDistance,
                gameTime, soundEntry, effectDurationTicks);
        PENDING_BLACKOUTS.put(player.getUUID(), pending);

        ColdSpawnControl.LOGGER.info("Started async blackout with {} candidates", candidates.size());
    }

    // Note: preloadChunks is no longer used - chunk loading is now handled async in
    // processPendingBlackout

    /**
     * Shows confusion messages on actionbar for the specified duration.
     * Uses tick-based tracking to properly rotate messages.
     * 
     * @param durationTicks How long to show messages (based on sound length)
     */
    private static void startConfusionTextLoop(ServerPlayer player, ServerLevel level, int durationTicks) {
        // Register this player for confusion messages
        long startTime = level.getGameTime();
        long endTime = startTime + durationTicks;
        ACTIVE_CONFUSION.put(player.getUUID(), new long[] { endTime, startTime });

        // Show first message immediately
        player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(CONFUSION_MESSAGES[0]), true);
    }

    /**
     * 10% chance per tool: wear durability, gain resources, award XP.
     */
    private static void simulateToolUsage(ServerPlayer player) {
        int totalXpAwarded = 0;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || !stack.isDamageableItem())
                continue;

            if (RANDOM.nextDouble() < 0.10) { // 10% chance
                int durabilityLoss = 5 + RANDOM.nextInt(11); // 5-15
                stack.setDamageValue(Math.min(stack.getDamageValue() + durabilityLoss, stack.getMaxDamage() - 1));

                // Give resources based on tool type and dimension
                String dimensionStr = player.level.dimension().location().getPath();
                ItemStack reward = getToolReward(stack, dimensionStr);
                if (!reward.isEmpty()) {
                    if (!player.getInventory().add(reward)) {
                        player.drop(reward, false);
                    }

                    // Award XP matching the simulated activity
                    int xp = getToolXpReward(stack, dimensionStr);
                    if (xp > 0) {
                        player.giveExperiencePoints(xp);
                        totalXpAwarded += xp;
                    }
                }
            }
        }

        // Always award a small baseline XP to simulate picking up ambient orbs
        // during the "forgotten" time (mob deaths, smelting, etc.)
        int baselineXp = 3 + RANDOM.nextInt(13); // 3-15 XP
        player.giveExperiencePoints(baselineXp);
        totalXpAwarded += baselineXp;

        if (totalXpAwarded > 0) {
            ColdSpawnControl.LOGGER.info("Blackout: awarded {} total XP to {}",
                    totalXpAwarded, player.getName().getString());
        }
    }

    /**
     * Returns XP appropriate for the simulated tool activity.
     * Pickaxe: 1-7 XP (mining coal/redstone/lapis ores)
     * Sword: 3-12 XP (killing hostile mobs, 1-5 XP per mob, simulates 1-3 kills)
     * Axe/Shovel: 0 XP (wood chopping and digging don't yield XP in vanilla)
     */
    private static int getToolXpReward(ItemStack tool, String dimension) {
        String itemName = tool.getItem().toString().toLowerCase();

        if (itemName.contains("pickaxe")) {
            if (dimension.equals("the_nether")) {
                return 1 + RANDOM.nextInt(4); // Quartz/gold XP
            }
            return 1 + RANDOM.nextInt(7); // Overworld/End ores
        } else if (itemName.contains("sword")) {
            if (dimension.equals("the_end")) {
                return 5 + RANDOM.nextInt(15); // Endermen give more XP
            }
            return 3 + RANDOM.nextInt(10); // Normal mobs
        }

        return 0;
    }

    private static ItemStack getToolReward(ItemStack tool, String dimension) {
        String itemName = tool.getItem().toString().toLowerCase();

        if (itemName.contains("pickaxe")) {
            if (dimension.equals("the_nether")) {
                return switch (RANDOM.nextInt(3)) {
                    case 0 -> new ItemStack(Items.NETHERRACK, 2 + RANDOM.nextInt(5));
                    case 1 -> new ItemStack(Items.QUARTZ, 1 + RANDOM.nextInt(3));
                    case 2 -> new ItemStack(Items.GOLD_NUGGET, 2 + RANDOM.nextInt(6));
                    default -> new ItemStack(Items.NETHERRACK, 4);
                };
            } else if (dimension.equals("the_end")) {
                return switch (RANDOM.nextInt(2)) {
                    case 0 -> new ItemStack(Items.END_STONE, 2 + RANDOM.nextInt(5));
                    case 1 -> new ItemStack(Items.OBSIDIAN, 1);
                    default -> new ItemStack(Items.END_STONE, 3);
                };
            } else {
                return switch (RANDOM.nextInt(3)) {
                    case 0 -> new ItemStack(Items.COAL, 1 + RANDOM.nextInt(3));
                    case 1 -> new ItemStack(Items.COBBLESTONE, 2 + RANDOM.nextInt(4));
                    default -> new ItemStack(Items.RAW_IRON, 1);
                };
            }
        } else if (itemName.contains("axe") && !itemName.contains("pickaxe")) {
            if (dimension.equals("the_nether")) {
                return switch (RANDOM.nextInt(2)) {
                    case 0 -> new ItemStack(Items.CRIMSON_STEM, 2 + RANDOM.nextInt(3));
                    case 1 -> new ItemStack(Items.WARPED_STEM, 2 + RANDOM.nextInt(3));
                    default -> new ItemStack(Items.CRIMSON_STEM, 2);
                };
            } else if (dimension.equals("the_end")) {
                return new ItemStack(Items.CHORUS_FRUIT, 1 + RANDOM.nextInt(3)); // No wood, maybe fruit chopped?
            } else {
                return new ItemStack(Items.SPRUCE_LOG, 2 + RANDOM.nextInt(5));
            }
        } else if (itemName.contains("sword")) {
            if (dimension.equals("the_nether")) {
                return switch (RANDOM.nextInt(3)) {
                    case 0 -> new ItemStack(Items.ROTTEN_FLESH, 1 + RANDOM.nextInt(2)); // Piglins
                    case 1 -> new ItemStack(Items.BONE, 1 + RANDOM.nextInt(3)); // Wither skeletons
                    case 2 -> new ItemStack(Items.MAGMA_CREAM, 1);
                    default -> new ItemStack(Items.GOLD_NUGGET, 1);
                };
            } else if (dimension.equals("the_end")) {
                return new ItemStack(Items.ENDER_PEARL, 1 + RANDOM.nextInt(2));
            } else {
                return switch (RANDOM.nextInt(3)) {
                    case 0 -> new ItemStack(Items.ROTTEN_FLESH, 1 + RANDOM.nextInt(2));
                    case 1 -> new ItemStack(Items.GUNPOWDER, 1);
                    default -> new ItemStack(Items.STRING, 1 + RANDOM.nextInt(2));
                };
            }
        } else if (itemName.contains("shovel")) {
            if (dimension.equals("the_nether")) {
                return switch (RANDOM.nextInt(3)) {
                    case 0 -> new ItemStack(Items.NETHERRACK, 2 + RANDOM.nextInt(4)); // Sometimes shovel works on
                                                                                      // netherrack
                    case 1 -> new ItemStack(Items.SOUL_SAND, 1 + RANDOM.nextInt(3));
                    case 2 -> new ItemStack(Items.SOUL_SOIL, 1 + RANDOM.nextInt(3));
                    default -> new ItemStack(Items.SOUL_SAND, 2);
                };
            } else if (dimension.equals("the_end")) {
                return new ItemStack(Items.END_STONE, 1 + RANDOM.nextInt(2));
            } else {
                return switch (RANDOM.nextInt(4)) {
                    case 0 -> new ItemStack(Items.DIRT, 2 + RANDOM.nextInt(4));
                    case 1 -> new ItemStack(Items.GRAVEL, 1 + RANDOM.nextInt(3));
                    case 2 -> new ItemStack(Items.FLINT, 1);
                    default -> new ItemStack(Items.SAND, 1 + RANDOM.nextInt(3));
                };
            }
        }

        return ItemStack.EMPTY;
    }

    /**
     * Calculate core temperature using NBT data from Cold Sweat.
     */
    /**
     * Calculate core temperature using NBT data from Cold Sweat.
     * Delegates to the shared utility.
     */
    private static double calculateInsulation(ServerPlayer player) {
        return com.maxsters.coldspawncontrol.util.InsulationCalculator.calculateInsulation(player);
    }

    /**
     * 10% shuffle blocks, 10% remove 5-10 blocks.
     */
    private static void applyInventoryEffects(ServerPlayer player) {
        List<Integer> blockSlots = new ArrayList<>();

        // Find all block items (excluding light sources like torches)
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.BlockItem) {
                // Skip light sources - player needs these to survive in darkness
                String itemId = stack.getItem().getDescriptionId();
                if (itemId.contains("torch") || itemId.contains("lantern") ||
                        itemId.contains("candle") || itemId.contains("campfire") ||
                        itemId.contains("glowstone") || itemId.contains("shroomlight") ||
                        itemId.contains("sea_lantern") || itemId.contains("jack_o_lantern")) {
                    continue;
                }
                blockSlots.add(i);
            }
        }

        if (blockSlots.isEmpty())
            return;

        // 10% chance: shuffle blocks
        if (RANDOM.nextDouble() < 0.10) {
            List<ItemStack> blocks = new ArrayList<>();
            for (int slot : blockSlots) {
                blocks.add(player.getInventory().getItem(slot).copy());
                player.getInventory().setItem(slot, ItemStack.EMPTY);
            }
            Collections.shuffle(blocks);
            Collections.shuffle(blockSlots);

            for (int i = 0; i < blocks.size() && i < blockSlots.size(); i++) {
                player.getInventory().setItem(blockSlots.get(i), blocks.get(i));
            }
        }

        // 10% chance: remove 5-10 blocks
        if (RANDOM.nextDouble() < 0.10) {
            int toRemove = 5 + RANDOM.nextInt(6);
            for (int i = 0; i < toRemove && !blockSlots.isEmpty(); i++) {
                int slotIndex = RANDOM.nextInt(blockSlots.size());
                int slot = blockSlots.get(slotIndex);
                ItemStack stack = player.getInventory().getItem(slot);

                if (!stack.isEmpty()) {
                    stack.shrink(1);
                    if (stack.isEmpty()) {
                        blockSlots.remove(slotIndex);
                    }
                }
            }
        }
    }

    /**
     * Remove 1-3 food items and 0-2 hunger bars.
     */
    private static void applyHungerEffects(ServerPlayer player) {
        // Remove food items
        int foodToRemove = 1 + RANDOM.nextInt(3);
        for (int i = 0; i < player.getInventory().getContainerSize() && foodToRemove > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (!stack.isEmpty() && stack.getItem().isEdible()) {
                int removeCount = Math.min(stack.getCount(), foodToRemove);
                stack.shrink(removeCount);
                foodToRemove -= removeCount;
            }
        }

        // Reduce hunger (deplete saturation first so hunger loss is visible)
        FoodData food = player.getFoodData();
        food.setSaturation(0.0F); // Clear saturation so hunger bar actually drops
        int hungerLoss = RANDOM.nextInt(3); // 0-2
        food.setFoodLevel(Math.max(0, food.getFoodLevel() - hungerLoss));
    }

    /**
     * Adjust core temperature based on destination ambient temp and distance.
     */
    private static void adjustTemperature(ServerPlayer player, ServerLevel level, BlockPos destination,
            int teleportDistance) {
        try {
            // Get destination ambient temp (negative = cold in Cold Sweat internal units)
            double destinationAmbientTemp = WorldHelper.getTemperatureAt(level, destination);

            // Only apply cold effects if the destination is genuinely cold
            if (destinationAmbientTemp >= 0) {
                ColdSpawnControl.LOGGER.debug("Blackout temp: destination not cold enough ({})",
                        destinationAmbientTemp);
                return;
            }

            double insulation = calculateInsulation(player);

            // Calculate protection factor (0.33 = full protection, 1.0 = no protection)
            double protectionFactor = Math.max(0.33, 1.0 - (insulation * 0.67));

            // Calculate cold exposure based on distance traveled
            double distanceRatio = Math.min(1.0, teleportDistance / 1000.0); // Cap at 1000 blocks
            double ambientColdness = Math.abs(destinationAmbientTemp);

            // Base exposure: 48 units at 1000 blocks in extreme cold
            double extremeColdReference = 2.5;
            double baseExposure = distanceRatio * (ambientColdness / extremeColdReference) * 48.0;

            // Apply protection factor
            double coldExposure = baseExposure * protectionFactor;
            double tempChange = -coldExposure;

            // Apply the temperature change
            if (Math.abs(tempChange) > 0.01) {
                double currentBodyTemp = Temperature.get(player, Trait.CORE);
                Temperature.add(player, Trait.CORE, tempChange);
                ColdSpawnControl.LOGGER.info(
                        "Blackout temp: dist={}, ambient={}, protection={}, body={} -> {} (change: {})",
                        teleportDistance, destinationAmbientTemp, protectionFactor,
                        currentBodyTemp, currentBodyTemp + tempChange, tempChange);
            }
        } catch (Exception e) {
            ColdSpawnControl.LOGGER.debug("Cold Sweat temperature adjustment failed: {}", e.getMessage());
        }
    }

    /**
     * Rolls for disorientation effects and sends them to the client.
     * Each effect has a 25% independent chance:
     * - FOV shift: changes FOV by ±1 to ±10 points
     * - Key swap: inverts one movement axis (left/right or forward/back)
     */
    private static void sendDisorientationEffects(ServerPlayer player) {
        int fovDelta = 0;
        int swapAxis = -1;

        // 25% chance: FOV shift
        if (RANDOM.nextInt(4) == 0) {
            // ±1 to ±10, never 0
            fovDelta = (RANDOM.nextInt(10) + 1) * (RANDOM.nextBoolean() ? 1 : -1);
            ColdSpawnControl.LOGGER.info("Blackout: applying FOV shift of {}", fovDelta);
        }

        // 25% chance: swap movement axis
        if (RANDOM.nextInt(4) == 0) {
            swapAxis = RANDOM.nextInt(2); // 0 = left/right, 1 = forward/back
            ColdSpawnControl.LOGGER.info("Blackout: swapping movement axis {}",
                    swapAxis == 0 ? "left/right" : "forward/back");
        }

        // Only send packet if at least one effect triggered
        if (fovDelta != 0 || swapAxis >= 0) {
            Networking.sendToPlayer(new AmnesiaDisorientPacket(fovDelta, swapAxis), player);
        }
    }

    // ==================== NEW AMNESIA EFFECTS ====================

    /**
     * Randomizes the fake day counter displayed in F3.
     */
    private static void randomizeFakeDay(ServerPlayer player) {
        int fakeDay = RANDOM.nextInt(10000); // 0-9999
        player.getPersistentData().putInt("amnesia_fake_day", fakeDay);
        AmnesiaNetworkHandler.sendFakeDayToClient(player, fakeDay);
        ColdSpawnControl.LOGGER.info("Randomized fake day to: {}", fakeDay);
    }

    /**
     * Randomizes ALL player statistics to reinforce the amnesia/forgetting theme.
     * The player should not be able to trust their statistics screen either.
     *
     * Always seeds values regardless of current value - a "survivor" who has
     * been here for a while would have stats across the board.
     *
     * Covers:
     * - Custom stats (time played, distance walked/sprinted/swum, jumps, deaths,
     * etc.)
     * - Block stats (mined)
     * - Item stats (crafted, used, broken, picked_up, dropped)
     * - Entity stats (killed, killed_by)
     *
     * Public so it can be called from FirstJoinHandler.
     */
    @SuppressWarnings("unchecked")
    public static void randomizePlayerStatistics(ServerPlayer player) {
        ServerStatsCounter statsCounter = player.getStats();
        int totalRandomized = 0;

        // ---- 1. Custom stats - ALWAYS seed all of them ----
        for (ResourceLocation statId : Registry.CUSTOM_STAT) {
            Stat<ResourceLocation> stat = Stats.CUSTOM.get(statId);
            int newValue = randomizeStatValue(statId);
            statsCounter.setValue(player, stat, newValue);
            totalRandomized++;
        }

        // ---- 2. Block mined stats - seed common survival blocks ----
        Block[] commonBlocks = {
                Blocks.STONE, Blocks.COBBLESTONE, Blocks.DIRT, Blocks.GRAVEL,
                Blocks.SAND, Blocks.OAK_LOG, Blocks.SPRUCE_LOG,
                Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.COAL_ORE,
                Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE, Blocks.DEEPSLATE_COAL_ORE,
                Blocks.COPPER_ORE, Blocks.GOLD_ORE, Blocks.DEEPSLATE,
                Blocks.GRASS_BLOCK, Blocks.SNOW, Blocks.SNOW_BLOCK, Blocks.ICE,
                Blocks.PACKED_ICE, Blocks.ANDESITE, Blocks.GRANITE, Blocks.DIORITE,
                Blocks.TUFF, Blocks.CLAY, Blocks.GRAVEL, Blocks.OAK_LEAVES,
                Blocks.SPRUCE_LEAVES, Blocks.CRAFTING_TABLE, Blocks.FURNACE,
                Blocks.CHEST, Blocks.TORCH, Blocks.COBBLESTONE_STAIRS,
                Blocks.OAK_DOOR, Blocks.SPRUCE_DOOR, Blocks.LADDER
        };
        for (Block block : commonBlocks) {
            Stat<Block> stat = Stats.BLOCK_MINED.get(block);
            int newValue = RANDOM.nextInt(500) + 5; // 5-504 blocks mined
            statsCounter.setValue(player, stat, newValue);
            totalRandomized++;
        }
        // Also randomize any blocks that already have a value
        for (Block block : ForgeRegistries.BLOCKS) {
            Stat<Block> stat = Stats.BLOCK_MINED.get(block);
            int currentValue = statsCounter.getValue(stat);
            if (currentValue > 0) {
                statsCounter.setValue(player, stat, randomizeCountStat(currentValue));
                totalRandomized++;
            }
        }

        // ---- 3. Item stats - seed common survival items ----
        Item[] commonItems = {
                Items.COBBLESTONE, Items.STONE, Items.DIRT, Items.OAK_LOG,
                Items.SPRUCE_LOG, Items.OAK_PLANKS, Items.SPRUCE_PLANKS,
                Items.STICK, Items.COAL, Items.RAW_IRON, Items.IRON_INGOT,
                Items.RAW_COPPER, Items.COPPER_INGOT, Items.BREAD,
                Items.TORCH, Items.CRAFTING_TABLE, Items.FURNACE,
                Items.CHEST, Items.IRON_PICKAXE, Items.IRON_AXE,
                Items.IRON_SWORD, Items.IRON_SHOVEL, Items.WOODEN_PICKAXE,
                Items.STONE_PICKAXE, Items.STONE_AXE, Items.COOKED_BEEF,
                Items.COOKED_PORKCHOP, Items.ROTTEN_FLESH, Items.BONE,
                Items.STRING, Items.GUNPOWDER, Items.ARROW, Items.BOW,
                Items.LEATHER, Items.FLINT, Items.FLINT_AND_STEEL,
                Items.BUCKET, Items.WATER_BUCKET, Items.SNOWBALL,
                Items.SNOW_BLOCK, Items.ICE, Items.PACKED_ICE,
                Items.LADDER, Items.BOWL, Items.MUSHROOM_STEW,
                Items.IRON_NUGGET, Items.RAW_GOLD, Items.GOLD_INGOT,
                Items.OAK_DOOR, Items.SPRUCE_DOOR
        };
        for (Item item : commonItems) {
            // Crafted (50% chance per item)
            if (RANDOM.nextBoolean()) {
                statsCounter.setValue(player, Stats.ITEM_CRAFTED.get(item),
                        RANDOM.nextInt(200) + 1);
                totalRandomized++;
            }
            // Used (70% chance per item)
            if (RANDOM.nextInt(10) < 7) {
                statsCounter.setValue(player, Stats.ITEM_USED.get(item),
                        RANDOM.nextInt(300) + 1);
                totalRandomized++;
            }
            // Picked up (80% chance per item)
            if (RANDOM.nextInt(10) < 8) {
                statsCounter.setValue(player, Stats.ITEM_PICKED_UP.get(item),
                        RANDOM.nextInt(500) + 1);
                totalRandomized++;
            }
            // Dropped (30% chance per item)
            if (RANDOM.nextInt(10) < 3) {
                statsCounter.setValue(player, Stats.ITEM_DROPPED.get(item),
                        RANDOM.nextInt(50) + 1);
                totalRandomized++;
            }
        }
        // Broken tools specifically
        Item[] breakableItems = {
                Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SWORD,
                Items.IRON_SHOVEL, Items.STONE_PICKAXE, Items.STONE_AXE,
                Items.WOODEN_PICKAXE, Items.BOW
        };
        for (Item item : breakableItems) {
            if (RANDOM.nextBoolean()) {
                statsCounter.setValue(player, Stats.ITEM_BROKEN.get(item),
                        RANDOM.nextInt(15) + 1);
                totalRandomized++;
            }
        }
        // Also randomize any items that already have values
        for (Item item : ForgeRegistries.ITEMS) {
            for (net.minecraft.stats.StatType<Item> statType : new net.minecraft.stats.StatType[] {
                    Stats.ITEM_CRAFTED, Stats.ITEM_USED, Stats.ITEM_BROKEN,
                    Stats.ITEM_PICKED_UP, Stats.ITEM_DROPPED }) {
                Stat<Item> stat = statType.get(item);
                int currentValue = statsCounter.getValue(stat);
                if (currentValue > 0) {
                    statsCounter.setValue(player, stat, randomizeCountStat(currentValue));
                    totalRandomized++;
                }
            }
        }

        // ---- 4. Entity stats - seed common mobs ----
        EntityType<?>[] commonKilled = {
                EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
                EntityType.CREEPER, EntityType.ENDERMAN, EntityType.WITCH,
                EntityType.CAVE_SPIDER, EntityType.DROWNED, EntityType.HUSK,
                EntityType.STRAY, EntityType.COW, EntityType.PIG,
                EntityType.SHEEP, EntityType.CHICKEN, EntityType.RABBIT
        };
        for (EntityType<?> entityType : commonKilled) {
            // Killed
            statsCounter.setValue(player, Stats.ENTITY_KILLED.get(entityType),
                    RANDOM.nextInt(80) + 1);
            totalRandomized++;
        }
        // Killed by (only hostile mobs, lower counts)
        EntityType<?>[] killedByMobs = {
                EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SPIDER,
                EntityType.CREEPER, EntityType.DROWNED, EntityType.STRAY
        };
        for (EntityType<?> entityType : killedByMobs) {
            if (RANDOM.nextBoolean()) {
                statsCounter.setValue(player, Stats.ENTITY_KILLED_BY.get(entityType),
                        RANDOM.nextInt(5) + 1);
                totalRandomized++;
            }
        }
        // Also randomize any entities that already have values
        for (EntityType<?> entityType : ForgeRegistries.ENTITY_TYPES) {
            Stat<EntityType<?>> killed = Stats.ENTITY_KILLED.get(entityType);
            if (statsCounter.getValue(killed) > 0) {
                statsCounter.setValue(player, killed, randomizeCountStat(statsCounter.getValue(killed)));
                totalRandomized++;
            }
            Stat<EntityType<?>> killedBy = Stats.ENTITY_KILLED_BY.get(entityType);
            if (statsCounter.getValue(killedBy) > 0) {
                statsCounter.setValue(player, killedBy, randomizeCountStat(statsCounter.getValue(killedBy)));
                totalRandomized++;
            }
        }

        // Force the server to send updated stats to the client
        player.getStats().markAllDirty();
        player.getStats().sendStats(player);

        ColdSpawnControl.LOGGER.info("Randomized {} player statistics for {}",
                totalRandomized, player.getName().getString());
    }

    /**
     * Randomizes a custom stat value based on the stat's ResourceLocation.
     * Always generates a plausible value - does not need a current value.
     */
    private static int randomizeStatValue(ResourceLocation statId) {
        String path = statId.getPath();

        // Time-based stats (measured in ticks) - randomize to 1-200 hours worth
        if (path.contains("time") || path.contains("play") || path.equals("total_world_time")
                || path.contains("since_")) {
            return (RANDOM.nextInt(200) + 1) * 72000; // 1-200 hours in ticks
        }

        // Distance stats (measured in cm) - randomize to 100m-500km
        if (path.contains("one_cm") || path.contains("walk") || path.contains("sprint")
                || path.contains("swim") || path.contains("fly") || path.contains("climb")
                || path.contains("fall") || path.contains("crouch") || path.contains("boat")
                || path.contains("horse") || path.contains("pig") || path.contains("minecart")
                || path.contains("aviate") || path.contains("strider")) {
            return (RANDOM.nextInt(50000) + 100) * 100; // 100m to 500km in cm
        }

        // Death count - keep low but randomized
        if (path.contains("death")) {
            return RANDOM.nextInt(30) + 1; // 1-30 deaths
        }

        // Damage dealt/taken (measured in tenths of hearts)
        if (path.contains("damage")) {
            return (RANDOM.nextInt(5000) + 100) * 10; // 100-5000 hearts
        }

        // Jump count
        if (path.equals("jump")) {
            return RANDOM.nextInt(50000) + 1000; // 1000-50000 jumps
        }

        // Sleep count
        if (path.contains("sleep")) {
            return RANDOM.nextInt(100) + 1; // 1-100 sleeps
        }

        // Interaction counts (open chest, searched containers, etc.)
        if (path.contains("open") || path.contains("search") || path.contains("trigger")
                || path.contains("use") || path.contains("fill") || path.contains("clean")
                || path.contains("inspect") || path.contains("interact") || path.contains("eat")
                || path.contains("enchant") || path.contains("talked") || path.contains("traded")) {
            return RANDOM.nextInt(200) + 1; // 1-200
        }

        // Everything else - generate a plausible value
        return RANDOM.nextInt(500) + 1;
    }

    /**
     * Randomizes a count-based stat (blocks mined, items crafted, entities killed,
     * etc.).
     * Uses the current value as an anchor but applies significant variance.
     */
    private static int randomizeCountStat(int currentValue) {
        // Randomize between 25% and 300% of the current value, minimum 1
        int min = Math.max(1, currentValue / 4);
        int max = Math.max(min + 1, currentValue * 3);
        return RANDOM.nextInt(max - min + 1) + min;
    }

    /**
     * Resets player to iron-age progression.
     * Revokes post-iron advancements and re-grants iron-age baseline.
     */
    private static void resetToIronAge(ServerPlayer player) {
        var server = player.getServer();
        if (server == null)
            return;

        var advancementManager = server.getAdvancements();
        var playerAdvancements = player.getAdvancements();

        // Revoke post-iron advancements
        int revokedCount = 0;
        for (ResourceLocation advancementId : AmnesiaAdvancementConfig.REVOKE_ON_BLACKOUT) {
            Advancement advancement = advancementManager.getAdvancement(advancementId);
            if (advancement == null)
                continue;

            // Check if player has this advancement
            var progress = playerAdvancements.getOrStartProgress(advancement);
            if (progress.isDone()) {
                // Revoke all criteria
                for (String criterion : advancement.getCriteria().keySet()) {
                    playerAdvancements.revoke(advancement, criterion);
                }
                revokedCount++;
            }
        }

        // Re-grant iron-age baseline
        isResetting = true;
        try {
            for (ResourceLocation advancementId : AmnesiaAdvancementConfig.IRON_AGE_BASELINE) {
                Advancement advancement = advancementManager.getAdvancement(advancementId);
                if (advancement == null)
                    continue;

                for (String criterion : advancement.getCriteria().keySet()) {
                    playerAdvancements.award(advancement, criterion);
                }
            }
        } finally {
            isResetting = false;
        }

        if (revokedCount > 0) {
            ColdSpawnControl.LOGGER.info("Reset to iron age: revoked {} advancements", revokedCount);
        }
    }

    /**
     * Shuffles names of named entities within 64 blocks of the destination.
     */
    private static void shuffleNearbyNamedEntities(ServerPlayer player, ServerLevel level) {
        List<LivingEntity> namedEntities = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(player.blockPosition()).inflate(64),
                entity -> entity.hasCustomName() &&
                        (entity instanceof TamableAnimal || !entity.getType().getCategory().isFriendly()));

        int renamedCount = 0;
        for (LivingEntity entity : namedEntities) {
            // Skip hostile mobs unless they're tamed
            if (!(entity instanceof TamableAnimal)) {
                continue;
            }

            String newName = AmnesiaNameList.getRandomName();
            entity.setCustomName(Component.literal(newName));
            renamedCount++;
        }

        if (renamedCount > 0) {
            ColdSpawnControl.LOGGER.info("Renamed {} nearby entities", renamedCount);
        }
    }

    /**
     * Injects random items into nearby chests (100% chance per chest).
     * Simulates items the player placed during the "forgotten" time.
     * Skips chests with hoppers above to avoid breaking redstone.
     */
    private static void injectChestContents(ServerPlayer player, ServerLevel level, BlockPos destination) {
        // Boosted: 100% chance base

        int searchRadius = 32;
        int injectedChests = 0;

        for (int dx = -searchRadius; dx <= searchRadius; dx += 4) {
            for (int dy = -8; dy <= 8; dy += 2) {
                for (int dz = -searchRadius; dz <= searchRadius; dz += 4) {
                    BlockPos checkPos = destination.offset(dx, dy, dz);

                    if (!level.isLoaded(checkPos))
                        continue;

                    var blockEntity = level.getBlockEntity(checkPos);
                    if (!(blockEntity instanceof ChestBlockEntity chest))
                        continue;

                    // Skip if hopper above
                    BlockPos abovePos = checkPos.above();
                    if (level.getBlockState(abovePos).getBlock() instanceof HopperBlock) {
                        continue;
                    }

                    // Boosted: 100% chance per chest

                    // Inject 3-6 random items
                    int itemCount = 3 + RANDOM.nextInt(4);
                    for (int i = 0; i < itemCount; i++) {
                        ItemStack toInject = getRandomForgottenItem();
                        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
                            if (chest.getItem(slot).isEmpty()) {
                                chest.setItem(slot, toInject);
                                break;
                            }
                        }
                    }
                    injectedChests++;

                    if (injectedChests >= 5)
                        return; // Cap at 5 chests
                }
            }
        }

        if (injectedChests > 0) {
            ColdSpawnControl.LOGGER.info("Injected items into {} chests", injectedChests);
        }
    }

    /**
     * Gets a random "forgotten" item that the player might have collected.
     */
    private static ItemStack getRandomForgottenItem() {
        return switch (RANDOM.nextInt(15)) {
            case 0 -> new ItemStack(Items.COAL, 1 + RANDOM.nextInt(4));
            case 1 -> new ItemStack(Items.RAW_IRON, 1 + RANDOM.nextInt(2));
            case 2 -> new ItemStack(Items.RAW_COPPER, 1 + RANDOM.nextInt(3));
            case 3 -> new ItemStack(Items.COBBLESTONE, 2 + RANDOM.nextInt(8));
            case 4 -> new ItemStack(Items.STICK, 1 + RANDOM.nextInt(4));
            case 5 -> new ItemStack(Items.OAK_PLANKS, 1 + RANDOM.nextInt(4));
            case 6 -> new ItemStack(Items.LEATHER, 1);
            case 7 -> new ItemStack(Items.STRING, 1 + RANDOM.nextInt(2));
            case 8 -> new ItemStack(Items.BONE, 1 + RANDOM.nextInt(3));
            case 9 -> new ItemStack(Items.ROTTEN_FLESH, 1 + RANDOM.nextInt(2));
            case 10 -> new ItemStack(Items.SNOWBALL, 2 + RANDOM.nextInt(6));
            case 11 -> new ItemStack(Items.FLINT, 1);
            case 12 -> new ItemStack(Items.GRAVEL, 1 + RANDOM.nextInt(3));
            case 13 -> new ItemStack(Items.DIRT, 2 + RANDOM.nextInt(4));
            default -> new ItemStack(Items.FEATHER, 1 + RANDOM.nextInt(2));
        };
    }

    /**
     * Toggles nearby doors (open/close) with 50% chance.
     * Simulates player activity or wind.
     */
    private static void toggleNearbyDoors(ServerLevel level, BlockPos center) {
        if (RANDOM.nextDouble() > 0.5)
            return; // 50% chance for the effect to happen at all

        int radius = 16;
        int toggledDoors = 0;

        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 4; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = center.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);

                    if (state.getBlock() instanceof DoorBlock) {
                        // Only act on the lower half to avoid double-processing
                        if (state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER) {
                            // 50% chance to toggle this specific door
                            if (RANDOM.nextBoolean()) {
                                boolean isOpen = state.getValue(DoorBlock.OPEN);
                                DoorBlock door = (DoorBlock) state.getBlock();
                                door.setOpen(null, level, state, pos, !isOpen);
                                toggledDoors++;
                            }
                        }
                    }
                }
            }
        }

        if (toggledDoors > 0) {
            ColdSpawnControl.LOGGER.info("Toggled {} doors", toggledDoors);
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onPlayerLoggedOut(
            net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        ACTIVE_CONFUSION.remove(uuid);
        PENDING_BLACKOUTS.remove(uuid);
        com.maxsters.coldspawncontrol.slm.JournalEntryGenerator.cleanUpPlayer(uuid);
    }
}
