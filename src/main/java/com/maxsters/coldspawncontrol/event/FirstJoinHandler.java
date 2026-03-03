package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.registry.AmnesiaAdvancementConfig;
import net.minecraft.advancements.Advancement;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Random;

/**
 * Handles first-time player joins, giving them a survival starter kit.
 * The kit includes Cold Sweat insulated armor, expedition tools, cluttered
 * inventory items, and the amnesia journal.
 * 
 * This only triggers once per player per world (tracked via persistent data).
 */
@SuppressWarnings("null")
@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public class FirstJoinHandler {

    private static final String FIRST_JOIN_KEY = "maxsters_first_join_complete";
    private static final Random RANDOM = new Random();

    // ==================== STARTER KIT CONFIGURATION ====================

    // Cold Sweat goat fur armor item IDs
    private static final String COLD_SWEAT_MOD_ID = "cold_sweat";
    private static final String HELMET_ID = "goat_fur_cap";
    private static final String CHESTPLATE_ID = "goat_fur_parka";
    private static final String LEGGINGS_ID = "goat_fur_pants";
    private static final String BOOTS_ID = "goat_fur_boots";

    // Survival items
    private static final int TORCH_COUNT = 3;
    private static final int BREAD_COUNT = 6;

    // First Aid mod items
    private static final String FIRSTAID_MOD_ID = "firstaid";

    // ==================== EVENT HANDLER ====================

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        // Only apply in the Overworld
        if (!Level.OVERWORLD.equals(player.level.dimension())) {
            return;
        }

        CompoundTag persistentData = player.getPersistentData();

        // Check if player has already received their starter kit in this world
        if (persistentData.getBoolean(FIRST_JOIN_KEY)) {
            return;
        }

        // Give the starter kit
        giveStarterKit(player);

        // Mark as complete so they don't receive it again
        persistentData.putBoolean(FIRST_JOIN_KEY, true);

        ColdSpawnControl.LOGGER.info("Gave first-join starter kit to player: {}", player.getName().getString());
    }

    /**
     * Copies persistent data from old player to new player on death/respawn.
     * Without this, the player entity replacement loses our tracking data.
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // Copy our persistent data keys from old player to new player
        net.minecraft.nbt.CompoundTag oldData = event.getOriginal().getPersistentData();
        net.minecraft.nbt.CompoundTag newData = event.getEntity().getPersistentData();

        if (oldData.contains(FIRST_JOIN_KEY)) {
            newData.putBoolean(FIRST_JOIN_KEY, oldData.getBoolean(FIRST_JOIN_KEY));
            ColdSpawnControl.LOGGER.info("FirstJoin: Copied persistent data on player clone (death: {})",
                    event.isWasDeath());
        }
    }

    // ==================== STARTER KIT LOGIC ====================

    private static void giveStarterKit(ServerPlayer player) {
        // 1. Equip Cold Sweat goat fur armor
        equipColdSweatArmor(player);

        // 2. Give hotbar items (expedition tools)
        giveHotbarItems(player);

        // 3. Give cluttered inventory items (frantic expedition remnants)
        giveClutteredInventory(player);

        // 4. Give First Aid items
        giveFirstAidItems(player);

        // 5. Give the amnesia journal
        giveJournal(player);

        // 6. Grant iron-age advancements (player has already progressed to iron age)
        grantIronAgeAdvancements(player);

        // 7. Apply head injury (sells the amnesia journal backstory)
        applyHeadInjury(player);

        // 8. Randomize all statistics (player has "been here" for a while)
        AmnesiaBlackoutHandler.randomizePlayerStatistics(player);
    }

    /**
     * Grants iron-age baseline advancements to the player.
     * The toast is suppressed by AdvancementToastMixin, so these are granted
     * silently.
     * This maintains the journal narrative that the player has already progressed
     * past stone.
     */
    private static void grantIronAgeAdvancements(ServerPlayer player) {
        var server = player.getServer();
        if (server == null)
            return;

        var advancementManager = server.getAdvancements();
        var playerAdvancements = player.getAdvancements();

        // Suppress chat announcements during this process
        AmnesiaBlackoutHandler.isResetting = true;
        try {
            for (ResourceLocation advancementId : AmnesiaAdvancementConfig.IRON_AGE_BASELINE) {
                Advancement advancement = advancementManager.getAdvancement(advancementId);
                if (advancement == null) {
                    ColdSpawnControl.LOGGER.warn("Iron-age advancement not found: {}", advancementId);
                    continue;
                }

                // Grant all criteria for this advancement
                for (String criterion : advancement.getCriteria().keySet()) {
                    playerAdvancements.award(advancement, criterion);
                }
            }
        } finally {
            AmnesiaBlackoutHandler.isResetting = false;
        }

        ColdSpawnControl.LOGGER.info("Granted {} iron-age advancements silently",
                AmnesiaAdvancementConfig.IRON_AGE_BASELINE.size());
    }

    private static void equipColdSweatArmor(ServerPlayer player) {
        ItemStack helmet = getModItem(COLD_SWEAT_MOD_ID, HELMET_ID);
        ItemStack chestplate = getModItem(COLD_SWEAT_MOD_ID, CHESTPLATE_ID);
        ItemStack leggings = getModItem(COLD_SWEAT_MOD_ID, LEGGINGS_ID);
        ItemStack boots = getModItem(COLD_SWEAT_MOD_ID, BOOTS_ID);

        // Apply wear damage to armor (expedition wear and tear)
        applyWearDamage(helmet, 0.15f); // 15% worn
        applyWearDamage(chestplate, 0.20f); // 20% worn
        applyWearDamage(leggings, 0.25f); // 25% worn
        applyWearDamage(boots, 0.30f); // 30% worn (most used)

        if (player.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && !helmet.isEmpty()) {
            player.setItemSlot(EquipmentSlot.HEAD, helmet);
        } else if (!helmet.isEmpty()) {
            giveOrDrop(player, helmet);
        }

        if (player.getItemBySlot(EquipmentSlot.CHEST).isEmpty() && !chestplate.isEmpty()) {
            player.setItemSlot(EquipmentSlot.CHEST, chestplate);
        } else if (!chestplate.isEmpty()) {
            giveOrDrop(player, chestplate);
        }

        if (player.getItemBySlot(EquipmentSlot.LEGS).isEmpty() && !leggings.isEmpty()) {
            player.setItemSlot(EquipmentSlot.LEGS, leggings);
        } else if (!leggings.isEmpty()) {
            giveOrDrop(player, leggings);
        }

        if (player.getItemBySlot(EquipmentSlot.FEET).isEmpty() && !boots.isEmpty()) {
            player.setItemSlot(EquipmentSlot.FEET, boots);
        } else if (!boots.isEmpty()) {
            giveOrDrop(player, boots);
        }
    }

    /**
     * Applies wear damage to an item (percentage of max durability).
     */
    private static void applyWearDamage(ItemStack item, float wearPercent) {
        if (!item.isEmpty() && item.isDamageableItem()) {
            int damage = (int) (item.getMaxDamage() * wearPercent);
            item.setDamageValue(damage);
        }
    }

    /**
     * Gives First Aid mod items for medical emergencies.
     */
    private static void giveFirstAidItems(ServerPlayer player) {
        // Bandages - basic healing
        ItemStack bandages = getModItem(FIRSTAID_MOD_ID, "bandage");
        if (!bandages.isEmpty()) {
            bandages.setCount(3);
            giveToRandomSlot(player, bandages);
        }

        // Plasters - minor wounds
        ItemStack plasters = getModItem(FIRSTAID_MOD_ID, "plaster");
        if (!plasters.isEmpty()) {
            plasters.setCount(2);
            giveToRandomSlot(player, plasters);
        }

        // Morphine - pain relief (rare, valuable)
        ItemStack morphine = getModItem(FIRSTAID_MOD_ID, "morphine");
        if (!morphine.isEmpty()) {
            morphine.setCount(1);
            giveToRandomSlot(player, morphine);
        }
    }

    private static void giveHotbarItems(ServerPlayer player) {
        // Slot 1: Iron Axe (worn from expedition)
        ItemStack axe = new ItemStack(Items.IRON_AXE);
        axe.setDamageValue(axe.getMaxDamage() / 4); // Partially used
        player.getInventory().setItem(1, axe);

        // Off-hand: Torches (for dynamic lighting - player wakes up holding light)
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.TORCH, TORCH_COUNT));

        // Slot 2: Flint and Steel
        ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
        flint.setDamageValue(flint.getMaxDamage() / 2); // Half used
        player.getInventory().setItem(2, flint);

        // Slot 3: Bread
        player.getInventory().setItem(3, new ItemStack(Items.BREAD, BREAD_COUNT));

        // Slot 8: Iron Pickaxe (worn from expedition)
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        pickaxe.setDamageValue(pickaxe.getMaxDamage() / 3); // Partially used
        player.getInventory().setItem(8, pickaxe);
    }

    private static void giveClutteredInventory(ServerPlayer player) {
        // Cluttered items - remnants of a frantic expedition
        // Scattered randomly in inventory slots 9-35 (non-hotbar)

        // Building materials (grabbed hastily)
        giveToRandomSlot(player, new ItemStack(Items.COBBLESTONE, 3 + RANDOM.nextInt(5)));
        giveToRandomSlot(player, new ItemStack(Items.SPRUCE_PLANKS, 4 + RANDOM.nextInt(3)));
        giveToRandomSlot(player, new ItemStack(Items.STICK, 2 + RANDOM.nextInt(4)));

        // Fuel/resources
        giveToRandomSlot(player, new ItemStack(Items.COAL, 1 + RANDOM.nextInt(3)));

        // Random expedition scraps
        if (RANDOM.nextBoolean()) {
            giveToRandomSlot(player, new ItemStack(Items.STRING, 1 + RANDOM.nextInt(2)));
        }
        if (RANDOM.nextBoolean()) {
            giveToRandomSlot(player, new ItemStack(Items.ROTTEN_FLESH, 1 + RANDOM.nextInt(2)));
        }
        if (RANDOM.nextBoolean()) {
            giveToRandomSlot(player, new ItemStack(Items.LEATHER, 1));
        }
        if (RANDOM.nextBoolean()) {
            giveToRandomSlot(player, new ItemStack(Items.BONE, 1 + RANDOM.nextInt(2)));
        }

        // Occasional "useful" finds
        if (RANDOM.nextInt(3) == 0) {
            giveToRandomSlot(player, new ItemStack(Items.IRON_NUGGET, 2 + RANDOM.nextInt(4)));
        }
        if (RANDOM.nextInt(4) == 0) {
            giveToRandomSlot(player, new ItemStack(Items.RAW_IRON, 1));
        }
    }

    private static void giveJournal(ServerPlayer player) {
        String playerName = player.getName().getString();
        ItemStack journal = JournalContent.createJournalBook(playerName);
        // Put journal in slot 0 - the first thing player holds after writing
        player.getInventory().setItem(0, journal);
    }

    /**
     * Applies head damage via First Aid's damagePart command to sell the
     * amnesia journal backstory. Delayed by 40 ticks (2 seconds) to ensure
     * First Aid has fully initialized the player's body part system.
     * The command targets whoever runs it, so we execute it as the player.
     */
    private static void applyHeadInjury(ServerPlayer player) {
        var server = player.getServer();
        if (server == null)
            return;

        // Delay execution to let First Aid initialize body parts
        server.tell(new net.minecraft.server.TickTask(server.getTickCount() + 40, () -> {
            // Ensure player is still online
            if (player.isRemoved() || !player.isAlive())
                return;

            // Execute the command with elevated permissions so non-ops get the injury
            server.getCommands().performPrefixedCommand(
                    player.createCommandSourceStack().withPermission(4).withSuppressedOutput(),
                    "damagePart HEAD 2 nodebuff");

            ColdSpawnControl.LOGGER.info("Applied head injury to player: {}", player.getName().getString());
        }));
    }

    // ==================== UTILITY METHODS ====================

    private static ItemStack getModItem(String modId, String itemId) {
        ResourceLocation itemRL = new ResourceLocation(modId, itemId);
        var item = ForgeRegistries.ITEMS.getValue(itemRL);

        if (item == null || item == Items.AIR) {
            ColdSpawnControl.LOGGER.warn("Could not find item: {}:{}", modId, itemId);
            return ItemStack.EMPTY;
        }

        return new ItemStack(item);
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    /**
     * Places item in a random inventory slot (9-35, non-hotbar).
     * If slot is occupied, finds next empty slot.
     */
    private static void giveToRandomSlot(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }

        // Try random slot first (slots 9-35 are main inventory)
        int targetSlot = 9 + RANDOM.nextInt(27);

        // If occupied, find any empty slot in main inventory
        if (!player.getInventory().getItem(targetSlot).isEmpty()) {
            for (int i = 9; i < 36; i++) {
                if (player.getInventory().getItem(i).isEmpty()) {
                    targetSlot = i;
                    break;
                }
            }
        }

        // Set item directly to slot
        if (player.getInventory().getItem(targetSlot).isEmpty()) {
            player.getInventory().setItem(targetSlot, stack);
        } else {
            // Fallback: add normally
            giveOrDrop(player, stack);
        }
    }
}
