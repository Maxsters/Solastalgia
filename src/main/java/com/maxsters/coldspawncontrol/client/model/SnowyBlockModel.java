package com.maxsters.coldspawncontrol.client.model;

import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraft.core.BlockPos;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * A BakedModel wrapper that applies snow textures to any solid block
 * when there is a snow layer directly above it.
 * - Top face: replaced with Minecraft snow texture
 * - Side faces: replaced with snow_side.png overlay (except faces adjacent to
 * powder snow)
 */
@SuppressWarnings("null")
public class SnowyBlockModel implements BakedModel {

    public static final ModelProperty<Boolean> HAS_SNOW_ABOVE = new ModelProperty<>();
    public static final ModelProperty<Set<Direction>> POWDER_SNOW_SIDES = new ModelProperty<>();

    private final BakedModel original;
    private final TextureAtlasSprite snowTopSprite;
    private final TextureAtlasSprite snowSideSprite;

    public SnowyBlockModel(BakedModel original, TextureAtlasSprite snowTopSprite, TextureAtlasSprite snowSideSprite) {
        this.original = original;
        this.snowTopSprite = snowTopSprite;
        this.snowSideSprite = snowSideSprite;
    }

    @Override
    public boolean useAmbientOcclusion() {
        return original.useAmbientOcclusion();
    }

    @Override
    public boolean isGui3d() {
        return original.isGui3d();
    }

    @Override
    public boolean usesBlockLight() {
        return original.usesBlockLight();
    }

    @Override
    public boolean isCustomRenderer() {
        return original.isCustomRenderer();
    }

    @Override
    @SuppressWarnings("deprecation")
    public @Nonnull TextureAtlasSprite getParticleIcon() {
        return original.getParticleIcon();
    }

    @Override
    public @Nonnull ItemOverrides getOverrides() {
        return original.getOverrides();
    }

    @Override
    @SuppressWarnings("deprecation")
    public @Nonnull ItemTransforms getTransforms() {
        return original.getTransforms();
    }

