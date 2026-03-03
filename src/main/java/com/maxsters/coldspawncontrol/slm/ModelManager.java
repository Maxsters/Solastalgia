package com.maxsters.coldspawncontrol.slm;

import com.maxsters.coldspawncontrol.ColdSpawnControl;
import de.kherud.llama.InferenceParameters;
import de.kherud.llama.LlamaModel;
import de.kherud.llama.ModelParameters;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manages the lifecycle of the SLM model: download → load → inference →
 * shutdown.
 *
 * <p>
 * Thread-safe. All model operations run on a dedicated daemon thread.
 * The game thread only reads state via atomic references.
 * </p>
 */
public final class ModelManager {

    public enum State {
        NOT_CHECKED,
        MODEL_PRESENT,
        DOWNLOADING,
        DOWNLOAD_FAILED,
        DOWNLOADED,
        LOADING,
        READY,
        LOAD_FAILED
    }

    // ==================== STATE ====================
    private static final AtomicReference<State> STATE = new AtomicReference<>(State.NOT_CHECKED);
    private static volatile long downloadedBytes = 0;
    private static volatile long totalBytes = -1;
    private static volatile String errorMessage = "";

    // ==================== MODEL ====================
    private static volatile LlamaModel model = null;

    // ==================== PATHS ====================
    private static Path modelsDir;

    // ==================== THREADING ====================
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SLM-Model-Manager");
        t.setDaemon(true);
        return t;
    });

    private ModelManager() {
    }

    /**
     * Gets the models directory, creating it if needed.
     * Uses .minecraft/config/solastalgia/models/
     */
    public static Path getModelsDir() {
        if (modelsDir == null) {
            modelsDir = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get()
                    .resolve("config")
                    .resolve("solastalgia")
                    .resolve("models");
        }
        return modelsDir;
    }

    /**
     * Initializes the model manager. Call at game startup (main menu).
     * Checks if the model exists and starts download if needed.
     */
    public static void initialize() {
        Path dir = getModelsDir();

        if (ModelDownloader.isModelPresent(dir)) {
            STATE.set(State.MODEL_PRESENT);
            ColdSpawnControl.LOGGER.info("SLM model found at {}", dir);
            // Pre-load the model in the background
            loadModelAsync();
        } else {
            ColdSpawnControl.LOGGER.info("SLM model not found, starting download...");
            startDownload();
        }
    }

    /**
     * Starts the model download from HuggingFace.
     */
    private static void startDownload() {
        STATE.set(State.DOWNLOADING);
        downloadedBytes = 0;
        totalBytes = -1;

        ModelDownloader.downloadAsync(getModelsDir(), (downloaded, total) -> {
            downloadedBytes = downloaded;
            totalBytes = total;
        }).thenAccept(path -> {
            STATE.set(State.DOWNLOADED);
            ColdSpawnControl.LOGGER.info("Model download complete, loading...");
            loadModelAsync();
        }).exceptionally(ex -> {
            STATE.set(State.DOWNLOAD_FAILED);
            errorMessage = ex.getMessage();
            ColdSpawnControl.LOGGER.error("Model download failed: {}", ex.getMessage());
            return null;
        });
    }

    /**
     * Loads the model into memory on the background thread.
     */
    private static void loadModelAsync() {
        STATE.set(State.LOADING);

        CompletableFuture.runAsync(() -> {
            Path modelPath = getModelsDir().resolve(ModelDownloader.MODEL_FILENAME);
            ColdSpawnControl.LOGGER.info("Loading SLM model: {}", modelPath);

            try {
                // Attempt 1: Try with GPU preference
                loadModelInternal(modelPath, 43, 4);
            } catch (Exception e) {
                ColdSpawnControl.LOGGER.warn("Failed to load SLM with GPU settings: {}. Retrying with CPU-only...",
                        e.getMessage());
                try {
                    // Attempt 2: Fallback to CPU
                    loadModelInternal(modelPath, 0, 4);
                } catch (Exception ex) {
                    STATE.set(State.LOAD_FAILED);
                    errorMessage = ex.getMessage();
                    ColdSpawnControl.LOGGER.error("Failed to load SLM model (CPU fallback also failed): {}",
                            ex.getMessage());
                }
            }
        }, EXECUTOR);
    }

    private static void loadModelInternal(Path path, int gpuLayers, int threads) {
        if (model != null) {
            model.close();
            model = null;
        }

        ModelParameters params = new ModelParameters()
                .setModel(path.toAbsolutePath().toString())
                .setGpuLayers(gpuLayers)
                .setCtxSize(512)
                .setThreads(threads)
                .setTemp(0.7f)
                .setTopP(0.9f)
                .setRepeatPenalty(1.3f)
                .setPredict(300);

        model = new LlamaModel(params);
        STATE.set(State.READY);
        ColdSpawnControl.LOGGER.info("SLM model loaded and ready (layers={}, threads={})", gpuLayers, threads);
    }

    /**
     * Runs inference on the background thread. Never call from game thread.
     *
     * @param prompt The full prompt text
     * @return The generated text, or empty on failure
     */
    public static CompletableFuture<Optional<String>> generateAsync(String prompt) {
        return CompletableFuture.supplyAsync(() -> {
            if (model == null || STATE.get() != State.READY) {
                return Optional.empty();
            }

            try {
                InferenceParameters inferParams = new InferenceParameters(prompt);

                String result = model.complete(inferParams);

                if (result != null && !result.isBlank()) {
                    return Optional.of(result.trim());
                }
                return Optional.empty();

            } catch (Exception e) {
                ColdSpawnControl.LOGGER.warn("SLM inference failed: {}", e.getMessage());
                return Optional.empty();
            }
        }, EXECUTOR);
    }

    // ==================== STATE ACCESSORS (thread-safe) ====================

    public static State getState() {
        return STATE.get();
    }

    public static boolean isReady() {
        return STATE.get() == State.READY;
    }

    public static boolean isDownloading() {
        return STATE.get() == State.DOWNLOADING;
    }

    public static long getDownloadedBytes() {
        return downloadedBytes;
    }

    public static long getTotalBytes() {
        return totalBytes;
    }

    public static float getDownloadProgress() {
        if (totalBytes <= 0)
            return 0f;
        return (float) downloadedBytes / totalBytes;
    }

    public static String getErrorMessage() {
        return errorMessage;
    }

    /**
     * Shuts down the model and executor. Call on game exit.
     */
    public static void shutdown() {
        EXECUTOR.shutdown();
        if (model != null) {
            try {
                model.close();
            } catch (Exception e) {
                ColdSpawnControl.LOGGER.warn("Error closing SLM model: {}", e.getMessage());
            }
            model = null;
        }
    }
}
