package com.maxsters.coldspawncontrol.mixin;

import com.maxsters.coldspawncontrol.event.PlantTemperatureHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import javax.annotation.Nonnull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@SuppressWarnings("null")
@Mixin(VineBlock.class)
public abstract class VineBlockMixin {
    // Parchment: "randomTick" (obfuscated: m_213898_) - target both for runtime
    // compatibility
    @Inject(method = { "randomTick", "m_213898_" }, at = @At("HEAD"), cancellable = true, remap = false)
    private void solastalgia$stopColdGrowth(
            BlockState state,
            ServerLevel level,
            BlockPos pos,
            RandomSource random,
            CallbackInfo ci) {
        if (shouldFreezeGrowth(level, pos)) {
            ci.cancel();
            return;
        }

    }

    private static boolean shouldFreezeGrowth(ServerLevel level, BlockPos pos) {
        BlockPos basePos = require(pos, "pos");
        if (PlantTemperatureHandler.shouldPreventVineGrowth(level, basePos)) {

            return true;
        }

        for (Direction direction : Direction.values()) {
            Direction safeDirection = require(direction, "direction");
            BlockPos neighbor = basePos.relative(safeDirection);
            if (PlantTemperatureHandler.shouldPreventVineGrowth(level, neighbor)) {

                return true;
            }
        }

        return false;
    }

    @Nonnull
    private static <T> T require(T value, String message) {
        return Objects.requireNonNull(value, message);
    }
}
