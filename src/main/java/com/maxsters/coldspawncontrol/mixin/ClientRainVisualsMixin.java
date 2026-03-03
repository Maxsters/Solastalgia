package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class ClientRainVisualsMixin {

    /**
     * Forces the client to believe it is not raining, ensuring a clear sky (visible
     * stars).
     * Server side still processes rain for snow accumulation logic.
     */
    @SuppressWarnings("resource")
    @Inject(method = "getRainLevel", at = @At("HEAD"), cancellable = true)
    private void solastalgia$forceClearSky(float partialTick, CallbackInfoReturnable<Float> cir) {
        Level level = (Level) (Object) this;
        // Check Client Config for Cloud Coverage rendering
        // If renderCloudCoverage is FALSE (default), we act to hide the clouds/darkness
        // by reporting 0 rain.
        // If TRUE, we do nothing and let Vanilla logic render clouds.
        if (level.isClientSide && !com.maxsters.coldspawncontrol.client.ClientRealisticModeState.renderCloudCoverage) {
            cir.setReturnValue(0.0f);
        }
    }
}
