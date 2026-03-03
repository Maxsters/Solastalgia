package com.maxsters.coldspawncontrol.registry;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import net.minecraft.core.Registry;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@SuppressWarnings("null")
public class ModPlacedFeatures {
    public static final DeferredRegister<PlacedFeature> PLACED_FEATURES = DeferredRegister
            .create(Registry.PLACED_FEATURE_REGISTRY, ColdSpawnControl.MOD_ID);

    public static final RegistryObject<PlacedFeature> FROZEN_WORLD_PLACED = PLACED_FEATURES.register("frozen_world",
            () -> new PlacedFeature(ModConfiguredFeatures.FROZEN_WORLD_CONFIGURED.getHolder().get(),
                    java.util.List.of()));

    public static void register(IEventBus eventBus) {
        PLACED_FEATURES.register(eventBus);
    }
}
