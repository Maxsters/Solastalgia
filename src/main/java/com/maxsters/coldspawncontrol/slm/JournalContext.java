package com.maxsters.coldspawncontrol.slm;

import com.maxsters.coldspawncontrol.util.InsulationCalculator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot of the player's state and surroundings,
 * captured at the moment SLM generation is triggered (10 seconds before
 * blackout).
 * All fields are simple types — no MC object references retained.
 */
@SuppressWarnings("null")
public final class JournalContext {

    // ==================== LOCATION ====================
    public final String dimension; // "overworld", "the_nether", "the_end"
    public final int yLevel;
    public final String biomeName;
    public final boolean isInCave; // no sky access

    // ==================== ENVIRONMENT ====================
    public final int lightLevel;
    public final boolean nearLava; // within 8 blocks
    public final boolean nearWater;
    public final boolean nearFire; // campfire, furnace, smoker, etc.
    public final boolean nearOres; // iron, gold, diamond, etc.
    public final boolean nearChests;
    public final boolean nearSpawner;
    public final List<String> nearbyMobTypes; // e.g. ["zombie", "skeleton"]
    public final int nearbyMobCount;

    // ==================== PLAYER STATE ====================
    public final float health; // 0-20
    public final int hunger; // 0-20
    public final double coreTemperature; // Cold Sweat internal units
    public final double insulationScore; // 0.0-1.0+
    public final List<String> wornArmor; // Names of equipped armor items

    // ==================== INVENTORY SUMMARY ====================
    public final int toolCount;
    public final int foodCount;
    public final int blockCount;
    public final int torchCount;
    public final boolean hasWeapon;

    private JournalContext(Builder b) {
        this.dimension = b.dimension;
        this.yLevel = b.yLevel;
        this.biomeName = b.biomeName;
        this.isInCave = b.isInCave;
        this.lightLevel = b.lightLevel;
        this.nearLava = b.nearLava;
        this.nearWater = b.nearWater;
        this.nearFire = b.nearFire;
        this.nearOres = b.nearOres;
        this.nearChests = b.nearChests;
        this.nearSpawner = b.nearSpawner;
        this.nearbyMobTypes = List.copyOf(b.nearbyMobTypes);
        this.nearbyMobCount = b.nearbyMobCount;
        this.health = b.health;
        this.hunger = b.hunger;
        this.coreTemperature = b.coreTemperature;
        this.insulationScore = b.insulationScore;
        this.wornArmor = List.copyOf(b.wornArmor);
        this.toolCount = b.toolCount;
        this.foodCount = b.foodCount;
        this.blockCount = b.blockCount;
        this.torchCount = b.torchCount;
        this.hasWeapon = b.hasWeapon;
    }

    /**
     * Captures a full context snapshot from the player and world.
     * Safe to call on the server thread.
     */
    public static JournalContext capture(ServerPlayer player, ServerLevel level) {
        Builder b = new Builder();
        BlockPos pos = player.blockPosition();

        // Location
        ResourceLocation dimKey = level.dimension().location();
        b.dimension = dimKey.getPath(); // "overworld", "the_nether", etc.
        b.yLevel = pos.getY();
        try {
            b.biomeName = level.getBiome(pos).value().toString();
        } catch (Exception e) {
            b.biomeName = "unknown";
        }
        b.isInCave = !level.canSeeSky(pos);

        // Environment scan (8-block radius for blocks, 16 for mobs)
        b.lightLevel = level.getMaxLocalRawBrightness(pos);
        scanNearbyBlocks(level, pos, 8, b);
        scanNearbyMobs(level, pos, 16, b);

        // Player state
        b.health = player.getHealth();
        b.hunger = player.getFoodData().getFoodLevel();

        try {
            b.coreTemperature = com.momosoftworks.coldsweat.api.util.Temperature.get(
                    player, com.momosoftworks.coldsweat.api.util.Temperature.Trait.CORE);
        } catch (Exception e) {
            b.coreTemperature = 0.0;
        }
        b.insulationScore = calculateInsulationScore(player);

        // Capture worn armor
        for (ItemStack stack : player.getInventory().armor) {
            if (!stack.isEmpty()) {
                b.wornArmor.add(stack.getHoverName().getString());
            }
        }

        // Inventory summary
        scanInventory(player, b);

        return new JournalContext(b);
    }

