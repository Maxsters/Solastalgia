package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.TreeFeature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enforces a minimum distance between trees to prevent them from spawning too
 * close together.
 * This is needed because without leaves, trunks can visually overlap.
 *
 * Also handles the distinction between worldgen tree placement (snowy variants)
 * and sapling-triggered tree growth (normal variants).
 */
@SuppressWarnings("null")
@Mixin(TreeFeature.class)
public class TreeFeatureMixin {

    private static final int MIN_TREE_DISTANCE = 7;

    @Inject(method = "place", at = @At("HEAD"), cancellable = true)
    private void onPlace(FeaturePlaceContext<TreeConfiguration> context, CallbackInfoReturnable<Boolean> cir) {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        TreeConfiguration config = context.config();

        // Prevent trees from generating underground (below Y 40)
        if (origin.getY() < 40) {
            cir.setReturnValue(false);
            return;
        }

        // Detect sapling growth vs worldgen:
        // During sapling growth the level is a direct ServerLevel.
        // During worldgen the level is a WorldGenRegion (not a ServerLevel).
        // Sapling-grown trees should be entirely vanilla — no branching trunk
        // placer, no snowy variant conversion, and no min-distance enforcement.
        if (level instanceof ServerLevel) {
            return;
        }

        // 1. DYNAMICALLY REPLACE TRUNK PLACER (worldgen only)
        // If the tree uses a StraightTrunkPlacer (standard oak/spruce), swap it for our
        // BranchingTrunkPlacer
        if (config.trunkPlacer instanceof net.minecraft.world.level.levelgen.feature.trunkplacers.StraightTrunkPlacer) {
            com.maxsters.coldspawncontrol.worldgen.BranchingTrunkPlacer newPlacer = new com.maxsters.coldspawncontrol.worldgen.BranchingTrunkPlacer(
                    5, 3, 2);

            TreeConfiguration newConfig = new TreeConfiguration.TreeConfigurationBuilder(
                    config.trunkProvider,
                    newPlacer,
                    config.foliageProvider,
                    config.foliagePlacer,
                    config.minimumSize).ignoreVines().build();

            // Create new context with updated config
            FeaturePlaceContext<TreeConfiguration> newContext = new FeaturePlaceContext<>(
                    context.topFeature(),
                    level,
                    context.chunkGenerator(),
                    context.random(),
                    origin,
                    newConfig);

            // Recursively call place with the new context
            // Since the new config uses BranchingTrunkPlacer, it won't trigger this block
            // again.
            boolean result = ((TreeFeature) (Object) this).place(newContext);
            cir.setReturnValue(result);
            return;
        }

        // Check in a radius around the proposed tree location for existing logs

        // Check in a radius around the proposed tree location for existing logs
        // Use squared distance for performance (avoid sqrt)
        int minDistSq = MIN_TREE_DISTANCE * MIN_TREE_DISTANCE;

        // Scan a cube around the origin checking for logs
        // We only need to check the base area (Y is less important for horizontal
        // spacing)
        for (int dx = -MIN_TREE_DISTANCE; dx <= MIN_TREE_DISTANCE; dx++) {
            for (int dz = -MIN_TREE_DISTANCE; dz <= MIN_TREE_DISTANCE; dz++) {
                // Skip if outside the circular distance check
                if (dx * dx + dz * dz > minDistSq) {
                    continue;
                }

                // Check a small vertical range for logs (trees can be on hills)
                for (int dy = -3; dy <= 3; dy++) {
                    BlockPos checkPos = origin.offset(dx, dy, dz);

                    if (level.getBlockState(checkPos).is(BlockTags.LOGS)) {
                        // Found an existing tree trunk nearby - cancel this tree placement
                        cir.setReturnValue(false);
                        return;
                    }
                }
            }
        }
        // No nearby trees found, allow placement to proceed
    }
}
