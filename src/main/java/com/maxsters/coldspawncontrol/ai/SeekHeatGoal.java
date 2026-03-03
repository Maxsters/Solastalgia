package com.maxsters.coldspawncontrol.ai;

import com.maxsters.coldspawncontrol.util.HeatSourceRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;
import java.util.ArrayList;

@SuppressWarnings("null")
public class SeekHeatGoal extends Goal {
    private final PathfinderMob mob;
    private final double speedModifier;
    private double targetX;
    private double targetY;
    private double targetZ;
    private int searchCooldown;
    private boolean isPanicking; // Track if mob is in freeze panic mode

    public SeekHeatGoal(PathfinderMob mob, double speedModifier) {
        this.mob = mob;
        this.speedModifier = speedModifier;
        // Start with basic flags - will add TARGET dynamically when panicking
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.mob.isNoAi())
            return false;

        // Check if freezing - this is URGENT and bypasses cooldown
        boolean isFreezing = this.mob.getTicksFrozen() > 0;

        // If freezing, NO COOLDOWN - react immediately!
        if (!isFreezing) {
            // Cooldown only applies when not actively freezing
            if (this.searchCooldown > 0) {
                this.searchCooldown--;
                return false;
            }
            this.searchCooldown = 20; // Check every second when not freezing
        }

        // Trigger if freezing OR in danger (snow/sky)
        // Deep Underground Exception: If Y < 32, ONLY trigger if Freezing
        boolean inSnow = this.mob.level.getBlockState(this.mob.blockPosition()).is(Blocks.SNOW) ||
                this.mob.level.getBlockState(this.mob.blockPosition()).is(Blocks.SNOW_BLOCK) ||
                this.mob.level.getBlockState(this.mob.blockPosition()).is(Blocks.POWDER_SNOW);
        boolean underSky = this.mob.level.canSeeSky(this.mob.blockPosition());
        boolean isDeepUnderground = this.mob.getY() < 32;

        boolean shouldTrigger = isFreezing || (!isDeepUnderground && (inSnow || underSky));

        if (!shouldTrigger) {
            return false;
        }

        // Update panic state - freezing mobs are in PANIC mode
        this.isPanicking = isFreezing;

        // Only claim TARGET flag when actually panicking (freezing) — this prevents
        // SeekHeatGoal from blocking attack goals during normal surface avoidance.
        // Without this fix, mobs on the surface would never attack because this
        // priority-0 goal permanently claimed MOVE+LOOK, starving attack goals.
        if (this.isPanicking) {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
            // IMMEDIATELY clear any existing target - survival is priority!
            if (this.mob.getTarget() != null) {
                this.mob.setTarget(null);
            }
        } else {
            // Non-panic: only claim MOVE — allow LOOK and TARGET for attack goals
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        // Find target
        Vec3 target = findTarget();
        if (target == null)
            return false;

        this.targetX = target.x;
        this.targetY = target.y;
        this.targetZ = target.z;
        return true;
    }

    @Override
    public void tick() {
        // Only clear target when PANICKING (freezing) - survival over aggression
        // Non-panic mobs should still be able to attack while seeking shelter
        boolean isFreezing = this.mob.getTicksFrozen() > 0;
        if (isFreezing) {
            this.isPanicking = true;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
            if (this.mob.getTarget() != null) {
                this.mob.setTarget(null);
            }
        }
    }

    @Override
    public void start() {
        // Clear target on start
        if (this.mob.getTarget() != null) {
            this.mob.setTarget(null);
        }
        // Use faster speed when panicking (1.25x)
        double speed = this.isPanicking ? this.speedModifier * 1.25 : this.speedModifier;
        this.mob.getNavigation().moveTo(this.targetX, this.targetY, this.targetZ, speed);
    }

    @Override
    public boolean canContinueToUse() {
        return !this.mob.getNavigation().isDone();
    }

