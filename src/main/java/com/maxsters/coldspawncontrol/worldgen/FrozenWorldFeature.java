package com.maxsters.coldspawncontrol.worldgen;

import com.mojang.serialization.Codec;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import javax.annotation.Nonnull;

@SuppressWarnings({ "null" })
public class FrozenWorldFeature extends Feature<NoneFeatureConfiguration> {

    private static final int POWDER_SNOW_HOLE_CHANCE = 54;
    private static final int POWDER_SNOW_PATCH_CHANCE = 200;
    private static final int SEA_LEVEL = 62;
    private static final int MAX_SPREAD_RADIUS = 5;

    public FrozenWorldFeature(Codec<NoneFeatureConfiguration> codec) {
        super(codec);
    }

    @Override
    public boolean place(@Nonnull FeaturePlaceContext<NoneFeatureConfiguration> context) {
        WorldGenLevel level = context.level();

        // Robust Dimension Check: Only generate in Overworld (minecraft:overworld)
        // We use the ResourceLocation string comparison to avoid any potential static
        // reference issues.
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dim = level.getLevel().dimension();
        if (!dim.location().getPath().equals("overworld")) {
            return false;
        }

        // Secondary check for Nether properties just in case
        if (level.getLevel().dimensionType().ultraWarm() || level.getLevel().dimensionType().hasCeiling()) {
            return false;
        }

        BlockPos origin = context.origin();
        RandomSource random = context.random();
        BlockPos chunkStart = new BlockPos(origin.getX() & ~15, 0, origin.getZ() & ~15);

        // Process the current chunk for snow/ice
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkStart.getX() + x;
                int worldZ = chunkStart.getZ() + z;

                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ) - 1;
                if (surfaceY < level.getMinBuildHeight()) {
                    continue;
                }

