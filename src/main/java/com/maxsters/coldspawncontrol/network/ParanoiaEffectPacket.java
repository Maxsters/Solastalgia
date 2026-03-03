package com.maxsters.coldspawncontrol.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Generic packet for triggering client-side paranoia effects.
 * Uses an effect type ID to determine which effect to play.
 */
public class ParanoiaEffectPacket {

    private final int effectType;

    public ParanoiaEffectPacket(int effectType) {
        this.effectType = effectType;
    }

    public ParanoiaEffectPacket(FriendlyByteBuf buf) {
        this.effectType = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(effectType);
    }

    public static ParanoiaEffectPacket decode(FriendlyByteBuf buf) {
        return new ParanoiaEffectPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handleClient());
        });
        ctx.get().setPacketHandled(true);
    }

    private void handleClient() {
        // No effects currently defined
    }
}
