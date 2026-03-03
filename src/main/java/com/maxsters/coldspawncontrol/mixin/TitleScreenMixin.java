package com.maxsters.coldspawncontrol.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import javax.annotation.Nullable;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component pTitle) {
        super(pTitle);
    }

    @Shadow
    @Nullable
    private String splash;

    @Unique
    @Nullable
    private String customSplash;

    @Inject(method = "init", at = @At("TAIL"))
    private void solastalgia$captureSplash(CallbackInfo ci) {
        if (this.customSplash == null && this.splash != null && !this.splash.isEmpty()) {
            this.customSplash = this.splash;
        }
        if (this.splash != null) {
            // Prevent original splash from rendering to clear the angled text
            this.splash = null;
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void solastalgia$renderCustomSplash(PoseStack pPoseStack, int pMouseX, int pMouseY, float pPartialTick,
            CallbackInfo ci) {
        String splashStr = this.customSplash;
        if (splashStr != null) {
            pPoseStack.pushPose();

            // Calculate scale based on text width to make it take up logo screen real
            // estate safely
            float width = this.font.width(splashStr);
            float baseScale = Math.min(250.0F / width, 3.5F);
            baseScale = Math.max(baseScale, 1.5F); // Give it a minimum size even for very long text

            // Breathing animation mimicking the original splash
            float time = (float) (net.minecraft.Util.getMillis() % 5000L) / 5000.0F * ((float) Math.PI * 2F);
            float pulse = 1.0F - net.minecraft.util.Mth.abs(net.minecraft.util.Mth.sin(time) * 0.1F);
            float scale = baseScale * pulse;

            // Apply modifications to position and scale at the translation origin
            pPoseStack.translate(this.width / 2.0F, 52.0F, 0.0F);
            pPoseStack.scale(scale, scale, scale);

            // Minecraft's font width() includes the trailing 1-pixel space after the final
            // character.
            // Visually, the actual text ink is 1 pixel narrower than the calculated
            // "width".
            // To find the exact visual center of the text, we ignore that trailing space.
            float visualWidth = width - 1.0F;
            float textX = -(visualWidth / 2.0F);

            // We'll also trim the 1-pixel bottom margin to perfectly center the text
            // vertically.
            float visualHeight = this.font.lineHeight - 1.0F;
            float textY = -(visualHeight / 2.0F);

            this.font.drawShadow(pPoseStack, splashStr, textX, textY, 16776960 | (255 << 24)); // Full yellow styling

            pPoseStack.popPose();
        }
    }
}
