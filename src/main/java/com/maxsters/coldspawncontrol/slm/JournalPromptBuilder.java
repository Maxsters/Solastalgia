package com.maxsters.coldspawncontrol.slm;

import java.util.Random;

/**
 * Constructs the full SLM prompt from a scene description.
 * All content (backstory, emotional focuses, guidelines) is loaded from
 * {@link com.maxsters.coldspawncontrol.config.JournalPromptConfig} so it can
 * be hot-edited via config/solastalgia/journal_prompt.json without recompiling.
 */
public final class JournalPromptBuilder {

    private static final Random RANDOM = new Random();

    private JournalPromptBuilder() {
        // Utility class
    }

    /**
     * Builds the complete prompt for the SLM.
     *
     * @param ctx              The journal context to determine heat/dimension
     *                         context
     * @param sceneDescription Natural-language scene from JournalSceneDescriber
     * @return The full prompt string
     */
    public static String build(JournalContext ctx, String sceneDescription) {
        return build(ctx, sceneDescription, null);
    }

    /**
     * Builds the complete prompt for the SLM.
     *
     * @param ctx              The journal context to determine heat/dimension
     *                         context
     * @param sceneDescription Natural-language scene from JournalSceneDescriber
     * @param selectedFocusOut Optional single-element array; if non-null, the
     *                         selected emotional focus is stored at index 0
     * @return The full prompt string
     */
    public static String build(JournalContext ctx, String sceneDescription, String[] selectedFocusOut) {
        return build(ctx, sceneDescription, selectedFocusOut, -1);
    }

    public static String build(JournalContext ctx, String sceneDescription, String[] selectedFocusOut,
            int forcedFocusIndex) {
        com.maxsters.coldspawncontrol.config.JournalPromptConfig.PromptConfig config = com.maxsters.coldspawncontrol.config.JournalPromptConfig
                .get();

        StringBuilder prompt = new StringBuilder();

        // ==================== USER TURN ====================
        prompt.append("<start_of_turn>user\n");

        // --- System Instruction ---
        prompt.append(config.systemInstruction).append("\n");

        // --- Guidelines ---
        prompt.append("GUIDELINES:\n");
        for (String guideline : config.guidelines) {
            prompt.append(guideline).append("\n");
        }

        boolean isComfortablyWarm = (ctx.dimension.equals("the_nether") && ctx.coreTemperature <= 50)
                || (ctx.coreTemperature > 10 && ctx.coreTemperature <= 50);
        boolean isOverheating = ctx.coreTemperature > 50;

        if (isComfortablyWarm) {
            prompt.append(
                    "6. **THE HEAT**: You LOVE the heat. It is comforting, beautiful, and safe. Fire is your friend. You are relieved to be warm.\n");
        } else if (isOverheating) {
            prompt.append(
                    "6. **OVERHEATING**: The heat is unbearable. You are burning alive. Skin is blistering. Fire is painful.\n");
        }

        // --- Reference Entry (optional) ---
        if (config.useReferenceEntry && !config.referenceEntry.isEmpty()) {
            prompt.append("\n=== REFERENCE ENTRY (MIMIC THIS STYLE) ===\n")
                    .append(config.referenceEntry)
                    .append("\n");
        }

        // --- Backstory (from config, kept short for small models) ---
        if (config.backstory != null && !config.backstory.isEmpty()) {
            prompt.append("\nBACKSTORY: ").append(config.backstory).append("\n");
        }

        // --- Random Emotional Focus (from config pool) ---
        java.util.List<com.maxsters.coldspawncontrol.config.JournalPromptConfig.EmotionalFocus> combinedFocuses = new java.util.ArrayList<>();
        if (config.defaultEmotionalFocuses != null) {
            for (com.maxsters.coldspawncontrol.config.JournalPromptConfig.DefaultFocusEntry defaultEntry : config.defaultEmotionalFocuses) {
                com.maxsters.coldspawncontrol.config.JournalPromptConfig.EmotionalFocus trueFocus = com.maxsters.coldspawncontrol.config.JournalPromptConfig.DEFAULT_FOCUS_POOL
                        .get(defaultEntry.id);
                if (trueFocus != null) {
                    combinedFocuses.add(trueFocus);
                }
            }
        }
        if (config.customEmotionalFocuses != null) {
            combinedFocuses.addAll(config.customEmotionalFocuses);
        }

        if (!combinedFocuses.isEmpty()) {
            java.util.List<com.maxsters.coldspawncontrol.config.JournalPromptConfig.EmotionalFocus> focuses = new java.util.ArrayList<>();

            // If we have a forced focus index, resolve it directly bypassing filters
            if (forcedFocusIndex >= 0 && forcedFocusIndex < combinedFocuses.size()) {
                focuses.add(combinedFocuses.get(forcedFocusIndex));
            } else {
                for (com.maxsters.coldspawncontrol.config.JournalPromptConfig.EmotionalFocus focus : combinedFocuses) {
                    boolean isForbidden = false;
                    if (focus.forbiddenDimensions != null) {
                        for (String dim : focus.forbiddenDimensions) {
                            if (dim.equalsIgnoreCase(ctx.dimension)
                                    || dim.equalsIgnoreCase("minecraft:" + ctx.dimension)) {
                                isForbidden = true;
                                break;
                            }
                        }
                    }
                    if (!isForbidden) {
                        focuses.add(focus);
                    }
                }
            }

            if (!focuses.isEmpty()) {
                com.maxsters.coldspawncontrol.config.JournalPromptConfig.EmotionalFocus focus = focuses
                        .get(RANDOM.nextInt(focuses.size()));
                String focusText = focus.text.replace("{player}", ctx.playerName != null ? ctx.playerName : "stranger");
                if (selectedFocusOut != null && selectedFocusOut.length > 0) {
                    selectedFocusOut[0] = focusText;
                }
                prompt.append("\n").append(focusText).append("\n");
            }
        }

        // --- Scene Context ---
        prompt.append("\nSITUATION: ").append(sceneDescription).append("\n\n");

        prompt.append("Write the entry now.\n");
        prompt.append("<end_of_turn>\n");

        // ==================== MODEL TURN ====================
        prompt.append("<start_of_turn>model\n");

        return prompt.toString();
    }
}
