package com.maxsters.coldspawncontrol.network;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class Networking {
        private static final String PROTOCOL_VERSION = "1";
        public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
                        new ResourceLocation(ColdSpawnControl.MOD_ID, "main"),
                        () -> PROTOCOL_VERSION,
                        PROTOCOL_VERSION::equals,
                        PROTOCOL_VERSION::equals);

        public static void register() {
                int id = 0;
                CHANNEL.registerMessage(id++, VisibilityPacket.class, VisibilityPacket::encode,
                                VisibilityPacket::decode,
                                VisibilityPacket::handle);
                CHANNEL.registerMessage(id++, FakeDayPacket.class, FakeDayPacket::encode, FakeDayPacket::decode,
                                FakeDayPacket::handle);
                CHANNEL.registerMessage(id++, PositionalSoundPacket.class, PositionalSoundPacket::encode,
                                PositionalSoundPacket::decode, PositionalSoundPacket::handle);
                CHANNEL.registerMessage(id++, ParanoiaFootstepPacket.class, ParanoiaFootstepPacket::encode,
                                ParanoiaFootstepPacket::decode, ParanoiaFootstepPacket::handle);
                CHANNEL.registerMessage(id++, ConfigSyncPacket.class, ConfigSyncPacket::encode,
                                ConfigSyncPacket::decode, ConfigSyncPacket::handle);
                CHANNEL.registerMessage(id++, AmnesiaDisorientPacket.class, AmnesiaDisorientPacket::encode,
                                AmnesiaDisorientPacket::decode, AmnesiaDisorientPacket::handle);
                CHANNEL.registerMessage(id++, ParanoiaEffectPacket.class, ParanoiaEffectPacket::encode,
                                ParanoiaEffectPacket::decode, ParanoiaEffectPacket::handle);
        }

        public static void sendToAll(Object packet) {
                CHANNEL.send(net.minecraftforge.network.PacketDistributor.ALL.noArg(), packet);
        }

        public static void sendToPlayer(Object packet, net.minecraft.server.level.ServerPlayer player) {
                CHANNEL.send(net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> player), packet);
        }
}
