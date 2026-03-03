package com.maxsters.coldspawncontrol.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Network packet to play a sound with proper positional audio on the client.
 * Uses SimpleSoundInstance with Attenuation.LINEAR for 3D positional sound.
 */
@SuppressWarnings("null")
public class PositionalSoundPacket {
    private final ResourceLocation soundId;
    private final double x, y, z;
    private final float volume;
    private final float pitch;

    public PositionalSoundPacket(SoundEvent sound, double x, double y, double z, float volume, float pitch) {
        this.soundId = sound.getLocation();
        this.x = x;
        this.y = y;
        this.z = z;
        this.volume = volume;
        this.pitch = pitch;
    }

    public PositionalSoundPacket(FriendlyByteBuf buf) {
        this.soundId = buf.readResourceLocation();
        this.x = buf.readDouble();
        this.y = buf.readDouble();
        this.z = buf.readDouble();
        this.volume = buf.readFloat();
        this.pitch = buf.readFloat();
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeResourceLocation(soundId);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        buf.writeFloat(volume);
        buf.writeFloat(pitch);
    }

    public static PositionalSoundPacket decode(FriendlyByteBuf buf) {
        return new PositionalSoundPacket(buf);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.maxsters.coldspawncontrol.client.ClientPacketHandler.handlePositionalSound(this));
        });
        ctx.get().setPacketHandled(true);
    }

    public ResourceLocation getSoundId() {
        return soundId;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getVolume() {
        return volume;
    }

    public float getPitch() {
        return pitch;
    }
}
