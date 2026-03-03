package com.maxsters.coldspawncontrol.network;

import com.maxsters.coldspawncontrol.client.ClientRealisticModeState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ConfigSyncPacket {

    public final boolean realisticMode;
    public final boolean renderCloudCoverage;

    public ConfigSyncPacket(boolean realisticMode, boolean renderCloudCoverage) {
        this.realisticMode = realisticMode;
        this.renderCloudCoverage = renderCloudCoverage;
    }

    public static void encode(ConfigSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.realisticMode);
        buffer.writeBoolean(packet.renderCloudCoverage);
    }

    public static ConfigSyncPacket decode(FriendlyByteBuf buffer) {
        return new ConfigSyncPacket(buffer.readBoolean(), buffer.readBoolean());
    }

    public static void handle(ConfigSyncPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            // Client-side: update the state
            ClientRealisticModeState.isRealisticModeEnabled = packet.realisticMode;
            ClientRealisticModeState.renderCloudCoverage = packet.renderCloudCoverage;
            com.maxsters.coldspawncontrol.ColdSpawnControl.LOGGER.info(
                    "Client received ConfigSyncPacket: Realistic={}, CloudCoverage={}",
                    packet.realisticMode, packet.renderCloudCoverage);
        });
        context.get().setPacketHandled(true);
    }
}
