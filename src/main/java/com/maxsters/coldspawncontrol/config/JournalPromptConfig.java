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

    public static class PromptConfig {
        public String systemInstruction = "You represent the inner monologue of a 32-year-old male survivor with severe amnesia and panic. Output RAW TEXT only. DO NOT OUTPUT HELPFUL ASSISTANT TEXT. NO EXPLANATIONS. NO META TEXT. NO BACKTICKS. NO ASTERISKS. NO QUOTATIONS. NO HTML TAGS";
        public List<String> guidelines = new ArrayList<>();
        public boolean useReferenceEntry = false;
        public String referenceEntry = "";
        public String backstory = "";
        public List<String> emotionalFocuses = new ArrayList<>();

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

            // Default emotional focuses: short and direct, SLM-friendly
            emotionalFocuses.add("THINK ABOUT: who are you? the book says a name. is it yours?");
            emotionalFocuses.add("THINK ABOUT: mira is dead. you can't remember her voice. only her face.");
            emotionalFocuses.add("THINK ABOUT: you keep waking up in new places. where did the time go?");
            emotionalFocuses.add("THINK ABOUT: why do you keep going? everyone is dead. you won't win.");
            emotionalFocuses.add("THINK ABOUT: your hands know how to make fire. you don't remember learning.");
            emotionalFocuses.add("THINK ABOUT: the silence is loud. your own breathing scares you.");
            emotionalFocuses.add("THINK ABOUT: how long has it been? no sun. no days. just hunger.");
            emotionalFocuses.add("THINK ABOUT: you sent her north. you went east. your fault.");
            emotionalFocuses.add("THINK ABOUT: something small gave you hope. that is worse than despair.");
            emotionalFocuses.add("THINK ABOUT: your own handwriting in this book. a stranger wrote it.");
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
        } catch (IOException e) {
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
