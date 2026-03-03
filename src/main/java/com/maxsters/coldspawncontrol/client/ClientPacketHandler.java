package com.maxsters.coldspawncontrol.client;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.network.PositionalSoundPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraftforge.registries.ForgeRegistries;

public class ClientPacketHandler {

    public static void handlePositionalSound(PositionalSoundPacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return;

        SoundEvent sound = ForgeRegistries.SOUND_EVENTS.getValue(packet.getSoundId());
        if (sound == null) {
            ColdSpawnControl.LOGGER.warn("[PositionalSound] Unknown sound event: {}", packet.getSoundId());
            return;
        }

        RandomSource random = RandomSource.create();

        @SuppressWarnings("null")
        SimpleSoundInstance soundInstance = new SimpleSoundInstance(
                sound.getLocation(),
                SoundSource.AMBIENT,
                packet.getVolume(),
                packet.getPitch(),
                random,
                false,
                0,
                SoundInstance.Attenuation.LINEAR,
                packet.getX(), packet.getY(), packet.getZ(),
                false);

        mc.getSoundManager().play(soundInstance);
        ColdSpawnControl.LOGGER.debug("[PositionalSound] Playing {} at ({}, {}, {})", packet.getSoundId(),
                packet.getX(), packet.getY(), packet.getZ());
    }

    public static void handleDisorient(int fovDelta, int swapAxis) {
        AmnesiaClientState.applyDisorientation(fovDelta, swapAxis);
    }
}
