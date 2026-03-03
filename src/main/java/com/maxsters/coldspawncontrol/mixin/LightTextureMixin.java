package com.maxsters.coldspawncontrol.mixin;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.maxsters.coldspawncontrol.client.ClientVisibilityState;
import net.minecraft.world.effect.MobEffects;

/**
 * Makes the world pitch black by eliminating sky light contribution.
 * Only block light (torches, lava, etc.) will illuminate the world.
 * 
 * The light texture is a 16x16 lookup table:
 * - X axis = block light level (0-15)
 * - Y axis = sky light level (0-15)
 * 
 * By treating all sky light levels as 0, we make the surface as dark as caves.
 * This eliminates flickering because there's no longer a conflict between
 * sky light darkening and block light brightening.
 */
@Mixin(LightTexture.class)
public class LightTextureMixin {

    // Precomputed power curve LUT — avoids 256 Math.pow() calls per frame
    @org.spongepowered.asm.mixin.Unique
    private static final float[] DARKNESS_LUT = new float[16];

    static {
        for (int i = 0; i < 16; i++) {
            DARKNESS_LUT[i] = (float) Math.pow(i / 15.0f, 1.5);
        }
    }

    // Per-frame cached state — set on first pixel (x=0,y=0), reused for remaining
    // 255
    @org.spongepowered.asm.mixin.Unique
    private boolean solastalgia$useVanilla;
    @org.spongepowered.asm.mixin.Unique
    private boolean solastalgia$frameStateValid = false;

    @SuppressWarnings("null")
    @Redirect(method = "updateLightTexture", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/NativeImage;setPixelRGBA(III)V"))
    private void redirectSetPixel(NativeImage image, int x, int y, int color) {

        // Cache per-frame state on first pixel to avoid 256 redundant lookups
        if (x == 0 && y == 0) {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            boolean isOverworld = mc.level != null
                    && net.minecraft.world.level.Level.OVERWORLD.equals(mc.level.dimension());
            boolean hasNightVision = mc.player != null && mc.player.hasEffect(MobEffects.NIGHT_VISION);
            solastalgia$useVanilla = ClientVisibilityState.debugMode || !isOverworld || hasNightVision;
            solastalgia$frameStateValid = true;
        }

        // Debug Mode OR Not Overworld OR Night Vision active: Use vanilla lighting
        if (solastalgia$useVanilla) {
            image.setPixelRGBA(x, y, color);
            return;
        }

        // x = block light level (0-15)
        // y = sky light level (0-15)

        // Use precomputed LUT instead of Math.pow()
        float factor = DARKNESS_LUT[x];

        if (y > 0 && x == 0) {
            // No block light, any sky light -> pitch black
            int a = (color >> 24) & 0xFF;
            image.setPixelRGBA(x, y, (a << 24));
            return;
        }

        // Decode color
        int a = (color >> 24) & 0xFF;
        int b = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int r = color & 0xFF;

        // Apply precomputed block light factor
        int rOut = (int) (r * factor);
        int gOut = (int) (g * factor);
        int bOut = (int) (b * factor);

        // Hard cutoff for very low light
        if (rOut < 5 && gOut < 5 && bOut < 5) {
            rOut = 0;
            gOut = 0;
            bOut = 0;
        }

        int newColor = (a << 24) | (bOut << 16) | (gOut << 8) | rOut;
        image.setPixelRGBA(x, y, newColor);
    }
}
