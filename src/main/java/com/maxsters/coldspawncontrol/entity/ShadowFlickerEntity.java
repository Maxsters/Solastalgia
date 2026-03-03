package com.maxsters.coldspawncontrol.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * An invisible entity that exists solely to cast a shadow in player peripheral
 * vision.
 * Used for the "Shadow Flicker" paranoia effect.
 * 
 * It has no AI (stands in place), no model (custom renderer), and
 * despawns after 4 seconds.
 * Completely non-interactable - cannot be hit, targeted, or collided with.
 */
@SuppressWarnings("null")
public class ShadowFlickerEntity extends PathfinderMob {

    private int lifeTicks = 0;
    private static final int MAX_LIFE = 80; // 4 seconds

    private BlockPos spawnPos;
    private boolean hasPlayedSound = false;

    public ShadowFlickerEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setSilent(true);
        this.setInvulnerable(true);
        this.setNoAi(true); // No movement - just stand in place
        this.noPhysics = true; // No physics needed
    }

    @Override
    protected void registerGoals() {
        // No AI goals - entity stands in place and despawns after 4 seconds
    }

    @Override
    public void setPos(double x, double y, double z) {
        super.setPos(x, y, z);
        // Remember spawn position on first placement
        if (this.spawnPos == null) {
            this.spawnPos = new BlockPos(x, y, z);
        }
    }

    public BlockPos getSpawnPos() {
        return spawnPos != null ? spawnPos : this.blockPosition();
    }

    // === Make completely non-interactable ===

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void pushEntities() {
        // Do nothing - don't push other entities
    }

    @Override
    public boolean isPickable() {
        // Not pickable by mouse/crosshair - prevents hotbar indicator
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity attacker) {
        // Skip all attack interactions
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Cannot be hurt at all
        return false;
    }

    @Override
    public boolean isAttackable() {
        // Not attackable
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        // No collision detection
        return false;
    }

    @Override
    protected void doPush(Entity entity) {
        // Don't push or be pushed
    }

    @Override
    public void playerTouch(Player player) {
        // No interaction on player touch
    }

    @Override
    public boolean isInvisibleTo(Player player) {
        // Invisible to all players - this should make mods like Jade skip it
        return true;
    }

    // === Attributes ===

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D); // Stationary
    }

    @Override
    public void tick() {
        super.tick();

        // Play appearance sound CLIENT-SIDE on first tick safely
        if (this.level.isClientSide && !hasPlayedSound) {
            hasPlayedSound = true;
            this.level.playLocalSound(
                    this.getX(), this.getY(), this.getZ(),
                    com.maxsters.coldspawncontrol.registry.ModSoundEvents.SHADOW_FLICKER_APPEARANCE.get(),
                    net.minecraft.sounds.SoundSource.AMBIENT,
                    0.25F,
                    0.9F + this.random.nextFloat() * 0.2F,
                    false);
            com.maxsters.coldspawncontrol.ColdSpawnControl.LOGGER.info(
                    "[ShadowFlicker] Playing appearance sound at ({}, {}, {})", this.getX(), this.getY(), this.getZ());
        }

        // Lifetime management SERVER-SIDE
        if (!this.level.isClientSide) {
            lifeTicks++;
            if (lifeTicks > MAX_LIFE) {
                this.discard();
            }
        }
    }

    // === Synced Data for Target Player ===

    private static final net.minecraft.network.syncher.EntityDataAccessor<java.util.Optional<java.util.UUID>> TARGET_PLAYER = net.minecraft.network.syncher.SynchedEntityData
            .defineId(ShadowFlickerEntity.class, net.minecraft.network.syncher.EntityDataSerializers.OPTIONAL_UUID);

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(TARGET_PLAYER, java.util.Optional.empty());
    }

    public void setTargetPlayer(java.util.UUID uuid) {
        this.entityData.set(TARGET_PLAYER, java.util.Optional.ofNullable(uuid));
    }

    public java.util.UUID getTargetPlayer() {
        return this.entityData.get(TARGET_PLAYER).orElse(null);
    }

}
