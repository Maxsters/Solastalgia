package com.maxsters.coldspawncontrol.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Biome.class)
public class BiomeMixin {

    /**
     * Intercepts the check for whether it is cold enough to snow.
     * When Realistic Mode is ON, we force this to return FALSE.
     * This prevents:
     * 1. Vanilla snow accumulation (tickChunk).
     * 2. Ice formation (tickChunk).
     * 3. Custom snow accumulation (ServerLevelMixin).
     * 
     * This ensures the "Still World" (no falling snow accumulation) effect.
     * World Generation (FrozenWorldFeature) is unaffected as it bypasses this
     * check.
     */
    @Inject(method = "coldEnoughToSnow", at = @At("HEAD"), cancellable = true)
    private void solastalgia$interceptColdCheck(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        // If Realistic Mode is ON, we simulate a "too cold for snow" / "frozen solid"
        // state
        // regarding precipitation accumulation.
        if (AccessGameRules()) {
            ;
            cir.setReturnValue(false);
        }
    }

    @SuppressWarnings("null")
    private boolean AccessGameRules() {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            // Dedicated Server or Single Player (Server+Client)
            // It is generally safe to read GameRules from any thread in SP as boolean read
            // is atomic.
            return server.getGameRules()
                    .getBoolean(com.maxsters.coldspawncontrol.config.ModGameRules.RULE_REALISTIC_MODE);
        }
        // Client connecting to remote server -> Use synced state
        return com.maxsters.coldspawncontrol.client.ClientRealisticModeState.isRealisticModeEnabled;
    }
}
