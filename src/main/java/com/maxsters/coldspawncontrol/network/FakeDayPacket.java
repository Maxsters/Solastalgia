package com.maxsters.coldspawncontrol.network;

import com.maxsters.coldspawncontrol.client.AmnesiaClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet to sync the fake day counter from server to client.
 * This updates the F3 display with the randomized day value.
 */
public class FakeDayPacket {

    private final int fakeDay;

    public FakeDayPacket(int fakeDay) {
        this.fakeDay = fakeDay;
    }

    public static void encode(FakeDayPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.fakeDay);
    }

    public static FakeDayPacket decode(FriendlyByteBuf buffer) {
        return new FakeDayPacket(buffer.readInt());
    }

    public static void handle(FakeDayPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            // Client-side: update the fake day state
            AmnesiaClientState.setFakeDay(packet.fakeDay);
        });
        context.get().setPacketHandled(true);
    }
}
