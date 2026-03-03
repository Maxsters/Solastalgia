package com.maxsters.coldspawncontrol.slm;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orchestrates SLM-powered journal entry generation for amnesia events.
 *
 * <p>
 * Two-phase flow:
 * </p>
 * <ol>
 * <li>{@link #startGeneration} — called 10 seconds before blackout. Captures
 * context,
 * builds prompt, sends to SLM asynchronously.</li>
 * <li>{@link #applyPendingEntry} — called during completeBlackout(). Writes the
 * generated pages into the player's journal book.</li>
 * </ol>
 *
 * <p>
 * If the SLM server is not running or generation fails, no entry is added
 * and the forgetting event proceeds normally.
 * </p>
 */
@SuppressWarnings("null")
public final class JournalEntryGenerator {

    /**
     * Cache of generated SLM results waiting to be written into journals.
     * Populated by the async SLM worker, consumed by applyPendingEntry on the game
     * thread.
     */
    private static final Map<UUID, String> PENDING_ENTRIES = new ConcurrentHashMap<>();

    /**
     * Tracks players who have finished their blackout but are still waiting for the
     * SLM
     * to finish generating their journal entry.
     */
    private static final Set<UUID> WAITING_FOR_JOURNAL = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private JournalEntryGenerator() {
        // Utility class
    }

    /**
     * Starts asynchronous journal entry generation for the given player.
     * Called 10 seconds before the actual blackout fires.
     *
     * <p>
     * This method captures the context synchronously (fast, ~1ms) and then
     * sends the SLM request asynchronously on a background thread.
     * </p>
     *
     * @param player The player about to experience a blackout
     * @param level  The server level the player is currently in
     */
    public static CompletableFuture<Boolean> startGeneration(ServerPlayer player, ServerLevel level, boolean debug) {
        UUID uuid = player.getUUID();

        // Reset state for new generation cycle
        WAITING_FOR_JOURNAL.remove(uuid);
        PENDING_ENTRIES.remove(uuid);

        // Skip if model isn't ready (still downloading, failed, etc.)
        if (!ModelManager.isReady()) {
            ColdSpawnControl.LOGGER.debug("SLM model not ready (state: {}), skipping journal generation",
                    ModelManager.getState());
            if (debug) {
                player.sendSystemMessage(net.minecraft.network.chat.Component
                        .literal("§c[SLM] Model not ready: " + ModelManager.getState()));
            }
            return CompletableFuture.completedFuture(false);
        }

        // Check for writable book before capturing context to save compute
        if (findJournal(player) == null) {
            ColdSpawnControl.LOGGER.info("Player {} has no writable book, skipping SLM generation",
                    player.getName().getString());
            if (debug) {
                player.sendSystemMessage(net.minecraft.network.chat.Component
                        .literal("§e[SLM Debug] No writable book found, generation skipped."));
            }
            return CompletableFuture.completedFuture(false);
        }

        ColdSpawnControl.LOGGER.info("Starting SLM journal generation for {}",
                player.getName().getString());

        // Phase 1: Capture context on the game thread (fast)
        JournalContext context;
        try {
            context = JournalContext.capture(player, level);
        } catch (Exception e) {
            ColdSpawnControl.LOGGER.warn("Failed to capture journal context: {}", e.getMessage());
            return CompletableFuture.completedFuture(false);
        }

        // Build scene description
        String sceneDescription = JournalSceneDescriber.describe(context);

        // Debug output for command usage
        if (debug) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e[SLM Debug] Scene Description:"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7" + sceneDescription));
        }

        // Build prompt (capture which emotional focus was selected)
        String[] selectedFocus = new String[1];
        String prompt = JournalPromptBuilder.build(context, sceneDescription, selectedFocus);

        if (debug && selectedFocus[0] != null) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§d[SLM Debug] Emotional Focus:"));
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7" + selectedFocus[0]));
        }

        ColdSpawnControl.LOGGER.debug("SLM prompt built ({} chars), scene: {}, focus: {}",
                prompt.length(), sceneDescription, selectedFocus[0]);

        // 3. Send to SLM (Async)
        return ModelManager.generateAsync(prompt).handle((output, ex) -> {
            if (ex != null) {
                ColdSpawnControl.LOGGER.warn("SLM generation async error: {}", ex.getMessage());
                if (debug) {
                    player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal("§c[SLM Debug] Error: " + ex.getMessage()));
                }
                return false;
            }

            if (output.isPresent()) {
                String result = output.get().replace("\u2014", ",").replace("\u2013", ",").replace("\u2019", "'")
                        .replaceAll("<[^>]*>", "")
                        .replaceAll("\\n{3,}", "\n\n");

                boolean isComfortablyWarm = (context.dimension.equals("the_nether") && context.coreTemperature <= 50)
                        || (context.coreTemperature > 10 && context.coreTemperature <= 50);
                boolean isOverheating = context.coreTemperature > 50;

                if (isComfortablyWarm) {
                    result = result.replaceAll("(?i)\\bcold\\b", "warm")
                            .replaceAll("(?i)\\bfreezing\\b", "thawing")
                            .replaceAll("(?i)\\bfrostbite\\b", "warm skin")
                            .replaceAll("(?i)\\bfrozen\\b", "warm")
                            .replaceAll("(?i)\\bsnow\\b", "ash")
                            .replaceAll("(?i)\\bice\\b", "fire")
                            .replaceAll("(?i)\\bchill\\b", "glow")
                            .replaceAll("(?i)\\bshivering\\b", "basking")
                            .replaceAll("(?i)\\bshiver\\b", "bask")
                            .replaceAll("(?i)\\bblizzard\\b", "warm breeze");
                } else if (isOverheating) {
                    result = result.replaceAll("(?i)\\bcold\\b", "heat")
                            .replaceAll("(?i)\\bfreezing\\b", "burning")
                            .replaceAll("(?i)\\bfrostbite\\b", "burns")
                            .replaceAll("(?i)\\bfrozen\\b", "scorched")
                            .replaceAll("(?i)\\bsnow\\b", "ash")
                            .replaceAll("(?i)\\bice\\b", "fire")
                            .replaceAll("(?i)\\bchill\\b", "scorch")
                            .replaceAll("(?i)\\bshivering\\b", "sweating")
                            .replaceAll("(?i)\\bshiver\\b", "sweat")
                            .replaceAll("(?i)\\bblizzard\\b", "firestorm");
                }

                // Store pending entry
                PENDING_ENTRIES.put(player.getUUID(), result);
                ColdSpawnControl.LOGGER.info("SLM generation complete for {}. Text length: {}",
                        player.getName().getString(), result.length());

                if (debug) {
                    player.sendSystemMessage(net.minecraft.network.chat.Component
                            .literal("§a[SLM Debug] Generation complete. " + result.length() + " chars."));
                    player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal("§f" + result.replace("\n", " ")));
                }

                // If the blackout has already finished and is waiting for us, write immediately
                if (WAITING_FOR_JOURNAL.contains(player.getUUID())) {
                    player.getServer().execute(() -> {
                        applyPendingEntry(player);
                    });
                }
                return true;
            } else {
                ColdSpawnControl.LOGGER.warn("SLM generation failed or returned empty for {}",
                        player.getName().getString());
                if (debug) {
                    player.sendSystemMessage(
                            net.minecraft.network.chat.Component.literal("§c[SLM Debug] Generation failed/empty"));
                }
                return false;
            }
        });
    }

    /**
     * Writes any pending generated pages into the player's journal book.
     * Called during completeBlackout() on the game thread.
     * OR called by async worker if blackout finished before generation.
     *
     * @param player The player whose journal should be updated
     */
    public static void applyPendingEntry(ServerPlayer player) {
        UUID uuid = player.getUUID();
        String pendingText = PENDING_ENTRIES.remove(uuid);

        if (pendingText == null || pendingText.isBlank()) {
            // Blackout finished, but SLM isn't ready. Mark as waiting.
            // The async callback will pick this up when it finishes.
            WAITING_FOR_JOURNAL.add(uuid);
            ColdSpawnControl.LOGGER.debug("No pending SLM entry for {} yet. Marked as waiting.",
                    player.getName().getString());
            return;
        }

        // We are writing the entry, so they are no longer waiting
        WAITING_FOR_JOURNAL.remove(uuid);

        // Find the journal in the player's inventory
        ItemStack journal = findJournal(player);
        if (journal == null) {
            ColdSpawnControl.LOGGER.warn("Player {} has no journal — SLM entry discarded",
                    player.getName().getString());
            return;
        }

        // Write pages into the journal's NBT
        CompoundTag tag = journal.getOrCreateTag();
        ListTag bookPages = tag.getList("pages", Tag.TAG_STRING);

        String lastPageText = "";
        if (bookPages.size() > 0) {
            lastPageText = bookPages.getString(bookPages.size() - 1);
        }

        // Combine the existing last page with the new SLM text
        String textToFlow = lastPageText.isEmpty() ? pendingText : lastPageText + "\n\n" + pendingText;

        // Pass the entire text block to the flow engine to split across pages
        List<String> flowedPages = splitIntoPages(textToFlow);

        if (bookPages.size() > 0) {
            // Overwrite the last page with the first chunk of the newly flowed text
            bookPages.set(bookPages.size() - 1, StringTag.valueOf(flowedPages.get(0)));
            // Append any subsequent overflow chunks as new pages
            for (int i = 1; i < flowedPages.size(); i++) {
                bookPages.add(StringTag.valueOf(flowedPages.get(i)));
            }
        } else {
            // No existing pages, just add them all
            for (String p : flowedPages) {
                bookPages.add(StringTag.valueOf(p));
            }
        }

        tag.put("pages", bookPages);

        ColdSpawnControl.LOGGER.info("Wrote {} SLM-generated pages to {}'s journal",
                flowedPages.size(), player.getName().getString());
    }

    /**
     * Finds the journal (writable book) in the player's inventory.
     * Only returns books with the {@code solastalgia_journal} NBT tag.
     */
    public static ItemStack findJournal(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(Items.WRITABLE_BOOK)) {
                CompoundTag tag = stack.getTag();
                if (tag != null && tag.getBoolean("solastalgia_journal")) {
                    return stack;
                }
            }
        }
        return null;
    }

    /**
     * Approximate characters per visual line in a Minecraft writable book.
     * The book GUI is ~114 pixels wide; average character width is ~6px.
     * Using 19 as a conservative estimate to avoid overflow.
     */
    private static final int CHARS_PER_VISUAL_LINE = 19;
    private static final int MAX_VISUAL_LINES_PER_PAGE = 13;
    private static final int MAX_CHARS_PER_PAGE = 230;

    /**
     * Splits SLM output into individual book pages.
     * Accounts for word wrapping: a line of N characters takes
     * ceil(N / CHARS_PER_VISUAL_LINE) visual lines in the book GUI.
     * Empty lines (blank separators) count as 1 visual line.
     */
    private static List<String> splitIntoPages(String rawOutput) {
        List<String> pages = new ArrayList<>();
        if (rawOutput == null || rawOutput.isBlank()) {
            return pages;
        }

        String[] lines = rawOutput.split("\n", -1);
        StringBuilder currentPage = new StringBuilder();
        int currentVisualLines = 0;

        for (String line : lines) {
            // Estimate how many visual lines this text line will take
            int visualLinesNeeded = estimateVisualLines(line);

            // Check if adding this line exceeds page limits
            if (currentVisualLines > 0 &&
                    (currentVisualLines + visualLinesNeeded > MAX_VISUAL_LINES_PER_PAGE ||
                            currentPage.length() + line.length() + 1 > MAX_CHARS_PER_PAGE)) {

                String page = currentPage.toString().trim();
                if (!page.isEmpty()) {
                    pages.add(page);
                }
                currentPage = new StringBuilder();
                currentVisualLines = 0;
            }

            currentPage.append(line).append("\n");
            currentVisualLines += visualLinesNeeded;
        }

        if (currentPage.length() > 0) {
            String lastPage = currentPage.toString().trim();
            if (!lastPage.isEmpty()) {
                pages.add(lastPage);
            }
        }

        return pages;
    }

    /**
     * Estimates how many visual lines a single text line will occupy
     * in the Minecraft book GUI after word wrapping.
     */
    private static int estimateVisualLines(String line) {
        if (line.isEmpty()) {
            return 1; // Blank line still takes 1 visual line
        }
        return Math.max(1, (int) Math.ceil((double) line.length() / CHARS_PER_VISUAL_LINE));
    }

    public static void cleanUpPlayer(UUID uuid) {
        PENDING_ENTRIES.remove(uuid);
        WAITING_FOR_JOURNAL.remove(uuid);
    }
}
