package com.maxsters.coldspawncontrol.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Utility for calculating insulation/cold resistance.
 * Extracted from AmnesiaBlackoutHandler to share logic with JournalContext.
 */
public final class InsulationCalculator {

    private InsulationCalculator() {
        // Utility class
    }

    /**
     * Calculate core temperature/insulation using NBT data from Cold Sweat.
     * returns a normalized score where 1.0 is full goat fur armor (approx 48
     * points).
     */
    public static double calculateInsulation(ServerPlayer player) {
        double totalColdPoints = 0.0;

        for (ItemStack stack : player.getArmorSlots()) {
            if (stack.isEmpty())
                continue;

            double itemColdPoints = 0.0;
            boolean hasInsulationTag = false;
            CompoundTag tag = stack.getTag();

            if (tag != null) {
                // 1. Check "Insulator" tag (Added via Sewing Table)
                if (tag.contains("Insulator")) {
                    CompoundTag insulator = tag.getCompound("Insulator");
                    if (insulator.contains("insulation")) {
                        itemColdPoints += insulator.getCompound("insulation").getDouble("cold");
                        hasInsulationTag = true;
                    }
                }

                // 2. Check "Insulation" tag (Native insulation)
                if (tag.contains("Insulation")) {
                    net.minecraft.nbt.ListTag values = tag.getList("Insulation", 10); // 10 = Compound
                    for (int i = 0; i < values.size(); i++) {
                        CompoundTag valueEntry = values.getCompound(i);
                        if (valueEntry.contains("Insulation")) {
                            itemColdPoints += valueEntry.getCompound("Insulation").getDouble("cold");
                            hasInsulationTag = true;
                        }
                    }
                }
            }

            // 3. Fallback (Manual assignment for items without tags)
            if (!hasInsulationTag) {
                String id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem()).toString();

                if (id.startsWith("cold_sweat:goat_fur_")) {
                    itemColdPoints = 9.6; // 0.2 * 48
                } else if (id.startsWith("minecraft:leather_")) {
                    itemColdPoints = 7.2; // 0.15 * 48
                } else if (stack.getItem() instanceof net.minecraft.world.item.ArmorItem) {
                    itemColdPoints = 4.8; // 0.1 * 48
                }
            }

            totalColdPoints += itemColdPoints;
        }

        // Normalize: 48.0 points (Full Goat Fur) = 1.0 insulation score
        // 0.2 = ~1 piece of armor
        return totalColdPoints / 48.0;
    }
}
