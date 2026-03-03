package com.maxsters.coldspawncontrol.common;

import net.minecraft.world.Difficulty;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.config.ModGameRules;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DifficultyEnforcer {

    /**
     * Checks whether debug/visibility mode is currently active for the given
     * server.
     * Reads from the per-world gamerule instead of a static field.
     */
    @SuppressWarnings("null")
    public static boolean isDebugMode(MinecraftServer server) {
        if (server == null || ModGameRules.RULE_DEBUG_MODE == null)
            return false;
        return server.getGameRules().getBoolean(ModGameRules.RULE_DEBUG_MODE);
    }

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        MinecraftServer server = event.getServer();
        if (server == null)
            return;

        // If Debug Mode is ON, do NOT enforce anything.
        if (isDebugMode(server))
            return;

        // Enforce HARD difficulty and Locked state
        // Note: server.getWorldData() returns the global world data.
        if (server.getWorldData().getDifficulty() != Difficulty.HARD) {
            server.setDifficulty(Difficulty.HARD, true);

        }

        if (!server.getWorldData().isDifficultyLocked()) {
            server.getWorldData().setDifficultyLocked(true);
        }

        // Enforce Permanent Midnight (Time = 18000)
        ServerLevel overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld != null) {
            long distinctTime = overworld.getDayTime() % 24000;
            if (Math.abs(distinctTime - 18000) > 100) {
                overworld.setDayTime(18000);
            }

            // Disable Daylight Cycle Rule
            if (overworld.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_DAYLIGHT)) {
                overworld.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_DAYLIGHT).set(false, server);
            }

            // Enable Reduced Debug Info (hide F3 coordinates)
            if (!overworld.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_REDUCEDDEBUGINFO)) {
                overworld.getGameRules().getRule(net.minecraft.world.level.GameRules.RULE_REDUCEDDEBUGINFO).set(true,
                        server);
            }
        }
    }
}
