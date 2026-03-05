package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.registry.OminousBookTracker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;

/**
 * Detects when any player acquires the "Torn Field Note" written book
 * and marks it as acquired server-wide via {@link OminousBookTracker}.
 *
 * Uses two detection methods:
 * - EntityItemPickupEvent for ground pickups (instant)
 * - PlayerTickEvent inventory scan for chest/hopper/trade acquisition
 * (periodic)
 */
@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public class OminousBookPickupHandler {

    private static final int INVENTORY_CHECK_INTERVAL = 20; // 1 second

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;

        ItemStack stack = event.getItem().getItem();
        if (isOminousBook(stack)) {
            markAcquired(player);
        }
    }

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.side != LogicalSide.SERVER)
            return;

        ServerPlayer player = (ServerPlayer) event.player;
        ServerLevel level = (ServerLevel) player.level;

        if (level.getGameTime() % INVENTORY_CHECK_INTERVAL != 0)
            return;

        OminousBookTracker tracker = OminousBookTracker.get(level);
        if (tracker.isBookAcquired())
            return;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (isOminousBook(stack)) {
                markAcquired(player);
                return;
            }
        }
    }

    @SuppressWarnings("null")
    private static boolean isOminousBook(ItemStack stack) {
        if (stack.isEmpty() || !stack.is(Items.WRITTEN_BOOK))
            return false;

        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean("solastalgia_ominous_book");
    }

    @SuppressWarnings("null")
    private static void markAcquired(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level;
        OminousBookTracker tracker = OminousBookTracker.get(level);
        if (!tracker.isBookAcquired()) {
            tracker.markBookAcquired();
            ColdSpawnControl.LOGGER.info("Player {} acquired the ominous book. Halting further generation.",
                    player.getName().getString());
        }
    }
}
