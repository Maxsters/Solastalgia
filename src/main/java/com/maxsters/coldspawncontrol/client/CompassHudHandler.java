package com.maxsters.coldspawncontrol.client;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = ColdSpawnControl.MOD_ID)
@SuppressWarnings("null")
public class CompassHudHandler {

    private static boolean wasHoldingCompass = false;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null)
            return;

        Player player = mc.player;

        // Check if holding compass in main hand or offhand
        boolean holdingCompass = player.getMainHandItem().getItem() == Items.COMPASS ||
                player.getOffhandItem().getItem() == Items.COMPASS;

        // If player just unequipped compass, clear the action bar
        if (wasHoldingCompass && !holdingCompass) {
            player.displayClientMessage(Component.literal(""), true);
        }

        wasHoldingCompass = holdingCompass;

        // Check for blindness (proxy for Amnesia/Forget mechanic)
        // If blind, we don't show coords to give priority to the confusion messages
        boolean isBlind = player.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);

        if (holdingCompass && !isBlind) {
            String coords = String.format("X: %d, Y: %d, Z: %d",
                    player.blockPosition().getX(),
                    player.blockPosition().getY(),
                    player.blockPosition().getZ());

            player.displayClientMessage(Component.literal(coords), true);
        }
    }
}
