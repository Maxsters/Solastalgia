package com.maxsters.coldspawncontrol.event;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Contains all journal page content for the amnesia survival narrative.
 * Each page is a separate constant for easy editing and localization.
 * 
 * To add a new page: Add a new PAGE_XX constant and include it in the PAGES
 * array.
 * To edit a page: Modify the corresponding PAGE_XX constant.
 */
@SuppressWarnings("null")
public final class JournalContent {

        // ==================== JOURNAL METADATA ====================

        public static final String JOURNAL_TITLE = "My Journal";

        // ==================== JOURNAL PAGES ====================
        // Each page is a separate constant for easy editing.
        // Use [PLAYER_NAME] placeholder - it will be replaced with the player's name.
        // Use \n for line breaks within a page.
        // Minecraft written books support ~256 characters per page.

        /**
         * Inside front cover - Lucid Pre-Amnesia
         */
        public static final String PAGE_01 = "Log: Day 735\n" +
                        "Author: [PLAYER_NAME]\n\n" +
                        "Rations ran out 4 days ago. The frost is creeping further down the bunker walls. We can't wait for a thaw that isn't coming. We have to leave.";

        /**
         * Explanation of the plan - Lucid Pre-Amnesia
         */
        public static final String PAGE_02 = "We drew lots to cover more ground. I'm taking the East ridge, Mira is heading North.\n\n"
                        +
                        "We regroup at the lower mineshaft in 12 hours.\n\n" +
                        "It's a massive risk splitting up, but there's no other choice. We need supplies.";

        /**
         * The accident and onset of memory loss (Frantic amnesic style begins)
         */
        public static final String PAGE_03 = "blood everywhere\n" +
                        "so much blood\n" +
                        "my head is split open\n" +
                        "i'm going to die here\n" +
                        "I don't know where I am or who I am.";

        /**
         * The accident - discovering the journal
         */
        public static final String PAGE_03B = "the words on the first pages...that's my handwriting.\n\n" +
                        "it says my name is [PLAYER_NAME].\n\n" +
                        "it says i'm supposed to meet mira at the mineshaft.";

        /**
         * Discovery of Mira's fate
         */
        public static final String PAGE_04 = "mira is dead.\n\n" +
                        "I found her pack in the snow. the strap was frozen to blood.\n\n" +
                        "I know her face in my mind.\n" +
                        "I can't remember her voice.\n" +
                        "I don't know what to do without her.\n";

        /**
         * Survival knowledge - muscle memory
         */
        public static final String PAGE_05 = "surviving\n\n" +
                        "- 3 torches minimum. always.\n" +
                        "- coal in cave walls.\n" +
                        "- never under open sky.\n" +
                        "- fire is warmth.\n" +
                        "- cook food first.\n\n" +
                        "hands know.";

        /**
         * Lost and disoriented
         */
        public static final String PAGE_06 = "day?? something.\n\n" +
                        "walked too far.\n" +
                        "stupid idiot.\n" +
                        "can't find the bunker.\n" +
                        "everything looks the same.\n\n" +
                        "sun won't come back.\n" +
                        "can't remember why.\n\n" +
                        "it doesn't matter.\n" +
                        "cold just is.";

        /**
         * Memory reset moment - part 1
         */
        public static final String PAGE_07 = "woke up\n\n" +
                        "first time awake.\n" +
                        "found this book in my coat.\n" +
                        "I didn't remember writing any of this.\n" +
                        "but it's my writing. I have to trust it.";

        /**
         * Memory reset moment - part 2
         */
        public static final String PAGE_07B = "last thing I clearly remember:\n" +
                        "the cold biting my face as we left the bunker.\n\n" +
                        "the book says Mira is dead.\n" +
                        "why did we leave?\n" +
                        "why did I let her go alone?";

        /**
         * The Nether as sanctuary - a new discovery
         */
        public static final String PAGE_08 = "found a book underground.\n" +
                        "old. guide. a frame made of dark glass.\n" +
                        "I built it. lit it.\n" +
                        "purple. screaming.\n" +
                        "I walked through. warm.\n\n" +
                        "it's warm on the other side.\n" +
                        "I don't understand.\n" +
                        "I don't care.";

