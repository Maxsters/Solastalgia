package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import com.maxsters.coldspawncontrol.client.ClientVisibilityState;

/**
 * Intercepts vanilla's fog setup to prevent powder snow from influencing
 * the horizon/sky color. Vanilla calls Camera.getFluidInCamera() inside
 * FogRenderer.setupColor() - if it returns POWDER_SNOW, vanilla computes
 * a bright bluish fog color that bleeds into the horizon rendering BEFORE
 * the ComputeFogColor event fires.
 *
 * By forcing NONE instead of POWDER_SNOW, we prevent vanilla from computing
 * any powder-snow-related fog effects. Our FogHandler event then handles
 * the powder snow fog color manually based on camera block position.
 */
@Mixin(FogRenderer.class)
public class FogRendererMixin {

    /**
     * Shared logic: if the camera would report POWDER_SNOW in the Overworld,
     * force it to NONE so vanilla does not compute any powder snow fog effects.
     */
    private static FogType overridePowderSnow(Camera camera) {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level != null
                && net.minecraft.world.level.Level.OVERWORLD.equals(mc.level.dimension())
                && !ClientVisibilityState.debugMode) {
            FogType original = camera.getFluidInCamera();
            if (original == FogType.POWDER_SNOW) {
                return FogType.NONE;
            }
            return original;
        }
        return camera.getFluidInCamera();
    }

    @Redirect(method = "setupColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getFluidInCamera()Lnet/minecraft/world/level/material/FogType;"))
    private static FogType solastalgia$overrideFogTypeColor(Camera camera) {
        return overridePowderSnow(camera);
    }

    @Redirect(method = "setupFog", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getFluidInCamera()Lnet/minecraft/world/level/material/FogType;"))
    private static FogType solastalgia$overrideFogTypeFog(Camera camera) {
        return overridePowderSnow(camera);
    }
}
