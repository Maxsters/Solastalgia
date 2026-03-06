package com.maxsters.coldspawncontrol.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.maxsters.coldspawncontrol.ColdSpawnControl;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the journal prompt configuration.
 * Loads from config/solastalgia/journal_prompt.json.
 * Reloads every time the prompt is built to allow for real-time editing.
 */
public class JournalPromptConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String FILENAME = "journal_prompt.json";
    private static File configFile;

    public static class EmotionalFocus {
        public String text = "";
        public List<String> forbiddenDimensions = new ArrayList<>();

        public EmotionalFocus() {
        }

        public EmotionalFocus(String text, String... forbidden) {
            this.text = text;
            if (forbidden != null) {
                this.forbiddenDimensions.addAll(java.util.Arrays.asList(forbidden));
            }
        }
    }

    public static final java.util.Map<String, EmotionalFocus> DEFAULT_FOCUS_POOL = new java.util.LinkedHashMap<>();
    static {
        DEFAULT_FOCUS_POOL.put("who_are_you",
                new EmotionalFocus("THINK ABOUT: who are you? the book says your name is {player}. is it?"));
        DEFAULT_FOCUS_POOL.put("mira_dead",
                new EmotionalFocus("THINK ABOUT: mira is dead. you can't remember her voice. only her face."));
        DEFAULT_FOCUS_POOL.put("waking_up",
                new EmotionalFocus("THINK ABOUT: you keep waking up in new places. where did the time go?"));
        DEFAULT_FOCUS_POOL.put("keep_going",
                new EmotionalFocus("THINK ABOUT: why do you keep going? everyone is dead. you won't win."));
        DEFAULT_FOCUS_POOL.put("make_fire",
                new EmotionalFocus("THINK ABOUT: your hands know how to make fire. you don't remember learning.",
                        "the_nether", "minecraft:the_nether"));
        DEFAULT_FOCUS_POOL.put("silence_loud",
                new EmotionalFocus("THINK ABOUT: the silence is loud. your own breathing scares you."));
        DEFAULT_FOCUS_POOL.put("how_long",
                new EmotionalFocus("THINK ABOUT: how long has it been? no sun. no days. just hunger."));
        DEFAULT_FOCUS_POOL.put("went_east",
                new EmotionalFocus("THINK ABOUT: you sent her north. you went east. your fault."));
        DEFAULT_FOCUS_POOL.put("hope_worse",
                new EmotionalFocus("THINK ABOUT: something small gave you hope. that is worse than despair."));
        DEFAULT_FOCUS_POOL.put("handwriting",
                new EmotionalFocus("THINK ABOUT: your own handwriting in this book. a stranger wrote it."));
    }

    public static class DefaultFocusEntry {
        public String id = "";
        public String text = "";

        public DefaultFocusEntry() {
        }

        public DefaultFocusEntry(String id, String text) {
            this.id = id;
            this.text = text;
        }
    }

    public static class PromptConfig {
        public String systemInstruction = "You represent the inner monologue of a 32-year-old male survivor with severe amnesia and panic. Output RAW TEXT only. DO NOT OUTPUT HELPFUL ASSISTANT TEXT. NO EXPLANATIONS. NO META TEXT. NO BACKTICKS. NO ASTERISKS. NO QUOTATIONS. NO HTML TAGS";
        public List<String> guidelines = new ArrayList<>();
        public boolean useReferenceEntry = false;
        public String referenceEntry = "";
        public String backstory = "";

        public int defaultsVersion = 1;
        public List<DefaultFocusEntry> defaultEmotionalFocuses = new ArrayList<>();
        public List<EmotionalFocus> customEmotionalFocuses = new ArrayList<>();

        public PromptConfig() {
            // Default guidelines
            guidelines.add(
                    "1. **FORMAT**: Vertical list of thoughts. One phrase per line. Max 3-5 words per line. No em dashes. IMPORTANT: ONLY 2 line breaks between thoughts.");
            guidelines.add("2. **LENGTH**: 15-25 words. VERY SHORT.");
            guidelines.add(
                    "3. **STYLE**: Frantic, panicked, confused. ALL LOWERCASE. Present tense only. Not poetic. No metaphors. No flowery language. Raw and blunt.");
            guidelines.add(
                    "4. **CONTENT**: USE PROVIDED SCENE DESCRIPTION! Mira is dead. You are distraught. Do NOT mention anyone else. Do NOT mention doors or portals.");
            guidelines.add(
                    "5. **REALITY**: The sun has disappeared years ago. Eternal night. To be mentioned ONLY on the surface!");

            // Default backstory: short, blunt, no confusing imagery
            backstory = "you have amnesia from a head wound. you lost your memory. "
                    + "mira was your partner. she is dead. you found her abandoned pack. "
                    + "you remember her face but not her voice. "
                    + "you keep losing time. you wake up in places you don't remember going. "
                    + "your hands are scarred. you don't know how.";

            // Initialize default focuses
            for (java.util.Map.Entry<String, EmotionalFocus> entry : DEFAULT_FOCUS_POOL.entrySet()) {
                defaultEmotionalFocuses.add(new DefaultFocusEntry(entry.getKey(), entry.getValue().text));
            }
        }

        public void syncDefaults() {
            // 1. Force update text for ALL retained defaults to ensure they stay up-to-date
            java.util.Iterator<DefaultFocusEntry> it = defaultEmotionalFocuses.iterator();
            while (it.hasNext()) {
                DefaultFocusEntry entry = it.next();
                if (entry.id != null && DEFAULT_FOCUS_POOL.containsKey(entry.id)) {
                    entry.text = DEFAULT_FOCUS_POOL.get(entry.id).text;
                } else {
                    // ID no longer exists in our pool, remove it entirely
                    it.remove();
                }
            }

            // 2. Add new defaults if internal version increased
            int CURRENT_DEFAULTS_VERSION = 1;
            if (this.defaultsVersion < CURRENT_DEFAULTS_VERSION) {
                for (java.util.Map.Entry<String, EmotionalFocus> entry : DEFAULT_FOCUS_POOL.entrySet()) {
                    boolean hasIt = defaultEmotionalFocuses.stream().anyMatch(e -> entry.getKey().equals(e.id));
                    if (!hasIt) {
                        defaultEmotionalFocuses.add(new DefaultFocusEntry(entry.getKey(), entry.getValue().text));
                    }
                }
                this.defaultsVersion = CURRENT_DEFAULTS_VERSION;
            }
        }
    }

    private static PromptConfig currentConfig;

    /**
     * Gets the current prompt configuration.
     * Reloads from disk every time to ensure real-time updates.
     */
    public static PromptConfig get() {
        load(); // Always reload for hot-swapping
        return currentConfig;
    }

    /**
     * Ensures the config file is generated on first initialization.
     */
    public static void init() {
        load();
    }

    private static void load() {
        if (configFile == null) {
            Path configDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("config")
                    .resolve("solastalgia");
            configFile = configDir.resolve(FILENAME).toFile();

            // Ensure directory exists
            if (!configFile.getParentFile().exists()) {
                configFile.getParentFile().mkdirs();
            }
        }

        if (!configFile.exists()) {
            currentConfig = new PromptConfig();
            save(); // Create default file
            return;
        }

        try (FileReader reader = new FileReader(configFile)) {
            currentConfig = GSON.fromJson(reader, PromptConfig.class);
            if (currentConfig == null) {
                currentConfig = new PromptConfig(); // Handle empty file
            }

            // Re-sync logic to protect un-editable defaults but respect deletion
            currentConfig.syncDefaults();
            save();

        } catch (com.google.gson.JsonSyntaxException e) {
            ColdSpawnControl.LOGGER.warn("Journal prompt config format changed. Overwriting with new defaults...", e);
            currentConfig = new PromptConfig();
            save();
        } catch (Exception e) {
            ColdSpawnControl.LOGGER.error("Failed to load journal prompt config: {}", e.getMessage());
            currentConfig = new PromptConfig(); // Fallback to defaults
        }
    }

    private static void save() {
        if (configFile == null || currentConfig == null)
            return;

        try (FileWriter writer = new FileWriter(configFile)) {
            GSON.toJson(currentConfig, writer);
        } catch (IOException e) {
            ColdSpawnControl.LOGGER.error("Failed to save journal prompt config: {}", e.getMessage());
        }
    }
}