        /**
         * The missing time - part 1
         */
        public static final String PAGE_09 = "days are gone.\n\n" +
                        "look at my hands. they are scarred.\n" +
                        "they weren't like this yesterday.\n\n" +
                        "i'm losing time.\n" +
                        "hours? weeks?";

        /**
         * The missing time - part 2
         */
        public static final String PAGE_09B = "I keep closing my eyes.\n" +
                        "and opening them somewhere else.\n\n" +
                        "my own writing tells me things.\n" +
                        "a warm place? dark glass?\n" +
                        "none of this is real.\n" +
                        "I am haunting myself.";

        /**
         * Implicit memory - what body knows
         */
        public static final String PAGE_10 = "what I remember.\n" +
                        "- my name is [PLAYER_NAME].\n" +
                        "- how to make fire.\n" +
                        "- how to make torches.\n" +
                        "- how to swing a pickaxe.\n" +
                        "- what's edible.\n" +
                        "- which sounds mean danger.";

        /**
         * Explicit memory - what keeps slipping
         */
        public static final String PAGE_10B = "I can't remember.\n\n" +
                        "- where I am right now.\n" +
                        "- how I got here.\n" +
                        "- what I did yesterday.\n" +
                        "- mira's voice.\n\n";

        /**
         * Unraveling
         */
        public static final String PAGE_11 = "I keep reading page 3.\n" +
                        "over and over.\n\n" +
                        "the blood on the strap.\n" +
                        "I can smell it.\n" +
                        "is that real? how would I\n\n" +
                        "pages talk about a purple door.\n" +
                        "there is no purple door.";

        /**
         * Final page - the present moment
         */
        public static final String PAGE_12 = "woke up again.\n\n" +
                        "somewhere dark. cave?\n" +
                        "dark.\n\n" +
                        "found this book.\n" +
                        "the book lies.\n\n" +
                        "awake first time.\n\n" +
                        "not ok.";

        // ==================== PAGE ARRAY ====================
        // Add new pages to this array in order.

        private static final String[] PAGES = {
                        PAGE_01, PAGE_02, PAGE_03, PAGE_03B, PAGE_04, PAGE_05, PAGE_06,
                        PAGE_07, PAGE_07B, PAGE_08, PAGE_09, PAGE_09B, PAGE_10, PAGE_10B, PAGE_11, PAGE_12
        };

        /**
         * Number of static narrative pages (used by SLM to identify player-written
         * content)
         */
        public static final int STATIC_PAGE_COUNT = PAGES.length;

        // ==================== BOOK CREATION ====================

        private JournalContent() {
                // Utility class
        }

        /**
         * Creates the tattered journal as a writable book (book and quill) ItemStack.
         * This allows the player to continue adding their own entries.
         * 
         * @param playerName The player's name to insert into the book
         * @return An ItemStack containing the writable book with all journal pages
         */
        public static ItemStack createJournalBook(String playerName) {
                ItemStack book = new ItemStack(Items.WRITABLE_BOOK);
                CompoundTag tag = book.getOrCreateTag();

                // Set custom display name for the book
                CompoundTag display = new CompoundTag();
                String nameJson = Component.Serializer.toJson(Component.literal(JOURNAL_TITLE));
                display.putString("Name", nameJson);
                tag.put("display", display);

                // Mark as our journal (used for unsignable check + identification)
                tag.putBoolean("solastalgia_journal", true);

                // Create pages list
                ListTag pages = new ListTag();
                for (String pageContent : PAGES) {
                        // Replace placeholder with actual player name
                        String processedContent = pageContent.replace("[PLAYER_NAME]", playerName);
                        // Writable books use plain text, not JSON components
                        pages.add(StringTag.valueOf(processedContent));
                }

                tag.put("pages", pages);
                return book;
        }
}
