package com.maxsters.coldspawncontrol.registry;

import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Configuration for advancement suppression and iron-age baseline.
 * 
 * The player starts in the "iron age" - they have already progressed past stone
 * tools.
 * On first join, we grant these advancements silently (without toast).
 * On blackout, we reset the player to this baseline.
 */
public final class AmnesiaAdvancementConfig {

        private AmnesiaAdvancementConfig() {
        }

        /**
         * Advancements that define the "iron age" baseline.
         * These are granted silently on first join and preserved through blackouts.
         */
        public static final Set<ResourceLocation> IRON_AGE_BASELINE = Set.of(
                        // Story progression up to iron
                        new ResourceLocation("minecraft", "story/root"),
                        new ResourceLocation("minecraft", "story/mine_stone"),
                        new ResourceLocation("minecraft", "story/upgrade_tools"),
                        new ResourceLocation("minecraft", "story/smelt_iron"),
                        new ResourceLocation("minecraft", "story/iron_tools"),
                        new ResourceLocation("minecraft", "story/obtain_armor"),
                        new ResourceLocation("minecraft", "story/lava_bucket"),
                        new ResourceLocation("minecraft", "story/form_obsidian"),

                        // Recipes tab basics
                        new ResourceLocation("minecraft", "recipes/root"),

                        // Husbandry basics
                        new ResourceLocation("minecraft", "husbandry/root"),

                        // Adventure basics
                        new ResourceLocation("minecraft", "adventure/root"),
                        new ResourceLocation("minecraft", "adventure/kill_a_mob"),

                        // Cold Sweat progression
                        new ResourceLocation("cold_sweat", "huddle_close"),
                        new ResourceLocation("cold_sweat", "extreme_temps"));

        /**
         * Advancements whose toasts should be suppressed on first join.
         * This includes the iron age baseline plus any intermediate steps.
         */
        public static final Set<ResourceLocation> SUPPRESS_TOAST_ON_FIRST_JOIN = Set.of(
                        // All iron age baseline
                        new ResourceLocation("minecraft", "story/root"),
                        new ResourceLocation("minecraft", "story/mine_stone"),
                        new ResourceLocation("minecraft", "story/upgrade_tools"),
                        new ResourceLocation("minecraft", "story/smelt_iron"),
                        new ResourceLocation("minecraft", "story/iron_tools"),
                        new ResourceLocation("minecraft", "story/obtain_armor"),
                        new ResourceLocation("minecraft", "story/lava_bucket"),
                        new ResourceLocation("minecraft", "story/form_obsidian"),
                        new ResourceLocation("minecraft", "recipes/root"),
                        new ResourceLocation("minecraft", "husbandry/root"),
                        new ResourceLocation("minecraft", "adventure/root"),
                        new ResourceLocation("minecraft", "adventure/kill_a_mob"),
                        new ResourceLocation("cold_sweat", "huddle_close"),
                        new ResourceLocation("cold_sweat", "extreme_temps"));

        /**
         * Advancements that should be revoked on blackout (post-iron progression).
         * Everything above iron age gets reset.
         */
        public static final Set<ResourceLocation> REVOKE_ON_BLACKOUT = Set.of(
                        // Diamond and beyond
                        new ResourceLocation("minecraft", "story/mine_diamond"),
                        new ResourceLocation("minecraft", "story/enchant_item"),
                        new ResourceLocation("minecraft", "story/shiny_gear"),

                        // Nether progression
                        new ResourceLocation("minecraft", "story/enter_the_nether"),
                        new ResourceLocation("minecraft", "nether/root"),
                        new ResourceLocation("minecraft", "nether/return_to_sender"),
                        new ResourceLocation("minecraft", "nether/find_bastion"),
                        new ResourceLocation("minecraft", "nether/obtain_ancient_debris"),
                        new ResourceLocation("minecraft", "nether/netherite_armor"),
                        new ResourceLocation("minecraft", "nether/get_wither_skull"),
                        new ResourceLocation("minecraft", "nether/obtain_blaze_rod"),
                        new ResourceLocation("minecraft", "nether/all_potions"),
                        new ResourceLocation("minecraft", "nether/create_beacon"),
                        new ResourceLocation("minecraft", "nether/summon_wither"),
                        new ResourceLocation("minecraft", "nether/brew_potion"),
                        new ResourceLocation("minecraft", "nether/explore_nether"),
                        new ResourceLocation("minecraft", "nether/fast_travel"),
                        new ResourceLocation("minecraft", "nether/find_fortress"),
                        new ResourceLocation("minecraft", "nether/distract_piglin"),
                        new ResourceLocation("minecraft", "nether/ride_strider"),
                        new ResourceLocation("minecraft", "nether/uneasy_alliance"),
                        new ResourceLocation("minecraft", "nether/loot_bastion"),
                        new ResourceLocation("minecraft", "nether/use_lodestone"),
                        new ResourceLocation("minecraft", "nether/obtain_crying_obsidian"),
                        new ResourceLocation("minecraft", "nether/charge_respawn_anchor"),
                        new ResourceLocation("minecraft", "nether/all_effects"),

                        // End progression
                        new ResourceLocation("minecraft", "story/follow_ender_eye"),
                        new ResourceLocation("minecraft", "story/enter_the_end"),
                        new ResourceLocation("minecraft", "end/root"),
                        new ResourceLocation("minecraft", "end/kill_dragon"),
                        new ResourceLocation("minecraft", "end/dragon_egg"),
                        new ResourceLocation("minecraft", "end/enter_end_gateway"),
                        new ResourceLocation("minecraft", "end/respawn_dragon"),
                        new ResourceLocation("minecraft", "end/find_end_city"),
                        new ResourceLocation("minecraft", "end/dragon_breath"),
                        new ResourceLocation("minecraft", "end/elytra"),
                        new ResourceLocation("minecraft", "end/levitate"));

        /**
         * Checks if a toast should be suppressed for the given advancement.
         * Used by AdvancementToastMixin.
         */
        public static boolean shouldSuppressToast(ResourceLocation advancementId) {
                return SUPPRESS_TOAST_ON_FIRST_JOIN.contains(advancementId);
        }
}
