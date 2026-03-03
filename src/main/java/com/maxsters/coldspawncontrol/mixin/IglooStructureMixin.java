package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.structures.IglooStructure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(IglooStructure.class)
public class IglooStructureMixin {

    @Inject(method = "findGenerationPoint", at = @At("RETURN"), cancellable = true)
    @SuppressWarnings("null")
    private void validIgnlooLocation(Structure.GenerationContext context,
            CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        Optional<Structure.GenerationStub> stub = cir.getReturnValue();
        if (stub.isPresent()) {
            BlockPos pos = stub.get().position();
            // Check the block at the generation height.
            // Using ChunkGenerator to query logic height directly at the position.
            int y = context.chunkGenerator().getBaseHeight(pos.getX(), pos.getZ(), Heightmap.Types.WORLD_SURFACE_WG,
                    context.heightAccessor(), context.randomState());

            // Get block at Y-1 (the surface block it rests on)
            BlockState surfaceBlock = context.chunkGenerator()
                    .getBaseColumn(pos.getX(), pos.getZ(), context.heightAccessor(), context.randomState())
                    .getBlock(y - 1);

            if (surfaceBlock.is(Blocks.ICE) ||
                    surfaceBlock.is(Blocks.PACKED_ICE) ||
                    surfaceBlock.is(Blocks.BLUE_ICE) ||
                    surfaceBlock.is(Blocks.WATER) ||
                    surfaceBlock.is(Blocks.POWDER_SNOW)) {

                // If the surface is ice/water, cancel generation.
                cir.setReturnValue(Optional.empty());
            }
        }
    }
}
