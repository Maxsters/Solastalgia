package com.maxsters.coldspawncontrol.registry;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import net.minecraft.core.Registry;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

@SuppressWarnings("null")
public class ModConfiguredFeatures {
    public static final DeferredRegister<ConfiguredFeature<?, ?>> CONFIGURED_FEATURES = DeferredRegister
            .create(Registry.CONFIGURED_FEATURE_REGISTRY, ColdSpawnControl.MOD_ID);

    public static final RegistryObject<ConfiguredFeature<?, ?>> FROZEN_WORLD_CONFIGURED = CONFIGURED_FEATURES.register(
            "frozen_world",
            () -> new ConfiguredFeature<>(ModFeatures.FROZEN_WORLD.get(), NoneFeatureConfiguration.INSTANCE));

    public static void register(IEventBus eventBus) {
        CONFIGURED_FEATURES.register(eventBus);
    }
}
