package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.DebugScreenOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Randomizes the day counter in the F3 debug screen.
 * 
 * This is a 4th wall break that reinforces the amnesia theme - the player
 * cannot trust even the debug information to tell them how long they've
 * survived.
 * The fake day value changes on each blackout and can range from 0 to 9999.
 */
@Mixin(DebugScreenOverlay.class)
public class DebugScreenOverlayMixin {

    // Pattern to match "Day X" in the F3 text
    private static final Pattern DAY_PATTERN = Pattern.compile("Day (\\d+)");

    /**
     * Intercepts getGameInformation and modifies the day display.
     */
    @Inject(method = "getGameInformation", at = @At("RETURN"))
    private void modifyDayCounter(CallbackInfoReturnable<List<String>> cir) {
        Minecraft mc = Minecraft.getInstance();

        // Only modify in Overworld
        if (mc.level == null || !net.minecraft.world.level.Level.OVERWORLD.equals(mc.level.dimension())) {
            return;
        }

        // Get the fake day from player persistent data (synced from server)
        // For client-side, we'll use a different approach: store it locally
        int fakeDay = getFakeDay();

        List<String> lines = cir.getReturnValue();
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            Matcher matcher = DAY_PATTERN.matcher(line);
            if (matcher.find()) {
                // Replace the actual day with the fake day
                String modified = matcher.replaceFirst("Day " + fakeDay);
                lines.set(i, modified);
                break;
            }
        }
    }

    /**
     * Gets the fake day value. This is stored client-side and updated
     * when the player receives a blackout notification from the server.
     */
    private int getFakeDay() {
        // Use a static reference to the client-side fake day tracker
        return com.maxsters.coldspawncontrol.client.AmnesiaClientState.getFakeDay();
    }
}
