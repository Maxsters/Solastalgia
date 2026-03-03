package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.mojang.blaze3d.vertex.PoseStack;

/**
 * Hides the "Sign" button on BookEditScreen when the writable book
 * has the {@code solastalgia_journal} NBT tag. Centers the Done button
 * since the Sign button is removed.
 *
 * Uses render injection to continuously enforce hidden state, since
 * BookEditScreen.updateButtonVisibility() re-shows buttons on page change.
 *
 * Uses translation keys for language-safe button detection.
 */
@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin extends net.minecraft.client.gui.screens.Screen {

    @Shadow
    @Final
    private InteractionHand hand;

    /** Cached result so we don't re-check NBT every frame. */
    @Unique
    private Boolean solastalgia_isJournal = null;

    /** Cached reference to the sign button. */
    @Unique
    private Button solastalgia_signButton = null;

    /** Cached reference to the done button. */
    @Unique
    private Button solastalgia_doneButton = null;

    protected BookEditScreenMixin(Component title) {
        super(title);
    }

    /**
     * Gets the translation key from a Component, or null if not translatable.
     */
    @Unique
    private static String solastalgia_getTranslationKey(Component component) {
        if (component != null && component.getContents() instanceof TranslatableContents) {
            return ((TranslatableContents) component.getContents()).getKey();
        }
        return null;
    }

    /**
     * After init, locate and cache the Sign and Done buttons.
     */
    @SuppressWarnings("null")
    @Inject(method = "init", at = @At("TAIL"))
    private void solastalgia_cacheButtons(CallbackInfo ci) {
        // Check if held book is our journal
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null) {
            solastalgia_isJournal = false;
            return;
        }

        ItemStack heldBook = mc.player.getItemInHand(this.hand);
        CompoundTag tag = heldBook.isEmpty() ? null : heldBook.getTag();
        solastalgia_isJournal = tag != null && tag.getBoolean("solastalgia_journal");

        if (!solastalgia_isJournal)
            return;

        // Find and cache Sign + Done buttons by translation key
        solastalgia_signButton = null;
        solastalgia_doneButton = null;

        for (net.minecraft.client.gui.components.events.GuiEventListener child : this.children()) {
            if (child instanceof Button button) {
                String key = solastalgia_getTranslationKey(button.getMessage());
                if ("book.signButton".equals(key)) {
                    solastalgia_signButton = button;
                } else if ("gui.done".equals(key)) {
                    solastalgia_doneButton = button;
                }
            }
        }

        // Apply immediately
        solastalgia_enforceJournalButtons();
    }

    /**
     * On every render tick, re-enforce the hidden Sign button and centered Done.
     * This catches updateButtonVisibility() which runs on page changes.
     */
    @Inject(method = "render", at = @At("HEAD"))
    private void solastalgia_enforceOnRender(PoseStack poseStack, int mouseX, int mouseY, float partialTick,
            CallbackInfo ci) {
        if (solastalgia_isJournal != null && solastalgia_isJournal) {
            solastalgia_enforceJournalButtons();
        }
    }

    /**
     * Hides the Sign button and centers + widens the Done button
     * to match the signed book screen's Done button (200px).
     */
    @Unique
    private void solastalgia_enforceJournalButtons() {
        if (solastalgia_signButton != null) {
            solastalgia_signButton.visible = false;
            solastalgia_signButton.active = false;
        }
        if (solastalgia_doneButton != null) {
            // Match BookViewScreen's Done button width (200px)
            solastalgia_doneButton.setWidth(200);
            // Center horizontally
            solastalgia_doneButton.x = (this.width - 200) / 2;
        }
    }
}
