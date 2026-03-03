package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.momosoftworks.coldsweat.util.world.WorldHelper;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BambooBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.GrowingPlantBodyBlock;
import net.minecraft.world.level.block.GrowingPlantHeadBlock;
import net.minecraft.world.level.block.MossBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.SpreadingSnowyDirtBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.SaplingGrowTreeEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Objects;
import java.util.OptionalDouble;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.world.level.block.BushBlock;

@SuppressWarnings("null")
@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public final class PlantTemperatureHandler {
    private static final double MIN_CROP_GROWTH_TEMPERATURE = 0.5D;
    private static final double MIN_SAPLING_GROWTH_TEMPERATURE = -1.0D;
    private static final double DEFAULT_BONEMEAL_TEMPERATURE = MIN_CROP_GROWTH_TEMPERATURE;
    private static final String BONEMEAL_MESSAGE_KEY = "message.solastalgia.plant_too_cold_bonemeal";
    private static final String PLACEMENT_MESSAGE_KEY = "message.solastalgia.plant_too_cold_place";

    private PlantTemperatureHandler() {
    }

    @SubscribeEvent
    public static void onBonemeal(BonemealEvent event) {
        Level level = event.getLevel();
        if (level.isClientSide()) {
            return;
        }

        BlockState state = require(event.getBlock(), "bonemeal block state");
        OptionalDouble thresholdOpt = bonemealThreshold(state);
        if (thresholdOpt.isEmpty()) {
            return;
        }

        BlockPos pos = event.getPos();
        if (pos == null) {
            return;
        }
        pos = require(pos, "bonemeal position");

        OptionalDouble temperature = queryTemperature(level, pos);
        if (temperature.isEmpty()) {
            return;
        }

        double current = temperature.getAsDouble();
        double threshold = thresholdOpt.getAsDouble();
        if (current < threshold) {
            event.setResult(Event.Result.DENY);
            event.setCanceled(true);

            notifyPlayer(event.getEntity(), state, BONEMEAL_MESSAGE_KEY);
        }
    }

    @SubscribeEvent
    public static void onCropGrow(BlockEvent.CropGrowEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }

        BlockState state = event.getState();
        if (state.getBlock() instanceof SaplingBlock) {
            return;
        }

        BlockPos pos = event.getPos();
        if (pos == null) {
            return;
        }
        pos = require(pos, "crop position");

        if (!isTemperatureSensitiveGrowth(level, pos, state)) {
            return;
        }

        OptionalDouble temperature = queryTemperature(level, pos);
        if (temperature.isEmpty()) {
            return;
        }

        double current = temperature.getAsDouble();
        if (current < MIN_CROP_GROWTH_TEMPERATURE) {
            event.setResult(Event.Result.DENY);
            event.setCanceled(true);

        }
    }

    @SubscribeEvent
    public static void onSaplingGrow(SaplingGrowTreeEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }

        BlockPos pos = event.getPos();
        if (pos == null) {
            return;
        }
        pos = require(pos, "sapling position");

        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (!(block instanceof SaplingBlock)) {
            return;
        }

        OptionalDouble temperature = queryTemperature(level, pos);
        if (temperature.isEmpty()) {
            return;
        }

        double current = temperature.getAsDouble();
        if (current < MIN_SAPLING_GROWTH_TEMPERATURE) {
            event.setResult(Event.Result.DENY);
            event.setCanceled(true);

        }
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (player instanceof FakePlayer) {
            return;
        }

        BlockPos pos = event.getPos();
        if (pos == null) {
            return;
        }
        pos = require(pos, "placement position");

        BlockState state = require(event.getPlacedBlock(), "placed block state");
        OptionalDouble thresholdOpt = placementThreshold(state);
        if (thresholdOpt.isEmpty()) {
            return;
        }

        OptionalDouble temperature = queryTemperature(level, pos);
        if (temperature.isEmpty()) {
            return;
        }

        double current = temperature.getAsDouble();
        double threshold = thresholdOpt.getAsDouble();
        if (current < threshold && !shouldSuppressPlacementMessage(state)) {
            notifyPlayer(player, state, PLACEMENT_MESSAGE_KEY);
        }
    }

    public static boolean shouldPreventVineGrowth(ServerLevel level, BlockPos pos) {
        BlockPos targetPos = Objects.requireNonNull(pos, "pos");
        OptionalDouble temperature = queryTemperature(level, targetPos);
        if (temperature.isEmpty()) {

            return false;
        }

        double current = temperature.getAsDouble();
        if (Double.isNaN(current)) {
            // ColdSpawnControl.LOGGER.warn(
            // "Temperature query returned NaN at {} in {} (state={}); allowing vine growth
            // as fallback",
            // targetPos,
            // level.dimension(),
            // stateAtPos);
            return false;
        }

        if (current < MIN_CROP_GROWTH_TEMPERATURE) {

            return true;
        }

        return false;
    }

    private static OptionalDouble queryTemperature(Level level, BlockPos pos) {
        try {
            return OptionalDouble.of(WorldHelper.getTemperatureAt(level, pos));
        } catch (RuntimeException ex) {
            ColdSpawnControl.LOGGER.error(
                    "Failed to query Cold Sweat temperature at {} in {}. Allowing action as fallback.", pos,
                    level.dimension(), ex);
            return OptionalDouble.empty();
        }
    }

    private static OptionalDouble bonemealThreshold(BlockState state) {
        if (!(state.getBlock() instanceof BonemealableBlock)) {
            return OptionalDouble.empty();
        }
        return placementThreshold(state);
    }

    private static OptionalDouble placementThreshold(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof SaplingBlock) {
            return OptionalDouble.of(MIN_SAPLING_GROWTH_TEMPERATURE);
        }

        if (isTemperatureSensitiveBlock(state)) {
            return OptionalDouble.of(MIN_CROP_GROWTH_TEMPERATURE);
        }

        if (block instanceof BonemealableBlock && isPlant(block)) {
            return OptionalDouble.of(DEFAULT_BONEMEAL_TEMPERATURE);
        }

        return OptionalDouble.empty();
    }

    private static boolean isPlant(Block block) {
        return block instanceof IPlantable
                || block instanceof BushBlock
                || block instanceof VineBlock
                || block instanceof GrowingPlantHeadBlock
                || block instanceof GrowingPlantBodyBlock;
    }

    private static boolean isTemperatureSensitiveGrowth(Level level, BlockPos pos, BlockState state) {
        if (isTemperatureSensitiveBlock(state)) {
            return true;
        }

        if (state.isAir()) {
            return hasTemperatureSensitiveNeighbor(level, pos);
        }

        TagKey<Block> dirtTag = BlockTags.DIRT;
        if (dirtTag != null) {
            if (state.is(dirtTag)) {
                return hasTemperatureSensitiveNeighbor(level, pos);
            }
        }

        TagKey<Block> nyliumTag = BlockTags.NYLIUM;
        if (nyliumTag != null) {
            if (state.is(nyliumTag)) {
                return hasTemperatureSensitiveNeighbor(level, pos);
            }
        }

        return false;
    }

    private static boolean hasTemperatureSensitiveNeighbor(Level level, BlockPos pos) {
        BlockPos basePos = Objects.requireNonNull(pos, "pos");
        BlockPos min = basePos.offset(-1, -3, -1);
        BlockPos max = basePos.offset(1, 1, 1);

        int baseX = basePos.getX();
        int baseY = basePos.getY();
        int baseZ = basePos.getZ();

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    if (x == baseX && y == baseY && z == baseZ) {
                        continue;
                    }
                    cursor.set(x, y, z);
                    BlockState neighborState = level.getBlockState(cursor);
                    if (isTemperatureSensitiveBlock(neighborState)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean isTemperatureSensitiveBlock(BlockState state) {
        TagKey<Block> cropsTag = BlockTags.CROPS;
        if (cropsTag != null) {
            if (state.is(cropsTag)) {
                return true;
            }
        }

        Block block = state.getBlock();
        return block instanceof CropBlock
                || block instanceof StemBlock
                || block instanceof NetherWartBlock
                || block instanceof SweetBerryBushBlock
                || block instanceof SugarCaneBlock
                || block instanceof CactusBlock
                || block instanceof CocoaBlock
                || block instanceof BambooBlock
                || block instanceof SpreadingSnowyDirtBlock
                || block instanceof MossBlock
                || block instanceof VineBlock
                || block instanceof GrowingPlantHeadBlock
                || block instanceof GrowingPlantBodyBlock;
    }

    private static boolean shouldSuppressPlacementMessage(BlockState state) {
        Block block = state.getBlock();
        return block instanceof SpreadingSnowyDirtBlock || block instanceof MossBlock;
    }

    @Nonnull
    private static <T> T require(@Nullable T value, String message) {
        return Objects.requireNonNull(value, message);
    }

    private static void notifyPlayer(Player player, BlockState state, String messageKey) {
        if (!(player instanceof ServerPlayer serverPlayer) || player instanceof FakePlayer) {
            return;
        }

        Objects.requireNonNull(messageKey, "messageKey must not be null");
        Component blockName = Objects.requireNonNull(state.getBlock().getName(), "blockName must not be null");
        Component message = Component.translatable(messageKey, blockName)
                .withStyle(ChatFormatting.RED);
        if (message != null) {
            serverPlayer.displayClientMessage(message, true);
        }
    }
}
