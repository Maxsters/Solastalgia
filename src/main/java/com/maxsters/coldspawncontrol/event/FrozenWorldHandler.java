package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@SuppressWarnings("null")
@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public final class FrozenWorldHandler {
    @SubscribeEvent
    public static void lockWeather(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            if (Level.OVERWORLD.equals(level.dimension())) {
                // Only update if not already in storm mode to avoid redundant calls
                if (!level.getLevelData().isThundering() || !level.getLevelData().isRaining()) {
                    // Lock the overworld into a perpetual blizzard.
                    level.setWeatherParameters(0, 600000, true, true);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerInteract(
            net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickItem event) {
        if (event.getItemStack().getItem() == net.minecraft.world.item.Items.STICK && event.getEntity().isCreative()) {
            net.minecraft.core.BlockPos pos = event.getPos();
            net.minecraft.world.level.biome.Biome biome = event.getLevel().getBiome(pos).value();
            String side = event.getLevel().isClientSide ? "CLIENT" : "SERVER";
            String msg = side + " | Biome: "
                    + event.getLevel().registryAccess().registryOrThrow(net.minecraft.core.Registry.BIOME_REGISTRY)
                            .getKey(biome)
                    +
                    " | Base Temp: " + biome.getBaseTemperature() +
                    " | Precip: " + biome.getPrecipitation();
            event.getEntity().sendSystemMessage(net.minecraft.network.chat.Component.literal(msg));
        }
    }
}