                processColumn(level, worldX, worldZ, surfaceY, random);
            }
        }

        return true;
    }

    private void processColumn(WorldGenLevel level, int worldX, int worldZ, int startY, RandomSource random) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(worldX, startY, worldZ);

        // Scan from heightmap down, but stop at Y=20 — no freeze effects below that
        int minY = Math.max(level.getMinBuildHeight(), 20);

        // Track if we've ALREADY FROZEN water in this column
        // If we have, we should NOT freeze again (prevents multi-layer ice)
        // and we should NOT process any solid ground below (it's the ocean floor)
        boolean alreadyFrozeWater = false;

        for (int y = startY; y >= minY; y--) {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);

            // Skip air blocks entirely
            if (state.isAir()) {
                continue;
            }

            // === WATER HANDLING ===
            if (isWater(state.getFluidState().getType())) {
                // If we've already frozen water above, just skip remaining water
                // This prevents multiple freeze layers in deep water
                if (alreadyFrozeWater) {
                    continue;
                }

                // Fix for holes: Don't freeze if there is vegetation/kelp/seagrass right here
                if (!state.getMaterial().isSolid() && !state.is(Blocks.WATER) && !state.is(Blocks.ICE)) {
                    // It's a waterlogged plant (seagrass, kelp).
                    // If we freeze, we replace it with Ice.
                    // This is fine.
                }

                if (shouldFreezeWater(level, pos, y, random)) {
                    int frozenCount = freezeSurfaceWater(level, pos, random, y);
                    alreadyFrozeWater = true; // Mark that we've frozen - don't freeze again
                    // Skip past the frozen water
                    y -= (frozenCount - 1);
                }
                continue;
            }

            // === SOLID GROUND HANDLING ===
            // Powder snow is non-solid but should be treated as ground for vegetation
            // removal
            if (state.getMaterial().isSolid() || state.is(Blocks.POWDER_SNOW)) {
                // ALWAYS convert grass blocks to dirt (no grass in frozen world)
                // This happens regardless of depth or what is above.
                if (state.is(Blocks.GRASS_BLOCK)) {
                    level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 2);
                }

                // FIX: If we've frozen water above in this column, this is the ocean/river
                // floor.
                // We SKIP the rest of the surface logic (snow, vegetation removal) to prevent
                // holes in ice.
                // We use continue (not break) so we keep scanning down for caves.
                if (alreadyFrozeWater) {
                    continue;
                }

                BlockState above = level.getBlockState(pos.above());

                // CRITICAL FIX: If there's ICE above, this is the ocean floor.
                // Skip it entirely - we don't want to process the ocean floor as land.
                // ICE has non-solid material which tricks the vegetation check below.
                if (above.is(Blocks.ICE) || above.is(Blocks.PACKED_ICE) || above.is(Blocks.BLUE_ICE)) {
                    continue;
                }

                // Check if this block can legally support snow
                if (!isValidSnowSurface(level, pos, state)) {
                    continue;
                }

                // Check if this is an exposed surface
                boolean isSurface = (above.isAir() && !above.is(Blocks.CAVE_AIR))
                        || above.is(Blocks.SNOW)
                        || isWater(above.getFluidState().getType());

                if (!isSurface) {
                    BlockState vegCheck = level.getBlockState(pos.above());
                    // Also check for vegetation
                    if (!vegCheck.getMaterial().isSolid() && !vegCheck.is(BlockTags.LOGS)
                            && !vegCheck.is(Blocks.CAVE_AIR) && !vegCheck.isAir()) {
                        isSurface = true;
                    }
                    if (!isSurface) {
                        continue;
                    }
                }

                boolean shouldFreeze = shouldFreezeLand(level, pos, random);

                // If we are freezing, applyFreezeEffects handles vegetation removal + snow.
                // If we are NOT freezing (e.g. under overhang), we still want to remove
                // vegetation if present.
                if (shouldFreeze) {
                    applyFreezeEffects(level, pos, state, random);
                } else {
                    removeVegetation(level, pos.above());
                }
                continue;
            }
        }
    }

    /**
     * Checks if a block is valid for placing snow layers on top.
     * Must be a full block face on top (e.g. not chains, fences, dripstone).
     */
    private boolean isValidSnowSurface(WorldGenLevel level, BlockPos pos, BlockState state) {
        return state.isFaceSturdy(level, pos, net.minecraft.core.Direction.UP,
                net.minecraft.world.level.block.SupportType.FULL);
    }

    /**
     * Determines if a land position should freeze (Snow/Dirt conversion).
     * Rule: Physical Sky access OR Spread from nearby sky-exposed blocks.
     * NO Y-LEVEL CHECK.
     */
    private boolean shouldFreezeLand(WorldGenLevel level, BlockPos pos, RandomSource random) {
        // 1. Physical Sky Access (Geometry check, not light check)
        if (isPhysicallyExposed(level, pos.above())) {
            return true;
        }
        // 2. Spread Check (Noisy)
        int spreadRadius = 1 + random.nextInt(MAX_SPREAD_RADIUS);
        return isNearSkyExposedBlock(level, pos, spreadRadius, random);
    }

    /**
     * Determines if water should freeze.
     * Rule: Y == 62 (Always, it's sea level) OR Sky access OR Spread.
     * Water above sea level (e.g. inside enclosed mountains) uses light checks
     * since the temperature inside a mountain would realistically be warm.
     */
    private boolean shouldFreezeWater(WorldGenLevel level, BlockPos pos, int y, RandomSource random) {
        // 1. Y == 62 Rule (Always freeze water at exact sea level)
        if (y == SEA_LEVEL) {
            return true;
        }
        // 2. Any other Y level: Use land rules (Sky/Spread)
        return shouldFreezeLand(level, pos, random);
    }

    /**
     * Robust check for sky exposure using heightmaps instead of light data.
     * canSeeSky() logic can be unreliable during worldgen if light isn't
     * initialized.
     */
    private boolean isPhysicallyExposed(WorldGenLevel level, BlockPos pos) {
        // Check if the position is at or above the blocking surface
        int surfY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ());
        return pos.getY() >= surfY - 1; // -1 allows being *in* the surface block (e.g. flowers)
    }

    /**
     * Checks if any block within radius has sky access at the SAME Y level
     * using the robust physical exposure check.
     * Also verifies there is a clear horizontal line-of-sight (no solid walls
     * between center and the sky-exposed neighbor).
     */
    private boolean isNearSkyExposedBlock(WorldGenLevel level, BlockPos center, int radius, RandomSource random) {
        int centerY = center.getY();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx == 0 && dz == 0)
                    continue;

                // Add noise
                double dist = Math.sqrt(dx * dx + dz * dz);
                if (dist > radius + random.nextDouble())
                    continue;

                int nx = center.getX() + dx;
                int nz = center.getZ() + dz;

                // 1. Check Sky Access (Geometry check)
                cursor.set(nx, centerY, nz);
                if (isPhysicallyExposed(level, cursor.move(0, 1, 0))) {
                    // 2. Verify line-of-sight: walk horizontally from center to neighbor
                    // and reject if any solid block blocks the path.
                    if (hasLineOfSight(level, center.getX(), center.getZ(), nx, nz, centerY)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Walks a horizontal line from (x0,z0) to (x1,z1) at the given Y level
     * and checks that no solid block obstructs the path.
     * Uses a simple step-along-axis approach (Bresenham-like).
     */
    private boolean hasLineOfSight(WorldGenLevel level, int x0, int z0, int x1, int z1, int y) {
        int dx = Math.abs(x1 - x0);
        int dz = Math.abs(z1 - z0);
        int sx = x0 < x1 ? 1 : -1;
        int sz = z0 < z1 ? 1 : -1;
        int err = dx - dz;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int cx = x0;
        int cz = z0;

        while (cx != x1 || cz != z1) {
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                cx += sx;
            }
            if (e2 < dx) {
                err += dx;
                cz += sz;
            }
            // Skip the start and end points — only check intermediate blocks
            if (cx == x1 && cz == z1)
                break;

            cursor.set(cx, y, cz);
            BlockState state = level.getBlockState(cursor);
            if (state.getMaterial().isSolid()) {
                return false; // Wall blocks the path
            }
            // Also check one block above (y+1) since snow sits on top
            cursor.setY(y + 1);
            state = level.getBlockState(cursor);
            if (state.getMaterial().isSolid()) {
                return false;
            }
        }
        return true;
    }

    private void applyFreezeEffects(WorldGenLevel level, BlockPos groundPos, BlockState groundState,
            RandomSource random) {
        // 1. Remove vegetation above (grass, flowers, but ensure we check correctly)
        removeVegetation(level, groundPos.above());

        // 2. Add snow layers on top
        BlockPos above = groundPos.above();
        BlockState aboveState = level.getBlockState(above);
        // Only add snow if it's air (don't overwrite water or other blocks)
        if (aboveState.isAir()) {
            int layers = 2 + random.nextInt(5);
            level.setBlock(above, Blocks.SNOW.defaultBlockState()
                    .setValue(SnowLayerBlock.LAYERS, layers), 2);
        }

        // 4. Rarely generate powder snow patches
        if (random.nextInt(POWDER_SNOW_PATCH_CHANCE) == 0) {
            if (!groundState.is(Blocks.ICE) && !groundState.is(Blocks.PACKED_ICE)) {
                generatePowderSnowPatch(level, groundPos.immutable(), random);
            }
        }
    }

    private int freezeSurfaceWater(WorldGenLevel level, BlockPos surfacePos, RandomSource random, int surfaceY) {
        boolean isTrap = surfaceY >= SEA_LEVEL && random.nextInt(POWDER_SNOW_HOLE_CHANCE) == 0;
        BlockState iceState = Blocks.ICE.defaultBlockState();
        BlockState snowState = Blocks.POWDER_SNOW.defaultBlockState();

        // Dynamic ice thickness: 3-5 blocks for realistic variation
        int iceThickness = 3 + random.nextInt(3);

        int frozenCount = 0;
        // Freeze up to iceThickness layers deep
        for (int i = 0; i < iceThickness; i++) {
            BlockPos targetPos = surfacePos.below(i);
            BlockState targetState = level.getBlockState(targetPos);

            // Only convert water/flowing water
            if (isWater(targetState.getFluidState().getType())) {
                level.setBlock(targetPos, isTrap ? snowState : iceState, 2);
                frozenCount++;
            } else {
                break;
            }
        }
        return frozenCount;
    }

    private void removeVegetation(WorldGenLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos checkPos = pos.mutable();
        for (int i = 0; i < 5; i++) {
            checkPos.setWithOffset(pos, 0, i, 0);
            BlockState state = level.getBlockState(checkPos);

            // Break if we hit something strictly air
            // BUT: We want to remove foliage that might NOT be air
            if (state.isAir()) {
                break;
            }

            // Remove plants, flowers, grass, and any non-solid non-log blocks
            // CRITICAL: Exclude water and ice! They are non-solid but NOT foliage.
            boolean isFoliage = !state.getMaterial().isSolid()
                    && !state.is(BlockTags.LOGS)
                    && !isWater(state.getFluidState().getType())
                    && !state.is(Blocks.ICE) && !state.is(Blocks.PACKED_ICE) && !state.is(Blocks.BLUE_ICE);

            if (isFoliage) {
                level.setBlock(checkPos, Blocks.AIR.defaultBlockState(), 2);
            } else if (state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK)) {
                level.setBlock(checkPos, Blocks.AIR.defaultBlockState(), 2);
            } else {
                break; // Hit water, ice, or something solid - stop removing
            }
        }
    }

    private boolean isWater(Fluid fluid) {
        return fluid == Fluids.WATER || fluid == Fluids.FLOWING_WATER;
    }

    private void generatePowderSnowPatch(WorldGenLevel level, BlockPos center, RandomSource random) {
        int radius = 2 + random.nextInt(3);
        int baseDepth = 2 + random.nextInt(3);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int manhattanDist = Math.abs(dx) + Math.abs(dz);
                if (manhattanDist > radius + random.nextInt(2))
                    continue;
                if (random.nextInt(4) == 0 && manhattanDist > radius - 1)
                    continue;

                int worldX = center.getX() + dx;
                int worldZ = center.getZ() + dz;

                int terrainY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, worldX, worldZ) - 1;
                BlockPos terrainPos = new BlockPos(worldX, terrainY, worldZ);

                if (level.getBlockState(terrainPos).is(Blocks.SNOW)) {
                    terrainPos = terrainPos.below();
                }

                BlockState groundState = level.getBlockState(terrainPos);
                if (!isNaturalTerrain(groundState))
                    continue;

                int distFromCenter = Math.abs(dx) + Math.abs(dz);
                int localDepth = Math.max(1, baseDepth - distFromCenter / 2 - random.nextInt(2));

                for (int dy = 0; dy < localDepth; dy++) {
                    BlockPos targetPos = terrainPos.below(dy);
                    BlockState targetState = level.getBlockState(targetPos);

                    if (isNaturalTerrain(targetState)) {
                        level.setBlock(targetPos, Blocks.POWDER_SNOW.defaultBlockState(), 2);
                    } else {
                        break;
                    }
                }

                // Remove vegetation and snow above the powder snow patch
                BlockPos abovePos = terrainPos.above();
                removeVegetation(level, abovePos);
            }
        }
    }

    private boolean isNaturalTerrain(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.SAND) || state.is(Blocks.GRAVEL) || state.is(Blocks.CLAY)
                || state.is(Blocks.STONE) || state.is(Blocks.GRANITE) || state.is(Blocks.DIORITE)
                || state.is(Blocks.ANDESITE) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW);
    }
}
