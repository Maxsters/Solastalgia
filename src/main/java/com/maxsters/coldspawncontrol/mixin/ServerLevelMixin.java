package com.maxsters.coldspawncontrol.mixin;

import com.maxsters.coldspawncontrol.util.TemperatureCache;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings({ "null" })
@Mixin(ServerLevel.class)
public abstract class ServerLevelMixin {

    // ---- Block conversion lookup maps (built once on first use, O(1) per lookup)

    /** Normal → Snowy block mappings. */
    @Unique
    private static final Map<Block, Block> FREEZE_MAP = new HashMap<>();

    /** Snowy → Normal block mappings. */
    @Unique
    private static final Map<Block, Block> THAW_MAP = new HashMap<>();

    @Unique
    private static boolean solastalgia$mapsInitialized = false;

    /**
     * Lazy init — cannot populate in static{} because mixin classes load before
     * RegistryObjects are available, causing a deadlock on startup.
     */
    @Unique
    private static void solastalgia$ensureInitialized() {
        if (solastalgia$mapsInitialized)
            return;
        solastalgia$mapsInitialized = true;

        // Logs
        addMapping(Blocks.OAK_LOG, com.maxsters.coldspawncontrol.registry.ModBlocks.SNOWY_OAK_LOG.get());
        addMapping(Blocks.SPRUCE_LOG, com.maxsters.coldspawncontrol.registry.ModBlocks.SNOWY_SPRUCE_LOG.get());
        addMapping(Blocks.STRIPPED_OAK_LOG,
                com.maxsters.coldspawncontrol.registry.ModBlocks.SNOWY_STRIPPED_OAK_LOG.get());
        addMapping(Blocks.STRIPPED_SPRUCE_LOG,
                com.maxsters.coldspawncontrol.registry.ModBlocks.SNOWY_STRIPPED_SPRUCE_LOG.get());
        addMapping(Blocks.OAK_WOOD, com.maxsters.coldspawncontrol.registry.ModBlocks.SNOWY_OAK_WOOD.get());
        addMapping(Blocks.SPRUCE_WOOD, com.maxsters.coldspawncontrol.registry.ModBlocks.SNOWY_SPRUCE_WOOD.get());
        addMapping(Blocks.STRIPPED_OAK_WOOD,
                com.maxsters.coldspawncontrol.registry.ModBlocks.SNOWY_STRIPPED_OAK_WOOD.get());
        addMapping(Blocks.STRIPPED_SPRUCE_WOOD,
                com.maxsters.coldspawncontrol.registry.ModBlocks.SNOWY_STRIPPED_SPRUCE_WOOD.get());
        // Leaves
        addMapping(Blocks.OAK_LEAVES, com.maxsters.coldspawncontrol.registry.ModBlocks.SNOWY_OAK_LEAVES.get());
        addMapping(Blocks.SPRUCE_LEAVES, com.maxsters.coldspawncontrol.registry.ModBlocks.SNOWY_SPRUCE_LEAVES.get());
        addMapping(Blocks.BIRCH_LEAVES, com.maxsters.coldspawncontrol.registry.ModBlocks.SNOWY_BIRCH_LEAVES.get());
        addMapping(Blocks.JUNGLE_LEAVES, com.maxsters.coldspawncontrol.registry.ModBlocks.SNOWY_JUNGLE_LEAVES.get());
        addMapping(Blocks.ACACIA_LEAVES, com.maxsters.coldspawncontrol.registry.ModBlocks.SNOWY_ACACIA_LEAVES.get());
        addMapping(Blocks.DARK_OAK_LEAVES,
                com.maxsters.coldspawncontrol.registry.ModBlocks.SNOWY_DARK_OAK_LEAVES.get());
    }

    @Unique
    private static void addMapping(Block normal, Block snowy) {
        FREEZE_MAP.put(normal, snowy);
        THAW_MAP.put(snowy, normal);
    }

    // ---- Tick injection ----

