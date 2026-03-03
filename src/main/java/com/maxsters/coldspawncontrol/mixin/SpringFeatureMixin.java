package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.SpringFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(SpringFeature.class)
@SuppressWarnings("null")
public class SpringFeatureMixin {

    @Redirect(method = "place", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/WorldGenLevel;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"))
    private boolean redirectSetBlock(WorldGenLevel level, BlockPos pos, BlockState state, int flags) {
        // Check if we are placing water (or water source)
        if (state.is(Blocks.WATER)) {
            // Check sky light to determine if the spring is near the surface
            // Underground caves have very low sky light (0-4), while surface areas have
            // high sky light (10+)
            int skyLight = level.getBrightness(net.minecraft.world.level.LightLayer.SKY, pos);
            boolean isNearSurface = skyLight >= 5;

            // Only freeze springs that are near the surface AND in cold biomes
            if (isNearSurface && level.getBiome(pos).value().coldEnoughToSnow(pos)) {
                return level.setBlock(pos, Blocks.ICE.defaultBlockState(), flags);
            }
        }
        return level.setBlock(pos, state, flags);
    }
}
