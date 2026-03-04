package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.client.model.SnowyBlockModel;
import com.maxsters.coldspawncontrol.block.SnowyLeavesBlock;
import com.maxsters.coldspawncontrol.registry.ModBlocks;
import net.minecraft.client.Minecraft;

import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import com.maxsters.coldspawncontrol.init.ModParticles;
import com.maxsters.coldspawncontrol.client.particle.OreGlintParticle;
import com.maxsters.coldspawncontrol.client.particle.SnowClusterParticle;

import com.maxsters.coldspawncontrol.client.AmnesiaClockProperty;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import net.minecraftforge.client.event.EntityRenderersEvent;
import com.maxsters.coldspawncontrol.registry.ModEntities;
import com.maxsters.coldspawncontrol.client.renderer.ShadowFlickerRenderer;

import java.util.Map;

@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
@SuppressWarnings({ "null", "deprecation" })
public final class ClientModEvents {
    private static final ResourceLocation SNOW_SIDE_TEXTURE = new ResourceLocation(ColdSpawnControl.MOD_ID,
            "block/snow_side");
    private static final ResourceLocation SNOW_TOP_TEXTURE = new ResourceLocation("minecraft",
            "block/snow");

    private ClientModEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(Items.CLOCK, new ResourceLocation("time"), new AmnesiaClockProperty());
        });
    }

    @SubscribeEvent
    public static void onTextureStitch(TextureStitchEvent.Pre event) {
        if (event.getAtlas().location().equals(TextureAtlas.LOCATION_BLOCKS)) {
            event.addSprite(SNOW_SIDE_TEXTURE);
            // Note: minecraft:block/snow is already in the atlas
        }
    }

    @SubscribeEvent
    public static void onModelBake(ModelEvent.BakingCompleted event) {
        Map<ResourceLocation, BakedModel> registry = event.getModels();

        // Get texture sprites
        TextureAtlasSprite snowSideSprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(SNOW_SIDE_TEXTURE);
        TextureAtlasSprite snowTopSprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS)
                .apply(SNOW_TOP_TEXTURE);

        for (ResourceLocation rl : registry.keySet()) {
            if (rl instanceof ModelResourceLocation modelRL) {
                ResourceLocation blockRL = new ResourceLocation(modelRL.getNamespace(), modelRL.getPath());
                Block block = ForgeRegistries.BLOCKS.getValue(blockRL);

                if (block == null)
                    continue;

                BakedModel original = registry.get(rl);

                // Prevent double-wrapping (crucial for reloads or shared references)
                if (original instanceof SnowyBlockModel) {
                    continue;
                }

                // Skip our snowy leaves — they use block color tinting, not model wrapping
                if (block instanceof SnowyLeavesBlock) {
                    continue;
                }

                // Apply SnowyBlockModel to solid opaque blocks
                // Skip: air, fluids, transparent blocks, non-solid blocks
                BlockState defaultState = block.defaultBlockState();
                if (defaultState.isAir())
                    continue;
                if (!defaultState.getMaterial().isSolid())
                    continue;
                if (!defaultState.getMaterial().blocksMotion())
                    continue;
                if (defaultState.getMaterial().isLiquid())
                    continue;

                // Restrict to full blocks that can occlude
                // This prevents weird snowy overlays on chains, fences, walls, etc.
                if (!defaultState.canOcclude())
                    continue;

                // Skip actual snow and ice blocks - they already look snowy!
                // Also skip grass/mycelium/podzol - they have native snowy side textures
                // Be specific to avoid skipping our "snowy_" wood variants
                String path = blockRL.getPath();
                if (path.equals("snow") || path.equals("snow_block") || path.equals("powder_snow") ||
                        path.equals("ice") || path.equals("packed_ice") || path.equals("blue_ice") ||
                        path.equals("frosted_ice") ||
                        path.equals("grass_block") || path.equals("mycelium") || path.equals("podzol"))
                    continue;

                // Apply the snowy block model wrapper
                registry.put(rl, new SnowyBlockModel(original, snowTopSprite, snowSideSprite));
            }
        }
    }

    /**
     * Register block color handler for frozen leaves — renders them solid white
     * by overriding the biome tint index with 0xFFFFFF.
     */
    @SubscribeEvent
    public static void registerBlockColors(RegisterColorHandlersEvent.Block event) {
        event.register(
                (state, level, pos, tintIndex) -> 0xFFFFFF,
                ModBlocks.SNOWY_OAK_LEAVES.get(),
                ModBlocks.SNOWY_SPRUCE_LEAVES.get(),
                ModBlocks.SNOWY_BIRCH_LEAVES.get(),
                ModBlocks.SNOWY_JUNGLE_LEAVES.get(),
                ModBlocks.SNOWY_ACACIA_LEAVES.get(),
                ModBlocks.SNOWY_DARK_OAK_LEAVES.get());
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent event) {
        event.register(ModParticles.SNOW_CLUSTER.get(), new SnowClusterParticle.Provider());
        event.register(ModParticles.ORE_GLINT.get(), new OreGlintParticle.Provider());
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.SHADOW_FLICKER.get(), ShadowFlickerRenderer::new);
    }

}
