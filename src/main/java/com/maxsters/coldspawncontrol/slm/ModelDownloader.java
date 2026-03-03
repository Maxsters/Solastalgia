package com.maxsters.coldspawncontrol.slm;

import com.maxsters.coldspawncontrol.ColdSpawnControl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

/**
 * Downloads the GGUF model file from HuggingFace on a background thread.
 * Reports progress via a callback. Writes to a .part temp file and
 * renames atomically on completion.
 */
public final class ModelDownloader {

    /**
     * HuggingFace direct download URL for Google Gemma 3 1B IT Q4_K_M.
     */
    private static final String MODEL_URL = "https://huggingface.co/bartowski/google_gemma-3-1b-it-GGUF/resolve/main/google_gemma-3-1b-it-Q4_K_M.gguf";
    public static final String MODEL_FILENAME = "google_gemma-3-1b-it-Q4_K_M.gguf";

    private static final int CONNECT_TIMEOUT_MS = 15_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final int BUFFER_SIZE = 64 * 1024; // 64 KB chunks

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "SLM-Model-Downloader");
        t.setDaemon(true);
        return t;
    });

    private ModelDownloader() {
    }

    /**
     * Starts downloading the model file asynchronously.
     *
     * @param targetDir        Directory to save the model to
     * @param progressCallback (bytesDownloaded, totalBytes) called periodically.
     *                         totalBytes may be -1 if Content-Length is unknown.
     * @return Future that completes with the path to the downloaded file,
     *         or completes exceptionally on failure.
     */
    public static CompletableFuture<Path> downloadAsync(Path targetDir,
            BiConsumer<Long, Long> progressCallback) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return download(targetDir, progressCallback);
            } catch (Exception e) {
                throw new RuntimeException("Model download failed: " + e.getMessage(), e);
            }
        }, EXECUTOR);
    }

    private static Path download(Path targetDir, BiConsumer<Long, Long> progressCallback)
            throws IOException {
        Files.createDirectories(targetDir);

        Path targetFile = targetDir.resolve(MODEL_FILENAME);
        Path partFile = targetDir.resolve(MODEL_FILENAME + ".part");

        // Check if already fully downloaded
        if (Files.exists(targetFile)) {
            ColdSpawnControl.LOGGER.info("Model file already exists: {}", targetFile);
            return targetFile;
        }

        ColdSpawnControl.LOGGER.info("Downloading model from HuggingFace...");
        ColdSpawnControl.LOGGER.info("  URL: {}", MODEL_URL);
        ColdSpawnControl.LOGGER.info("  Target: {}", targetFile);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(MODEL_URL);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestProperty("User-Agent", "Solastalgia-Minecraft-Mod/1.0");

            // Support resume if partial download exists
            long existingBytes = 0;
            if (Files.exists(partFile)) {
                existingBytes = Files.size(partFile);
                conn.setRequestProperty("Range", "bytes=" + existingBytes + "-");
                ColdSpawnControl.LOGGER.info("  Resuming from byte {}", existingBytes);
            }

            int responseCode = conn.getResponseCode();

            // Handle redirects (HuggingFace may redirect to CDN)
            if (responseCode == 301 || responseCode == 302 || responseCode == 307) {
                String redirectUrl = conn.getHeaderField("Location");
                conn.disconnect();
                conn = (HttpURLConnection) new URL(redirectUrl).openConnection();
                conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
                conn.setReadTimeout(READ_TIMEOUT_MS);
                conn.setRequestProperty("User-Agent", "Solastalgia-Minecraft-Mod/1.0");
                if (existingBytes > 0) {
                    conn.setRequestProperty("Range", "bytes=" + existingBytes + "-");
                }
                responseCode = conn.getResponseCode();
            }

            boolean resuming = (responseCode == 206);
            if (responseCode != 200 && responseCode != 206) {
                throw new IOException("HTTP " + responseCode + " from HuggingFace");
            }

            long contentLength = conn.getContentLengthLong();
            long totalBytes = resuming ? (existingBytes + contentLength) : contentLength;

            ColdSpawnControl.LOGGER.info("  Total size: {} MB",
                    totalBytes > 0 ? totalBytes / (1024 * 1024) : "unknown");

            try (InputStream in = conn.getInputStream();
                    OutputStream out = resuming
                            ? Files.newOutputStream(partFile, java.nio.file.StandardOpenOption.APPEND)
                            : Files.newOutputStream(partFile)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                long downloaded = existingBytes;
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    downloaded += bytesRead;

                    // Report progress
                    if (progressCallback != null) {
                        progressCallback.accept(downloaded, totalBytes);
                    }
                }

                out.flush();
            }

            // Atomic rename from .part to final filename
            Files.move(partFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
            ColdSpawnControl.LOGGER.info("Model download complete: {}", targetFile);

            return targetFile;

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    /**
     * Checks if the model file exists at the expected location.
     */
    public static boolean isModelPresent(Path modelsDir) {
        return Files.exists(modelsDir.resolve(MODEL_FILENAME));
    }
}
