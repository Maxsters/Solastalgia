package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.registry.OminousBookTracker;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Detects when any player picks up the "Torn Field Note" written book
 * and marks it as acquired server-wide via {@link OminousBookTracker}.
 */
@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public class OminousBookPickupHandler {

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onItemPickup(EntityItemPickupEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player))
            return;

        ItemStack stack = event.getItem().getItem();
        if (stack.isEmpty() || !stack.is(Items.WRITTEN_BOOK))
            return;

        // Check if this is our book via custom NBT tag
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean("solastalgia_ominous_book"))
            return;

        // Mark as acquired server-wide
        ServerLevel level = (ServerLevel) player.level;
        OminousBookTracker tracker = OminousBookTracker.get(level);
        if (!tracker.isBookAcquired()) {
            tracker.markBookAcquired();
            ColdSpawnControl.LOGGER.info("Player {} picked up the ominous book. Halting further generation.",
                    player.getName().getString());
        }
    }
}
