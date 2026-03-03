package com.maxsters.coldspawncontrol.client;

import com.maxsters.coldspawncontrol.registry.ModSoundEvents;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;

public class AmbienceSoundInstance extends AbstractTickableSoundInstance {

    public AmbienceSoundInstance() {
        super(ModSoundEvents.AMBIENCE.get(), SoundSource.AMBIENT, SoundInstance.createUnseededRandom());
        this.looping = true;
        this.delay = 0;
        this.volume = Mth.clamp(FogHandler.currentFogIntensity, 0.0f, 1.0f); // Set initial volume
        this.relative = true;
        this.attenuation = Attenuation.NONE;
    }

    @Override
    public void tick() {
        net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;

        if (player != null && player.isUnderWater()) {
            this.volume = 0.0F;
            this.pitch = 0.2F; // Keep pitch low/muffled
        } else {
            // Update volume based on fog intensity
            float baseVolume = Mth.clamp(FogHandler.currentFogIntensity, 0.0f, 1.0f);

            // Realistic Mode: Reduce volume to 25% if enabled
            if (ClientRealisticModeState.isRealisticModeEnabled) {
                baseVolume *= 0.25f;
            }

            this.volume = baseVolume;

            // Simulate Low Pass Filter/Muffling by lowering pitch as volume decreases
            // Pitch ranges from 0.2 (deep/muffled) to 1.0 (clear)
            this.pitch = 0.2F + (this.volume * 0.8F);
        }

        // LOGGER.info("AmbienceSoundInstance: Volume updated to " + this.volume);
    }
}
