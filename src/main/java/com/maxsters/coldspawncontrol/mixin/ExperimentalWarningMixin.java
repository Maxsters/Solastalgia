package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts screen changes to auto-confirm experimental warning screens.
 * Uses translation keys for language-independent identification.
 */
@Mixin(Minecraft.class)
public class ExperimentalWarningMixin {

    /**
     * Gets the translation key from a Component, or null if not translatable.
     * This is language-independent unlike getString() which returns localized text.
     */
    private static String getTranslationKey(Component component) {
        if (component != null && component.getContents() instanceof TranslatableContents) {
            return ((TranslatableContents) component.getContents()).getKey();
        }
        return null;
    }

    /**
     * Checks if a translation key indicates an experimental warning dialog.
     */
    private static boolean isExperimentalWarningKey(String key) {
        return key != null && (key.contains("experimental") ||
                key.contains("selectWorld.warning") ||
                key.equals("selectWorld.experimental.title"));
    }

    /**
     * Checks if a translation key indicates a proceed/confirm button.
     */
    private static boolean isProceedButtonKey(String key) {
        return key != null && (key.contains("proceed") ||
                key.contains("continue") ||
                key.equals("gui.proceed") ||
                key.equals("gui.yes") ||
                key.equals("selectWorld.experimental.details.proceed"));
    }

    @SuppressWarnings("null")
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void onSetScreen(Screen screen, CallbackInfo ci) {
        if (screen instanceof ConfirmScreen confirmScreen) {
            String titleKey = getTranslationKey(confirmScreen.getTitle());

            // Check for experimental warning using translation key
            if (isExperimentalWarningKey(titleKey)) {
                // Defer to next tick and click the proceed button
                Minecraft.getInstance().tell(() -> {
                    Screen currentScreen = Minecraft.getInstance().screen;
                    if (currentScreen instanceof ConfirmScreen) {
                        // Find and click the proceed button using translation key
                        for (Object child : ((ConfirmScreen) currentScreen).children()) {
                            if (child instanceof net.minecraft.client.gui.components.Button button) {
                                String buttonKey = getTranslationKey(button.getMessage());
                                if (isProceedButtonKey(buttonKey)) {
                                    button.onPress();
                                    return;
                                }
                            }
                        }
                    }
                });
            }
        }
    }
}
