package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.world.level.LevelSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Forces hardcore mode for all worlds.
 * This ensures permadeath is enforced regardless of UI selection.
 * 
 * The CreateWorldScreenMixin locks the UI, this mixin enforces the actual
 * setting.
 */
@Mixin(LevelSettings.class)
public class LevelSettingsMixin {

    /**
     * Forces isHardcore() to always return true.
     * This ensures the game treats all worlds as hardcore.
     */
    @Inject(method = "hardcore", at = @At("HEAD"), cancellable = true)
    private void forceHardcore(CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(true);
    }
}