    @Inject(method = "tickChunk", at = @At("HEAD"))
    private void solastalgia$stackSnow(LevelChunk chunk, int randomTickSpeed, CallbackInfo ci) {
        // Lazy-init the block maps (safe here — registries are fully loaded by the time
        // ticking starts)
        solastalgia$ensureInitialized();

        ServerLevel level = (ServerLevel) (Object) this;

        // Run cache cleanup once per tick (internally throttled to every ~5 minutes)
        TemperatureCache.tick(level.getGameTime());

        // Only run in Overworld and when Raining
        if (!level.isRaining() || !net.minecraft.world.level.Level.OVERWORLD.equals(level.dimension())) {
            return;
        }

        // ============================================================
        // PART 1: Snow Accumulation (16 random positions at heightmap)
        // ============================================================
        BlockPos.MutableBlockPos mutableTickPos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < 16; i++) {
            ChunkPos chunkPos = chunk.getPos();
            int x = chunkPos.getMinBlockX() + level.random.nextInt(16);
            int z = chunkPos.getMinBlockZ() + level.random.nextInt(16);
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            BlockPos currentPos = new BlockPos(x, surfaceY, z);
            BlockPos belowPos = new BlockPos(x, surfaceY - 1, z);
            BlockState belowState = level.getBlockState(belowPos);
            BlockState currentState = level.getBlockState(currentPos);

            // Cold Check: Only accumulate/convert if it is actually cold enough to snow.
            if (level.getBiome(currentPos).value().coldEnoughToSnow(currentPos)) {
                // Realistic Mode Check: If enabled, STOP snow accumulation (only run if
                // disabled)
                if (!level.getGameRules()
                        .getBoolean(com.maxsters.coldspawncontrol.config.ModGameRules.RULE_REALISTIC_MODE)) {
                    // Snow Accumulation Logic
                    // If the block below is snow layer < 8, add to it.
                    if (belowState.is(Blocks.SNOW)) {
                        int layers = belowState.getValue(SnowLayerBlock.LAYERS);
                        if (layers < 8) {
                            // 1 in 1000 chance to stack (0.1% chance per attempt)
                            if (level.random.nextInt(1000) == 0) {
                                level.setBlockAndUpdate(belowPos,
                                        belowState.setValue(SnowLayerBlock.LAYERS, layers + 1));
                                // Update local state for conversion check
                                belowState = level.getBlockState(belowPos);
                            }
                        }
                    }
                    // If current block (on top) is snow layer < 8, add to it.
                    else if (currentState.is(Blocks.SNOW)) {
                        int layers = currentState.getValue(SnowLayerBlock.LAYERS);
                        if (layers < 8) {
                            if (level.random.nextInt(1000) == 0) {
                                level.setBlockAndUpdate(currentPos,
                                        currentState.setValue(SnowLayerBlock.LAYERS, layers + 1));
                                currentState = level.getBlockState(currentPos);
                            }
                        }
                    }
                }
            }

            if (belowState.is(Blocks.SNOW) && belowState.getValue(SnowLayerBlock.LAYERS) == 8) {
                level.setBlockAndUpdate(belowPos, Blocks.SNOW_BLOCK.defaultBlockState());
            } else {
                // Check if we accidentally hit the snow block itself (currentPos)
                if (currentState.is(Blocks.SNOW) && currentState.getValue(SnowLayerBlock.LAYERS) == 8) {
                    level.setBlockAndUpdate(currentPos, Blocks.SNOW_BLOCK.defaultBlockState());
                }
            }

            BlockState targetState = belowState;
            BlockPos targetPos = belowPos;

            // If below is Snow Layer, we check below THAT to find the "ground" block.
            if (targetState.getBlock() instanceof SnowLayerBlock) {
                targetPos = targetPos.below();
                targetState = level.getBlockState(targetPos);
            }

            // Ground block conversion is no longer needed - SnowyBlockModel handles
            // the visual snow appearance dynamically when snow layers are placed on top
        }

        // ============================================================
        // PART 2: Log/Leaves conversion — heightmap-bounded Y sampling
        // ============================================================
        // Instead of sampling across the full 384 Y-levels, we constrain to the
        // range where logs/leaves actually exist: surface down to surface-20.
        // This dramatically increases the hit rate per sample.
        for (int i = 0; i < 4; i++) {
            ChunkPos chunkPos = chunk.getPos();
            int x = chunkPos.getMinBlockX() + level.random.nextInt(16);
            int z = chunkPos.getMinBlockZ() + level.random.nextInt(16);

            // Use WORLD_SURFACE heightmap (ignores non-solid like leaves/flowers)
            // to approximate where trees are. Then sample within a reasonable band.
            int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            int minY = Math.max(level.getMinBuildHeight(), surfaceY - 20);
            int maxY = surfaceY;

            if (maxY <= minY)
                continue;

            int y = minY + level.random.nextInt(maxY - minY);
            mutableTickPos.set(x, y, z);
            BlockState logState = level.getBlockState(mutableTickPos);
            Block logBlock = logState.getBlock();

            // O(1) lookup in pre-built maps
            Block targetBlock = FREEZE_MAP.get(logBlock);
            boolean isFreezing = targetBlock != null;

            if (targetBlock == null) {
                targetBlock = THAW_MAP.get(logBlock);
            }

            if (targetBlock == null)
                continue;

            // Check temperature using shared cache
            double temperature = TemperatureCache.get(level, mutableTickPos);
            double freezeThreshold = -0.5D;

            boolean shouldConvert;
            if (isFreezing) {
                shouldConvert = temperature <= freezeThreshold;
            } else {
                shouldConvert = temperature > freezeThreshold;
            }

            if (shouldConvert) {
                BlockPos logPos = mutableTickPos.immutable();
                BlockState newState = targetBlock.defaultBlockState();
                // Preserve axis property for logs
                if (logState.hasProperty(BlockStateProperties.AXIS)
                        && newState.hasProperty(BlockStateProperties.AXIS)) {
                    newState = newState.setValue(BlockStateProperties.AXIS,
                            logState.getValue(BlockStateProperties.AXIS));
                }
                // Preserve distance and persistence properties for leaves
                if (logState.hasProperty(BlockStateProperties.DISTANCE)
                        && newState.hasProperty(BlockStateProperties.DISTANCE)) {
                    newState = newState.setValue(BlockStateProperties.DISTANCE,
                            logState.getValue(BlockStateProperties.DISTANCE));
                }
                if (logState.hasProperty(BlockStateProperties.PERSISTENT)
                        && newState.hasProperty(BlockStateProperties.PERSISTENT)) {
                    newState = newState.setValue(BlockStateProperties.PERSISTENT,
                            logState.getValue(BlockStateProperties.PERSISTENT));
                }
                level.setBlockAndUpdate(logPos, newState);
            }
        }
    }
}