    @Override
    @SuppressWarnings("deprecation")
    public @Nonnull List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
            @Nonnull RandomSource rand) {
        // Without ModelData, just return original
        return original.getQuads(state, side, rand);
    }

    @Override
    public net.minecraftforge.client.ChunkRenderTypeSet getRenderTypes(@Nonnull BlockState state,
            @Nonnull RandomSource rand, @Nonnull ModelData data) {
        ChunkRenderTypeSet originalTypes = original.getRenderTypes(state, rand, data);
        Boolean hasSnow = data.get(HAS_SNOW_ABOVE);

        if (hasSnow != null && hasSnow) {
            return ChunkRenderTypeSet.union(originalTypes, ChunkRenderTypeSet.of(RenderType.cutout()));
        }

        return originalTypes;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @Nonnull RandomSource rand,
            @Nonnull ModelData data, @Nullable RenderType renderType) {
        List<BakedQuad> quads = new ArrayList<>(original.getQuads(state, side, rand, data, renderType));

        Boolean hasSnow = data.get(HAS_SNOW_ABOVE);
        if (hasSnow != null && hasSnow) {
            RenderType overlayLayer = RenderType.cutout();
            if (renderType == null || renderType == overlayLayer) {
                Set<Direction> powderSnowSides = data.get(POWDER_SNOW_SIDES);
                if (powderSnowSides == null) {
                    powderSnowSides = EnumSet.noneOf(Direction.class);
                }

                List<BakedQuad> baseQuads = original.getQuads(state, side, rand, data, null);
                for (BakedQuad quad : baseQuads) {
                    Direction quadDir = quad.getDirection();
                    if (quadDir == Direction.UP) {
                        quads.add(createOverlayQuad(quad, snowTopSprite));
                    } else if (quadDir != Direction.DOWN && quadDir != null) {
                        if (!powderSnowSides.contains(quadDir)) {
                            quads.add(createOverlayQuad(quad, snowSideSprite));
                        }
                    }
                }
            }
        }
        return quads;
    }

    private BakedQuad createOverlayQuad(BakedQuad original, TextureAtlasSprite newSprite) {
        int[] vertexData = original.getVertices().clone();
        Direction facing = original.getDirection();

        // DefaultVertexFormat.BLOCK stride is 8 integers (32 bytes)
        int stride = 8;

        // First pass: read positions and apply inflation
        float[] positions = new float[12]; // 4 vertices * 3 coords
        for (int i = 0; i < 4; i++) {
            int offset = i * stride;

            float x = Float.intBitsToFloat(vertexData[offset + 0]);
            float y = Float.intBitsToFloat(vertexData[offset + 1]);
            float z = Float.intBitsToFloat(vertexData[offset + 2]);

            // Push overlay slightly outward to reduce Z-fighting
            if (original.getDirection() != null) {
                float offsetAmount = 0.002f;
                net.minecraft.core.Vec3i normal = original.getDirection().getNormal();
                x += normal.getX() * offsetAmount;
                y += normal.getY() * offsetAmount;
                z += normal.getZ() * offsetAmount;
            }

            positions[i * 3 + 0] = x;
            positions[i * 3 + 1] = y;
            positions[i * 3 + 2] = z;

            vertexData[offset + 0] = Float.floatToRawIntBits(x);
            vertexData[offset + 1] = Float.floatToRawIntBits(y);
            vertexData[offset + 2] = Float.floatToRawIntBits(z);
        }

        // Second pass: calculate UVs from vertex positions based on face direction
        // This ensures correct UVs regardless of block rotation
        for (int i = 0; i < 4; i++) {
            int offset = i * stride;

            float x = positions[i * 3 + 0];
            float y = positions[i * 3 + 1];
            float z = positions[i * 3 + 2];

            float uRel, vRel;

            if (facing == Direction.UP || facing == Direction.DOWN) {
                // Horizontal face: use X and Z for UVs
                uRel = x;
                vRel = z;
            } else if (facing == Direction.NORTH || facing == Direction.SOUTH) {
                // North/South face: use X for U, Y for V
                uRel = x;
                vRel = 1.0f - y; // Flip V so it goes top-to-bottom
            } else {
                // East/West face: use Z for U, Y for V
                uRel = z;
                vRel = 1.0f - y;
            }

            // Clamp to [0, 1]
            uRel = Math.max(0, Math.min(1, uRel));
            vRel = Math.max(0, Math.min(1, vRel));

            float newU = newSprite.getU0() + uRel * (newSprite.getU1() - newSprite.getU0());
            float newV = newSprite.getV0() + vRel * (newSprite.getV1() - newSprite.getV0());

            vertexData[offset + 4] = Float.floatToRawIntBits(newU);
            vertexData[offset + 5] = Float.floatToRawIntBits(newV);
        }

        // Use tint index -1 (no tint) to prevent grass/foliage tint from applying to
        // snow
        return new BakedQuad(vertexData, -1, original.getDirection(), newSprite,
                original.isShade());
    }

    @Override
    public @Nonnull ModelData getModelData(@Nonnull BlockAndTintGetter level, @Nonnull BlockPos pos,
            @Nonnull BlockState state, @Nonnull ModelData modelData) {
        // Check if there's a snow layer above this block
        BlockPos above = pos.above();
        BlockState aboveState = level.getBlockState(above);
        boolean hasSnowAbove = aboveState.is(Blocks.SNOW) || aboveState.is(Blocks.SNOW_BLOCK) ||
                aboveState.is(Blocks.POWDER_SNOW);

        // Check which sides have powder snow adjacent (to skip overlay on those faces)
        Set<Direction> powderSnowSides = EnumSet.noneOf(Direction.class);
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            BlockPos adjacentPos = pos.relative(dir);
            BlockState adjacentState = level.getBlockState(adjacentPos);
            if (adjacentState.is(Blocks.POWDER_SNOW)) {
                powderSnowSides.add(dir);
            }
        }

        return modelData.derive()
                .with(HAS_SNOW_ABOVE, hasSnowAbove)
                .with(POWDER_SNOW_SIDES, powderSnowSides)
                .build();
    }
}
