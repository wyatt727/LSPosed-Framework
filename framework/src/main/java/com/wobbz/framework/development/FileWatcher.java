package com.wobbz.framework.development;

import io.methvin.watcher.DirectoryWatcher;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * FileWatcher monitors specific directories for Java file changes and triggers
 * hot-reloading when changes are detected. It debounces multiple rapid changes
 * to avoid excessive recompilations.
 */
public class FileWatcher {
    private static final Logger LOGGER = Logger.getLogger(FileWatcher.class.getName());
    private static final long DEBOUNCE_DELAY_MS = 1000; // Debounce delay in milliseconds
    
    private final String sourceDir;
    private final int port;
    private final String adbPath;
    private final String deviceArg;
    private final ScheduledExecutorService executor;
    private CompletableFuture<Void> pendingReload;
    private List<Path> changedFiles;

    public FileWatcher(String sourceDir, int port, String adbPath, String deviceArg) {
        this.sourceDir = sourceDir;
        this.port = port;
        this.adbPath = adbPath;
        this.deviceArg = deviceArg;
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.changedFiles = new ArrayList<>();
        this.pendingReload = null;
    }

    public void start() {
        Path path = Paths.get(sourceDir);
        
        try {
            LOGGER.info("Starting file watcher for directory: " + sourceDir);
            
            DirectoryWatcher watcher = DirectoryWatcher.builder()
                .path(path)
                .listener(event -> {
                    Path filePath = event.path();
                    String fileName = filePath.toString();
                    
                    // Only trigger for Java/Kotlin source files
                    if (fileName.endsWith(".java") || fileName.endsWith(".kt")) {
                        LOGGER.info("Detected change in file: " + fileName);
                        changedFiles.add(filePath);
                        scheduleReload();
                    }
                })
                .build();
            
            watcher.watchAsync();
            LOGGER.info("File watcher started successfully. Waiting for changes...");
            
            // Keep the main thread alive
            Thread.currentThread().join();
            
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error starting file watcher", e);
        } catch (InterruptedException e) {
            LOGGER.info("File watcher interrupted");
            Thread.currentThread().interrupt();
        }
    }

    private synchronized void scheduleReload() {
        // Cancel any pending reload
        if (pendingReload != null && !pendingReload.isDone()) {
            LOGGER.info("Cancelling pending reload due to new changes");
        }
        
        // Schedule a new reload after the debounce delay
        pendingReload = CompletableFuture.runAsync(() -> {
            try {
                // Wait for the debounce period
                Thread.sleep(DEBOUNCE_DELAY_MS);
                
                // Perform the reload
                LOGGER.info("Debounce time elapsed. Processing " + changedFiles.size() + " file changes");
                performReload();
                
                // Clear the list of changed files
                changedFiles.clear();
            } catch (InterruptedException e) {
                LOGGER.info("Reload task interrupted");
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error during reload", e);
            }
        }, executor);
    }

    private void performReload() {
        try {
            LOGGER.info("Building project for hot-reload...");
            
            // Build the project
            ProcessBuilder buildProcess = new ProcessBuilder("./gradlew", "assembleDebug");
            buildProcess.directory(new File(sourceDir));
            buildProcess.redirectErrorStream(true);
            
            Process process = buildProcess.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.info("[Build] " + line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                LOGGER.severe("Build failed with exit code " + exitCode);
                return;
            }
            
            LOGGER.info("Build successful. Pushing update to device...");
            
            // Push the updated module to the device
            String hotSwapCmd = String.format("%s %s shell am broadcast -a com.wobbz.framework.HOT_RELOAD", 
                    adbPath, deviceArg);
            
            Process hotSwapProcess = Runtime.getRuntime().exec(hotSwapCmd);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(hotSwapProcess.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LOGGER.info("[HotSwap] " + line);
                }
            }
            
            exitCode = hotSwapProcess.waitFor();
            if (exitCode != 0) {
                LOGGER.severe("Hot-swap failed with exit code " + exitCode);
                return;
            }
            
            LOGGER.info("Hot-reload successful!");
            
        } catch (IOException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Error during hot-reload", e);
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            LOGGER.severe("Usage: FileWatcher <source-directory> <port> [adb-path] [device-arg]");
            System.exit(1);
        }
        
        String sourceDir = args[0];
        int port = Integer.parseInt(args[1]);
        String adbPath = args.length > 2 ? args[2] : "adb";
        String deviceArg = args.length > 3 ? args[3] : "";
        
        FileWatcher watcher = new FileWatcher(sourceDir, port, adbPath, deviceArg);
        watcher.start();
    }
} 