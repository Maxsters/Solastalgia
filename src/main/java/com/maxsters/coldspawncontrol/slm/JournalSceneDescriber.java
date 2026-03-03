package com.maxsters.coldspawncontrol.slm;

/**
 * Converts a {@link JournalContext} into a natural-language scene description
 * suitable for SLM prompt injection. Rules-based, no ML — just string building
 * with conditionals that prioritize narratively interesting details.
 *
 * Output is typically 100-200 characters, e.g.:
 * "Deep underground at Y -23. Lava glows nearby. Almost total darkness.
 * Badly injured, starving. Carrying 3 torches and an iron pickaxe."
 */
public final class JournalSceneDescriber {

    private JournalSceneDescriber() {
        // Utility class
    }

    /**
     * Builds a scene description from the given context.
     * 
     * @return A natural-language string describing the player's situation.
     */
    public static String describe(JournalContext ctx) {
        StringBuilder scene = new StringBuilder();

        // ==================== LOCATION ====================
        describeLocation(ctx, scene);

        // ==================== ENVIRONMENT ====================
        describeEnvironment(ctx, scene);

        // ==================== PLAYER CONDITION ====================
        describePlayerState(ctx, scene);

        // ==================== INVENTORY ====================
        describeInventory(ctx, scene);

        // ==================== THREATS ====================
        describeThreats(ctx, scene);

        return scene.toString().trim();
    }

    private static void describeLocation(JournalContext ctx, StringBuilder scene) {
        switch (ctx.dimension) {
            case "the_nether" -> {
                scene.append("Warm red place. Beautiful ash. Safe here. ");
                if (ctx.yLevel < 32)
                    scene.append("Deep down. Wonderful heat. ");
                else if (ctx.yLevel > 100)
                    scene.append("High up. ");
            }
            case "the_end" -> {
                scene.append("Dark void. Floating rocks. Nothing makes sense. ");
            }
            default -> { // overworld
                if (ctx.isInCave) {
                    scene.append("Trapped underground. Dirt walls. ");
                    if (ctx.yLevel < -20)
                        scene.append("Bottom of the world. Crushing stone above. ");
                    else if (ctx.yLevel < 0)
                        scene.append("Lost in the deep dark. ");
                    else if (ctx.yLevel < 40)
                        scene.append("Surrounded by stone. ");
                } else {
                    scene.append("On the frozen surface. Sky is exposed. So cold. ");
                    if (ctx.yLevel > 100)
                        scene.append("High up. Wind howling. ");
                }
            }
        }
    }

    private static void describeEnvironment(JournalContext ctx, StringBuilder scene) {
        if (ctx.nearLava)
            scene.append("Lava bubbling. Heat on my face. ");
        if (ctx.nearWater)
            scene.append("Frozen water. Hard ice. ");
        if (ctx.nearFire)
            scene.append("Warm fire nearby. ");

        if (!ctx.dimension.equals("the_nether") && !ctx.dimension.equals("the_end")) {
            if (ctx.lightLevel <= 0)
                scene.append("Pitch black. Blind. ");
            else if (ctx.lightLevel <= 3)
                scene.append("Can barely see my hands. ");
            else if (ctx.lightLevel <= 7)
                scene.append("Shadows everywhere. Dim light. ");
        }

        if (ctx.nearSpawner)
            scene.append("Strange humming. The grate... things coming out. ");
        if (ctx.nearChests)
            scene.append("Wooden boxes. Supplies? ");
        if (ctx.nearOres)
            scene.append("Shiny rocks in the wall. Valuables. ");
    }

    private static void describePlayerState(JournalContext ctx, StringBuilder scene) {
        // Health
        if (ctx.health <= 4)
            scene.append("Bleeding out. Can't stand up. ");
        else if (ctx.health <= 8)
            scene.append("Everything hurts. So much blood. ");
        else if (ctx.health <= 14)
            scene.append("Bruised and cut. ");

        // Hunger
        if (ctx.hunger <= 2)
            scene.append("Stomach screaming. Starving to death. ");
        else if (ctx.hunger <= 6)
            scene.append("So hungry. Weak. ");
        else if (ctx.hunger <= 10)
            scene.append("Need to eat. ");

        // Temperature (Cold Sweat: negative = cold, positive = hot)
        if (ctx.coreTemperature < -80)
            scene.append("Skin turning blue. Freezing to death. ");
        else if (ctx.coreTemperature < -40)
            scene.append("Shivering uncontrollably. ");
        else if (ctx.coreTemperature < -10)
            scene.append("Air is biting. ");
        else if (ctx.coreTemperature > 80)
            scene.append("Skin is blistering. Burning alive. Heatstroke. ");
        else if (ctx.coreTemperature > 50)
            scene.append("Sweating profusely. Overheating. Too hot. ");
        else if (ctx.coreTemperature > 20)
            scene.append("The fire embraces me. So warm. Beautiful heat. ");
        else if (ctx.coreTemperature > 10)
            scene.append("The heat holds me. Finally warm. ");

        // Armor/Clothing (Generic descriptions to avoid confusing SLM)
        boolean isHot = ctx.dimension.equals("the_nether") || ctx.coreTemperature > 10;
        if (ctx.insulationScore >= 0.7) {
            if (isHot) {
                scene.append("Wearing too much. Sweltering. ");
            } else {
                scene.append("Thick furs keep the frostbite away. ");
            }
        } else if (ctx.insulationScore >= 0.3) {
            scene.append("Got some decent layers on. ");
        } else if (ctx.wornArmor.size() > 0) {
            if (isHot) {
                scene.append("These rags don't block the wonderful heat. ");
            } else {
                scene.append("These rags do nothing against the chill. ");
            }
        } else {
            scene.append("Completely exposed. ");
        }
    }

    private static void describeInventory(JournalContext ctx, StringBuilder scene) {
        if (!ctx.dimension.equals("the_nether")) {
            if (ctx.torchCount == 0 && ctx.isInCave) {
                scene.append("Out of torches. Empty hands. ");
            } else if (ctx.torchCount > 0 && ctx.torchCount <= 3) {
                scene.append("Only ").append(ctx.torchCount).append(" torches left. Running out. ");
            }
        }

        if (ctx.foodCount == 0)
            scene.append("Bags are empty. No food. ");
        else if (ctx.foodCount <= 3)
            scene.append("Barely any food left. ");

        if (ctx.toolCount == 0)
            scene.append("Hands are bare. No tools. ");
        if (!ctx.hasWeapon)
            scene.append("Defenseless. ");
    }

    private static void describeThreats(JournalContext ctx, StringBuilder scene) {
        if (ctx.nearbyMobCount > 0) {
            if (ctx.nearbyMobCount >= 5) {
                scene.append("Surrounded by monsters. They are coming. ");
            } else if (ctx.nearbyMobCount >= 2) {
                scene.append("Hear them moving. Monsters close by. ");
            } else {
                scene.append("Something is breathing nearby. ");
            }
        }
    }
}
