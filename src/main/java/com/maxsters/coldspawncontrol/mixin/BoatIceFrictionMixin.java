package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Prevents boats from using ice friction to reach insane speeds.
 *
 * In our frozen wasteland, all oceans are frozen solid. Without this fix,
 * players can grab a boat and zoom across the entire world at ~40 blocks/sec
 * on ice (friction 0.98) or ~70 blocks/sec on blue ice (friction 0.989).
 *
 * This mixin caps the friction value that boats receive from the ground,
 * effectively treating ice as normal ground for boats only. Players walking
 * on ice still experience the full slipperiness.
 *
 * Normal block friction: 0.6
 * Ice/Packed Ice: 0.98 -> capped to 0.6 for boats
 * Blue Ice: 0.989 -> capped to 0.6 for boats
 */
@Mixin(Boat.class)
public class BoatIceFrictionMixin {

    /**
     * Intercepts the ground friction value returned for the boat and caps it
     * to normal ground friction (0.6). This prevents ice from giving boats
     * any speed advantage over normal terrain.
     */
    @Inject(method = "getGroundFriction", at = @At("RETURN"), cancellable = true)
    private void capBoatFriction(CallbackInfoReturnable<Float> cir) {
        float friction = cir.getReturnValue();

        // If friction is higher than normal ground (0.6), it's an ice block.
        // Cap it to normal ground friction so boats can't cheese frozen oceans.
        if (friction > 0.6f) {
            cir.setReturnValue(0.6f);
        }
    }
}
