package com.maxsters.coldspawncontrol.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * Helper class for sending amnesia-related network packets.
 */
public class AmnesiaNetworkHandler {

    /**
     * Sends the fake day counter to a specific client.
     */
    public static void sendFakeDayToClient(ServerPlayer player, int fakeDay) {
        Networking.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new FakeDayPacket(fakeDay));
    }
}