    private Vec3 findTarget() {
        try {
            BlockPos mobPos = this.mob.blockPosition();
            Level level = this.mob.level;
            int radius = 15; // Reduced from 30 - cuts search volume by 75%

            // 1. Search for Heat Sources (spiral outward for early exit)
            BlockPos bestHeat = null;
            double maxHeat = 0.0;
            double minDistSq = Double.MAX_VALUE;
            BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

            // Search in expanding shells for early exit opportunity
            for (int r = 0; r <= radius; r++) {
                for (int y = -10; y <= 10; y++) {
                    // Only check the shell at distance r (not inner blocks again)
                    for (int x = -r; x <= r; x++) {
                        for (int z = -r; z <= r; z++) {
                            // Skip inner blocks (already checked in previous shells)
                            if (r > 0 && Math.abs(x) < r && Math.abs(z) < r)
                                continue;

                            mutablePos.set(mobPos.getX() + x, mobPos.getY() + y, mobPos.getZ() + z);
                            double heat = HeatSourceRegistry.getHeatValue(level, mutablePos);
                            if (heat > 0) {
                                double distSq = mutablePos.distSqr(mobPos);
                                if (heat > maxHeat || (heat == maxHeat && distSq < minDistSq)) {
                                    maxHeat = heat;
                                    minDistSq = distSq;
                                    bestHeat = mutablePos.immutable();

                                    // Early exit: if we found a strong heat source nearby, stop searching
                                    if (heat >= 5.0 && distSq < 25) { // Within 5 blocks
                                        break;
                                    }
                                }
                            }
                        }
                        if (bestHeat != null && maxHeat >= 5.0 && minDistSq < 25)
                            break;
                    }
                    if (bestHeat != null && maxHeat >= 5.0 && minDistSq < 25)
                        break;
                }
                if (bestHeat != null && maxHeat >= 5.0 && minDistSq < 25)
                    break;
            }

            if (bestHeat != null) {
                // Found heat! Move near it (not inside)
                // Always try to find a safe spot adjacent to the heat source
                for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
                    BlockPos adj = bestHeat.relative(dir);
                    // Check if adjacent spot is air (walkable) and has a solid floor
                    if (level.getBlockState(adj).isAir()
                            && level.getBlockState(adj.below()).isRedstoneConductor(level, adj.below())) {
                        return Vec3.atBottomCenterOf(adj);
                    }
                }
                // If no adjacent spot found, fallback to the heat source itself (might be
                // walkable like campfire)
                return Vec3.atBottomCenterOf(bestHeat);
            }

            // 2. Fallback: Look for ROOFED spots only (caves/buildings)
            // Mobs will NOT go to open-sky areas - staying put is better than going outside

            List<BlockPos> roofedSpots = new ArrayList<>();

            // Scan random positions - look for roofed spots
            for (int i = 0; i < 20; i++) { // Reduced from 40 - most good spots found early
                Vec3 vec = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPos(this.mob, 15, 10);
                if (vec != null) {
                    BlockPos pos = new BlockPos(vec);

                    // Skip dangerous spots, snow, and fluids
                    if (com.maxsters.coldspawncontrol.ai.DangerZoneManager.isDangerous(level, pos) ||
                            level.getBlockState(pos).is(Blocks.SNOW) ||
                            level.getBlockState(pos).is(Blocks.SNOW_BLOCK) ||
                            level.getBlockState(pos).is(Blocks.POWDER_SNOW) ||
                            level.getFluidState(pos).isSource() ||
                            pos.distSqr(mobPos) <= 4) {
                        continue;
                    }

                    // Only accept roofed spots
                    if (!level.canSeeSky(pos)) {
                        roofedSpots.add(pos);
                    }
                }
            }

            // If we found roofed spots, pick randomly from them to prevent clumping
            if (!roofedSpots.isEmpty()) {
                return Vec3.atBottomCenterOf(roofedSpots.get(this.mob.getRandom().nextInt(roofedSpots.size())));
            }

            // No roofed spot found - return null and let the mob stay put
            // Going to open-sky areas would make things worse, not better
        } catch (Exception e) {
            // Prevent AI crash
        }
        return null;
    }
}
