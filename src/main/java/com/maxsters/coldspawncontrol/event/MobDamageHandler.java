package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.ai.DangerZoneManager;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public class MobDamageHandler {

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getSource() == DamageSource.FREEZE) {
            LivingEntity entity = event.getEntity();
            if (entity.level.isClientSide)
                return;

            // Always add/refresh danger zone on freezing damage (First hit learning)
            DangerZoneManager.addDangerZone(entity.level, entity.blockPosition());

            // Check config if debug logging is enabled (assuming config exists)

        }
    }
}
