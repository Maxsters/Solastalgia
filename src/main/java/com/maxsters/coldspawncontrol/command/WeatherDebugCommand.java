package com.maxsters.coldspawncontrol.command;

import com.maxsters.coldspawncontrol.client.SnowRequestHandler;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class WeatherDebugCommand {

    @SuppressWarnings("null")
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("weatherdebug")
                .requires(source -> source.hasPermission(2)) // OP only
                .executes(context -> {
                    boolean isAnchored = SnowRequestHandler.isAnchored();
                    boolean inCaveAir = SnowRequestHandler.isInCaveAir();
                    double anchorDist = SnowRequestHandler.getAnchorDistance();
                    double radius = SnowRequestHandler.getRadius();

                    String mode;
                    if (inCaveAir) {
                        if (isAnchored) {
                            mode = "CAVE MODE (Anchored to exit)";
                        } else {
                            mode = "DEEP CAVE (No sky found)";
                        }
                    } else {
                        mode = "FOLLOWING PLAYER (Open field)";
                    }

                    String msg = "Weather System State:\n" +
                            "  Mode: " + mode + "\n" +
                            "  In Cave Air: " + inCaveAir + "\n" +
                            "  Anchored: " + isAnchored + "\n" +
                            "  Anchor Distance: " + String.format("%.1f", anchorDist) + " blocks\n" +
                            "  Radius: " + String.format("%.1f", radius) + " blocks";

                    context.getSource().sendSuccess(Component.literal(msg), false);
                    return 1;
                }));
    }
}
