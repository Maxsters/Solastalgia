package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.world.Difficulty;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Forces hardcore mode in world creation.
 * Immersion: if you die, you stay dead. No respawns.
 * Cheats button remains available for testing purposes.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
@Mixin(CreateWorldScreen.class)
public abstract class CreateWorldScreenMixin extends Screen {
    @Shadow
    private CycleButton<Difficulty> difficultyButton;

    @Shadow
    private CycleButton modeButton; // Use raw type to avoid package-private enum

    protected CreateWorldScreenMixin(Component title) {
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

    @Inject(method = "init", at = @At("TAIL"))
    private void forceHardcoreMode(CallbackInfo ci) {
        // Force Hardcore mode by finding the HARDCORE enum value
        if (this.modeButton != null) {
            Object hardcoreMode = findHardcoreMode();
            if (hardcoreMode != null) {
                this.modeButton.setValue(hardcoreMode);
            }
            this.modeButton.active = false; // Lock it
        }
        // Force Hard difficulty
        if (this.difficultyButton != null) {
            this.difficultyButton.setValue(Difficulty.HARD);
            this.difficultyButton.active = false;
        }
    }

    @SuppressWarnings("null")
    @Inject(method = "render", at = @At("HEAD"))
    private void enforceHardcoreOnRender(PoseStack poseStack, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        // Constantly enforce hardcore mode
        if (this.modeButton != null) {
            Object hardcoreMode = findHardcoreMode();
            Object currentMode = this.modeButton.getValue();
            if (hardcoreMode != null && !hardcoreMode.equals(currentMode)) {
                this.modeButton.setValue(hardcoreMode);
            }
            this.modeButton.active = false;
        }
        // Enforce hard difficulty
        if (this.difficultyButton != null) {
            if (this.difficultyButton.getValue() != Difficulty.HARD) {
                this.difficultyButton.setValue(Difficulty.HARD);
            }
            this.difficultyButton.active = false;
        }
        // Handle UI buttons using translation keys (language-independent)
        CreateWorldScreen screen = (CreateWorldScreen) (Object) this;
        for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                String key = getTranslationKey(widget.getMessage());
                if (key != null) {
                    // Keep Allow Commands button visible and enabled
                    if (key.equals("selectWorld.allowCommands")) {
                        widget.active = true;
                        widget.visible = true;
                    }
                    // Hide buttons that could interfere with hardcore mode
                    if (key.equals("selectWorld.moreWorldOptions")) {
                        widget.active = false;
                        widget.visible = false;
                    }
                    if (key.equals("selectWorld.gameRules")) {
                        widget.active = false;
                        widget.visible = false;
                    }
                    if (key.equals("selectWorld.dataPacks")) {
                        widget.active = false;
                        widget.visible = false;
                    }
                }
            }
        }
    }

    /**
     * Finds the HARDCORE enum value using reflection to avoid package-private
     * access issues.
     */
    private Object findHardcoreMode() {
        try {
            // Get the SelectedGameMode enum class from within CreateWorldScreen
            for (Class<?> declaredClass : CreateWorldScreen.class.getDeclaredClasses()) {
                if (declaredClass.isEnum() && declaredClass.getSimpleName().equals("SelectedGameMode")) {
                    for (Object enumConstant : declaredClass.getEnumConstants()) {
                        if (enumConstant.toString().equals("HARDCORE")
                                || enumConstant.toString().contains("HARDCORE")) {
                            return enumConstant;
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore and return null
        }
        return null;
    }
}