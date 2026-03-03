package com.maxsters.coldspawncontrol.network;

import com.maxsters.coldspawncontrol.client.ClientVisibilityState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class VisibilityPacket {
    private final boolean enabled;

    public VisibilityPacket(boolean enabled) {
        this.enabled = enabled;
    }

    public static void encode(VisibilityPacket msg, FriendlyByteBuf buffer) {
        buffer.writeBoolean(msg.enabled);
    }

    public static VisibilityPacket decode(FriendlyByteBuf buffer) {
        return new VisibilityPacket(buffer.readBoolean());
    }

    public static void handle(VisibilityPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            // Client-side handling
            ClientVisibilityState.debugMode = msg.enabled;
        });
        ctx.get().setPacketHandled(true);
    }
}
