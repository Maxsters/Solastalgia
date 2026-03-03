package com.maxsters.coldspawncontrol.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Packet sent during blackout to disorient the player by modifying
 * client-side settings they wouldn't expect to change.
 *
 * Effects:
 * - FOV shift: changes the player's FOV by a random delta (±1 to ±10)
 * - Key swap: inverts one movement axis (left↔right or forward↔back)
 *
 * These are persistent until the next blackout or manual correction,
 * reinforcing the amnesia "something is wrong" feeling.
 */
public class AmnesiaDisorientPacket {

    /** FOV change amount. 0 = no change. */
    private final int fovDelta;

    /**
     * Which movement axis to swap.
     * -1 = no swap, 0 = left/right (A↔D), 1 = forward/back (W↔S)
     */
    private final int swapAxis;

    public AmnesiaDisorientPacket(int fovDelta, int swapAxis) {
        this.fovDelta = fovDelta;
        this.swapAxis = swapAxis;
    }

    public static void encode(AmnesiaDisorientPacket packet, FriendlyByteBuf buffer) {
        buffer.writeInt(packet.fovDelta);
        buffer.writeInt(packet.swapAxis);
    }

    public static AmnesiaDisorientPacket decode(FriendlyByteBuf buffer) {
        return new AmnesiaDisorientPacket(buffer.readInt(), buffer.readInt());
    }

    public static void handle(AmnesiaDisorientPacket packet, Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> {
            net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                    () -> () -> {
                        com.maxsters.coldspawncontrol.client.ClientPacketHandler.handleDisorient(packet.fovDelta,
                                packet.swapAxis);
                    });
        });
        context.get().setPacketHandled(true);
    }
}
