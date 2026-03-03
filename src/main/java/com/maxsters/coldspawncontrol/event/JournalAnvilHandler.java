package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Listens for anvil renames on writable books. If the new name contains
 * "Journal" (case-insensitive), the {@code solastalgia_journal} tag is
 * added to the output, making it unsignable and identifiable as our journal.
 *
 * If renamed to something without "Journal", the tag is removed.
 */
@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public class JournalAnvilHandler {

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack input = event.getLeft();

        // Only care about writable books
        if (input.isEmpty() || !input.is(Items.WRITABLE_BOOK))
            return;

        // Only care about renames (right slot empty, name present)
        if (!event.getRight().isEmpty())
            return;

        String newName = event.getName();
        if (newName == null || newName.isEmpty())
            return;

        boolean containsJournal = newName.toLowerCase().contains("journal");

        // Create the output as a copy
        ItemStack output = input.copy();
        CompoundTag tag = output.getOrCreateTag();

        if (containsJournal) {
            // Add our journal tag
            tag.putBoolean("solastalgia_journal", true);
        } else {
            // Remove the tag if renamed to something else
            tag.remove("solastalgia_journal");
        }

        // Set the display name
        output.setHoverName(net.minecraft.network.chat.Component.literal(newName));

        event.setOutput(output);
        event.setCost(1); // 1 level to rename
    }
}
