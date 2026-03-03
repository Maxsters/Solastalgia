package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;

import com.maxsters.coldspawncontrol.network.Networking;
import com.maxsters.coldspawncontrol.config.ModGameRules;
import com.maxsters.coldspawncontrol.network.VisibilityPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public class RealisticModeSyncHandler {

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // 1. Sync Realistic Mode and Cloud Coverage
            net.minecraft.world.level.GameRules rules = player.getLevel().getGameRules();
            boolean realistic = rules.getBoolean(ModGameRules.RULE_REALISTIC_MODE);
            boolean cloudCoverage = rules.getBoolean(ModGameRules.RULE_RENDER_CLOUD_COVERAGE);
            Networking.sendToPlayer(
                    new com.maxsters.coldspawncontrol.network.ConfigSyncPacket(realistic, cloudCoverage), player);

            // 2. Sync Visibility/Debug Mode (from per-world gamerule)
            boolean debug = rules.getBoolean(ModGameRules.RULE_DEBUG_MODE);
            Networking.sendToPlayer(new VisibilityPacket(debug), player);

            ColdSpawnControl.LOGGER.info("Synced state to player {}: Realistic={}, Cloud={}, Debug={}",
                    player.getName().getString(), realistic, cloudCoverage, debug);
        }
    }
}
