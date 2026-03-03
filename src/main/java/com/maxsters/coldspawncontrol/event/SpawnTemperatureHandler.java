package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.util.TemperatureCache;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;

import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public final class SpawnTemperatureHandler {
    private static final String COLD_SPAWN_BYPASS_TAG = SurfaceFreezeHandler.COLD_SPAWN_BYPASS_TAG;
    private static final EnumSet<MobSpawnType> DEFAULT_MANUAL_TYPES = EnumSet.of(
            MobSpawnType.COMMAND,
            MobSpawnType.SPAWN_EGG,
            MobSpawnType.DISPENSER,
            MobSpawnType.BUCKET);

    private SpawnTemperatureHandler() {
    }

    @SubscribeEvent
    public static void onCheckSpawn(LivingSpawnEvent.CheckSpawn event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) {
            return;
        }

        LevelAccessor accessor = event.getLevel();
        if (!(accessor instanceof Level level) || level.isClientSide) {
            return;
        }

        if (shouldBlockSpawn(level, entity, event.getSpawnReason())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onSpecialSpawn(LivingSpawnEvent.SpecialSpawn event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) {
            return;
        }
        LevelAccessor accessor = event.getLevel();
        if (!(accessor instanceof Level level) || level.isClientSide) {
            return;
        }
        if (shouldBlockSpawn(level, entity, event.getSpawnReason())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof Mob mob)) {
            return;
        }
        Level level = Objects.requireNonNull((Level) event.getLevel(), "server level");
        if (shouldBlockSpawn(level, mob, mob.getSpawnType())) {
            if (event.isCancelable()) {
                event.setCanceled(true);
            } else {
                mob.discard();
            }
        } else if (mob instanceof PathfinderMob pathfinderMob) {
            // Inject heat seeking goal
            // Priority 0: Highest priority to ensure survival
            pathfinderMob.goalSelector.addGoal(0,
                    new com.maxsters.coldspawncontrol.ai.SeekHeatGoal(pathfinderMob, 1.2D));
        }
    }

    private static boolean dimensionMatches(Level level) {
        ResourceKey<Level> key = level.dimension();
        ResourceLocation id = key.location();

        // Hardcoded: dimensionTargets = ["minecraft:overworld"]
        Set<String> targets = java.util.Collections.singleton("minecraft:overworld");

        if (targets.isEmpty()) {
            return true;
        }
        String dimId = id.toString().toLowerCase(Locale.ROOT);
        return targets.contains(dimId);
    }

    private static boolean isManualSpawn(@Nullable MobSpawnType spawnType) {
        if (spawnType == MobSpawnType.SPAWNER) {
            return false;
        }

        if (spawnType != null && DEFAULT_MANUAL_TYPES.contains(spawnType)) {
            return true;
        }

        // Hardcoded: manualSpawnReasons = ["COMMAND", "SPAWN_EGG", "DISPENSER",
        // "BUCKET"]
        // (Note: These are already covered by DEFAULT_MANUAL_TYPES, so the config check
        // was usually redundant unless user added more)
        // We will keep the logic equivalent to "Config has these 4 default values".
        Set<String> manualReasons = java.util.Set.of("COMMAND", "SPAWN_EGG", "DISPENSER", "BUCKET");

        return spawnType != null && manualReasons.contains(spawnType.name());
    }

    private static boolean shouldBlockSpawn(Level level, LivingEntity entity, @Nullable MobSpawnType spawnType) {
        if (entity.getPersistentData().getBoolean(COLD_SPAWN_BYPASS_TAG)) {
            return false;
        }

        if (!dimensionMatches(level)) {
            return false;
        }

        if (isManualSpawn(spawnType)) {
            // logManualBypass(entity, spawnType, level);
            return false;
        }

        BlockPos pos = entity.blockPosition();
        double temperature;
        try {
            // Use TemperatureCache to avoid redundant Cold Sweat API calls
            // for entities spawning in the same 16×16×16 region
            temperature = TemperatureCache.get((ServerLevel) level, pos);
        } catch (RuntimeException ex) {
            ColdSpawnControl.LOGGER.error(
                    "Failed to query Cold Sweat temperature at {} in {}. Allowing spawn as fallback.", pos,
                    level.dimension(), ex);
            return false;
        }

        // Hardcoded: minimumSpawnTemperature = 0D
        double minTemp = 0D;
        if (temperature < minTemp) {
            return true;
        }
        return false;
    }
}
