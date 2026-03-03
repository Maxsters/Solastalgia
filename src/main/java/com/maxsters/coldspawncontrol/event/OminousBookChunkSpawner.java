package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.config.ModGameRules;
import com.maxsters.coldspawncontrol.registry.OminousBookFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/**
 * Spawns "Torn Field Note" books as dropped items on the surface of newly
 * generated Overworld chunks. Each chunk has a tiny chance (0.1%) to spawn
 * a book at a random surface location.
 *
 * Uses SavedData to track which chunks have been processed, persisted
 * across world saves. When the debugLootBook gamerule is active, every
 * new chunk is guaranteed a book.
 */
@SuppressWarnings("null")
@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public class OminousBookChunkSpawner {

    private static final Random RANDOM = new Random();

    /**
     * Default chance per chunk: 0.1% (1 in 1000 chunks).
     */
    private static final float DEFAULT_CHANCE = 0.001F;

    // ==================== SAVED DATA ====================

    /**
     * Persistent data that tracks which chunks have been processed.
     */
    public static class ProcessedChunksData extends SavedData {
        private static final String DATA_NAME = "solastalgia_book_chunks";
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

    // ==================== EVENT HANDLER ====================

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        // Only process on server side
        if (event.getLevel().isClientSide())
            return;

        if (!(event.getLevel() instanceof ServerLevel serverLevel))
            return;

        // Only in the Overworld
        if (serverLevel.dimension() != Level.OVERWORLD)
            return;

        if (!(event.getChunk() instanceof LevelChunk chunk))
            return;

        ChunkPos chunkPos = chunk.getPos();

        // Check if debug mode is active
        boolean debugMode = ModGameRules.RULE_DEBUG_LOOT_BOOK != null
                && serverLevel.getGameRules().getBoolean(ModGameRules.RULE_DEBUG_LOOT_BOOK);

        // Skip generation if book already acquired (unless debug mode)
        if (!debugMode) {
            com.maxsters.coldspawncontrol.registry.OminousBookTracker tracker = com.maxsters.coldspawncontrol.registry.OminousBookTracker
                    .get(serverLevel);
            if (tracker.isBookAcquired())
                return;
        }

        // Check if already processed via saved data
        ProcessedChunksData data = ProcessedChunksData.get(serverLevel);
        if (data.isProcessed(chunkPos))
            return;

        // Mark as processed immediately
        data.markProcessed(chunkPos);

        // Determine chance: debug mode = 100%, normal = 0.1%
        float chance = debugMode ? 1.0F : DEFAULT_CHANCE;

        if (RANDOM.nextFloat() >= chance)
            return;

        // Pick a random position within the chunk
        int localX = RANDOM.nextInt(16);
        int localZ = RANDOM.nextInt(16);
        int worldX = chunkPos.getMinBlockX() + localX;
        int worldZ = chunkPos.getMinBlockZ() + localZ;

        // Get the surface Y using the motion-blocking heightmap
        int surfaceY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, localX, localZ) + 1;

        // Sanity check
        if (surfaceY < 1 || surfaceY > 255)
            return;

        BlockPos spawnPos = new BlockPos(worldX, surfaceY, worldZ);

        // Create the book item entity
        ItemStack book = OminousBookFactory.createBook();
        ItemEntity itemEntity = new ItemEntity(serverLevel, spawnPos.getX() + 0.5, spawnPos.getY(),
                spawnPos.getZ() + 0.5, book);

        // Make the item persist (won't despawn)
        itemEntity.setUnlimitedLifetime();
        itemEntity.setPickUpDelay(0);

        serverLevel.addFreshEntity(itemEntity);

        ColdSpawnControl.LOGGER.debug("Spawned Torn Field Note at {} in chunk [{}, {}]",
                spawnPos, chunkPos.x, chunkPos.z);
    }
}
