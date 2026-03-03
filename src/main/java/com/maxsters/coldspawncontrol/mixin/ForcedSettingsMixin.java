package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin to force certain settings to always be at specific values.
 * - Forces entity shadows to always be enabled (required for Shadow Flicker
 * paranoia effect).
 * - Forces cloud coverage to always be turned off.
 */
@Mixin(Options.class)
public class ForcedSettingsMixin {

    @Shadow
    @Final
    private OptionInstance<Boolean> entityShadows;

    @Shadow
    @Final
    private OptionInstance<CloudStatus> cloudStatus;

    /**
     * Force settings whenever options are loaded.
     */
    @Inject(method = "load", at = @At("RETURN"))
    private void forceSettingsOnLoad(CallbackInfo ci) {
        entityShadows.set(true);
        cloudStatus.set(CloudStatus.OFF);
    }

    /**
     * Force settings whenever options are saved.
     * This ensures the settings stay at their forced values even if the user tries
     * to change them.
     */
    @Inject(method = "save", at = @At("HEAD"))
    private void forceSettingsOnSave(CallbackInfo ci) {
        entityShadows.set(true);
        cloudStatus.set(CloudStatus.OFF);
    }
}
