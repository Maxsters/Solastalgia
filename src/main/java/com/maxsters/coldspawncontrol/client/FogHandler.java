package com.maxsters.coldspawncontrol.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;

@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
@SuppressWarnings("null")
public class FogHandler {

    public static float currentFogIntensity = 0.0f;
    private static float targetExposure = 0.0f;

    /**
     * Calculates dynamic exposure based on sky light.
     * 0 = Fully Enclosed
     * 15 = Fully Exposed
     * Result is 0.0f to 1.0f
     */
    private static void updateExposure(net.minecraft.world.level.Level level,
            net.minecraft.world.entity.player.Player player) {
        BlockPos playerPos = player.blockPosition();

        // Chunk not loaded at all — zero out exposure (e.g. before initial load)
        if (!level.getChunkSource().hasChunk(playerPos.getX() >> 4, playerPos.getZ() >> 4)) {
            targetExposure = 0.0f;
            return;
        }

        // Get sky light at player position (0-15)
        // We no longer gate on isLightCorrect() because it was preventing wind
        // sound from ever starting — the light engine can lag behind chunk loading,
        // causing targetExposure to stay at 0 indefinitely. A slightly inaccurate
        // sky light reading is far better than permanent silence.
        int skyLight = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, playerPos);

        // Normalize to 0.0 - 1.0 range
        targetExposure = skyLight / 15.0f;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        currentFogIntensity = 0.0f;
        targetExposure = 0.0f;

        // Reset debug mode so it doesn't leak into other worlds
        ClientVisibilityState.debugMode = false;

        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null && ambienceSound != null) {
            mc.getSoundManager().stop(ambienceSound);
            ambienceSound = null;
        }
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        // Reset state for new session
        currentFogIntensity = 0.0f;
        targetExposure = 0.0f;
    }

    private static AmbienceSoundInstance ambienceSound = null;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null || mc.player == null)
                return;

            // Disable Fog in Nether and End
            if (!net.minecraft.world.level.Level.OVERWORLD.equals(mc.level.dimension())) {
                currentFogIntensity = 0.0f;
                return;
            }

            // Debug Mode Bypass
            if (ClientVisibilityState.debugMode) {
                currentFogIntensity = 0.0f;
                return;
            }

            // Update exposure (Binary)
            updateExposure(mc.level, mc.player);

            // Smooth transition (Lerp) - Faster response (0.05) for realtime feedback
            if (currentFogIntensity < targetExposure) {
                currentFogIntensity += 0.05f;
                if (currentFogIntensity > targetExposure)
                    currentFogIntensity = targetExposure;
            } else if (currentFogIntensity > targetExposure) {
                currentFogIntensity -= 0.05f;
                if (currentFogIntensity < targetExposure)
                    currentFogIntensity = targetExposure;
            }

            // Ambience Sound Logic
            SoundManager soundManager = mc.getSoundManager();
            if (currentFogIntensity > 0.01f) {
                if (ambienceSound == null || !soundManager.isActive(ambienceSound)) {
                    ambienceSound = new AmbienceSoundInstance();
                    soundManager.play(ambienceSound);
                }
            } else {
                if (ambienceSound != null) {
                    soundManager.stop(ambienceSound);
                    ambienceSound = null;
                }
            }

            // Fog color logic removed.
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.LOWEST)
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        Minecraft mc = Minecraft.getInstance();

        // Skip fog color modifications for non-Overworld dimensions or if level is null
        if (mc.level == null || !net.minecraft.world.level.Level.OVERWORLD.equals(mc.level.dimension())) {
            return;
        }

        // Force pitch black fog when underwater (unless debug mode or Night Vision)
        if (mc.player != null && mc.player.isUnderWater() && !ClientVisibilityState.debugMode) {
            if (!mc.player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)) {
                event.setRed(0.0f);
                event.setGreen(0.0f);
                event.setBlue(0.0f);
                return;
            } else {
                // If Night Vision is active underwater, allow vanilla fog color
                // (brightness/blue)
                // Do not fall through to global black enforcement.
                return;
            }
        }

        if (!ClientVisibilityState.debugMode) {
            // Powder Snow Exception: Allow snow fog color only when camera is
            // actually inside a powder snow block. The FogRendererMixin prevents vanilla
            // from computing powder snow fog (which leaked into the horizon), so we
            // handle it manually here with a strict camera-only check.
            if (mc.level.getBlockState(event.getCamera().getBlockPosition())
                    .is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) {
                event.setRed(0.623f);
                event.setGreen(0.734f);
                event.setBlue(0.785f);
                return;
            }

            // Always force pitch black fog in Overworld.
            event.setRed(0.0f);
            event.setGreen(0.0f);
            event.setBlue(0.0f);
        }
    }

    @SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onRenderFog(ViewportEvent.RenderFog event) {
        Minecraft mc = Minecraft.getInstance();

        // Skip fog rendering modifications for non-Overworld dimensions or if level is
        // null
        if (mc.level == null || !net.minecraft.world.level.Level.OVERWORLD.equals(mc.level.dimension())) {
            return;
        }

        // Force very tight fog when underwater for pitch black visibility (unless debug
        // mode or Night Vision)
        if (mc.player != null && mc.player.isUnderWater() && !ClientVisibilityState.debugMode) {
            if (!mc.player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)) {
                event.setNearPlaneDistance(0.0f);
                event.setFarPlaneDistance(0.5f); // Extremely short - almost no visibility
                event.setCanceled(true);
                return;
            }
        }

        // Apply powder snow fog distances manually since our FogRendererMixin
        // blocks vanilla from detecting powder snow in setupColor/setupFog.
        if (!ClientVisibilityState.debugMode
                && mc.level.getBlockState(event.getCamera().getBlockPosition())
                        .is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) {
            event.setNearPlaneDistance(0.0f);
            event.setFarPlaneDistance(2.0f); // Vanilla powder snow fog distance
            event.setCanceled(true);
            return;
        }

        // Previous fog distance logic removed to allow clear view of sky (Stellar View
        // mod support).
        // LightTextureMixin ensures the ground is black.
    }

}
