package com.maxsters.coldspawncontrol.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/**
 * Log-like snowy pillar that drops the configured base block.
 */
public class SnowyPillarBlock extends RotatedPillarBlock {
    private final Supplier<? extends Block> dropsAs;

    public SnowyPillarBlock(Supplier<? extends Block> dropsAs, Properties properties) {
        super(properties);
        this.dropsAs = Objects.requireNonNull(dropsAs, "dropsAs");
    }

    @Override
    @Nonnull
    public List<ItemStack> getDrops(BlockState state, LootContext.Builder builder) {
        Block drop = Objects.requireNonNull(dropsAs.get(), "dropsAs supplier returned null");
        return Objects.requireNonNull(List.of(new ItemStack(drop)));
    }

    @Override
    @Nonnull
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        Block drop = Objects.requireNonNull(dropsAs.get(), "dropsAs supplier returned null");
        return new ItemStack(drop);
    }

    // --- Forge flammability hooks (matches vanilla log values) ---

    @SuppressWarnings("null")
    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return true;
    }

    @SuppressWarnings("null")
    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 5;
    }

    @SuppressWarnings("null")
    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 5;
    }
}
