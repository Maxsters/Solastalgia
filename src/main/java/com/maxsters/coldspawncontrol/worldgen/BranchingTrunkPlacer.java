package com.maxsters.coldspawncontrol.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.maxsters.coldspawncontrol.registry.ModBlocks;
import com.maxsters.coldspawncontrol.registry.ModFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacer;
import net.minecraft.world.level.levelgen.feature.trunkplacers.TrunkPlacerType;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nonnull;
import java.util.function.BiConsumer;

@SuppressWarnings("null")
public class BranchingTrunkPlacer extends TrunkPlacer {
    public static final Codec<BranchingTrunkPlacer> CODEC = RecordCodecBuilder
            .create(instance -> trunkPlacerParts(instance).apply(instance, BranchingTrunkPlacer::new));

    private static final Direction[] HORIZONTALS = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

    /**
     * Thread-local flag controlling whether logs are converted to snowy variants.
     * Defaults to {@code true} (worldgen behaviour). Set to {@code false} before
     * sapling-triggered tree growth so that saplings produce normal trees.
     */
    private static final ThreadLocal<Boolean> SNOWY_CONVERSION_ENABLED = ThreadLocal.withInitial(() -> true);

    public static void setSnowyConversionEnabled(boolean enabled) {
        SNOWY_CONVERSION_ENABLED.set(enabled);
    }

    public static boolean isSnowyConversionEnabled() {
        return SNOWY_CONVERSION_ENABLED.get();
    }

    public BranchingTrunkPlacer(int baseHeight, int heightRandA, int heightRandB) {
        super(baseHeight, heightRandA, heightRandB);
    }

    @Override
    @Nonnull
    protected TrunkPlacerType<?> type() {
        return ModFeatures.BRANCHING_TRUNK_PLACER.get();
    }

    @Override
    @Nonnull
    public List<FoliagePlacer.FoliageAttachment> placeTrunk(@Nonnull LevelSimulatedReader level,
            @Nonnull BiConsumer<BlockPos, BlockState> blockSetter, @Nonnull RandomSource random, int freeTreeHeight,
            @Nonnull BlockPos pos,
            @Nonnull TreeConfiguration config) {

        // 0. Fallen Tree Logic (10% chance)
        if (random.nextFloat() < 0.10f) {
            // Pick a random direction
            Direction fallDirection = HORIZONTALS[random.nextInt(4)];
            BlockPos.MutableBlockPos mutablePos = pos.mutable();

            // Length of fallen tree (4-6 blocks)
            int length = 4 + random.nextInt(3);
            int supportedBlocks = 0;

            // Check support first
            BlockPos.MutableBlockPos checkPos = pos.mutable();
            for (int i = 0; i < length; i++) {
                if (level.isStateAtPosition(checkPos.below(), state -> state.getMaterial().isSolid())) {
                    supportedBlocks++;
                }
                checkPos.move(fallDirection);
            }

            // Only generate if at least 60% is supported
            if (supportedBlocks >= length * 0.6f) {
                // Generate horizontal logs
                for (int i = 0; i < length; i++) {
                    // Ensure we place it on the ground (or slightly embedded if terrain is uneven)
                    // We start at 'pos' which is usually the block above ground.
                    if (this.validTreePos(level, mutablePos)) {
                        placeBranchLog(level, blockSetter, random, mutablePos, config, fallDirection.getAxis());
                    }
                    mutablePos.move(fallDirection);
                }

                // Return empty list so no leaves are generated for fallen trees
                return new ArrayList<>();
            }
            // If validation failed, fall through to regular tree generation
        }

        // Standard Standing Tree Logic
        setDirtAt(level, blockSetter, random, pos.below(), config);
        List<FoliagePlacer.FoliageAttachment> foliageAttachments = new ArrayList<>(1);
        BlockPos.MutableBlockPos mutablePos = pos.mutable();

        // 1. Place the main trunk
        for (int i = 0; i < freeTreeHeight; i++) {
            mutablePos.setWithOffset(pos, 0, i, 0);
            placeLog(level, blockSetter, random, mutablePos, config);
        }
        foliageAttachments.add(new FoliagePlacer.FoliageAttachment(pos.above(freeTreeHeight), 0, false));

        // 2. Generate Mid-Trunk Short Branches
        // 2. Generate Mid-Trunk Short Branches
        int midBranchCount = 0;
        int midStartIdx = random.nextInt(4);

        // Track the first direction and Y used to prevent opposite (cross) placement at
        // same height
        Direction firstBranchDir = null;
        int firstBranchY = -1;

        for (int i = 0; i < 4 && midBranchCount < 2; i++) {
            Direction direction = HORIZONTALS[(midStartIdx + i) % 4];

            if (random.nextFloat() < 0.30f) {
                // Deviation is +/- quarter of tree height
                int deviationRange = Math.max(1, freeTreeHeight / 4);
                // Random deviation between -deviationRange and +deviationRange
                int randomDeviation = random.nextInt(deviationRange * 2 + 1) - deviationRange;

                int midY = (freeTreeHeight / 2) + randomDeviation;

                // Clamp to ensure it stays within trunk bounds (avoid negative or above top)
                midY = Math.max(1, Math.min(freeTreeHeight - 2, midY));

                // Prevent Opposite Placement AT SAME HEIGHT
                if (firstBranchDir != null && direction == firstBranchDir.getOpposite()) {
                    if (midY == firstBranchY) {
                        // Try to shift Y to avoid collision
                        if (midY < freeTreeHeight - 2) {
                            midY++;
                        } else if (midY > 1) {
                            midY--;
                        }

                        // If still same (e.g. tree too short), skip this branch
                        if (midY == firstBranchY) {
                            continue;
                        }
                    }
                }

                mutablePos.setWithOffset(pos, 0, midY, 0).move(direction);
                placeBranchLog(level, blockSetter, random, mutablePos, config, direction.getAxis());

                if (firstBranchDir == null) {
                    firstBranchDir = direction;
                    firstBranchY = midY;
                }
                midBranchCount++;
            }
        }

        return foliageAttachments;
    }

