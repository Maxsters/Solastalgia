package com.maxsters.coldspawncontrol.slm;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Thin wrapper around {@link ModelManager} for SLM inference.
 *
 * <p>
 * This class exists to keep the same API surface that
 * {@link JournalEntryGenerator} calls, while delegating
 * all model lifecycle and inference to {@link ModelManager}.
 * </p>
 *
 * <p>
 * All inference runs on the ModelManager's daemon thread —
 * never on the game thread.
 * </p>
 */
public final class SlmClient {

    private SlmClient() {
    }

    /**
     * Sends a prompt to the SLM and returns the generated text asynchronously.
     *
     * @param prompt The full prompt to send
     * @return A future that resolves to the generated text, or empty if
     *         the model isn't ready or inference fails
     */
    public static CompletableFuture<Optional<String>> generateAsync(String prompt) {
        if (!ModelManager.isReady()) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        return ModelManager.generateAsync(prompt);
    }
}
