package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.client.gui.screens.ShareToLanScreen;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.mojang.blaze3d.vertex.PoseStack;

@Mixin(ShareToLanScreen.class)
public class ShareToLanScreenMixin {

    /**
     * Gets the translation key from a Component, or null if not translatable.
     */
    private static String getTranslationKey(Component component) {
        if (component != null && component.getContents() instanceof TranslatableContents) {
            return ((TranslatableContents) component.getContents()).getKey();
        }
        return null;
    }

    /**
     * Recursively checks if a Component or its arguments contains a specific
     * translation key.
     */
    @SuppressWarnings("null")
    private static boolean containsKey(Component component, String targetKeyFragment) {
        boolean found = false;

        if (component != null) {
            String key = getTranslationKey(component);
            if (key != null && key.contains(targetKeyFragment)) {
                found = true;
            }

            if (!found && component.getContents() instanceof TranslatableContents) {
                TranslatableContents tc = (TranslatableContents) component.getContents();
                Object[] args = tc.getArgs();
                for (int i = 0; i < args.length && !found; i++) {
                    Object arg = args[i];
                    if (arg instanceof Component) {
                        found = containsKey((Component) arg, targetKeyFragment);
                    }
                }
            }

            if (!found) {
                for (Component sibling : component.getSiblings()) {
                    if (containsKey(sibling, targetKeyFragment)) {
                        found = true;
                        break;
                    }
                }
            }
        }

        return found;
    }

    /**
     * Checks if a button should be hidden on the LAN screen.
     */
    private static boolean shouldHideButton(Component component) {
        // Check for specific keys known to be used for these buttons
        // selectWorld.gameMode, selectWorld.allowCommands, etc.
        return component != null && (containsKey(component, "gameMode") ||
                containsKey(component, "allowCheats") ||
                containsKey(component, "allowCommands"));
    }

    /**
     * Checks if a label should be hidden on the LAN screen.
     */
    private static boolean shouldHideLabel(Component component) {
        return component != null && containsKey(component, "otherPlayers");
    }

    // Hide Game Mode and Allow Cheats buttons on render
    @SuppressWarnings("null")
    @Inject(method = "render", at = @At("HEAD"))
    private void hideButtonsOnRender(PoseStack poseStack, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ShareToLanScreen screen = (ShareToLanScreen) (Object) this;

        for (net.minecraft.client.gui.components.events.GuiEventListener child : screen.children()) {
            if (child instanceof net.minecraft.client.gui.components.AbstractWidget) {
                net.minecraft.client.gui.components.AbstractWidget widget = (net.minecraft.client.gui.components.AbstractWidget) child;
                Component msg = widget.getMessage();

                if (shouldHideButton(msg)) {
                    widget.active = false;
                    widget.visible = false;
                }
            }
        }
    }

    // Redirect drawCenteredString to hide the "Settings for Other Players" label
    // Try multiple targets (GuiComponent, Screen, ShareToLanScreen) to catch the
    // static method call

    // Target: GuiComponent (defining class)
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiComponent;drawCenteredString(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"), require = 0)
    private void hideLabelGuiComponent(PoseStack poseStack, Font font, Component component, int x, int y, int color) {
        if (shouldHideLabel(component)) {
            return;
        }
        GuiComponent.drawCenteredString(poseStack, font, component, x, y, color);
    }

    // Target: Screen (parent class)
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/Screen;drawCenteredString(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"), require = 0)
    private void hideLabelScreen(PoseStack poseStack, Font font, Component component, int x, int y, int color) {
        if (shouldHideLabel(component)) {
            return;
        }
        GuiComponent.drawCenteredString(poseStack, font, component, x, y, color);
    }

    // Target: ShareToLanScreen (calling class) - This was the one causing crash
    // before due to wrong signature, but target likely matches
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screens/ShareToLanScreen;drawCenteredString(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"), require = 0)
    private void hideLabelSelf(PoseStack poseStack, Font font, Component component, int x, int y, int color) {
        if (shouldHideLabel(component)) {
            return;
        }
        GuiComponent.drawCenteredString(poseStack, font, component, x, y, color);
    }
}
