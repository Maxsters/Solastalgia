package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;

/**
 * Spawns passive cave mobs during chunk generation only.
 * Each newly generated chunk has a chance to contain passive mobs
 * in valid cave spaces. Mobs will not continuously spawn near players,
 * preventing unrealistic mob accumulation around AFK players.
 *
 * This mirrors how Minecraft handles passive mob spawning on the surface:
 * they generate with the chunk and don't re-spawn over time.
 *
 * Spawning is deferred to the next server tick to avoid cascading chunk
 * loads during the "Preparing spawn area" phase.
 */
@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
@SuppressWarnings("null")
public class CaveMobSpawner {

    private static final float CHUNK_SPAWN_CHANCE = 0.01F;
    private static final int MAX_MOBS_PER_CHUNK = 2;
    private static final int SPAWN_ATTEMPTS_PER_MOB = 8;

    private static final Map<EntityType<?>, Integer> SPAWN_WEIGHTS = new HashMap<>();
    private static int TOTAL_WEIGHT = 0;

    private static final Queue<ChunkPos> PENDING_CHUNKS = new ArrayDeque<>();

    static {
        addSpawn(EntityType.GOAT, 10);
        addSpawn(EntityType.WOLF, 6);
        addSpawn(EntityType.RABBIT, 4);
        addSpawn(EntityType.FOX, 4);
        addSpawn(EntityType.COW, 1);
        addSpawn(EntityType.SHEEP, 1);
        addSpawn(EntityType.PIG, 1);
        addSpawn(EntityType.CHICKEN, 1);
    }

    private static void addSpawn(EntityType<?> type, int weight) {
        SPAWN_WEIGHTS.put(type, weight);
        TOTAL_WEIGHT += weight;
    }

    // ==================== SAVED DATA ====================

    /**
     * Persistent data tracking which chunks have already had cave mob generation.
     * Prevents duplicate spawning when chunks are loaded/unloaded.
     */
    public static class ProcessedChunksData extends SavedData {
        private static final String DATA_NAME = "solastalgia_cave_mob_chunks";
        private final Set<Long> processedChunks = new HashSet<>();

        public ProcessedChunksData() {
        }

        public static ProcessedChunksData load(CompoundTag tag) {
            ProcessedChunksData data = new ProcessedChunksData();
            ListTag list = tag.getList("chunks", Tag.TAG_LONG);
            for (int i = 0; i < list.size(); i++) {
                data.processedChunks.add(((LongTag) list.get(i)).getAsLong());
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            ListTag list = new ListTag();
            for (long pos : processedChunks) {
                list.add(LongTag.valueOf(pos));
            }
            tag.put("chunks", list);
            return tag;
        }

        public boolean isProcessed(ChunkPos pos) {
            return processedChunks.contains(pos.toLong());
        }

        public void markProcessed(ChunkPos pos) {
            processedChunks.add(pos.toLong());
            setDirty();
        }

        public static ProcessedChunksData get(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(
                    ProcessedChunksData::load,
                    ProcessedChunksData::new,
                    DATA_NAME);
        }
    }

    // ==================== EVENT HANDLERS ====================

    /**
     * On chunk load, mark it as processed and queue it for deferred spawning.
     * No block access, entity creation, or light queries happen here —
     * those are deferred to the next server tick to prevent cascading
     * chunk loads during "Preparing spawn area".
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel().isClientSide())
            return;

        if (!(event.getLevel() instanceof ServerLevel serverLevel))
            return;

        if (serverLevel.dimension() != Level.OVERWORLD)
            return;

        if (!(event.getChunk() instanceof LevelChunk))
            return;

        ChunkPos chunkPos = event.getChunk().getPos();

        ProcessedChunksData data = ProcessedChunksData.get(serverLevel);
        if (data.isProcessed(chunkPos))
            return;

        data.markProcessed(chunkPos);

        RandomSource random = serverLevel.getRandom();
        if (random.nextFloat() >= CHUNK_SPAWN_CHANCE)
            return;

        PENDING_CHUNKS.add(chunkPos);
    }

    /**
     * Processes pending chunk spawns on the next server tick, when
     * the world is in a stable state and all chunks are fully loaded.
     */
    @SubscribeEvent
    public static void onServerTick(net.minecraftforge.event.TickEvent.LevelTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.END || event.level.isClientSide())
            return;

        if (!(event.level instanceof ServerLevel serverLevel))
            return;

        if (serverLevel.dimension() != Level.OVERWORLD)
            return;

        int processed = 0;
        int maxPerTick = 4;

        while (!PENDING_CHUNKS.isEmpty() && processed < maxPerTick) {
            ChunkPos chunkPos = PENDING_CHUNKS.poll();
            if (chunkPos == null)
                break;

            if (!serverLevel.hasChunk(chunkPos.x, chunkPos.z))
                continue;

            spawnMobsInChunk(serverLevel, chunkPos);
            processed++;
        }
    }

    // ==================== SPAWN LOGIC ====================

    private static void spawnMobsInChunk(ServerLevel level, ChunkPos chunkPos) {
        RandomSource random = level.getRandom();
        int mobsToSpawn = 1 + random.nextInt(MAX_MOBS_PER_CHUNK);
        int spawned = 0;

        for (int m = 0; m < mobsToSpawn && spawned < mobsToSpawn; m++) {
            for (int attempt = 0; attempt < SPAWN_ATTEMPTS_PER_MOB; attempt++) {
                BlockPos spawnPos = findCaveSpawnInChunk(level, chunkPos, random);
                if (spawnPos != null) {
                    EntityType<?> type = pickRandomMob(random);
                    if (spawnMob(level, type, spawnPos)) {
                        spawned++;
                        break;
                    }
                }
            }
        }

        if (spawned > 0) {
            ColdSpawnControl.LOGGER.debug("Cave mobs: Spawned {} in chunk [{}, {}]",
                    spawned, chunkPos.x, chunkPos.z);
        }
    }

    /**
     * Finds a valid cave spawn position within a chunk.
     * Scans random positions in the chunk at cave-appropriate Y levels.
     */
    private static BlockPos findCaveSpawnInChunk(ServerLevel level, ChunkPos chunkPos, RandomSource random) {
        int localX = random.nextInt(16);
        int localZ = random.nextInt(16);
        int worldX = chunkPos.getMinBlockX() + localX;
        int worldZ = chunkPos.getMinBlockZ() + localZ;

        int minY = 0;
        int maxY = 56;
        int y = minY + random.nextInt(maxY - minY + 1);

        BlockPos target = new BlockPos(worldX, y, worldZ);

        if (!level.getBlockState(target).isAir() || !level.getBlockState(target.above()).isAir()) {
            return null;
        }

        BlockState ground = level.getBlockState(target.below());
        if (!ground.getMaterial().isSolid() || !ground.isValidSpawn(level, target.below(), EntityType.GOAT)) {
            return null;
        }

        if (level.getBrightness(LightLayer.SKY, target) > 0) {
            return null;
        }

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
        return EntityType.GOAT;
    }

    private static boolean spawnMob(ServerLevel level, EntityType<?> type, BlockPos pos) {
        if (type.create(level) instanceof Mob mob) {
            mob.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            if (level.noCollision(mob) && level.isUnobstructed(mob)) {
                mob.finalizeSpawn(level, level.getCurrentDifficultyAt(pos), MobSpawnType.CHUNK_GENERATION, null, null);
                mob.setPersistenceRequired();
                level.addFreshEntity(mob);
                return true;
            }
        }
        return false;
    }
}
