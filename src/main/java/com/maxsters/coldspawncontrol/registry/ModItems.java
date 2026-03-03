package com.maxsters.coldspawncontrol.registry;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.function.Supplier;

@SuppressWarnings("null")
public final class ModItems {
        public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
                        ColdSpawnControl.MOD_ID);

        // Wood block items - keep these as they have dedicated snowy variants
        public static final RegistryObject<Item> SNOWY_OAK_LOG = registerBlockItem("snowy_oak_log",
                        ModBlocks.SNOWY_OAK_LOG::get);
        public static final RegistryObject<Item> SNOWY_SPRUCE_LOG = registerBlockItem("snowy_spruce_log",
                        ModBlocks.SNOWY_SPRUCE_LOG::get);
        public static final RegistryObject<Item> SNOWY_STRIPPED_OAK_LOG = registerBlockItem("snowy_stripped_oak_log",
                        ModBlocks.SNOWY_STRIPPED_OAK_LOG::get);
        public static final RegistryObject<Item> SNOWY_STRIPPED_SPRUCE_LOG = registerBlockItem(
                        "snowy_stripped_spruce_log",
                        ModBlocks.SNOWY_STRIPPED_SPRUCE_LOG::get);
        public static final RegistryObject<Item> SNOWY_OAK_WOOD = registerBlockItem("snowy_oak_wood",
                        ModBlocks.SNOWY_OAK_WOOD::get);
        public static final RegistryObject<Item> SNOWY_SPRUCE_WOOD = registerBlockItem("snowy_spruce_wood",
                        ModBlocks.SNOWY_SPRUCE_WOOD::get);
        public static final RegistryObject<Item> SNOWY_STRIPPED_OAK_WOOD = registerBlockItem("snowy_stripped_oak_wood",
                        ModBlocks.SNOWY_STRIPPED_OAK_WOOD::get);
        public static final RegistryObject<Item> SNOWY_STRIPPED_SPRUCE_WOOD = registerBlockItem(
                        "snowy_stripped_spruce_wood",
                        ModBlocks.SNOWY_STRIPPED_SPRUCE_WOOD::get);

        private ModItems() {
        }

        private static RegistryObject<Item> registerBlockItem(String name,
                        Supplier<? extends net.minecraft.world.level.block.Block> block) {
                return ITEMS.register(name,
                                () -> new BlockItem(block.get(),
                                                new Item.Properties().tab(CreativeModeTab.TAB_BUILDING_BLOCKS)));
        }
}
