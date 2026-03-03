package com.maxsters.coldspawncontrol.command;

import com.maxsters.coldspawncontrol.event.ParanoiaManager;
import com.maxsters.coldspawncontrol.network.Networking;
import com.maxsters.coldspawncontrol.network.ParanoiaFootstepPacket;
import com.maxsters.coldspawncontrol.network.PositionalSoundPacket;
import com.maxsters.coldspawncontrol.registry.ModSoundEvents;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraftforge.network.PacketDistributor;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Random;

/**
 * Debug commands for testing paranoia effects.
 * Supports targeting specific players (multiplayer compatible).
 * 
 * Commands:
 * /paranoia footsteps [targets] - Ghost footsteps behind player
 * /paranoia shadow [targets] - Shadow flicker entity near light
 * /paranoia sound customsound [targets] - Environmental paranoia sound (custom
 * audio)
 * /paranoia sound wrongblock [targets] - Wrong block break sound
 * /paranoia sound hostile [targets] - False hostile mob targeting sound
 * /paranoia sound mobsound [targets] - Fake mob ambient sound
 */
@SuppressWarnings("null")
public class ParanoiaCommand {

    private static final Random RANDOM = new Random();

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("paranoia")
                .requires(source -> source.hasPermission(2))
                // Non-sound effects
                .then(Commands.literal("footsteps")
                        .executes(context -> triggerFootsteps(context,
                                Collections.singleton(context.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> triggerFootsteps(context,
                                        EntityArgument.getPlayers(context, "targets")))))
                .then(Commands.literal("shadow")
                        .executes(context -> triggerShadow(context,
                                Collections.singleton(context.getSource().getPlayerOrException())))
                        .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> triggerShadow(context,
                                        EntityArgument.getPlayers(context, "targets")))))
                // Sound effects grouped under /paranoia sound <type>
                .then(Commands.literal("sound")
                        .then(Commands.literal("customsound")
                                .executes(context -> triggerSound(context,
                                        Collections.singleton(context.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(context -> triggerSound(context,
                                                EntityArgument.getPlayers(context, "targets")))))
                        .then(Commands.literal("wrongblock")
                                .executes(context -> triggerWrongSound(context,
                                        Collections.singleton(context.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(context -> triggerWrongSound(context,
                                                EntityArgument.getPlayers(context, "targets")))))
                        .then(Commands.literal("hostile")
                                .executes(context -> triggerHostile(context,
                                        Collections.singleton(context.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(context -> triggerHostile(context,
                                                EntityArgument.getPlayers(context, "targets")))))
                        .then(Commands.literal("mobsound")
                                .executes(context -> triggerMobSound(context,
                                        Collections.singleton(context.getSource().getPlayerOrException())))
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(context -> triggerMobSound(context,
                                                EntityArgument.getPlayers(context, "targets")))))));

        // Subtitle hallucinations
        LiteralArgumentBuilder<CommandSourceStack> subtitleNode = Commands.literal("subtitle");
        Map<String, java.util.function.Supplier<SoundEvent>> subtitles = Map.of(
                "confusion", ModSoundEvents.SUBTITLE_CONFUSION,
                "grief", ModSoundEvents.SUBTITLE_GRIEF,
                "disorientation", ModSoundEvents.SUBTITLE_DISORIENTATION,
                "despair", ModSoundEvents.SUBTITLE_DESPAIR,
                "muscle_memory", ModSoundEvents.SUBTITLE_MUSCLE_MEMORY,
                "paranoia", ModSoundEvents.SUBTITLE_PARANOIA,
                "starvation", ModSoundEvents.SUBTITLE_STARVATION,
                "guilt", ModSoundEvents.SUBTITLE_GUILT,
                "false_hope", ModSoundEvents.SUBTITLE_FALSE_HOPE,
                "dissociation", ModSoundEvents.SUBTITLE_DISSOCIATION);

        // Base /paranoia subtitle command to trigger a random subtitle
        subtitleNode.executes(context -> {
            var keys = new java.util.ArrayList<>(subtitles.keySet());
            String randomKey = keys.get(RANDOM.nextInt(keys.size()));
            return triggerSubtitle(context, subtitles.get(randomKey).get(),
                    Collections.singleton(context.getSource().getPlayerOrException()));
        });

        // Base /paranoia subtitle <targets> command to trigger a random subtitle for
        // targets
        subtitleNode.then(Commands.argument("targets", EntityArgument.players())
                .executes(context -> {
                    var keys = new java.util.ArrayList<>(subtitles.keySet());
                    String randomKey = keys.get(RANDOM.nextInt(keys.size()));
                    return triggerSubtitle(context, subtitles.get(randomKey).get(),
                            EntityArgument.getPlayers(context, "targets"));
                }));

        for (Map.Entry<String, java.util.function.Supplier<SoundEvent>> entry : subtitles.entrySet()) {
            subtitleNode.then(Commands.literal(entry.getKey())
                    .executes(context -> triggerSubtitle(context, entry.getValue().get(),
                            Collections.singleton(context.getSource().getPlayerOrException())))
                    .then(Commands.argument("targets", EntityArgument.players())
                            .executes(context -> triggerSubtitle(context, entry.getValue().get(),
                                    EntityArgument.getPlayers(context, "targets")))));
        }

        dispatcher
                .register(Commands.literal("paranoia").requires(source -> source.hasPermission(2)).then(subtitleNode));
    }

    // ==================== EXISTING COMMANDS ====================

    private static int triggerFootsteps(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets) {
        try {
            int successCount = 0;
            for (ServerPlayer player : targets) {
                // Send packet to trigger client-side effect
                Networking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ParanoiaFootstepPacket(2, 4));
                successCount++;
            }

            if (successCount == 1 && targets.size() == 1) {
                context.getSource().sendSuccess(
                        Component.literal("Triggered footsteps for " + targets.iterator().next().getName().getString()),
                        true);
            } else {
                context.getSource()
                        .sendSuccess(Component.literal("Triggered footsteps for " + successCount + " players"), true);
            }
            return successCount;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int triggerShadow(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets) {
        try {
            int successCount = 0;
            for (ServerPlayer player : targets) {
                ServerLevel level = player.getLevel();
                ParanoiaManager.SpawnResult result = ParanoiaManager.trySpawnShadow(player, level);

                if (result == ParanoiaManager.SpawnResult.SUCCESS) {
                    successCount++;
                } else if (targets.size() == 1) {
                    // If only targeting one player, report specific failure
                    context.getSource().sendFailure(Component.literal("Failed to spawn shadow: " + result.name()));
                }
            }

            if (successCount > 0) {
                context.getSource().sendSuccess(Component.literal("Spawned shadows for " + successCount + " players"),
                        true);
            }
            return successCount;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int triggerSound(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets) {
        try {
            int successCount = 0;
            for (ServerPlayer player : targets) {
                ServerLevel level = player.getLevel();
                triggerPlayerSound(player, level);
                successCount++;
            }

            if (successCount == 1 && targets.size() == 1) {
                context.getSource().sendSuccess(
                        Component.literal("Triggered sound for " + targets.iterator().next().getName().getString()),
                        true);
            } else {
                context.getSource().sendSuccess(Component.literal("Triggered sound for " + successCount + " players"),
                        true);
            }
            return successCount;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== NEW COMMANDS ====================

    private static int triggerWrongSound(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets) {
        try {
            int successCount = 0;
            for (ServerPlayer player : targets) {
                // Use the player's current block position as the "broken block" position
                BlockPos pos = player.blockPosition();
                ParanoiaManager.triggerWrongBlockSound(player, pos);
                successCount++;
            }
            sendSuccessMessage(context, "wrong block sound", targets, successCount);
            return successCount;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int triggerHostile(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets) {
        try {
            int successCount = 0;
            for (ServerPlayer player : targets) {
                ParanoiaManager.triggerFalseHostile(player);
                successCount++;
            }
            sendSuccessMessage(context, "false hostile indicator", targets, successCount);
            return successCount;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static int triggerMobSound(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> targets) {
        try {
            int successCount = 0;
            for (ServerPlayer player : targets) {
                ParanoiaManager.triggerFakeMobSound(player);
                successCount++;
            }
            sendSuccessMessage(context, "fake mob sound", targets, successCount);
            return successCount;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    // ==================== UTILITY METHODS ====================

    private static void sendSuccessMessage(CommandContext<CommandSourceStack> context, String effectName,
            Collection<ServerPlayer> targets, int successCount) {
        if (successCount == 1 && targets.size() == 1) {
            context.getSource().sendSuccess(
                    Component.literal("Triggered " + effectName + " for "
                            + targets.iterator().next().getName().getString()),
                    true);
        } else {
            context.getSource().sendSuccess(
                    Component.literal("Triggered " + effectName + " for " + successCount + " players"), true);
        }
    }

    private static int triggerSubtitle(CommandContext<CommandSourceStack> context, SoundEvent sound,
            Collection<ServerPlayer> targets) {
        try {
            int successCount = 0;
            for (ServerPlayer player : targets) {
                // Play sound packet at 1.0F volume so it hits the player's listener.
                // It remains silent because the SoundEvent has no 'sounds' mapped in
                // sounds.json, but still fires the subtitle.
                Networking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                        new PositionalSoundPacket(sound, player.getX(), player.getY(), player.getZ(), 1.0F, 1.0F));
                successCount++;
            }
            sendSuccessMessage(context, "subtitle hallucination", targets, successCount);
            return successCount;
        } catch (Exception e) {
            context.getSource().sendFailure(Component.literal("Error: " + e.getMessage()));
            return 0;
        }
    }

    private static void triggerPlayerSound(ServerPlayer player, ServerLevel level) {
        // Distance 5-12 blocks behind player
        double dist = 5 + RANDOM.nextDouble() * 7;
        double[] pos = getPositionBehindPlayer(player, dist);

        // Choose sound based on cave/outside
        boolean inCave = isPlayerInCave(player, level);
        SoundEvent sound = inCave ? ModSoundEvents.PARANOIA_CAVE.get() : ModSoundEvents.PARANOIA_OUTSIDE.get();

        float volume = 0.8f + RANDOM.nextFloat() * 0.4f;
        float pitch = 0.8f + RANDOM.nextFloat() * 0.4f;

        // Send packet to play sound at position
        Networking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new PositionalSoundPacket(sound, pos[0], pos[1], pos[2], volume, pitch));
    }

    private static boolean isPlayerInCave(ServerPlayer player, Level level) {
        BlockPos pos = player.blockPosition();
        int skyLight = level.getBrightness(LightLayer.SKY, pos.above());
        // Also check if player is below Y=60 for added certainty
        boolean belowSurface = pos.getY() < 60;
        // In cave if no sky light OR definitely underground
        return skyLight == 0 || (belowSurface && skyLight < 4);
    }

    private static double[] getPositionBehindPlayer(ServerPlayer player, double distance) {
        float yRot = player.getYRot();
        // Add 120-240 degrees offset to be definitely behind/peripheral
        float offsetAngle = 120 + RANDOM.nextFloat() * 120; // degrees
        float totalAngle = yRot + offsetAngle;

        float radians = totalAngle * ((float) Math.PI / 180F);
        float x = -Mth.sin(radians);
        float z = Mth.cos(radians);

        double tx = player.getX() + x * distance;
        double tz = player.getZ() + z * distance;

        // +/- 3 blocks vertical
        double ty = player.getY() + (RANDOM.nextDouble() * 6 - 3);

        return new double[] { tx, ty, tz };
    }
}
