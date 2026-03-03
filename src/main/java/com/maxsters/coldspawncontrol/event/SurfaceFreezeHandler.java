package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.util.TemperatureCache;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Stray;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@SuppressWarnings("null")
@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public final class SurfaceFreezeHandler {
    private static final String EXPOSURE_KEY = "kjs_exposure_ticks";
    public static final String COLD_SPAWN_BYPASS_TAG = "kjs_cold_spawn_bypass";
    private static final int FROZEN_TICKS = 300;
    private static long tickCounter = 0;

    private static final Map<BlockPos, Integer> FREEZE_TIMERS = new HashMap<>();
    private static final Map<BlockPos, Long> LAST_SEEN_TICK = new HashMap<>();

    private SurfaceFreezeHandler() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        // Hardcoded: tickInterval = 10
        int interval = 10;
        tickCounter++;
        if (tickCounter % interval != 0) {
            return;
        }

        MinecraftServer server = event.getServer();
        if (server == null) {
            return;
        }

        for (ServerLevel level : server.getAllLevels()) {
            processLevel(require(level, "server level"), interval);

            if (tickCounter % 100 == 0) {
                // Hard cap: if maps grow too large, force full cleanup to prevent unbounded
                // memory growth
                if (FREEZE_TIMERS.size() > 10_000) {
                    FREEZE_TIMERS.clear();
                    LAST_SEEN_TICK.clear();
                } else {
                    LAST_SEEN_TICK.entrySet().removeIf(entry -> {
                        if (tickCounter - entry.getValue() > 400) {
                            FREEZE_TIMERS.remove(entry.getKey());
                            return true;
                        }
                        return false;
                    });
                }
            }
        }
    }

    private static void processLevel(ServerLevel level, int interval) {
        // Dedup set prevents processing the same entity multiple times when
        // multiple players have overlapping 64-block AABBs (multiplayer)
        java.util.Set<Integer> processedEntityIds = new java.util.HashSet<>();
        java.util.Set<Long> processedBlocks = new java.util.HashSet<>();
        for (ServerPlayer player : level.players()) {
            // Scope entity processing to 64-block radius around each player
            // instead of scanning every entity in the entire dimension
            AABB area = new AABB(player.blockPosition()).inflate(64);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area, LivingEntity::isAlive)) {
                if (entity instanceof Player)
                    continue;
                if (processedEntityIds.add(entity.getId())) {
                    handleEntity(level, entity, interval);
                }
            }
            handlePlayerEnvironment(level, player, interval, processedBlocks);
        }
    }

    private static void handlePlayerEnvironment(ServerLevel level, ServerPlayer player, int interval,
            java.util.Set<Long> processedBlocks) {
        // Hardcoded: overworldOnly = true
        if (true && !Level.OVERWORLD.equals(level.dimension())) {
            return;
        }

        BlockPos playerPos = player.blockPosition();
        int radius = 16;
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > radius * radius)
                    continue;

                int worldX = playerPos.getX() + x;
                int worldZ = playerPos.getZ() + z;

                int surfaceY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        worldX, worldZ);

                if (Math.abs((surfaceY - 1) - playerPos.getY()) > 10)
                    continue;

                mutablePos.set(worldX, surfaceY - 1, worldZ);
                if (!processedBlocks.add(mutablePos.asLong())) {
                    continue; // Skip if already processed this tick by another player
                }
                BlockState state = level.getBlockState(mutablePos);

                if (state.is(net.minecraft.world.level.block.Blocks.WATER) && state.getFluidState().isSource()) {
                    // Use shared temperature cache — avoids redundant Cold Sweat API calls
                    // for blocks in the same 16×16×16 region
                    double temper = TemperatureCache.get(level, mutablePos);

                    // Hardcoded: freezeTemperature = -0.5D
                    double freezeTemp = -0.5D;
                    if (temper <= freezeTemp) {
                        BlockPos immutablePos = mutablePos.immutable();
                        LAST_SEEN_TICK.put(immutablePos, tickCounter);

                        if (!FREEZE_TIMERS.containsKey(immutablePos)) {
                            double diff = freezeTemp - temper;
                            int targetDelay = (int) Math.max(10, 400 - (diff * 5.13));
                            int noise = level.random.nextInt(11) - 5;
                            FREEZE_TIMERS.put(immutablePos, targetDelay + noise);
                        } else {
                            int currentTimer = FREEZE_TIMERS.get(immutablePos);
                            int nextTimer = currentTimer - interval;

                            if (nextTimer <= 0) {
                                if (level.canSeeSky(mutablePos.setWithOffset(immutablePos, 0, 1, 0))) {
                                    level.setBlockAndUpdate(immutablePos,
                                            net.minecraft.world.level.block.Blocks.ICE.defaultBlockState());
                                }
                                FREEZE_TIMERS.remove(immutablePos);
                                LAST_SEEN_TICK.remove(immutablePos);
                            } else {
                                FREEZE_TIMERS.put(immutablePos, nextTimer);
                            }
                        }
                    } else {
                        // Only allocate immutable copy if there's actually something to remove
                        if (!FREEZE_TIMERS.isEmpty()) {
                            BlockPos immutablePos = mutablePos.immutable();
                            FREEZE_TIMERS.remove(immutablePos);
                        }
                    }
                } else if (state.hasProperty(
                        net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED) &&
                        state.getValue(
                                net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
                    // Use shared temperature cache
                    double temper = TemperatureCache.get(level, mutablePos);

                    // Hardcoded: freezeTemperature = -0.5D
                    double freezeTemp = -0.5D;

                    if (temper <= freezeTemp && level.canSeeSky(mutablePos.setWithOffset(mutablePos, 0, 1, 0))) {
                        // Defer key creation until after temperature check passes
                        BlockPos waterloggedKey = new BlockPos(worldX, (surfaceY - 1) + 10000, worldZ);
                        mutablePos.set(worldX, surfaceY - 1, worldZ);
                        LAST_SEEN_TICK.put(waterloggedKey, tickCounter);

                        if (!FREEZE_TIMERS.containsKey(waterloggedKey)) {
                            double diff = freezeTemp - temper;
                            int targetDelay = (int) Math.max(10, 400 - (diff * 5.13));
                            int noise = level.random.nextInt(11) - 5;
                            FREEZE_TIMERS.put(waterloggedKey, targetDelay + noise);
                        } else {
                            int currentTimer = FREEZE_TIMERS.get(waterloggedKey);
                            int nextTimer = currentTimer - interval;

                            if (nextTimer <= 0) {
                                level.setBlockAndUpdate(mutablePos,
                                        net.minecraft.world.level.block.Blocks.ICE.defaultBlockState());
                                FREEZE_TIMERS.remove(waterloggedKey);
                                LAST_SEEN_TICK.remove(waterloggedKey);
                            } else {
                                FREEZE_TIMERS.put(waterloggedKey, nextTimer);
                            }
                        }
                    } else {
                        if (!FREEZE_TIMERS.isEmpty()) {
                            BlockPos waterloggedKey = new BlockPos(worldX, (surfaceY - 1) + 10000, worldZ);
                            FREEZE_TIMERS.remove(waterloggedKey);
                        }
                    }
                }
            }
        }
    }

    private static void handleEntity(ServerLevel level, LivingEntity entity, int interval) {
        // Hardcoded: overworldOnly = true
        if (true && !Level.OVERWORLD.equals(level.dimension())) {
            clearExposure(entity);
            return;
        }

        BlockPos pos = entity.blockPosition();
        // Use shared temperature cache for entity checks too
        double temperature = TemperatureCache.get(level, pos);

        if (Double.isNaN(temperature)) {
            clearExposure(entity);
            return;
        }

        // Hardcoded: freezeTemperature = -0.5D
        boolean isCold = temperature <= -0.5D;
        if (!isCold) {
            clearExposure(entity);
            return;
        }

        if (entity.getType() == EntityType.SKELETON && entity instanceof Skeleton skeleton) {
            int previousExposure = entity.getPersistentData().getInt(EXPOSURE_KEY);
            int nextExposure = previousExposure + interval;
            entity.getPersistentData().putInt(EXPOSURE_KEY, nextExposure);

            // Hardcoded: exposureNeeded = 440
            if (nextExposure >= 440) {
                convertSkeleton(skeleton);
            }
        } else if (entity.getType() != EntityType.STRAY) {
            entity.setTicksFrozen(FROZEN_TICKS);
            entity.getPersistentData().remove(EXPOSURE_KEY);
        } else {
            entity.getPersistentData().remove(EXPOSURE_KEY);
        }
    }

    private static void clearExposure(LivingEntity entity) {
        if (entity.getType() == EntityType.SKELETON) {
            entity.getPersistentData().remove(EXPOSURE_KEY);
        }
    }

    private static void convertSkeleton(Skeleton skeleton) {
        if (!(skeleton.level instanceof ServerLevel level)) {
            return;
        }

        Stray stray = EntityType.STRAY.create(level);
        if (stray == null) {
            return;
        }

        stray.moveTo(skeleton.getX(), skeleton.getY(), skeleton.getZ(), skeleton.getYRot(), skeleton.getXRot());
        stray.setYHeadRot(skeleton.getYHeadRot());
        stray.setYBodyRot(skeleton.yBodyRot);
        stray.setNoAi(skeleton.isNoAi());
        stray.setLeftHanded(skeleton.isLeftHanded());

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            EquipmentSlot nonNullSlot = Objects.requireNonNull(slot, "slot");
            ItemStack stack = Objects.requireNonNull(skeleton.getItemBySlot(nonNullSlot), "itemStack");
            ItemStack stackCopy = Objects.requireNonNull(stack.copy(), "itemStackCopy");
            stray.setItemSlot(nonNullSlot, stackCopy);
        }

        if (skeleton.hasCustomName()) {
            stray.setCustomName(skeleton.getCustomName());
            stray.setCustomNameVisible(skeleton.isCustomNameVisible());
        }

        if (skeleton.isPersistenceRequired()) {
            stray.setPersistenceRequired();
        }

        stray.getPersistentData().putBoolean(COLD_SPAWN_BYPASS_TAG, true);
        level.addFreshEntity(stray);
        playConversionSound(level, skeleton.blockPosition());
        skeleton.discard();
    }

    private static void playConversionSound(ServerLevel level, BlockPos pos) {

        // Hardcoded: volume = 1.0f, pitch = 1.0f, radius = 24.0
        float volume = 1.0f;
        float pitch = 1.0f;
        double radius = 24.0;
        double radiusSq = radius * radius;

        SoundEvent soundEvent = Objects.requireNonNull(SoundEvents.SKELETON_CONVERTED_TO_STRAY, "soundEvent");
        // Hardcoded: SoundSource.HOSTILE
        SoundSource soundSource = SoundSource.HOSTILE;
        ClientboundSoundPacket packet = new ClientboundSoundPacket(
                soundEvent,
                soundSource,
                pos.getX() + 0.5D,
                pos.getY() + 0.5D,
                pos.getZ() + 0.5D,
                volume,
                pitch,
                level.getRandom().nextLong());

        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= radiusSq) {
                player.connection.send(packet);
            }
        }
    }

    @Nonnull
    private static <T> T require(@Nullable T value, String message) {
        return Objects.requireNonNull(value, message);
    }
}
