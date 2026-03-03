package com.maxsters.coldspawncontrol.registry;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.block.SnowyPillarBlock;
import com.maxsters.coldspawncontrol.block.SnowyLeavesBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Registration for snowy wood block variants used during world generation.
 * Non-wood blocks now use dynamic texture replacement via SnowyBlockModel.
 */
@SuppressWarnings("null")
public final class ModBlocks {
        public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,
                        ColdSpawnControl.MOD_ID);

        // Wood blocks - keep these as they have special conversion logic
        public static final RegistryObject<Block> SNOWY_OAK_LOG = BLOCKS.register(
                        "snowy_oak_log",
                        () -> new SnowyPillarBlock(blockSupplier(Blocks.OAK_LOG),
                                        require(Block.Properties.copy(Blocks.OAK_LOG))));

        public static final RegistryObject<Block> SNOWY_SPRUCE_LOG = BLOCKS.register(
                        "snowy_spruce_log",
                        () -> new SnowyPillarBlock(blockSupplier(Blocks.SPRUCE_LOG),
                                        require(Block.Properties.copy(Blocks.SPRUCE_LOG))));

        public static final RegistryObject<Block> SNOWY_STRIPPED_OAK_LOG = BLOCKS.register(
                        "snowy_stripped_oak_log",
                        () -> new SnowyPillarBlock(blockSupplier(Blocks.STRIPPED_OAK_LOG),
                                        require(Block.Properties.copy(Blocks.STRIPPED_OAK_LOG))));

        public static final RegistryObject<Block> SNOWY_STRIPPED_SPRUCE_LOG = BLOCKS.register(
                        "snowy_stripped_spruce_log",
                        () -> new SnowyPillarBlock(blockSupplier(Blocks.STRIPPED_SPRUCE_LOG),
                                        require(Block.Properties.copy(Blocks.STRIPPED_SPRUCE_LOG))));

        public static final RegistryObject<Block> SNOWY_OAK_WOOD = BLOCKS.register(
                        "snowy_oak_wood",
                        () -> new SnowyPillarBlock(blockSupplier(Blocks.OAK_WOOD),
                                        require(Block.Properties.copy(Blocks.OAK_WOOD))));

        public static final RegistryObject<Block> SNOWY_SPRUCE_WOOD = BLOCKS.register(
                        "snowy_spruce_wood",
                        () -> new SnowyPillarBlock(blockSupplier(Blocks.SPRUCE_WOOD),
                                        require(Block.Properties.copy(Blocks.SPRUCE_WOOD))));

        public static final RegistryObject<Block> SNOWY_STRIPPED_OAK_WOOD = BLOCKS.register(
                        "snowy_stripped_oak_wood",
                        () -> new SnowyPillarBlock(blockSupplier(Blocks.STRIPPED_OAK_WOOD),
                                        require(Block.Properties.copy(Blocks.STRIPPED_OAK_WOOD))));

        public static final RegistryObject<Block> SNOWY_STRIPPED_SPRUCE_WOOD = BLOCKS.register(
                        "snowy_stripped_spruce_wood",
                        () -> new SnowyPillarBlock(blockSupplier(Blocks.STRIPPED_SPRUCE_WOOD),
                                        require(Block.Properties.copy(Blocks.STRIPPED_SPRUCE_WOOD))));

        // Leaves blocks
        public static final RegistryObject<Block> SNOWY_OAK_LEAVES = BLOCKS.register(
                        "snowy_oak_leaves",
                        () -> new SnowyLeavesBlock(blockSupplier(Blocks.OAK_LEAVES),
                                        require(Block.Properties.copy(Blocks.OAK_LEAVES))));

        public static final RegistryObject<Block> SNOWY_SPRUCE_LEAVES = BLOCKS.register(
                        "snowy_spruce_leaves",
                        () -> new SnowyLeavesBlock(blockSupplier(Blocks.SPRUCE_LEAVES),
                                        require(Block.Properties.copy(Blocks.SPRUCE_LEAVES))));

        public static final RegistryObject<Block> SNOWY_BIRCH_LEAVES = BLOCKS.register(
                        "snowy_birch_leaves",
                        () -> new SnowyLeavesBlock(blockSupplier(Blocks.BIRCH_LEAVES),
                                        require(Block.Properties.copy(Blocks.BIRCH_LEAVES))));

        public static final RegistryObject<Block> SNOWY_JUNGLE_LEAVES = BLOCKS.register(
                        "snowy_jungle_leaves",
                        () -> new SnowyLeavesBlock(blockSupplier(Blocks.JUNGLE_LEAVES),
                                        require(Block.Properties.copy(Blocks.JUNGLE_LEAVES))));

        public static final RegistryObject<Block> SNOWY_ACACIA_LEAVES = BLOCKS.register(
                        "snowy_acacia_leaves",
                        () -> new SnowyLeavesBlock(blockSupplier(Blocks.ACACIA_LEAVES),
                                        require(Block.Properties.copy(Blocks.ACACIA_LEAVES))));

        public static final RegistryObject<Block> SNOWY_DARK_OAK_LEAVES = BLOCKS.register(
                        "snowy_dark_oak_leaves",
                        () -> new SnowyLeavesBlock(blockSupplier(Blocks.DARK_OAK_LEAVES),
                                        require(Block.Properties.copy(Blocks.DARK_OAK_LEAVES))));

        private ModBlocks() {
        }

        private static <T> T require(@Nullable T value) {
                return Objects.requireNonNull(value);
        }

        private static Supplier<Block> blockSupplier(Block block) {
                final Block safeBlock = require(block);
                return () -> safeBlock;
        }

}
