package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.entity.ShadowFlickerEntity;
import com.maxsters.coldspawncontrol.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Common mod bus events (server + client).
 * Handles entity attribute registration and other mod lifecycle events.
 */
@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class CommonModEvents {

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        // Register attributes for ShadowFlickerEntity
        event.put(ModEntities.SHADOW_FLICKER.get(), ShadowFlickerEntity.createAttributes().build());
        ColdSpawnControl.LOGGER.debug("Registered ShadowFlickerEntity attributes");
    }

    @SubscribeEvent
    public static void onCommonSetup(net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // Start SLM model download/loading at startup (server and client compatible)
            com.maxsters.coldspawncontrol.slm.ModelManager.initialize();
        });
    }
}
