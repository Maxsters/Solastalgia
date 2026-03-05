package com.maxsters.coldspawncontrol.client;

import com.maxsters.coldspawncontrol.init.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns rare, glowing star particles on ore blocks and valuable mineral
 * blocks to help players find them in pitch-black caves. Client-side only.
 *
 * More valuable ores emit more particles, allowing players to gauge
 * value from the sparkle density alone.
 */
@Mod.EventBusSubscriber(modid = "solastalgia", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
@SuppressWarnings("null")
public final class OreParticleHandler {

    private static final int SCAN_RADIUS = 16;
    private static final int SCAN_VERTICAL = 8;
    private static final int BLOCKS_PER_TICK = 60;

    // Particle multipliers — higher = more particles = visually "richer"
    private static final int TIER_LOW = 1; // Coal, copper
    private static final int TIER_MEDIUM = 2; // Iron, redstone, lapis, quartz, amethyst
    private static final int TIER_HIGH = 3; // Gold, emerald
    private static final int TIER_PRECIOUS = 4; // Diamond, ancient debris, nether star blocks
    private static final int TIER_MODDED = 3; // Scaling Health crystals

    private static Map<Block, Integer> GLINT_BLOCKS;

    private OreParticleHandler() {
    }

    private static Map<Block, Integer> getGlintBlocks() {
        if (GLINT_BLOCKS == null) {
            Map<Block, Integer> map = new HashMap<>();

            // Tier LOW — common ores
            map.put(Blocks.RAW_COPPER_BLOCK, TIER_LOW);
            map.put(Blocks.COPPER_BLOCK, TIER_LOW);

            // Tier MEDIUM — mid-value
            map.put(Blocks.RAW_IRON_BLOCK, TIER_MEDIUM);
            map.put(Blocks.IRON_BLOCK, TIER_MEDIUM);
            map.put(Blocks.REDSTONE_BLOCK, TIER_MEDIUM);
            map.put(Blocks.LAPIS_BLOCK, TIER_MEDIUM);
            map.put(Blocks.NETHER_QUARTZ_ORE, TIER_MEDIUM);
            map.put(Blocks.AMETHYST_CLUSTER, TIER_MEDIUM);
            map.put(Blocks.LARGE_AMETHYST_BUD, TIER_MEDIUM);
            map.put(Blocks.MEDIUM_AMETHYST_BUD, TIER_LOW);
            map.put(Blocks.SMALL_AMETHYST_BUD, TIER_LOW);
            map.put(Blocks.AMETHYST_BLOCK, TIER_MEDIUM);
            map.put(Blocks.BUDDING_AMETHYST, TIER_MEDIUM);

            // Tier HIGH — valuable
            map.put(Blocks.RAW_GOLD_BLOCK, TIER_HIGH);
            map.put(Blocks.GOLD_BLOCK, TIER_HIGH);
            map.put(Blocks.NETHER_GOLD_ORE, TIER_HIGH);
            map.put(Blocks.EMERALD_BLOCK, TIER_HIGH);

            // Tier PRECIOUS — top value
            map.put(Blocks.DIAMOND_BLOCK, TIER_PRECIOUS);
            map.put(Blocks.ANCIENT_DEBRIS, TIER_PRECIOUS);

            // Modded blocks
            addModdedBlock(map, "scalinghealth:heart_crystal_ore", TIER_MODDED);
            addModdedBlock(map, "scalinghealth:deepslate_heart_crystal_ore", TIER_MODDED);
            addModdedBlock(map, "scalinghealth:power_crystal_ore", TIER_MODDED);
            addModdedBlock(map, "scalinghealth:deepslate_power_crystal_ore", TIER_MODDED);

            GLINT_BLOCKS = map;
        }
        return GLINT_BLOCKS;
    }

    private static void addModdedBlock(Map<Block, Integer> map, String id, int tier) {
        net.minecraft.resources.ResourceLocation key = new net.minecraft.resources.ResourceLocation(id);
        Block block = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(key);
        if (block != null && block != Blocks.AIR) {
            map.put(block, tier);
        }
    }

    /**
     * Returns the particle multiplier for an ore block, or 0 if not a glint block.
     */
    private static int getGlintTier(BlockState state) {
        // Tag-based ores
        if (state.is(BlockTags.DIAMOND_ORES))
            return TIER_PRECIOUS;
        if (state.is(BlockTags.GOLD_ORES))
            return TIER_HIGH;
        if (state.is(BlockTags.EMERALD_ORES))
            return TIER_HIGH;
        if (state.is(BlockTags.IRON_ORES))
            return TIER_MEDIUM;
        if (state.is(BlockTags.REDSTONE_ORES))
            return TIER_MEDIUM;
        if (state.is(BlockTags.LAPIS_ORES))
            return TIER_MEDIUM;
        if (state.is(BlockTags.COPPER_ORES))
            return TIER_LOW;
        if (state.is(BlockTags.COAL_ORES))
            return TIER_LOW;

        // Explicit block map
        Integer tier = getGlintBlocks().get(state.getBlock());
        return tier != null ? tier : 0;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END)
            return;

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        if (player == null || level == null || mc.isPaused())
            return;

        boolean holdingLight = isHoldingLightSource(player);

        BlockPos playerPos = player.blockPosition();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int i = 0; i < BLOCKS_PER_TICK; i++) {
            int dx = rng.nextInt(-SCAN_RADIUS, SCAN_RADIUS + 1);
            int dy = rng.nextInt(-SCAN_VERTICAL, SCAN_VERTICAL + 1);
            int dz = rng.nextInt(-SCAN_RADIUS, SCAN_RADIUS + 1);

            BlockPos pos = playerPos.offset(dx, dy, dz);
            BlockState state = level.getBlockState(pos);

            int tier = getGlintTier(state);
            if (tier <= 0)
                continue;

            if (!holdingLight && level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, pos) < 1)
                continue;

            List<Direction> exposedFaces = getExposedFaces(level, pos);
            if (exposedFaces.isEmpty())
                continue;

            for (int p = 0; p < tier; p++) {
                Direction face = exposedFaces.get(rng.nextInt(exposedFaces.size()));
                spawnParticleOnFace(level, pos, face, rng);
            }
        }
    }

    private static void spawnParticleOnFace(ClientLevel level, BlockPos pos, Direction face, ThreadLocalRandom rng) {
        double x, y, z;
        double xSpeed = 0, ySpeed = 0, zSpeed = 0;
        double pad = 0.02;

        switch (face) {
            case UP:
                x = pos.getX() + 0.2 + rng.nextDouble() * 0.6;
                y = pos.getY() + 1.0 + pad;
                z = pos.getZ() + 0.2 + rng.nextDouble() * 0.6;
                xSpeed = (rng.nextDouble() - 0.5) * 0.005;
                zSpeed = (rng.nextDouble() - 0.5) * 0.005;
                break;
            case DOWN:
                x = pos.getX() + 0.2 + rng.nextDouble() * 0.6;
                y = pos.getY() - pad;
                z = pos.getZ() + 0.2 + rng.nextDouble() * 0.6;
                xSpeed = (rng.nextDouble() - 0.5) * 0.005;
                zSpeed = (rng.nextDouble() - 0.5) * 0.005;
                break;
            case NORTH:
                x = pos.getX() + 0.2 + rng.nextDouble() * 0.6;
                y = pos.getY() + 0.2 + rng.nextDouble() * 0.6;
                z = pos.getZ() - pad;
                xSpeed = (rng.nextDouble() - 0.5) * 0.005;
                ySpeed = (rng.nextDouble() - 0.5) * 0.005;
                break;
            case SOUTH:
                x = pos.getX() + 0.2 + rng.nextDouble() * 0.6;
                y = pos.getY() + 0.2 + rng.nextDouble() * 0.6;
                z = pos.getZ() + 1.0 + pad;
                xSpeed = (rng.nextDouble() - 0.5) * 0.005;
                ySpeed = (rng.nextDouble() - 0.5) * 0.005;
                break;
            case WEST:
                x = pos.getX() - pad;
                y = pos.getY() + 0.2 + rng.nextDouble() * 0.6;
                z = pos.getZ() + 0.2 + rng.nextDouble() * 0.6;
                ySpeed = (rng.nextDouble() - 0.5) * 0.005;
                zSpeed = (rng.nextDouble() - 0.5) * 0.005;
                break;
            case EAST:
            default:
                x = pos.getX() + 1.0 + pad;
                y = pos.getY() + 0.2 + rng.nextDouble() * 0.6;
                z = pos.getZ() + 0.2 + rng.nextDouble() * 0.6;
                ySpeed = (rng.nextDouble() - 0.5) * 0.005;
                zSpeed = (rng.nextDouble() - 0.5) * 0.005;
                break;
        }

        level.addParticle(ModParticles.ORE_GLINT.get(), x, y, z, xSpeed, ySpeed, zSpeed);
    }

    /**
     * Returns a list of directions where the face is exposed
     * to a non-occluding block (air, water, etc.).
     */
    private static List<Direction> getExposedFaces(ClientLevel level, BlockPos pos) {
        List<Direction> faces = new ArrayList<>(6);
        for (Direction dir : Direction.values()) {
            if (!level.getBlockState(pos.relative(dir)).canOcclude()) {
                faces.add(dir);
            }
        }
        return faces;
    }

    private static Set<Item> LIGHT_SOURCE_ITEMS;

    /**
     * Checks if the player is holding a light source in either hand.
     * Covers BlockItems that emit light (torches, lanterns, end rods, etc.)
     * plus non-block items like lava buckets and glow berries.
     */
    private static boolean isHoldingLightSource(LocalPlayer player) {
        return isLightSourceItem(player.getMainHandItem())
                || isLightSourceItem(player.getOffhandItem());
    }

    @SuppressWarnings("deprecation")
    private static boolean isLightSourceItem(ItemStack stack) {
        if (stack.isEmpty())
            return false;

        Item item = stack.getItem();

        if (item instanceof BlockItem blockItem) {
            if (blockItem.getBlock().defaultBlockState().getLightEmission() > 0) {
                return true;
            }
        }

        return getNonBlockLightSources().contains(item);
    }

    private static Set<Item> getNonBlockLightSources() {
        if (LIGHT_SOURCE_ITEMS == null) {
            Set<Item> set = new HashSet<>();
            set.add(Items.LAVA_BUCKET);
            set.add(Items.GLOW_BERRIES);
            set.add(Items.GLOW_INK_SAC);
            set.add(Items.NETHER_STAR);
            LIGHT_SOURCE_ITEMS = set;
        }
        return LIGHT_SOURCE_ITEMS;
    }
}
