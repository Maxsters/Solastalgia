package com.maxsters.coldspawncontrol.network;

import com.maxsters.coldspawncontrol.client.AmbientParanoiaHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class ParanoiaFootstepPacket {
    private final int minSteps;
    private final int maxSteps;

    public ParanoiaFootstepPacket(int minSteps, int maxSteps) {
        this.minSteps = minSteps;
        this.maxSteps = maxSteps;
    }

    public ParanoiaFootstepPacket(FriendlyByteBuf buf) {
        this.minSteps = buf.readInt();
        this.maxSteps = buf.readInt();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeInt(minSteps);
        buf.writeInt(maxSteps);
    }

    public static ParanoiaFootstepPacket decode(FriendlyByteBuf buf) {
        return new ParanoiaFootstepPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                AmbientParanoiaHandler.startFootstepSequence(minSteps, maxSteps);
            });
        });
        ctx.get().setPacketHandled(true);
    }
}
