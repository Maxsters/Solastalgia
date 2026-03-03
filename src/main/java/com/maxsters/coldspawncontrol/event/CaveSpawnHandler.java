package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Handles spawning players inside caves on first join.
 * Matches the journal narrative: "woke up again, somewhere dark. cave?"
 * 
 * Always spawns player around Y40 in a proper enclosed cave.
 */
@SuppressWarnings("null")
@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public class CaveSpawnHandler {

    private static final String CAVE_SPAWN_KEY = "maxsters_cave_spawn_complete";

    // Search parameters
    private static final int SEARCH_RADIUS = 300;
    private static final int SEARCH_STEP = 4;

    // Target Y level - proper cave depth, not shallow ledges
    private static final int TARGET_Y = 40;
    private static final int Y_VARIANCE = 5; // Search Y 35-45

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }

        if (!(player.level instanceof ServerLevel serverLevel)) {
            return;
        }

        // Only apply in Overworld
        if (!Level.OVERWORLD.equals(serverLevel.dimension())) {
            return;
        }

        CompoundTag persistentData = player.getPersistentData();
        boolean alreadySpawned = persistentData.getBoolean(CAVE_SPAWN_KEY);

        // Check if we have already set a shared spawn for this world
        com.maxsters.coldspawncontrol.world.CaveSpawnSavedData worldData = com.maxsters.coldspawncontrol.world.CaveSpawnSavedData
                .get(serverLevel);

        if (!worldData.isSpawnSet()) {
            // First player ever! Find a cave and set it as the world spawn.
            BlockPos caveSpawn = findCaveSpawnLocation(serverLevel, player.blockPosition());

            if (caveSpawn != null) {
                // Set World Spawn
                serverLevel.setDefaultSpawnPos(caveSpawn, 0.0f);
                worldData.setSpawnSet(true);

                ColdSpawnControl.LOGGER.info("CaveSpawn: First join! Set world spawn to cave at {}", caveSpawn);

                // Teleport this player there
                teleportPlayerSafely(player, serverLevel, caveSpawn);
                persistentData.putBoolean(CAVE_SPAWN_KEY, true);
            } else {
                ColdSpawnControl.LOGGER.warn("CaveSpawn: Could not find cave spawn for first player. Using default.");
            }
        } else {
            // Shared spawn is already set.
            if (!alreadySpawned) {
                // New player, send them to the shared spawn
                BlockPos sharedSpawn = serverLevel.getSharedSpawnPos();
                ColdSpawnControl.LOGGER.info("CaveSpawn: New player joining. Teleporting to shared spawn at {}",
                        sharedSpawn);

                teleportPlayerSafely(player, serverLevel, sharedSpawn);
                persistentData.putBoolean(CAVE_SPAWN_KEY, true);
            } else {
                // Player has already spawned before, do nothing.
                ColdSpawnControl.LOGGER.info("CaveSpawn: Player already has history. Skipping teleport.");
            }
        }
    }

    /**
     * Copies persistent data from old player to new player on death/respawn.
     * Without this, the player entity replacement loses our tracking data.
     */
    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // Copy our persistent data keys from old player to new player
        CompoundTag oldData = event.getOriginal().getPersistentData();
        CompoundTag newData = event.getEntity().getPersistentData();

        if (oldData.contains(CAVE_SPAWN_KEY)) {
            newData.putBoolean(CAVE_SPAWN_KEY, oldData.getBoolean(CAVE_SPAWN_KEY));
            ColdSpawnControl.LOGGER.info("CaveSpawn: Copied persistent data on player clone (death: {})",
                    event.isWasDeath());
        }
    }

    /**
     * Finds a cave spawn location around Y40.
     * Searches in expanding pattern until a valid cave is found.
     */
    private static BlockPos findCaveSpawnLocation(ServerLevel level, BlockPos startPos) {
        // Search in expanding squares
        for (int radius = 0; radius <= SEARCH_RADIUS; radius += SEARCH_STEP) {
            for (int dx = -radius; dx <= radius; dx += SEARCH_STEP) {
                for (int dz = -radius; dz <= radius; dz += SEARCH_STEP) {
                    // Only check perimeter (except for radius 0)
                    if (radius > 0 && Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }

                    int x = startPos.getX() + dx;
                    int z = startPos.getZ() + dz;

                    BlockPos cave = findCaveAt(level, x, z);
                    if (cave != null) {
                        return cave;
                    }
                }
            }
        }

        return null;
    }

    /**
     * Finds a valid cave position at the given x,z around Y40.
     */
    private static BlockPos findCaveAt(ServerLevel level, int x, int z) {
        // Search around target Y level
        for (int y = TARGET_Y + Y_VARIANCE; y >= TARGET_Y - Y_VARIANCE; y--) {
            BlockPos pos = new BlockPos(x, y, z);

            // Note: Structure check removed for performance (was causing tick freezes)
            if (isValidCaveSpawn(level, pos)) {
                return pos;
            }
        }
        return null;
    }

    /**
     * Checks if position is a valid cave spawn point.
     * Requirements:
     * - 2 blocks tall air space
     * - Solid floor below (no lava/water)
     * - Solid roof above (must be truly underground)
     * - Enough surrounding cave space
     */
    private static boolean isValidCaveSpawn(ServerLevel level, BlockPos pos) {
        // Check feet and head position are air
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        if (!feet.isAir() || !head.isAir()) {
            return false;
        }

        // Check solid floor - no liquids
        BlockState floor = level.getBlockState(pos.below());
        if (!floor.getMaterial().isSolid()
                || floor.is(Blocks.LAVA)
                || floor.is(Blocks.WATER)) {
            return false;
        }

        // Must have solid roof (check up to 20 blocks - ensures truly underground)
        boolean hasRoof = false;
        int roofDistance = 0;
        for (int dy = 2; dy <= 20; dy++) {
            BlockState above = level.getBlockState(pos.above(dy));
            if (above.getMaterial().isSolid()) {
                hasRoof = true;
                roofDistance = dy;
                break;
            }
        }
        if (!hasRoof || roofDistance < 7) {
            return false; // Open to sky or too cramped
        }

        // Check this is a real cave with space around
        int airBlocks = 0;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                BlockPos check = pos.offset(dx, 0, dz);
                if (level.getBlockState(check).isAir()
                        && level.getBlockState(check.above()).isAir()) {
                    airBlocks++;
                }
            }
        }

        // Need at least 6 of 25 positions to be air (decent cave space)
        if (airBlocks < 6) {
            return false;
        }

        // Check for nearby lava or dungeon/mineshafts (could flow or drip onto player,
        // or spawn mobs)
        if (hasDangerNearby(level, pos, 10)) {
            return false;
        }

        return true;
    }

    /**
     * Check if there's lava or dungeon/mineshafts within the given radius that
     * could endanger the player.
     * Checks in a sphere around the spawn position.
     */
    private static boolean hasDangerNearby(ServerLevel level, BlockPos center, int radius) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    // Sphere check
                    if (dx * dx + dy * dy + dz * dz > radius * radius) {
                        continue;
                    }

                    BlockPos checkPos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(checkPos);

                    if (state.is(Blocks.LAVA)
                            || state.getFluidState().getType() == net.minecraft.world.level.material.Fluids.LAVA
                            || state.getFluidState()
                                    .getType() == net.minecraft.world.level.material.Fluids.FLOWING_LAVA) {
                        ColdSpawnControl.LOGGER.debug("CaveSpawn: Rejected position {} - lava nearby at {}",
                                center, checkPos);
                        return true;
                    }

                    if (state.is(Blocks.SPAWNER) || state.is(Blocks.MOSSY_COBBLESTONE)
                            || state.is(Blocks.COBBLESTONE)) {
                        ColdSpawnControl.LOGGER.debug("CaveSpawn: Rejected position {} - dungeon/spawner nearby at {}",
                                center, checkPos);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Teleports player safely
     */
    private static void teleportPlayerSafely(ServerPlayer player, ServerLevel level, BlockPos target) {
        player.teleportTo(level,
                target.getX() + 0.5,
                target.getY(),
                target.getZ() + 0.5,
                player.getYRot(),
                player.getXRot());

        player.fallDistance = 0;
    }
}
