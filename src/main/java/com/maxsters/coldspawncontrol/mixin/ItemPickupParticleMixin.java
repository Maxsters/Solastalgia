package com.maxsters.coldspawncontrol.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.particle.ItemPickupParticle;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes a vanilla Minecraft bug where ItemPickupParticle and ExperienceOrb
 * rendering corrupts the OpenGL shader color state, causing all custom
 * render type particles to flash black for one frame when items are
 * picked up or experience orbs are collected.
 *
 * The root cause is that ItemPickupParticle.render() uses the entity
 * rendering pipeline (MultiBufferSource/EntityRenderDispatcher) which
 * calls setShaderColor() and enables lightmap overlays, but never resets
 * the shader color back to white after the draw call completes.
 * Custom particle render types that follow in the same frame inherit
 * the corrupted shader color and render black.
 */
@Mixin(ItemPickupParticle.class)
public class ItemPickupParticleMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void resetGLStateAfterRender(VertexConsumer buffer, Camera camera, float partialTick, CallbackInfo ci) {
        // Entity rendering corrupts the lightmap and overlay states. We must restore
        // them
        // so following custom particles don't flash black.
        net.minecraft.client.renderer.LightTexture lightTexture = net.minecraft.client.Minecraft
                .getInstance().gameRenderer.lightTexture();
        if (lightTexture != null) {
            lightTexture.turnOnLightLayer();
        }

        net.minecraft.client.Minecraft.getInstance().gameRenderer.overlayTexture().teardownOverlayColor();

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // CUSTOM render type expects blend to be disabled, but entity renderer might
        // have left it on
        RenderSystem.disableBlend();

        // Entity renderer modifies depth state (e.g. for enchant glints), causing
        // particles to draw through walls
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(515); // GL_LEQUAL
    }
}
