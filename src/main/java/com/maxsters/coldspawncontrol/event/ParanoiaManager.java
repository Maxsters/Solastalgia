package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.entity.ShadowFlickerEntity;
import com.maxsters.coldspawncontrol.network.Networking;
import com.maxsters.coldspawncontrol.network.PositionalSoundPacket;
import com.maxsters.coldspawncontrol.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LightLayer;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * Server-side manager for ambient paranoia effects that require entity spawning
 * or server-side event handling.
 * 
 * Handles: Shadow Flickers, Wrong Block Sounds, False Hostile Indicators,
 * and Fake Mob Sounds.
 */
@SuppressWarnings("null")
@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public class ParanoiaManager {

    private static final Random RANDOM = new Random();

    // ==================== TICK-BASED EFFECTS ====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level.isClientSide) {
            return;
        }

        // Run every 100 ticks (5 seconds) per player
        if (event.player.tickCount % 100 != 0) {
            return;
        }

        ServerPlayer player = (ServerPlayer) event.player;
        ServerLevel level = (ServerLevel) player.level;

        // Shadow Flicker Logic
        // 0.1% chance per 5 seconds (very rare)
        if (RANDOM.nextFloat() < 0.001f) {
            trySpawnShadow(player, level);
        }

        // False Hostile Indicator
        // 0.05% chance per 5 seconds (~every 2.8 hours on average)
        if (RANDOM.nextFloat() < 0.0005f) {
            triggerFalseHostile(player);
        }

        // Fake Mob Sound
        // 0.08% chance per 5 seconds (~every 1.7 hours on average)
        if (RANDOM.nextFloat() < 0.0008f) {
            triggerFakeMobSound(player);
        }

        // Subtitle Hallucination
        // 0.2% chance per 5 seconds (~every 40 minutes on average)
        if (RANDOM.nextFloat() < 0.002f) {
            triggerRandomSubtitleHallucination(player);
        }
    }

    // ==================== WRONG BLOCK SOUNDS ====================

    /**
     * Listens for block break events and very rarely plays the wrong
     * block's break sound at a slightly offset position.
     * Creates a subtle "that didn't sound right" moment.
     */
    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() == null || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }

        if (player.level.isClientSide) {
            return;
        }

        // 0.5% chance per block break
        if (RANDOM.nextFloat() >= 0.005f) {
            return;
        }

        BlockPos pos = event.getPos();
        triggerWrongBlockSound(player, pos);
    }

    /**
     * Plays a random wrong block sound at a slightly offset position from the
     * broken block.
     */
    public static void triggerWrongBlockSound(ServerPlayer player, BlockPos brokenPos) {
        // Pick a random wrong block type's sound
        SoundEvent wrongSound = getRandomBlockBreakSound();

        // Slightly offset position (1-2 blocks away)
        double ox = brokenPos.getX() + 0.5 + (RANDOM.nextDouble() - 0.5) * 3.0;
        double oy = brokenPos.getY() + 0.5 + (RANDOM.nextDouble() - 0.5) * 1.0;
        double oz = brokenPos.getZ() + 0.5 + (RANDOM.nextDouble() - 0.5) * 3.0;

        float volume = 0.6f + RANDOM.nextFloat() * 0.3f;
        float pitch = 0.7f + RANDOM.nextFloat() * 0.3f; // Slightly off-pitch for eeriness

        Networking.sendToPlayer(new PositionalSoundPacket(wrongSound, ox, oy, oz, volume, pitch), player);

        ColdSpawnControl.LOGGER.debug("[Paranoia] Wrong block sound: {} at ({}, {}, {})",
                wrongSound.getLocation(), (int) ox, (int) oy, (int) oz);
    }

    /**
     * Returns a random block break sound that will feel "wrong".
     * Picks from a curated list of distinctive, recognizable sounds.
     */
    private static SoundEvent getRandomBlockBreakSound() {
        SoundEvent[] wrongSounds = {
                SoundEvents.GLASS_BREAK,
                SoundEvents.BONE_BLOCK_BREAK,
                SoundEvents.ANCIENT_DEBRIS_BREAK,
                SoundEvents.CHAIN_BREAK,
                SoundEvents.DEEPSLATE_BREAK,
                SoundEvents.NETHER_BRICKS_BREAK,
                SoundEvents.AMETHYST_BLOCK_BREAK,
                SoundEvents.HONEY_BLOCK_BREAK,
                SoundEvents.CORAL_BLOCK_BREAK,
                SoundEvents.METAL_BREAK,
        };
        return wrongSounds[RANDOM.nextInt(wrongSounds.length)];
    }

    // ==================== FALSE HOSTILE INDICATOR ====================

    /**
     * Plays a hostile mob targeting sound (enderman stare, guardian attack, etc.)
     * at a nearby dark position, with no actual mob present.
     * Creates the panic of feeling targeted by something invisible.
     */
    public static void triggerFalseHostile(ServerPlayer player) {
        // Prevent if player is in water
        if (player.isUnderWater()) {
            return;
        }

        // Choose from threatening "targeting" sounds
        SoundEvent[] hostileSounds = {
                SoundEvents.ENDERMAN_STARE,
                SoundEvents.GUARDIAN_ATTACK,
                SoundEvents.PHANTOM_BITE,
                SoundEvents.WARDEN_NEARBY_CLOSEST,
        };
        SoundEvent sound = hostileSounds[RANDOM.nextInt(hostileSounds.length)];

        // Position 5-10 blocks behind/beside player
        double dist = 5.0 + RANDOM.nextDouble() * 5.0;
        double[] pos = getPositionBehindPlayer(player, dist);

        float volume = 0.3f + RANDOM.nextFloat() * 0.2f; // Quiet enough to question
        float pitch = 0.8f + RANDOM.nextFloat() * 0.2f;

        Networking.sendToPlayer(new PositionalSoundPacket(sound, pos[0], pos[1], pos[2], volume, pitch), player);

        ColdSpawnControl.LOGGER.debug("[Paranoia] False hostile: {} for {}",
                sound.getLocation(), player.getName().getString());
    }

    // ==================== FAKE MOB SOUND ====================

    /**
     * Plays a generic mob presence sound (creeper hiss, zombie groan, skeleton
     * rattle, etc.) behind the player. The panic of hearing a mob with no source.
     */
    public static void triggerFakeMobSound(ServerPlayer player) {
        // Prevent if player is in water
        if (player.isUnderWater()) {
            return;
        }

        // Choose from ambient mob sounds
        SoundEvent[] mobSounds = {
                SoundEvents.CREEPER_PRIMED,
                SoundEvents.ZOMBIE_AMBIENT,
                SoundEvents.SKELETON_AMBIENT,
                SoundEvents.SPIDER_AMBIENT,
                SoundEvents.SPIDER_STEP,
                SoundEvents.ENDERMAN_AMBIENT,
                SoundEvents.WITCH_AMBIENT,
        };
        SoundEvent sound = mobSounds[RANDOM.nextInt(mobSounds.length)];

        // Position 8-14 blocks behind player (close enough to hear, far enough to not
        // find)
        double dist = 8.0 + RANDOM.nextDouble() * 6.0;
        double[] pos = getPositionBehindPlayer(player, dist);

        float volume = 0.4f + RANDOM.nextFloat() * 0.3f;
        float pitch = 0.85f + RANDOM.nextFloat() * 0.15f; // Slightly lower pitch for eeriness

        Networking.sendToPlayer(new PositionalSoundPacket(sound, pos[0], pos[1], pos[2], volume, pitch), player);

        ColdSpawnControl.LOGGER.debug("[Paranoia] Fake mob sound: {} for {}",
                sound.getLocation(), player.getName().getString());
    }

    // ==================== SUBTITLE HALLUCINATION ====================

    /**
     * Triggers a random silent subtitle hallucination based on the emotions
     * defined for the SLM model memory.
     */
    public static void triggerRandomSubtitleHallucination(ServerPlayer player) {
        SoundEvent[] subtitleSounds = {
                com.maxsters.coldspawncontrol.registry.ModSoundEvents.SUBTITLE_CONFUSION.get(),
                com.maxsters.coldspawncontrol.registry.ModSoundEvents.SUBTITLE_GRIEF.get(),
                com.maxsters.coldspawncontrol.registry.ModSoundEvents.SUBTITLE_DISORIENTATION.get(),
                com.maxsters.coldspawncontrol.registry.ModSoundEvents.SUBTITLE_DESPAIR.get(),
                com.maxsters.coldspawncontrol.registry.ModSoundEvents.SUBTITLE_MUSCLE_MEMORY.get(),
                com.maxsters.coldspawncontrol.registry.ModSoundEvents.SUBTITLE_PARANOIA.get(),
                com.maxsters.coldspawncontrol.registry.ModSoundEvents.SUBTITLE_STARVATION.get(),
                com.maxsters.coldspawncontrol.registry.ModSoundEvents.SUBTITLE_GUILT.get(),
                com.maxsters.coldspawncontrol.registry.ModSoundEvents.SUBTITLE_FALSE_HOPE.get(),
                com.maxsters.coldspawncontrol.registry.ModSoundEvents.SUBTITLE_DISSOCIATION.get()
        };

        SoundEvent sound = subtitleSounds[RANDOM.nextInt(subtitleSounds.length)];

        // Play the sound event centered on the player at 1.0F volume.
        // It remains mostly silent due to the 0.0001 volume configuration in
        // sounds.json,
        // but the 1.0 packet volume ensures the client registers the event to trigger
        // the subtitle.
        Networking.sendToPlayer(
                new PositionalSoundPacket(sound, player.getX(), player.getY(), player.getZ(), 1.0F, 1.0F), player);

        ColdSpawnControl.LOGGER.debug("[Paranoia] Random subtitle hallucination: {} for {}",
                sound.getLocation(), player.getName().getString());
    }

    // ==================== SHADOW FLICKER ====================

    /**
     * Result of attempting to spawn a shadow flicker.
     */
    public enum SpawnResult {
        SUCCESS,
        NO_LIGHT_SOURCE,
        NO_VALID_POSITION
    }

    /**
     * Attempts to spawn a shadow flicker near the player at a light source.
     * 
     * @return SpawnResult indicating success or failure reason
     */
    public static SpawnResult trySpawnShadow(ServerPlayer player, ServerLevel level) {
        // Find the nearest light source within search radius
        BlockPos playerPos = player.blockPosition();
        BlockPos lightSourcePos = findNearestLightSource(level, playerPos, 16);

        if (lightSourcePos == null) {
            return SpawnResult.NO_LIGHT_SOURCE;
        }

        // Find a valid spawn position near the light source
        BlockPos spawnPos = findValidSpawnNearLight(level, lightSourcePos);

        if (spawnPos == null) {
            return SpawnResult.NO_VALID_POSITION;
        }

        // Spawn entity
        ShadowFlickerEntity shadow = ModEntities.SHADOW_FLICKER.get().create(level);
        if (shadow != null) {
            // Center on block, at surface level
            shadow.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                    RANDOM.nextFloat() * 360f, 0);
            shadow.setTargetPlayer(player.getUUID()); // Set visibility target
            level.addFreshEntity(shadow);
            ColdSpawnControl.LOGGER.debug("Spawned Shadow Flicker near {} at light source {}",
                    player.getName().getString(), lightSourcePos);
        }
        return SpawnResult.SUCCESS;
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Calculates a position behind the player (out of FOV).
     * Server-side version that works with ServerPlayer.
     */
    private static double[] getPositionBehindPlayer(ServerPlayer player, double distance) {
        float yRot = player.getYRot();

        // Add 120-240 degrees offset to be definitely behind/peripheral
        float offsetAngle = 120 + RANDOM.nextFloat() * 120; // degrees
        float totalAngle = yRot + offsetAngle;

        float radians = totalAngle * ((float) Math.PI / 180F);
        float x = -Mth.sin(radians);
        float z = Mth.cos(radians);

        double tx = player.getX() + x * distance;
        double tz = player.getZ() + z * distance;
        double ty = player.getY() + (RANDOM.nextDouble() * 6 - 3); // +/- 3 blocks vertical

        return new double[] { tx, ty, tz };
    }

    /**
     * Finds the nearest block that emits light (light level >= 8) within the given
     * radius.
     */
    private static BlockPos findNearestLightSource(ServerLevel level, BlockPos center, int radius) {
        BlockPos nearest = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius / 2; dy <= radius / 2; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos checkPos = center.offset(dx, dy, dz);
                    int lightEmission = level.getBlockState(checkPos).getLightEmission(level, checkPos);

                    if (lightEmission >= 8) {
                        double distSq = center.distSqr(checkPos);
                        if (distSq < nearestDistSq) {
                            nearestDistSq = distSq;
                            nearest = checkPos;
                        }
                    }
                }
            }
        }
        return nearest;
    }

    /**
     * Finds a valid spawn position near the light source.
     * Valid = air at feet and head, solid block below, good block light level.
     */
    private static BlockPos findValidSpawnNearLight(ServerLevel level, BlockPos lightPos) {
        // Search in a small radius around the light source
        for (int attempt = 0; attempt < 20; attempt++) {
            int dx = RANDOM.nextInt(3) - 1; // -1 to +1 (1-2 blocks from center)
            int dz = RANDOM.nextInt(3) - 1;

            // Start from light source Y and search downward for ground
            BlockPos checkBase = lightPos.offset(dx, 0, dz);

            // Search vertically to find a valid surface
            for (int dy = 3; dy >= -3; dy--) {
                BlockPos feetPos = checkBase.offset(0, dy, 0);
                BlockPos headPos = feetPos.above();
                BlockPos groundPos = feetPos.below();

                // Check: solid ground, air at feet, air at head
                if (!level.getBlockState(groundPos).isAir() &&
                        level.getBlockState(groundPos).getMaterial().isSolid() &&
                        level.getBlockState(feetPos).isAir() &&
                        level.getBlockState(headPos).isAir()) {

                    // Verify good light level at spawn position
                    int blockLight = level.getBrightness(LightLayer.BLOCK, feetPos);
                    if (blockLight >= 7) {
                        return feetPos;
                    }
                }
            }
        }
        return null;
    }
}
