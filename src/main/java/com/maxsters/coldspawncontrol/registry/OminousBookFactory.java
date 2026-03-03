package com.maxsters.coldspawncontrol.registry;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Shared utility for creating the "Torn Field Note" ominous book item.
 * Used by both the loot modifier and the chunk-based world spawner.
 */
public final class OminousBookFactory {

        private OminousBookFactory() {
                // Utility class
        }

        /**
         * Creates a complete "Torn Field Note" written book ItemStack.
         */
        @SuppressWarnings("null")
        public static ItemStack createBook() {
                ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
                CompoundTag compoundtag = book.getOrCreateTag();
                compoundtag.putString("title", "msg");
                compoundtag.putString("author", "clay");
                compoundtag.putBoolean("solastalgia_ominous_book", true);

                ListTag listtag = new ListTag();

                // All pages written in one sitting with teeth clenched around pen.
                // Every word costs agony. Filler words dropped.
                String p1 = "whoevr fnds ths.\n\n"
                                + "ded by now. cold tok  finges frst thn thouhts.\n\n"
                                + "fond somthngway ot. coldnt  finsh mabye you cn.";
                String p2 = "drk glas. forms dep  undregrund lava mets watr. "
                                + "blak rok. hardr thn iron ned  lot of it.\n\n"
                                + "arange 14 bloks standng  frame. 4 wide 5 tal holow midle. "
                                + "strke flintinsde";
                String p3 = "opns dor pruple  loud. othr side hot. "
                                + "burnig. nosnw no forst. groud bleds fire  wont freze.\n\n"
                                + "stod at ege. flt haet on  face  warmht in moths.";
                String p4 = "mde it bak gongbring othrs thruogh.\n\n"
                                + "lst. strm came coldnt find  frame agan.\n\n"
                                + "hnds stoped workng  two days ago writng ths with teth aroud pen.\n\n"
                                + "bild frame lht it. wlk  throgh. dont com bak.go.";

                listtag.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(p1))));
                listtag.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(p2))));
                listtag.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(p3))));
                listtag.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(p4))));

                compoundtag.put("pages", listtag);
                book.setTag(compoundtag);

                return book;
        }
}
