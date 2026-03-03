package com.maxsters.coldspawncontrol.client;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

import java.util.Random;

/**
 * Client-side state for amnesia effects.
 * 
 * This tracks values that need to be displayed on the client
 * but are controlled by server events (like blackouts).
 */
public final class AmnesiaClientState {

    private AmnesiaClientState() {
    }

    private static final Random RANDOM = new Random();

    /**
     * The fake day counter shown in F3.
     * Initialized to a random value on client start.
     * Changes when server sends a blackout notification.
     */
    private static int fakeDay = RANDOM.nextInt(10000);

    /**
     * Gets the current fake day for display in F3.
     */
    public static int getFakeDay() {
        return fakeDay;
    }

    /**
     * Sets a new random fake day. Called when a blackout occurs.
     * The day can jump wildly - from 0 to 9999.
     */
    public static void randomizeFakeDay() {
        fakeDay = RANDOM.nextInt(10000);
    }

    /**
     * Sets a specific fake day value (used when syncing from server).
     */
    public static void setFakeDay(int day) {
        fakeDay = day;
    }

    // ==================== DISORIENTATION EFFECTS ====================

    /**
     * Applies disorientation effects triggered by a blackout.
     * These modify client-side settings the player wouldn't expect to change.
     *
     * @param fovDelta FOV change amount (0 = no change)
     * @param swapAxis Movement axis to swap (-1 = none, 0 = left/right, 1 =
     *                 forward/back)
     */
    @SuppressWarnings("null")
    public static void applyDisorientation(int fovDelta, int swapAxis) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null)
            return;

        // ---- FOV shift ----
        if (fovDelta != 0) {
            applyFovShift(mc.options, fovDelta);
        }

        // ---- Key swap ----
        if (swapAxis >= 0) {
            applyKeySwap(mc.options, swapAxis);
        }
    }

    /**
     * Shifts the player's FOV by the given delta, clamped to Minecraft's
     * valid range (30-110).
     */
    private static void applyFovShift(Options options, int delta) {
        int currentFov = options.fov().get();
        int newFov = Math.max(30, Math.min(110, currentFov + delta));

        options.fov().set(newFov);

        ColdSpawnControl.LOGGER.info("Amnesia: FOV shifted from {} to {} (delta: {})",
                currentFov, newFov, delta);
    }

    /**
     * Swaps two movement keys on the specified axis.
     * axis 0 = left/right (keyLeft ↔ keyRight)
     * axis 1 = forward/back (keyUp ↔ keyDown)
     *
     * The swap persists until another blackout or the player manually
     * rebinds their keys. This creates a subtle "something is wrong"
     * feeling that takes a moment to identify.
     */
    @SuppressWarnings("null")
    private static void applyKeySwap(Options options, int axis) {
        KeyMapping key1, key2;
        String axisName;

        if (axis == 0) {
            key1 = options.keyLeft;
            key2 = options.keyRight;
            axisName = "left/right";
        } else {
            key1 = options.keyUp;
            key2 = options.keyDown;
            axisName = "forward/back";
        }

        // Get current key codes
        InputConstants.Key code1 = key1.getKey();
        InputConstants.Key code2 = key2.getKey();

        // Swap them
        key1.setKey(code2);
        key2.setKey(code1);

        // Refresh the key mapping system so the game recognizes the swap
        KeyMapping.resetMapping();

        // Save the options so the swap persists through restarts
        // (maximum confusion)
        options.save();

        ColdSpawnControl.LOGGER.info("Amnesia: Swapped {} movement keys ({} ↔ {})",
                axisName, code1.getDisplayName().getString(), code2.getDisplayName().getString());
    }
}
