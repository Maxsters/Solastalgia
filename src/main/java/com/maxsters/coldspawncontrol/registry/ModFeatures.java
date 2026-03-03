package com.maxsters.coldspawncontrol.registry;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.worldgen.FrozenWorldFeature;
import com.maxsters.coldspawncontrol.worldgen.BranchingTrunkPlacer;
import net.minecraft.core.Registry;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@SuppressWarnings("null")
public class ModFeatures {
        public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES,
                        ColdSpawnControl.MOD_ID);

        public static final RegistryObject<Feature<NoneFeatureConfiguration>> FROZEN_WORLD = FEATURES.register(
                        "frozen_world",
                        () -> new FrozenWorldFeature(NoneFeatureConfiguration.CODEC));

        public static final DeferredRegister<TrunkPlacerType<?>> TRUNK_PLACERS = DeferredRegister.create(
                        Registry.TRUNK_PLACER_TYPE_REGISTRY,
                        ColdSpawnControl.MOD_ID);

        public static final RegistryObject<TrunkPlacerType<BranchingTrunkPlacer>> BRANCHING_TRUNK_PLACER = TRUNK_PLACERS
                        .register(
                                        "branching_trunk_placer",
                                        () -> new TrunkPlacerType<>(BranchingTrunkPlacer.CODEC));

        public static void register(IEventBus eventBus) {
                FEATURES.register(eventBus);
                TRUNK_PLACERS.register(eventBus);
        }
}
