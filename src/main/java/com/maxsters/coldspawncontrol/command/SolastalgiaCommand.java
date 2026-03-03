package com.maxsters.coldspawncontrol.command;

import com.maxsters.coldspawncontrol.config.ModGameRules;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.GameRules;

public class SolastalgiaCommand {

        @SuppressWarnings("null")
        public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
                dispatcher.register(Commands.literal("solastalgia")
                                .requires(source -> true) // Allow ALL players to see the command (Permissions handled
                                                          // below if needed,
                                                          // but intended to be open)
                                .then(Commands.literal("realisticMode")
                                                .then(Commands.argument("value", BoolArgumentType.bool())
                                                                .executes(context -> {
                                                                        boolean value = BoolArgumentType
                                                                                        .getBool(context, "value");
                                                                        GameRules rules = context.getSource().getLevel()
                                                                                        .getGameRules();
                                                                        rules.getRule(ModGameRules.RULE_REALISTIC_MODE)
                                                                                        .set(value,
                                                                                                        context.getSource()
                                                                                                                        .getServer());
                                                                        context.getSource().sendSuccess(Component
                                                                                        .literal("Set realisticMode to "
                                                                                                        + value),
                                                                                        true);
                                                                        return 1;
                                                                })))
                                .then(Commands.literal("renderCloudCoverage")
                                                .then(Commands.argument("value", BoolArgumentType.bool())
                                                                .executes(context -> {
                                                                        boolean value = BoolArgumentType
                                                                                        .getBool(context, "value");
                                                                        GameRules rules = context.getSource().getLevel()
                                                                                        .getGameRules();
                                                                        rules.getRule(ModGameRules.RULE_RENDER_CLOUD_COVERAGE)
                                                                                        .set(value,
                                                                                                        context.getSource()
                                                                                                                        .getServer());
                                                                        context.getSource().sendSuccess(
                                                                                        Component.literal(
                                                                                                        "Set renderCloudCoverage to "
                                                                                                                        + value),
                                                                                        true);
                                                                        return 1;
                                                                }))));
        }
}
