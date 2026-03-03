package com.maxsters.coldspawncontrol.config;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.network.ConfigSyncPacket;
import com.maxsters.coldspawncontrol.network.Networking;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameRules.Category;
import net.minecraft.world.level.GameRules.Key;
import net.minecraftforge.server.ServerLifecycleHooks;
import java.lang.reflect.Method;
import java.util.function.BiConsumer;

public class ModGameRules {

    public static Key<GameRules.BooleanValue> RULE_REALISTIC_MODE;
    public static Key<GameRules.BooleanValue> RULE_RENDER_CLOUD_COVERAGE;
    public static Key<GameRules.BooleanValue> RULE_DEBUG_MODE;
    public static Key<GameRules.BooleanValue> RULE_DEBUG_LOOT_BOOK;

    public static void register() {
        try {
            RULE_REALISTIC_MODE = createBooleanRule("realisticMode", Category.UPDATES, true, (server, value) -> {
                ColdSpawnControl.LOGGER.info("GameRule realisticMode changed to: " + value.get());
                syncToAll();
            });

            RULE_RENDER_CLOUD_COVERAGE = createBooleanRule("renderCloudCoverage", Category.UPDATES, false,
                    (server, value) -> {
                        ColdSpawnControl.LOGGER.info("GameRule renderCloudCoverage changed to: " + value.get());
                        syncToAll();
                    });

            RULE_DEBUG_MODE = createBooleanRule("debugVisibility", Category.MISC, false,
                    (server, value) -> {
                        ColdSpawnControl.LOGGER.info("GameRule debugVisibility changed to: " + value.get());
                        // Sync debug mode to all clients
                        Networking.sendToAll(new com.maxsters.coldspawncontrol.network.VisibilityPacket(value.get()));
                    });

            RULE_DEBUG_LOOT_BOOK = createBooleanRule("debugLootBook", Category.MISC, false,
                    (server, value) -> {
                        ColdSpawnControl.LOGGER.info("GameRule debugLootBook changed to: " + value.get());
                    });

        } catch (Exception e) {
            ColdSpawnControl.LOGGER.error("Failed to register GameRules via reflection", e);
        }
    }

    @SuppressWarnings("null")
    private static void syncToAll() {
        net.minecraft.server.MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            boolean realistic = server.getGameRules().getBoolean(RULE_REALISTIC_MODE);
            boolean cloud = server.getGameRules().getBoolean(RULE_RENDER_CLOUD_COVERAGE);
            Networking.sendToAll(new ConfigSyncPacket(realistic, cloud));
        }
    }

    @SuppressWarnings("unchecked")
    private static Key<GameRules.BooleanValue> createBooleanRule(String name, Category category, boolean defaultValue,
            BiConsumer<net.minecraft.server.MinecraftServer, GameRules.BooleanValue> callback) throws Exception {

        // SRG Name for GameRules.BooleanValue.create is m_46252_
        Method createMethod = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(
                GameRules.BooleanValue.class,
                "m_46252_",
                boolean.class, BiConsumer.class);
        createMethod.setAccessible(true);

        GameRules.Type<GameRules.BooleanValue> ruleType = (GameRules.Type<GameRules.BooleanValue>) createMethod
                .invoke(null, defaultValue, callback);

        // SRG Name for GameRules.register is m_46189_
        Method registerMethod = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(
                GameRules.class,
                "m_46189_",
                String.class, Category.class, GameRules.Type.class);
        registerMethod.setAccessible(true);

        return (Key<GameRules.BooleanValue>) registerMethod.invoke(null, name, category, ruleType);
    }
}
