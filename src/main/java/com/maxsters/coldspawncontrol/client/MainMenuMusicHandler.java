package com.maxsters.coldspawncontrol.client;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.registry.ModSoundEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles playing custom music on the main menu.
 * This is a looping track that respects the music volume slider.
 */
@SuppressWarnings("null")
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID, value = Dist.CLIENT)
public class MainMenuMusicHandler {

    private static MenuMusicSoundInstance currentMusic = null;

    public static MenuMusicSoundInstance getCurrentMusic() {
        return currentMusic;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        Minecraft mc = Minecraft.getInstance();
        boolean inWorld = mc.level != null;

        if (inWorld) {
            if (currentMusic != null) {
                ColdSpawnControl.LOGGER.info("[MainMenuMusicHandler] Stopping music: Check entered world.");
                forceStopMusic(mc);
            }
            return;
        }

        // We are in the menu

        if (mc.options.getSoundSourceVolume(SoundSource.MASTER) == 0.0f
                || mc.options.getSoundSourceVolume(SoundSource.MUSIC) == 0.0f) {
            if (currentMusic != null) {
                ColdSpawnControl.LOGGER.info("[MainMenuMusicHandler] Stopping music: Volume is 0.");
                forceStopMusic(mc);
            }
            return;
        }

        // Prevent music from restarting if a loading overlay is active (e.g. world
        // load, resource reload)
        // This fixes the "blip" where music starts for a split second before joining
        // world.
        if (mc.getOverlay() != null) {
            return;
        }

        boolean inSingleplayerMenu = mc.screen instanceof net.minecraft.client.gui.screens.worldselection.SelectWorldScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.worldselection.CreateWorldScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.worldselection.EditWorldScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.worldselection.OptimizeWorldScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.ConfirmScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.GenericDirtMessageScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.AlertScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.DatapackLoadFailureScreen;

        // Also check for specific loading screens to prevent blips
        boolean inLoadingScreen = mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.LevelLoadingScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.ConnectScreen
                || mc.screen instanceof net.minecraft.client.gui.screens.ProgressScreen;

        if (inSingleplayerMenu || inLoadingScreen) {
            if (currentMusic != null) {
                ColdSpawnControl.LOGGER
                        .info("[MainMenuMusicHandler] Stopping music: Entered singleplayer/loading screen.");
                forceStopMusic(mc);
            }
            return;
        }

        if (currentMusic == null || !mc.getSoundManager().isActive(currentMusic)) {
            // Only start if we are allowed to
            if (currentMusic == null) { // Initial start
                currentMusic = new MenuMusicSoundInstance();
                mc.getSoundManager().play(currentMusic);
                ColdSpawnControl.LOGGER.info("[MainMenuMusicHandler] Started new music instance.");
            } else if (!mc.getSoundManager().isActive(currentMusic)) {
                // Restart case
                currentMusic = new MenuMusicSoundInstance();
                mc.getSoundManager().play(currentMusic);
                ColdSpawnControl.LOGGER.info("[MainMenuMusicHandler] Restarting music (was inactive).");
            }
        }
    }

    /**
     * Force stop music - sets currentMusic to null BEFORE calling stop
     * so our mixin knows to allow this stop.
     */
    public static void forceStopMusic(Minecraft mc) {
        if (currentMusic != null) {
            // ColdSpawnControl.LOGGER.info("[MainMenuMusicHandler] Force stopping music.");
            MenuMusicSoundInstance toStop = currentMusic;
            currentMusic = null; // Clear first so mixin allows the stop
            mc.getSoundManager().stop(toStop);
        }
    }

    /**
     * Called by SoundManagerMixin to check if a sound is our protected music.
     */
    public static boolean isProtectedMusic(Object sound) {
        return currentMusic != null && currentMusic == sound;
    }

    /**
     * Custom sound instance for menu music.
     */
    @OnlyIn(Dist.CLIENT)
    private static class MenuMusicSoundInstance extends AbstractSoundInstance {

        protected MenuMusicSoundInstance() {
            super(ModSoundEvents.MENU_MUSIC.get(), SoundSource.MUSIC, RandomSource.create());
            this.looping = true;
            this.delay = 0;
            this.volume = 1.0F;
            this.pitch = 1.0F;
            this.relative = true;
            this.x = 0.0;
            this.y = 0.0;
            this.z = 0.0;
            this.attenuation = Attenuation.NONE;
        }
    }
}
