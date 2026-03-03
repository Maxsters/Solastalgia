package com.maxsters.coldspawncontrol.mixin;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.registry.AmnesiaAdvancementConfig;
import net.minecraft.advancements.Advancement;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Suppresses advancement toast notifications for iron-age and below
 * advancements.
 * 
 * This prevents the "Stone Age" and "Isn't It Iron Pick" toasts from appearing
 * on first join, which would break the journal narrative that implies the
 * player
 * has already progressed to the iron age.
 */
@SuppressWarnings("null")
@Mixin(AdvancementToast.class)
public class AdvancementToastMixin {

    @Shadow
    @Final
    private Advancement advancement;

    /**
     * Intercepts the render method and immediately hides the toast if it's for
     * a suppressed advancement.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void suppressIronAgeToasts(com.mojang.blaze3d.vertex.PoseStack poseStack,
            ToastComponent toastComponent, long timeSinceLastVisible,
            CallbackInfoReturnable<Toast.Visibility> cir) {

        if (advancement == null || advancement.getId() == null) {
            return;
        }

        ResourceLocation advancementId = advancement.getId();

        if (AmnesiaAdvancementConfig.shouldSuppressToast(advancementId)) {
            ColdSpawnControl.LOGGER.debug("Suppressing advancement toast: {}", advancementId);
            // Immediately hide the toast without rendering
            cir.setReturnValue(Toast.Visibility.HIDE);
        }
    }
}
