package com.maxsters.coldspawncontrol.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fixes a vanilla bug where entity rendering (item pickups, XP orbs)
 * corrupts OpenGL state (shader color, shader program, lightmap) before
 * the particle engine renders. This corruption causes particles with
 * custom render types to flash black for one frame.
 *
 * By resetting the critical GL state at the very start of particle
 * rendering (before any render type's begin() is called), we guarantee
 * a clean state regardless of what the entity renderer left behind.
 */
@Mixin(ParticleEngine.class)
public class ParticleEngineRenderMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void resetGLStateBeforeParticles(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource,
            LightTexture lightTexture, Camera camera, float partialTick, CallbackInfo ci) {

        // ItemPickupParticle and ExperienceOrb render completely flush the
        // BufferBuilder
        // inside the particle loop, which leaves the Lightmap (TU2) unbound, Overlay
        // (TU1) bound,
        // and the shader color tinted. This completely resets the 2D rendering state.

        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Fix Lightmap (Texture Unit 2 was unbound)
        lightTexture.turnOnLightLayer();

        // Fix Overlay (Texture Unit 1 was left bound with entity hit overlay)
        net.minecraft.client.Minecraft.getInstance().gameRenderer.overlayTexture().teardownOverlayColor();

        // Fix depth state (Entity renderer disables depth testing or alters depth func,
        // causing particles to render through walls)
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.depthFunc(515); // GL_LEQUAL
        RenderSystem.disableBlend();
    }
}
