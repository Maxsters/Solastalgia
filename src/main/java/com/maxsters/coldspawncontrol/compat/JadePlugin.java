package com.maxsters.coldspawncontrol.compat;

import com.maxsters.coldspawncontrol.entity.ShadowFlickerEntity;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

/**
 * Jade compatibility plugin to hide ShadowFlickerEntity from the tooltip.
 * The shadow should be an unexplained phenomenon, not labeled in any HUD.
 */
@WailaPlugin
public class JadePlugin implements IWailaPlugin {

    private static final ResourceLocation UID = new ResourceLocation("solastalgia", "shadow_hider");

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        // Register component provider that clears tooltip for ShadowFlickerEntity
        registration.registerEntityComponent(ShadowHider.INSTANCE, ShadowFlickerEntity.class);
    }

    /**
     * Component provider that hides all information for ShadowFlickerEntity
     */
    public enum ShadowHider implements IEntityComponentProvider {
        INSTANCE;

        @Override
        public ResourceLocation getUid() {
            return UID;
        }

        @Override
        public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
            // Clear any previously added tooltip content to minimize the HUD
            tooltip.clear();
        }

        @Override
        public int getDefaultPriority() {
            // Run last to override everything else
            return Integer.MAX_VALUE;
        }
    }
}