    // ==================== SCANNING HELPERS ====================

    private static void scanNearbyBlocks(ServerLevel level, BlockPos center, int radius, Builder b) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos check = center.offset(dx, dy, dz);
                    if (!level.isLoaded(check))
                        continue;

                    BlockState state = level.getBlockState(check);

                    if (state.is(Blocks.LAVA))
                        b.nearLava = true;
                    else if (state.is(Blocks.WATER) || state.is(Blocks.ICE)
                            || state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE))
                        b.nearWater = true;
                    else if (state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE)
                            || state.is(Blocks.CAMPFIRE) || state.is(Blocks.SOUL_CAMPFIRE)
                            || state.is(Blocks.FURNACE) || state.is(Blocks.BLAST_FURNACE)
                            || state.is(Blocks.SMOKER))
                        b.nearFire = true;
                    else if (state.is(Blocks.IRON_ORE) || state.is(Blocks.DEEPSLATE_IRON_ORE)
                            || state.is(Blocks.GOLD_ORE) || state.is(Blocks.DEEPSLATE_GOLD_ORE)
                            || state.is(Blocks.DIAMOND_ORE) || state.is(Blocks.DEEPSLATE_DIAMOND_ORE)
                            || state.is(Blocks.COAL_ORE) || state.is(Blocks.DEEPSLATE_COAL_ORE))
                        b.nearOres = true;
                    else if (state.is(Blocks.CHEST) || state.is(Blocks.BARREL))
                        b.nearChests = true;
                    else if (state.is(Blocks.SPAWNER))
                        b.nearSpawner = true;
                }
            }
        }
    }

    private static void scanNearbyMobs(ServerLevel level, BlockPos center, int radius, Builder b) {
        List<LivingEntity> mobs = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(center).inflate(radius),
                e -> e instanceof Monster);

        // Deduplicate mob type names
        Map<String, Integer> typeCounts = new HashMap<>();
        for (LivingEntity mob : mobs) {
            String name = mob.getType().getDescriptionId()
                    .replace("entity.minecraft.", "")
                    .replace("entity.", "");
            typeCounts.merge(name, 1, Integer::sum);
        }

        b.nearbyMobTypes = new ArrayList<>(typeCounts.keySet());
        b.nearbyMobCount = mobs.size();
    }

    private static void scanInventory(ServerPlayer player, Builder b) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty())
                continue;

            String id = stack.getItem().getDescriptionId().toLowerCase();

            if (id.contains("sword") || id.contains("axe") || id.contains("bow") || id.contains("crossbow")
                    || id.contains("trident")) {
                b.hasWeapon = true;
            }
            if (id.contains("pickaxe") || id.contains("axe") || id.contains("shovel")
                    || id.contains("hoe")) {
                b.toolCount++;
            }
            if (stack.getItem().isEdible()) {
                b.foodCount += stack.getCount();
            }
            if (id.contains("torch") || id.contains("lantern")) {
                b.torchCount += stack.getCount();
            }
            if (stack.getItem() instanceof net.minecraft.world.item.BlockItem) {
                b.blockCount += stack.getCount();
            }
        }
    }

    private static double calculateInsulationScore(ServerPlayer player) {
        return InsulationCalculator.calculateInsulation(player);
    }

    // ==================== BUILDER ====================

    private static class Builder {
        String dimension = "overworld";
        int yLevel = 64;
        String biomeName = "unknown";
        boolean isInCave = false;
        int lightLevel = 0;
        boolean nearLava = false;
        boolean nearWater = false;
        boolean nearFire = false;
        boolean nearOres = false;
        boolean nearChests = false;
        boolean nearSpawner = false;
        List<String> nearbyMobTypes = new ArrayList<>();
        int nearbyMobCount = 0;
        float health = 20f;
        int hunger = 20;
        double coreTemperature = 0.0;
        double insulationScore = 0.0;
        List<String> wornArmor = new ArrayList<>();
        int toolCount = 0;
        int foodCount = 0;
        int blockCount = 0;
        int torchCount = 0;
        boolean hasWeapon = false;
    }
}
