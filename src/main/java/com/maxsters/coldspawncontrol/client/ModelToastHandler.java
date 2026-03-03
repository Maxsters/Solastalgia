package com.maxsters.coldspawncontrol.client;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.maxsters.coldspawncontrol.slm.ModelManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(value = Dist.CLIENT, modid = ColdSpawnControl.MOD_ID)
public class ModelToastHandler {
    private static final SystemToast.SystemToastIds TOAST_ID = SystemToast.SystemToastIds.TUTORIAL_HINT;
    private static ModelManager.State lastState = ModelManager.State.NOT_CHECKED;
    private static int lastProgress = -1;

    @SuppressWarnings("null")
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null)
            return; // Only run when in-game or valid context

        ModelManager.State currentState = ModelManager.getState();
        int currentProgress = (int) (ModelManager.getDownloadProgress() * 100);

        // Check if state changed OR if we are downloading and progress changed
        // significantly (every 5%)
        boolean stateChanged = currentState != lastState;
        boolean progressChanged = currentState == ModelManager.State.DOWNLOADING &&
                Math.abs(currentProgress - lastProgress) >= 5;

        if (stateChanged || progressChanged) {
            updateToast(mc, currentState, currentProgress);
            lastState = currentState;
            if (currentState == ModelManager.State.DOWNLOADING) {
                lastProgress = currentProgress;
            }
        }
    }

    @SuppressWarnings("null")
    private static void updateToast(Minecraft mc, ModelManager.State state, int progress) {
        Component title = Component.literal("AI Model Status");
        Component message = null;

        switch (state) {
            case DOWNLOADING -> {
                long downloadedMB = ModelManager.getDownloadedBytes() / (1024 * 1024);
                long totalMB = ModelManager.getTotalBytes() / (1024 * 1024);
                if (totalMB > 0) {
                    message = Component
                            .literal(String.format("Downloading... %d%% (%d/%d MB)", progress, downloadedMB, totalMB));
                } else {
                    message = Component.literal(String.format("Downloading... %d MB", downloadedMB));
                }
            }
            case DOWNLOADED, LOADING -> message = Component.literal("Loading model into memory...");
            case READY -> message = Component.literal("Model Loaded & Ready!");
            case DOWNLOAD_FAILED, LOAD_FAILED ->
                message = Component.literal("Error: " + ModelManager.getErrorMessage());
            default -> {
                /* Do nothing for other states */ }
        }

        if (message != null) {
            // SystemToast.addOrUpdate will update the existing toast if it's currently
            // displayed
            SystemToast.addOrUpdate(mc.getToasts(), TOAST_ID, title, message);
        }
    }
}
