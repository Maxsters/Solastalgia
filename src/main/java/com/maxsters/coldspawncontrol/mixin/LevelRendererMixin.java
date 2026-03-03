package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LevelRenderer.class, priority = 2000)
public class LevelRendererMixin {
    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void cancelVanillaSnow(LightTexture lightTexture, float partialTick, double camX, double camY, double camZ,
            CallbackInfo ci) {
        // Disable vanilla snow/rain logic completely in favor of our particle system OR
        // still world logic.
        // Whether Realistic Mode is ON (Still) or OFF (Particle Blizzard), we never
        // want vanilla weather lines.
        ci.cancel();
    }

}
