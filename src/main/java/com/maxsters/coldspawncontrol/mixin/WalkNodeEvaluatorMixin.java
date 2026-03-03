package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WalkNodeEvaluator.class)
@SuppressWarnings("null")
public class WalkNodeEvaluatorMixin {

    @Inject(method = "getBlockPathType", at = @At("RETURN"), cancellable = true)
    private void avoidColdPath(BlockGetter level, int x, int y, int z, Mob mob, int width, int height, int depth,
            boolean canOpenDoors, boolean canEnterDoors, CallbackInfoReturnable<BlockPathTypes> cir) {
        BlockPathTypes type = cir.getReturnValue();
        if (type == BlockPathTypes.WALKABLE || type == BlockPathTypes.OPEN) {
            try {
                // Deep Underground Aggression: If Y < 32, we are "comfortable" in Snow/Sky
                // (unless freezing). Check this first — cheapest possible early exit.
                boolean isDeepUnderground = mob.getY() < 32;

                // Freezing check is a simple int comparison — very cheap
                boolean isFreezing = mob.getTicksFrozen() > 0;

                // Cache mob's block state — avoids 3 separate chunk lookups
                BlockPos mobPos = mob.blockPosition();
                net.minecraft.world.level.block.state.BlockState mobState = level.getBlockState(mobPos);
                boolean inSnow = mobState.is(net.minecraft.world.level.block.Blocks.SNOW)
                        || mobState.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)
                        || mobState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW);

                boolean underSky = mob.level.canSeeSky(mobPos);

                // Cache isDangerous — avoids iterating spatial hash twice (once here, once for
                // target pos)
                boolean inDangerZone = com.maxsters.coldspawncontrol.ai.DangerZoneManager.isDangerous(mob.level,
                        mobPos);

                // We should "escape" (ignore obstacles) if:
                // 1. We are freezing (Panic!)
                // 2. We are in a Danger Zone (Get out!)
                // 3. We are in Snow/Sky AND NOT deep underground (Surface mobs hate snow)
                boolean shouldEscape = isFreezing || inDangerZone || (!isDeepUnderground && (inSnow || underSky));

                if (!shouldEscape) {
                    // We are comfortable. We should avoid things that are "bad" for us.
                    BlockPos pos = new BlockPos(x, y, z);

                    // Check 1: Danger Zones
                    // ALWAYS avoid learned danger zones if we are not currently in one/freezing.
                    if (com.maxsters.coldspawncontrol.ai.DangerZoneManager.isDangerous(mob.level, pos)) {
                        cir.setReturnValue(BlockPathTypes.BLOCKED);
                        return;
                    }

                    // Check 2: Snow — cache target block state (avoids 3 separate lookups)
                    // Avoid if NOT deep underground
                    if (!isDeepUnderground) {
                        net.minecraft.world.level.block.state.BlockState targetState = level.getBlockState(pos);
                        if (targetState.is(net.minecraft.world.level.block.Blocks.SNOW)
                                || targetState.is(net.minecraft.world.level.block.Blocks.SNOW_BLOCK)
                                || targetState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW)) {
                            cir.setReturnValue(BlockPathTypes.BLOCKED);
                            return;
                        }

                        // Check 3: Sky — only if NOT deep underground (already gated)
                        if (mob.level.canSeeSky(pos)) {
                            cir.setReturnValue(BlockPathTypes.BLOCKED);
                            return;
                        }
                    }
                }
                // If escaping, do nothing. Let vanilla logic handle it.
            } catch (Exception e) {
                // Ignore errors during pathfinding to prevent crashes
            }
        }
    }
}
