package com.maxsters.coldspawncontrol.registry;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.entity.ShadowFlickerEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@SuppressWarnings("null")
public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES,
            ColdSpawnControl.MOD_ID);

    public static final RegistryObject<EntityType<ShadowFlickerEntity>> SHADOW_FLICKER = ENTITIES.register(
            "shadow_flicker",
            () -> EntityType.Builder.of(ShadowFlickerEntity::new, MobCategory.MISC)
                    .sized(0.5F, 1.0F) // Small profile
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .noSave() // Don't save this entity - it's transient
                    .build(new ResourceLocation(ColdSpawnControl.MOD_ID, "shadow_flicker").toString()));

    public static void register(net.minecraftforge.eventbus.api.IEventBus eventBus) {
        ENTITIES.register(eventBus);
    }
}
