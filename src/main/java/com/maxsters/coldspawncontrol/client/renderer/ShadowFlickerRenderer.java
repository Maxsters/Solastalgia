package com.maxsters.coldspawncontrol.client.renderer;

import com.maxsters.coldspawncontrol.entity.ShadowFlickerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.LightLayer;

/**
 * Renderer for ShadowFlickerEntity.
 * Renders NO model, only the shadow.
 * Shadow opacity scales with block light level.
 */
@SuppressWarnings("null")
public class ShadowFlickerRenderer extends EntityRenderer<ShadowFlickerEntity> {

    public ShadowFlickerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.5F; // Default shadow size
    }

    @Override
    public void render(ShadowFlickerEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {

        // Check visibility target
        java.util.UUID targetUUID = entity.getTargetPlayer();
        net.minecraft.client.player.LocalPlayer clientPlayer = net.minecraft.client.Minecraft.getInstance().player;

        if (targetUUID != null && clientPlayer != null) {
            if (!clientPlayer.getUUID().equals(targetUUID)) {
                // Not the target player -> invisible shadow
                this.shadowStrength = 0.0f;
                return;
            }
        }

        // Use precise light check from world
        int blockLight = entity.level.getBrightness(LightLayer.BLOCK, entity.blockPosition());

        // High light -> stronger shadow. Low light -> invisible shadow.
        // Needs high light (> 7) to be visible at all? Or user said "high block light".
        if (blockLight < 7) {
            this.shadowStrength = 0.0f;
        } else {
            // Scale from 0.0 at light 7 to 1.0 at light 15
            this.shadowStrength = (blockLight - 7) / 8.0f;
        }

        // This sets the opacity for the shadow that the dispatcher draws later
    }

    @Override
    public ResourceLocation getTextureLocation(ShadowFlickerEntity entity) {
        return InventoryMenu.BLOCK_ATLAS; // Valid texture to avoid crashes
    }
}
