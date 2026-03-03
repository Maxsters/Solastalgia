package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelSimulatedReader;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.foliageplacers.FoliagePlacer;
import net.minecraft.world.level.block.state.BlockState;
import java.util.function.BiConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FoliagePlacer.class)
public class FoliagePlacerMixin {
    @Inject(method = "tryPlaceLeaf", at = @At("HEAD"), cancellable = true)
    private static void onTryPlaceLeaf(LevelSimulatedReader level, BiConsumer<BlockPos, BlockState> blockSetter,
            RandomSource random, TreeConfiguration config, BlockPos pos, CallbackInfo ci) {
        // Allow leaf placement during sapling growth (ServerLevel).
        // Only cancel during world generation (WorldGenRegion).
        if (level instanceof net.minecraft.server.level.ServerLevel) {
            return;
        }

        // Cancel placing any leaf during worldgen.
        ci.cancel();
    }
}