package com.maxsters.coldspawncontrol.command;

import com.maxsters.coldspawncontrol.event.AmnesiaBlackoutHandler;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Debug command to trigger amnesia blackout immediately.
 * Usage: /forget [distance]
 * - /forget - Random teleport 100-1000 blocks
 * - /forget 500 - Teleport exactly 500 blocks away
 */
@SuppressWarnings("null")
@Mod.EventBusSubscriber(modid = com.maxsters.coldspawncontrol.ColdSpawnControl.MOD_ID)
public class ForgetCommand {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("forget")
                .requires(source -> source.hasPermission(2)) // Require cheats/op
                // No argument - random distance
                .executes(context -> {
                    return executeForget(context.getSource(), -1); // -1 = random
                })
                // With distance argument
                .then(Commands.argument("distance", IntegerArgumentType.integer(10, 1000))
                        .executes(context -> {
                            int distance = IntegerArgumentType.getInteger(context, "distance");
                            return executeForget(context.getSource(), distance);
                        })));
    }

    private static int executeForget(CommandSourceStack source, int forcedDistance) {
        if (source.getEntity() instanceof ServerPlayer executor) {
            boolean hasJournal = com.maxsters.coldspawncontrol.slm.JournalEntryGenerator.findJournal(executor) != null;

            if (forcedDistance > 0) {
                source.sendSuccess(
                        Component.literal("§7[Amnesia] Scheduling global blackout (" + forcedDistance + " blocks)..."),
                        false);
                AmnesiaBlackoutHandler.scheduleGlobalBlackout(executor.server.getPlayerList().getPlayers(),
                        forcedDistance, true);
            } else {
                source.sendSuccess(Component.literal("§7[Amnesia] Scheduling global blackout (random distance)..."),
                        false);
                AmnesiaBlackoutHandler.scheduleGlobalBlackout(executor.server.getPlayerList().getPlayers(), -1, true);
            }

            if (hasJournal) {
                if (com.maxsters.coldspawncontrol.slm.ModelManager.isReady()) {
                    boolean isGpu = com.maxsters.coldspawncontrol.slm.ModelManager.isRunningOnGpu();
                    String hwName = com.maxsters.coldspawncontrol.slm.ModelManager.getHardwareName();
                    String hwType = isGpu ? "GPU" : "CPU";
                    source.sendSuccess(Component.literal(
                            String.format("§7[Amnesia] SLM generation started. Running on %s: %s", hwType, hwName)),
                            false);
                } else {
                    source.sendSuccess(
                            Component.literal("§7[Amnesia] SLM generation started, event will trigger shortly."),
                            false);
                }
            }
            return 1;
        } else {
            source.sendFailure(Component.literal("This command can only be used by a player."));
            return 0;
        }
    }
}
