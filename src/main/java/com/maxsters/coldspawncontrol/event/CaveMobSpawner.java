package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
@SuppressWarnings("null")
public class CaveMobSpawner {

    private static final int SPAWN_INTERVAL = 200; // 10 seconds
    private static final int SPAWN_RADIUS = 24;
    private static final int MIN_SPAWN_DISTANCE = 12;

    // Weighted spawn map
    private static final Map<EntityType<?>, Integer> SPAWN_WEIGHTS = new HashMap<>();
    private static int TOTAL_WEIGHT = 0;

    static {
        addSpawn(EntityType.GOAT, 10);
        addSpawn(EntityType.WOLF, 6);
        addSpawn(EntityType.RABBIT, 4);
        addSpawn(EntityType.FOX, 4);
        // Rare others
        addSpawn(EntityType.COW, 1);
        addSpawn(EntityType.SHEEP, 1);
        addSpawn(EntityType.PIG, 1);
        addSpawn(EntityType.CHICKEN, 1);
    }

    private static void addSpawn(EntityType<?> type, int weight) {
        SPAWN_WEIGHTS.put(type, weight);
        TOTAL_WEIGHT += weight;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide) {
            return;
        }

        // Only run on Overworld
        if (!event.level.dimension().equals(Level.OVERWORLD)) {
            return;
        }

        // Run periodically
        if (event.level.getGameTime() % SPAWN_INTERVAL != 0) {
            return;
        }

        ServerLevel level = (ServerLevel) event.level;

        // Dedup players within 32 blocks of each other to prevent exponential
        // mob spawning in multiplayer when players group together
        double minDistanceSqr = 32.0 * 32.0;
        java.util.Set<ServerPlayer> processedPlayers = java.util.Collections
                .newSetFromMap(new java.util.IdentityHashMap<>());

        // Attempt spawn for each player
        for (ServerPlayer player : level.players()) {
            boolean isClustered = false;
            for (ServerPlayer processed : processedPlayers) {
                if (player.distanceToSqr(processed) < minDistanceSqr) {
                    isClustered = true;
                    break;
                }
            }
            if (isClustered) {
                continue;
            }
            processedPlayers.add(player);
            attemptSpawnNearPlayer(level, player);
        }
    }

    private static void attemptSpawnNearPlayer(ServerLevel level, ServerPlayer player) {
        RandomSource random = level.getRandom();

        // 50% chance to skip this player this cycle to keep rates low
        if (random.nextBoolean()) {
            return;
        }

        // Try 5 times to find a valid spot
        for (int i = 0; i < 5; i++) {
            BlockPos spawnPos = findRandomSpawnPos(level, player, random);
            if (spawnPos != null) {
                // Pick a mob
                EntityType<?> type = pickRandomMob(random);
                spawnMob(level, type, spawnPos);
                break; // Spawned one, done for this player
            }
        }
    }

    private static BlockPos findRandomSpawnPos(ServerLevel level, ServerPlayer player, RandomSource random) {
        // Random offset
        int dx = (random.nextInt(SPAWN_RADIUS * 2) - SPAWN_RADIUS);
        int dz = (random.nextInt(SPAWN_RADIUS * 2) - SPAWN_RADIUS);
        int dy = (random.nextInt(10) - 5); // +/- 5 Y from player

        BlockPos target = player.blockPosition().offset(dx, dy, dz);

        // Distance check
        if (target.distSqr(player.blockPosition()) < MIN_SPAWN_DISTANCE * MIN_SPAWN_DISTANCE) {
            return null;
        }

        // Y Level Check: Y >= 0 (Shallower caves)
        if (target.getY() < 0) {
            return null;
        }

        // Check if air and valid ground
        if (!level.getBlockState(target).isAir() || !level.getBlockState(target.above()).isAir()) {
            return null;
        }

        BlockState ground = level.getBlockState(target.below());
        if (!ground.getMaterial().isSolid() || !ground.isValidSpawn(level, target.below(), EntityType.GOAT)) { // heuristic
            return null;
        }

        // Ensure it's a "cave like" block (Stone, etc) - optional but good for theme
        // For now just solid ground is okay, but user mentioned caves.
        // We can check sky light to ensure we are "in a cave"
        if (level.getBrightness(LightLayer.SKY, target) > 0) {
            return null; // Exposed to sky -> surface (not a cave)
        }

        // Check block light (must be dark enough to spawn)
        if (level.getBrightness(LightLayer.BLOCK, target) > 7) {
            return null;
        }

        return target;
    }

    private static EntityType<?> pickRandomMob(RandomSource random) {
        int roll = random.nextInt(TOTAL_WEIGHT);
        int current = 0;
        for (Map.Entry<EntityType<?>, Integer> entry : SPAWN_WEIGHTS.entrySet()) {
            current += entry.getValue();
            if (roll < current) {
                return entry.getKey();
            }
        }
        return EntityType.GOAT; // Fallback
    }

    private static void spawnMob(ServerLevel level, EntityType<?> type, BlockPos pos) {
        if (type.create(level) instanceof Mob mob) {
            mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            // Final check for collision checks
            if (level.noCollision(mob) && level.isUnobstructed(mob)) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.NATURAL, null, null);
                level.addFreshEntity(mob);
                // Optional: Log for debug
                // ColdSpawnControl.LOGGER.debug("Spawned cave mob {} at {}",
                // type.getDescriptionId(), pos);
            }
        }
    }
}
