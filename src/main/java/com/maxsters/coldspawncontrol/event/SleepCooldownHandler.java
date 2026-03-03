package com.maxsters.coldspawncontrol.event;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ColdSpawnControl.MOD_ID)
public class SleepCooldownHandler {

    private static final String LAST_SLEEP_TIME_KEY = ColdSpawnControl.MOD_ID + ":last_sleep_time";
    private static final long COOLDOWN_MS = 30 * 60 * 1000L; // 30 real-life minutes in milliseconds

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onPlayerSleep(PlayerSleepInBedEvent event) {
        Player player = event.getEntity();
        if (player.level.isClientSide) {
            return; // We only want to handle this on the server side to maintain sync
        }

        long currentTime = System.currentTimeMillis();
        long lastSleepTime = player.getPersistentData().getLong(LAST_SLEEP_TIME_KEY);

        if (lastSleepTime > 0 && (currentTime - lastSleepTime) < COOLDOWN_MS) {
            // Player is still on cooldown
            event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
            player.displayClientMessage(Component.literal("You don't feel tired yet."), true);
        }
    }

    @SubscribeEvent
    public static void onPlayerWakeUp(PlayerWakeUpEvent event) {
        Player player = event.getEntity();
        if (player.level.isClientSide)
            return;

        // Only record the cooldown if the player actually slept long enough.
        // isSleepingLongEnough() returns true when the player has been in bed
        // for >= 100 ticks (5 seconds). The Forge event fires BEFORE the sleep
        // state is cleared, so this check is still valid here.
        // This prevents the cooldown from triggering when the player enters a
        // bed and gets out before actually falling asleep.
        if (player.isSleepingLongEnough()) {
            player.getPersistentData().putLong(LAST_SLEEP_TIME_KEY, System.currentTimeMillis());
        }
    }
}
