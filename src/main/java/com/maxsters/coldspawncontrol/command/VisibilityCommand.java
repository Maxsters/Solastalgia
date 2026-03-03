package com.maxsters.coldspawncontrol.command;

import com.maxsters.coldspawncontrol.config.ModGameRules;
import com.maxsters.coldspawncontrol.network.Networking;
import com.maxsters.coldspawncontrol.network.VisibilityPacket;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.GameRules;

public class VisibilityCommand {

        @SuppressWarnings("null")
        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
                dispatcher.register(Commands.literal("visibility")
                                .requires(source -> source.hasPermission(2)) // OP only
                                .executes(context -> {
                                        MinecraftServer server = context.getSource().getServer();
                                        GameRules rules = server.getGameRules();

                                        // Toggle the per-world gamerule
                                        boolean currentState = rules.getBoolean(ModGameRules.RULE_DEBUG_MODE);
                                        boolean newState = !currentState;
                                        rules.getRule(ModGameRules.RULE_DEBUG_MODE).set(newState, server);

                                        // Sync visibility to all clients
                                        Networking.sendToAll(new VisibilityPacket(newState));

                                        // Also re-sync Realistic Mode to ensure consistent state
                                        boolean realisticParams = rules.getBoolean(ModGameRules.RULE_REALISTIC_MODE);
                                        boolean cloudCoverageParams = rules
                                                        .getBoolean(ModGameRules.RULE_RENDER_CLOUD_COVERAGE);
                                        Networking.sendToAll(new com.maxsters.coldspawncontrol.network.ConfigSyncPacket(
                                                        realisticParams, cloudCoverageParams));

                                        // Handle Server Side Time
                                        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
                                        if (overworld != null) {
                                                if (newState) {
                                                        // Debug ON: Day time
                                                        overworld.setDayTime(6000);
                                                } else {
                                                        // Debug OFF: Will be fixed by Enforcer next tick.
                                                }

                                                // Handle Reduced Debug Info
                                                // If Debug Mode is ON (newState=true) -> Reduced Info OFF (Show F3)
                                                // If Debug Mode is OFF (newState=false) -> Reduced Info ON (Hide F3)
                                                overworld.getGameRules().getRule(GameRules.RULE_REDUCEDDEBUGINFO)
                                                                .set(!newState, server);
                                        }

                                        context.getSource().sendSuccess(Component.literal(
                                                        "Visibility Debug Mode: " + (newState
                                                                        ? "ENABLED (Day/No Fog/F3 Shown)"
                                                                        : "DISABLED (Night/Black/F3 Hidden)")),
                                                        true);
                                        return 1;
                                }));
        }
}
