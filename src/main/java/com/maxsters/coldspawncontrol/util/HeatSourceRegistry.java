package com.maxsters.coldspawncontrol.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

@SuppressWarnings("null")
public class HeatSourceRegistry {

    // Cached block reference for Cold Sweat boiler (lazy initialized)
    private static net.minecraft.world.level.block.Block COLD_SWEAT_BOILER = null;
    private static boolean COLD_SWEAT_BOILER_CHECKED = false;

    private static net.minecraft.world.level.block.Block getColdSweatBoiler() {
        if (!COLD_SWEAT_BOILER_CHECKED) {
            COLD_SWEAT_BOILER_CHECKED = true;
            ResourceLocation boilerId = new ResourceLocation("cold_sweat", "boiler");
            if (ForgeRegistries.BLOCKS.containsKey(boilerId)) {
                COLD_SWEAT_BOILER = ForgeRegistries.BLOCKS.getValue(boilerId);
            }
        }
        return COLD_SWEAT_BOILER;
    }

    public static double getHeatValue(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        // Lava (Hottest)
        if (state.is(Blocks.LAVA) || state.is(Blocks.MAGMA_BLOCK)) {
            return 10.0;
        }

        // Fire
        if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)) {
            return 8.0;
        }

        // Campfire (Lit)
        if ((state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE))
                && state.getValue(net.minecraft.world.level.block.CampfireBlock.LIT)) {
            return 6.0;
        }

        // Furnace/Smoker (Lit)
        if ((state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE) || state.is(Blocks.SMOKER))
                && state.getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT)) {
            return 5.0;
        }

        // Modded: Cold Sweat Boiler (cached lookup)
        net.minecraft.world.level.block.Block boiler = getColdSweatBoiler();
        if (boiler != null && state.is(boiler)) {
            return 7.0;
        }

        return 0.0;
    }

    public static boolean isHeatSource(Level level, BlockPos pos) {
        return getHeatValue(level, pos) > 0;
    }
}
