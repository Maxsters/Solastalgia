package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ConfigTracker;
import net.minecraftforge.fml.config.ModConfig;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import com.electronwill.nightconfig.core.CommentedConfig;

/**
 * Patches the First Aid mod's server config on each server start to disable
 * sleep healing. Modifies both the in-memory config (so it takes effect
 * immediately) and the file on disk (so it persists across restarts).
 *
 * Runs once on ServerStartedEvent (not per player join), safe for multiplayer.
 */
@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public class FirstAidConfigEnforcer {

    private static final String CONFIG_FILE = "firstaid-server.toml";
    private static final List<String> SLEEP_HEAL_PATH = Arrays.asList("External Healing", "sleepHealPercentage");
    private static final double DESIRED_VALUE = 0.0;

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        try {
            // Access ConfigTracker's internal fileMap to find First Aid's config
            Field fileMapField = ConfigTracker.class.getDeclaredField("fileMap");
            fileMapField.setAccessible(true);

            @SuppressWarnings("unchecked")
            ConcurrentHashMap<String, ModConfig> fileMap = (ConcurrentHashMap<String, ModConfig>) fileMapField
                    .get(ConfigTracker.INSTANCE);

            ModConfig config = fileMap.get(CONFIG_FILE);
            if (config == null) {
                ColdSpawnControl.LOGGER.warn(
                        "FirstAidConfigEnforcer: No config registered for '{}'. First Aid may not be installed.",
                        CONFIG_FILE);
                return;
            }

            CommentedConfig data = config.getConfigData();
            if (data == null) {
                ColdSpawnControl.LOGGER.warn(
                        "FirstAidConfigEnforcer: Config data for '{}' is null.", CONFIG_FILE);
                return;
            }

            // Read the current value
            double currentValue = data.getOrElse(SLEEP_HEAL_PATH, -1.0);

            if (currentValue != DESIRED_VALUE) {
                // Update the in-memory config — ForgeConfigSpec values read from
                // this backing config, so the change takes effect immediately
                data.set(SLEEP_HEAL_PATH, DESIRED_VALUE);

                // Persist to disk so the change survives restarts
                config.save();

                ColdSpawnControl.LOGGER.info(
                        "FirstAidConfigEnforcer: Changed sleepHealPercentage from {} to {} (in-memory + file).",
                        currentValue, DESIRED_VALUE);
            } else {
                ColdSpawnControl.LOGGER.debug(
                        "FirstAidConfigEnforcer: sleepHealPercentage already set to {}. No changes needed.",
                        DESIRED_VALUE);
            }

        } catch (NoSuchFieldException | IllegalAccessException e) {
            ColdSpawnControl.LOGGER.error("FirstAidConfigEnforcer: Failed to access ConfigTracker internals.", e);
        } catch (Exception e) {
            ColdSpawnControl.LOGGER.error("FirstAidConfigEnforcer: Unexpected error while patching config.", e);
        }
    }
}
