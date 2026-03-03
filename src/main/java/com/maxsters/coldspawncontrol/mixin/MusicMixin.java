package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.client.sounds.MusicManager;
import net.minecraft.sounds.Music;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MusicManager.class)
public class MusicMixin {

    @Inject(method = "startPlaying", at = @At("HEAD"), cancellable = true)
    private void silenceMusic(Music music, CallbackInfo ci) {
        // Prevent any music from playing
        ci.cancel();
    }

    // Also inject tick just in case
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void stopMusicTicker(CallbackInfo ci) {
        ci.cancel();
    }
}
