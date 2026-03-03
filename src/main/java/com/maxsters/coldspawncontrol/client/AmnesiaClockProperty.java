package com.maxsters.coldspawncontrol.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.Random;

/**
 * Custom property override for the Clock item.
 * Mimics the "Nether" behavior (random spinning) at all times,
 * regardless of dimension or daylight cycle.
 */
public class AmnesiaClockProperty implements ClampedItemPropertyFunction {
    private static final Random RANDOM = new Random();

    private double rotation;
    private double rota;
    private long lastUpdateTick;

    @Override
    public float unclampedCall(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
        long gameTime = level != null ? level.getGameTime() : 0;

        if (lastUpdateTick != gameTime) {
            lastUpdateTick = gameTime;

            // Pick a random target rotation (0.0 to 1.0)
            // Vanilla logic uses a hash of the seed, but true random is more chaotic
            double target = RANDOM.nextDouble();

            // Calculate distance to target
            double diff = target - this.rotation;

            // Wrap around circle logic
            diff = Mth.positiveModulo(diff + 0.5D, 1.0D) - 0.5D;

            // Add momentum
            this.rota += diff * 0.1D;

            // Apply damping/friction
            this.rota *= 0.8D;

            // Apply rotation
            this.rotation = Mth.positiveModulo(this.rotation + this.rota, 1.0D);
        }

        return (float) this.rotation;
    }
}
