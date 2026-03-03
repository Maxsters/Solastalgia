package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public class StrippingHandler {

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onBlockToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.isSimulated() || event.isCanceled()) {
            return;
        }

        if (event.getToolAction() == ToolActions.AXE_STRIP) {
            BlockState originalState = event.getState();
            Block block = originalState.getBlock();
            BlockState strippedState = null;

            if (block == ModBlocks.SNOWY_OAK_LOG.get()) {
                strippedState = ModBlocks.SNOWY_STRIPPED_OAK_LOG.get().defaultBlockState();
            } else if (block == ModBlocks.SNOWY_SPRUCE_LOG.get()) {
                strippedState = ModBlocks.SNOWY_STRIPPED_SPRUCE_LOG.get().defaultBlockState();
            } else if (block == ModBlocks.SNOWY_OAK_WOOD.get()) {
                strippedState = ModBlocks.SNOWY_STRIPPED_OAK_WOOD.get().defaultBlockState();
            } else if (block == ModBlocks.SNOWY_SPRUCE_WOOD.get()) {
                strippedState = ModBlocks.SNOWY_STRIPPED_SPRUCE_WOOD.get().defaultBlockState();
            }

            if (strippedState != null) {
                // Copy Axis property if applicable
                if (originalState.hasProperty(BlockStateProperties.AXIS)
                        && strippedState.hasProperty(BlockStateProperties.AXIS)) {
                    strippedState = strippedState.setValue(BlockStateProperties.AXIS,
                            originalState.getValue(BlockStateProperties.AXIS));
                }
                event.setFinalState(strippedState);
            }
        }
    }
}
