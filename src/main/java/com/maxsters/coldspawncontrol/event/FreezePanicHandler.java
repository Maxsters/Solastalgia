package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Prevents freezing mobs from targeting players.
 * This acts as a safety net even if AI goals try to set targets.
 */
@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public class FreezePanicHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity entity = event.getEntity();

        // Only intercept mobs trying to target players
        if (!(entity instanceof Mob mob)) {
            return;
        }

        LivingEntity newTarget = event.getNewTarget();
        if (!(newTarget instanceof Player)) {
            return;
        }

        // If the mob is freezing, CANCEL the targeting attempt
        if (mob.getTicksFrozen() > 0) {
            event.setCanceled(true);
            // Also clear any existing target
            mob.setTarget(null);
        }
    }
}