    private void placeBranchLog(@Nonnull LevelSimulatedReader level,
            @Nonnull BiConsumer<BlockPos, BlockState> blockSetter,
            @Nonnull RandomSource random, @Nonnull BlockPos pos, @Nonnull TreeConfiguration config,
            @Nonnull Direction.Axis axis) {
        // Use validTreePos to check for replaceability, but also allow placing in
        // air/water/leaves
        // effectively similar to placeLog but with axis rotation support.
        if (this.validTreePos(level, pos)) {
            BlockState logState = config.trunkProvider.getState(random, pos);
            if (logState.hasProperty(RotatedPillarBlock.AXIS)) {
                logState = logState.setValue(RotatedPillarBlock.AXIS, axis);
            }
            // Convert to snowy variant
            logState = toSnowyVariant(logState);
            blockSetter.accept(pos, logState);
        }
    }

    /**
     * Overrides the default placeLog to convert logs to snowy variants.
     */
    @Override
    protected boolean placeLog(@Nonnull LevelSimulatedReader level,
            @Nonnull BiConsumer<BlockPos, BlockState> blockSetter,
            @Nonnull RandomSource random,
            @Nonnull BlockPos pos,
            @Nonnull TreeConfiguration config) {
        if (this.validTreePos(level, pos)) {
            BlockState logState = config.trunkProvider.getState(random, pos);
            // Convert to snowy variant
            logState = toSnowyVariant(logState);
            blockSetter.accept(pos, logState);
            return true;
        }
        return false;
    }

    /**
     * Converts a regular log block to its snowy variant if one exists.
     */
    private BlockState toSnowyVariant(BlockState state) {
        // Skip snowy conversion when disabled (e.g. during sapling growth)
        if (!SNOWY_CONVERSION_ENABLED.get()) {
            return state;
        }

        Direction.Axis axis = state.hasProperty(RotatedPillarBlock.AXIS)
                ? state.getValue(RotatedPillarBlock.AXIS)
                : Direction.Axis.Y;

        if (state.is(Blocks.OAK_LOG)) {
            return ModBlocks.SNOWY_OAK_LOG.get().defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, axis);
        }
        if (state.is(Blocks.SPRUCE_LOG)) {
            return ModBlocks.SNOWY_SPRUCE_LOG.get().defaultBlockState()
                    .setValue(RotatedPillarBlock.AXIS, axis);
        }
        // Return original state if no snowy variant exists
        return state;
    }
}
