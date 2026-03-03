package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Mixin to remove the Resource Packs button from the Options screen.
 * This prevents players from using x-ray or other cheating resource packs
 * and also avoids the music reset issue when reloading resource packs.
 */
@Mixin(OptionsScreen.class)
public abstract class OptionsScreenMixin extends net.minecraft.client.gui.screens.Screen {

    protected OptionsScreenMixin(net.minecraft.network.chat.Component title) {
        super(title);
    }

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
     * After the screen is initialized, find and remove the Resource Packs button.
     * Uses translation key for language-independent identification.
     */
    @SuppressWarnings("null")
    @Inject(method = "init", at = @At("TAIL"))
    private void removeResourcePacksButton(CallbackInfo ci) {
        List<net.minecraft.client.gui.components.events.GuiEventListener> toRemove = new ArrayList<>();

        for (net.minecraft.client.gui.components.events.GuiEventListener child : this.children()) {
            if (child instanceof Button button) {
                String key = getTranslationKey(button.getMessage());
                // Match the Resource Packs button by translation key
                if ("options.resourcepack".equals(key)) {
                    toRemove.add(child);
                }
            }
        }

        // Remove the buttons
        for (net.minecraft.client.gui.components.events.GuiEventListener widget : toRemove) {
            this.removeWidget(widget);
        }
    }
}
