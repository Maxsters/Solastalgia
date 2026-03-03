package com.maxsters.coldspawncontrol.client;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.registry.ModSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Random;

/**
 * Handles client-side ambient paranoia effects:
 * 1. Footprint echoes (sounds of steps behind you)
 * 2. Environmental percussion (cave vs outside sounds)
 * 3. Shadow flickers (handled via entity spawning)
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = ColdSpawnControl.MOD_ID)
@SuppressWarnings("null")
public class AmbientParanoiaHandler {

    private static final Random RANDOM = new Random();

    // Footprint tracking
    private static boolean wasMoving = false;
    private static int ticksSinceStop = 0;
    private static final int ECHO_DELAY_TICKS = 15; // 0.75 seconds after stopping

    // Footstep sequence
    private static int stepsToPlay = 0;
    private static int stepTimer = 0;

    // Environmental sound timer - EXTREME RARITY
    private static int soundTimer = 0;
    // Check every 30 seconds (600 ticks), 0.5% chance -> ~Every 100 minutes on
    // average
    private static final int SOUND_CHECK_INTERVAL = 600;
    private static final float SOUND_CHANCE = 0.005f; // 0.5%

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.isPaused())
            return; // Don't play sounds if paused

        Player player = mc.player;
        Level level = mc.level;

        if (player == null || level == null || !level.isClientSide)
            return;

        // Only in Overworld
        if (!Level.OVERWORLD.equals(level.dimension()))
            return;

        handleFootprintEchoes(player, level);
        handleEnvironmentalSounds(player, level);
        processActiveSteps(player, level);
    }

    private static void handleFootprintEchoes(Player player, Level level) {
        // Simple movement check
        boolean isMoving = player.getDeltaMovement().lengthSqr() > 0.001 && !player.isPassenger();

        if (isMoving) {
            wasMoving = true;
            ticksSinceStop = 0;
        } else if (wasMoving) {
            ticksSinceStop++;

            // Effect window: shortly after stopping
            if (ticksSinceStop >= ECHO_DELAY_TICKS && ticksSinceStop < ECHO_DELAY_TICKS + 5) {
                // Very rare chance (approx 1 in 20 stops)
                if (RANDOM.nextFloat() < 0.05f) {
                    startFootstepSequence(2, 4);
                }
                wasMoving = false; // Prevent multiple echoes per stop
            }

            if (ticksSinceStop > 40) {
                wasMoving = false; // Reset state
            }
        }
    }

    /**
     * Starts a sequence of footsteps.
     * 
     * @param minSteps Minimum number of steps (inclusive)
     * @param maxSteps Maximum number of steps (inclusive)
     */
    public static void startFootstepSequence(int minSteps, int maxSteps) {
        // Queue footsteps
        int range = Math.max(1, maxSteps - minSteps + 1);
        stepsToPlay = minSteps + RANDOM.nextInt(range);
        stepTimer = 0;
    }

    private static void processActiveSteps(Player player, Level level) {
        if (stepsToPlay > 0) {
            stepTimer++;
            // Play a step every ~6-8 ticks (fast walk)
            if (stepTimer >= 6 + RANDOM.nextInt(3)) {
                stepTimer = 0;
                triggerFootprintEcho(player, level);
                stepsToPlay--;
            }
        }
    }

    private static void handleEnvironmentalSounds(Player player, Level level) {
        soundTimer++;

        // Check at interval with extreme rarity
        if (soundTimer >= SOUND_CHECK_INTERVAL) {
            soundTimer = 0;

            if (RANDOM.nextFloat() < SOUND_CHANCE) {
                triggerEnvironmentalSound(player, level);
            }
        }
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Determines if the player is in a cave (underground with no sky access).
     */
    private static boolean isPlayerInCave(Player player, Level level) {
        BlockPos pos = player.blockPosition();

        // Unloaded chunk or light not computed — can't determine sky state, assume not
        // in cave
        if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) {
            return false;
        }
        net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource().getChunkNow(
                pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !chunk.isLightCorrect()) {
            return false;
        }

        // Check if sky light is 0 (fully underground)
        int skyLight = level.getBrightness(LightLayer.SKY, pos.above());

        // Also check if player is below Y=60 for added certainty
        boolean belowSurface = pos.getY() < 60;

        // In cave if no sky light OR definitely underground
        return skyLight == 0 || (belowSurface && skyLight < 4);
    }

    /**
     * Calculates a position behind the player (out of FOV).
     * FOV is typically ~70-110 degrees, so we target 120-240 degrees behind.
     */
    private static double[] getPositionBehindPlayer(Player player, double distance) {
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
     * Plays a positional sound using Minecraft's SoundManager.
     */
    private static void playPositionalSound(SoundEvent sound, double x, double y, double z, float volume, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        RandomSource random = RandomSource.create();

        ColdSpawnControl.LOGGER.debug("[Paranoia] Playing sound: {} at ({}, {}, {}) vol={} pitch={}",
                sound.getLocation(), x, y, z, volume, pitch);

        // Create a positional sound instance
        SimpleSoundInstance soundInstance = new SimpleSoundInstance(
                sound.getLocation(),
                SoundSource.AMBIENT,
                volume,
                pitch,
                random,
                false, // looping
                0, // delay
                SoundInstance.Attenuation.LINEAR, // This is key for positional audio!
                x, y, z,
                false // relative
        );

        mc.getSoundManager().play(soundInstance);
    }

    // Public methods for debug/command triggering

    public static void triggerFootprintEcho(Player player, Level level) {
        // Prevent footsteps if player is not on the ground (jumping, falling, flying)
        if (!player.isOnGround()) {
            return;
        }

        // Position behind player (out of FOV)
        double[] pos = getPositionBehindPlayer(player, 2.0 + RANDOM.nextDouble());

        // Get block sound type at target location
        BlockPos blockPos = new BlockPos((int) pos[0], (int) (pos[1] - 0.2), (int) pos[2]);
        BlockState state = level.getBlockState(blockPos);
        if (state.isAir()) {
            blockPos = blockPos.below();
            state = level.getBlockState(blockPos);
        }

        SoundType soundType = state.getSoundType(level, blockPos, player);

        // Play positional sound
        playPositionalSound(soundType.getStepSound(), pos[0], pos[1], pos[2],
                soundType.getVolume() * 0.5f, soundType.getPitch() * 0.9f);
    }

    public static void triggerEnvironmentalSound(Player player, Level level) {
        // Prevent paranoia sounds if player is fully submerged in water
        if (player.isUnderWater()) {
            return;
        }

        // Distance 5-12 blocks behind player (within attenuation range)
        double dist = 5 + RANDOM.nextDouble() * 7;
        double[] pos = getPositionBehindPlayer(player, dist);

        // Choose sound based on cave/outside
        boolean inCave = isPlayerInCave(player, level);
        SoundEvent sound = inCave ? ModSoundEvents.PARANOIA_CAVE.get() : ModSoundEvents.PARANOIA_OUTSIDE.get();

        ColdSpawnControl.LOGGER.info("[Paranoia] Triggering environmental sound: {} (inCave={})",
                sound.getLocation(), inCave);

        // Play positional sound
        playPositionalSound(sound, pos[0], pos[1], pos[2],
                0.8f + RANDOM.nextFloat() * 0.4f,
                0.8f + RANDOM.nextFloat() * 0.4f);
    }
}
