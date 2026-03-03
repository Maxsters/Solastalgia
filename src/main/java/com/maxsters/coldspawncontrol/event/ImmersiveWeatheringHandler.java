package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public class ImmersiveWeatheringHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getItemStack().getItem() == Items.SNOWBALL) {
            // Cancel default interaction to prevent issues with other mods or default
            // behavior
            event.setCanceled(true);
        }
    }

    /**
     * Prevents thrown snowballs from triggering conversion on impact.
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getEntity() instanceof Snowball) {
            if (event.getRayTraceResult() instanceof BlockHitResult) {
                // Cancel impact logic (which includes IW conversion)
                event.setCanceled(true);
            }
        }
    }
}
