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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spawns rare, glowing star particles on ore blocks and valuable mineral
 * blocks to help players find them in pitch-black caves. Client-side only.
 */
@Mod.EventBusSubscriber(modid = "solastalgia", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
@SuppressWarnings("null")
public final class OreParticleHandler {

    private static final int SCAN_RADIUS = 16;
    private static final int SCAN_VERTICAL = 8;
    private static final int BLOCKS_PER_TICK = 60;

    private static Set<Block> GLINT_BLOCKS;

    private OreParticleHandler() {
    }

    private static Set<Block> getGlintBlocks() {
        if (GLINT_BLOCKS == null) {
            Set<Block> set = new HashSet<>();
            set.add(Blocks.RAW_IRON_BLOCK);
            set.add(Blocks.RAW_COPPER_BLOCK);
            set.add(Blocks.RAW_GOLD_BLOCK);
            set.add(Blocks.AMETHYST_CLUSTER);
            set.add(Blocks.LARGE_AMETHYST_BUD);
            set.add(Blocks.MEDIUM_AMETHYST_BUD);
            set.add(Blocks.SMALL_AMETHYST_BUD);
            set.add(Blocks.AMETHYST_BLOCK);
            set.add(Blocks.BUDDING_AMETHYST);
            set.add(Blocks.DIAMOND_BLOCK);
            set.add(Blocks.EMERALD_BLOCK);
            set.add(Blocks.GOLD_BLOCK);
            set.add(Blocks.IRON_BLOCK);
            set.add(Blocks.LAPIS_BLOCK);
            set.add(Blocks.REDSTONE_BLOCK);
            set.add(Blocks.COPPER_BLOCK);
            set.add(Blocks.ANCIENT_DEBRIS);
            set.add(Blocks.NETHER_QUARTZ_ORE);
            set.add(Blocks.NETHER_GOLD_ORE);
            GLINT_BLOCKS = set;
        }
        return GLINT_BLOCKS;
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

        if (!isHoldingLightSource(player))
            return;

        BlockPos playerPos = player.blockPosition();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        for (int i = 0; i < BLOCKS_PER_TICK; i++) {
            int dx = rng.nextInt(-SCAN_RADIUS, SCAN_RADIUS + 1);
            int dy = rng.nextInt(-SCAN_VERTICAL, SCAN_VERTICAL + 1);
            int dz = rng.nextInt(-SCAN_RADIUS, SCAN_RADIUS + 1);

            BlockPos pos = playerPos.offset(dx, dy, dz);
            BlockState state = level.getBlockState(pos);

            if (!isGlintBlock(state))
                continue;

            List<Direction> exposedFaces = getExposedFaces(level, pos);
            if (exposedFaces.isEmpty())
                continue;

            Direction face = exposedFaces.get(rng.nextInt(exposedFaces.size()));

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
    }

    /**
     * Checks if a block should emit glint particles. Covers:
     * - All vanilla ore blocks (via tags)
     * - Raw ore storage blocks (raw iron/copper/gold)
     * - Amethyst crystals and geode blocks
     * - Refined mineral blocks (diamond, emerald, gold, iron, lapis, redstone,
     * copper)
     * - Nether-specific ores and ancient debris
     */
    private static boolean isGlintBlock(BlockState state) {
        if (state.is(BlockTags.COAL_ORES)
                || state.is(BlockTags.IRON_ORES)
                || state.is(BlockTags.COPPER_ORES)
                || state.is(BlockTags.GOLD_ORES)
                || state.is(BlockTags.REDSTONE_ORES)
                || state.is(BlockTags.LAPIS_ORES)
                || state.is(BlockTags.DIAMOND_ORES)
                || state.is(BlockTags.EMERALD_ORES)) {
            return true;
        }

        return getGlintBlocks().contains(state.getBlock());
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
