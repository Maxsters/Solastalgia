package com.maxsters.coldspawncontrol.mixin;

import com.maxsters.coldspawncontrol.event.AmnesiaBlackoutHandler;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.server.PlayerAdvancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(PlayerAdvancements.class)
public class PlayerAdvancementsMixin {

    /**
     * Redirects the call to DisplayInfo.shouldAnnounceChat() in the award() method.
     * Prevents chat announcements when we are resetting/re-granting advancements.
     */
    @Redirect(method = "award(Lnet/minecraft/advancements/Advancement;Ljava/lang/String;)Z", at = @At(value = "INVOKE", target = "Lnet/minecraft/advancements/DisplayInfo;shouldAnnounceChat()Z"))
    private boolean suppressChatIfResetting(DisplayInfo instance) {
        if (AmnesiaBlackoutHandler.isResetting) {
            return false;
        }
        return instance.shouldAnnounceChat();
    }
}
