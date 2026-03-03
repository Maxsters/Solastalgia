package com.maxsters.coldspawncontrol.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootContext;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nonnull;

/**
 * Frozen leaves variant that renders white (via block color handler) and
 * self-destructs via random tick — simulating leaves dying in extreme cold.
 */
public class SnowyLeavesBlock extends LeavesBlock {
    private final Supplier<? extends Block> dropsAs;

    public SnowyLeavesBlock(Supplier<? extends Block> dropsAs, Properties properties) {
        super(properties);
        this.dropsAs = Objects.requireNonNull(dropsAs, "dropsAs");
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.destroyBlock(pos, false);
    }

    @Override
    @Nonnull
    @SuppressWarnings("null")
    public List<ItemStack> getDrops(BlockState state, LootContext.Builder builder) {
        Block drop = Objects.requireNonNull(dropsAs.get(), "dropsAs supplier returned null");
        return List.of(new ItemStack(drop));
    }

    @Override
    @Nonnull
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        Block drop = Objects.requireNonNull(dropsAs.get(), "dropsAs supplier returned null");
        return new ItemStack(drop);
    }

    // --- Forge flammability hooks (matches vanilla leaves values) ---

    @SuppressWarnings("null")
    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return true;
    }

    @SuppressWarnings("null")
    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 60;
    }

    @SuppressWarnings("null")
    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 30;
    }
}
