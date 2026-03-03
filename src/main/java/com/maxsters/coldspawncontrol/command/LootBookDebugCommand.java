package com.maxsters.coldspawncontrol.command;

import com.maxsters.coldspawncontrol.config.ModGameRules;
import com.maxsters.coldspawncontrol.registry.OminousBookTracker;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;

public class LootBookDebugCommand {

    @SuppressWarnings("null")
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lootbook")
                .requires(source -> source.hasPermission(2)) // OP only
                .executes(context -> {
                    MinecraftServer server = context.getSource().getServer();
                    GameRules rules = server.getGameRules();

                    // Toggle the gamerule
                    boolean currentState = rules.getBoolean(ModGameRules.RULE_DEBUG_LOOT_BOOK);
                    boolean newState = !currentState;
                    rules.getRule(ModGameRules.RULE_DEBUG_LOOT_BOOK).set(newState, server);

                    // Get book acquisition status
                    ServerLevel overworld = server.getLevel(Level.OVERWORLD);
                    OminousBookTracker tracker = OminousBookTracker.get(overworld);
                    boolean bookFound = tracker.isBookAcquired();

                    // Build status message
                    String debugStatus = newState
                            ? "§aENABLED §7(100% drop rate, ignores acquired status)"
                            : "§cDISABLED §7(1% drop rate)";
                    String acquiredStatus = bookFound
                            ? "§6Book already acquired §7- generation halted"
                            : "§2Book not yet found §7- generation active";

                    context.getSource().sendSuccess(Component.literal(
                            "§eLoot Book Debug: " + debugStatus), true);
                    context.getSource().sendSuccess(Component.literal(
                            "§eAcquisition Status: " + acquiredStatus), true);
                    return 1;
                }));
    }
}
