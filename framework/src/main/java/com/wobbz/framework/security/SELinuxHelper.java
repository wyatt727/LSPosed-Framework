package com.wobbz.framework.security;

import com.wobbz.framework.development.LoggingHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Provides utility methods for interacting with SELinux (Security-Enhanced Linux) on Android.
 * This includes checking SELinux status, validating file contexts, and attempting to fix contexts.
 *
 * <p>Many operations in this class, particularly those involving {@code chcon} (change context),
 * require root privileges to execute successfully. The methods attempt these operations
 * using {@code su -c} and report success or failure based on the command's exit code.</p>
 *
 * <p>The class also includes specific workarounds, such as for SELinux contexts on OxygenOS devices.</p>
 *
 * <p><b>Note:</b> Direct execution of shell commands can be fragile. This helper should be used
 * with an understanding of its limitations and potential for failure on different Android versions
 * or custom ROMs.</p>
 */
public class SELinuxHelper {
    private static final String TAG = "SELinuxHelper";

    /**
     * Predefined map of typical SELinux contexts expected for various file types or directories
     * managed by the framework. Keys are identifiers (e.g., file extensions or directory names),
     * and values are the expected SELinux context strings.
     */
    private static final Map<String, String> EXPECTED_CONTEXTS;

    static {
        Map<String, String> modifiableContexts = new HashMap<>();
        // Common contexts for app data files.
        modifiableContexts.put("updates", "u:object_r:app_data_file:s0"); // For downloaded update files
        modifiableContexts.put("logs", "u:object_r:app_data_file:s0");    // For log files
        modifiableContexts.put("json", "u:object_r:app_data_file:s0");   // For JSON configuration files
        modifiableContexts.put("dat", "u:object_r:app_data_file:s0");    // Generic data files

        // Context for overlay APKs when handled by an overlay service.
        // This might vary based on Android version or specific overlay mechanisms.
        modifiableContexts.put("overlays_dir", "u:object_r:overlay_service_app_data_file:s0"); // For a directory containing overlays

        // Context for APK files that might be treated as system files (e.g., if placed in certain locations).
        // This is a general example and might need adjustment based on where APKs are handled.
        modifiableContexts.put("apk", "u:object_r:system_file:s0");
        EXPECTED_CONTEXTS = Collections.unmodifiableMap(modifiableContexts);
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private SELinuxHelper() {}

    /**
     * Checks if SELinux is currently in "Enforcing" mode.
     *
     * @return {@code true} if SELinux is determined to be enforcing, {@code false} if it's permissive
     *         or if the status cannot be determined (in which case it defaults to assuming enforcing for safety).
     */
    public static boolean isEnforcing() {
        try {
            Process process = Runtime.getRuntime().exec("getenforce");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String result = reader.readLine();
                boolean enforcing = "Enforcing".equalsIgnoreCase(result);
                LoggingHelper.logInfo(TAG, "SELinux status: " + (result != null ? result : "<unknown>") + " (isEnforcing: " + enforcing + ")");
                return enforcing;
            }
        } catch (IOException e) {
            LoggingHelper.logError(TAG, "IOException while checking SELinux status via getenforce. Assuming enforcing.", e);
            return true; // Default to true (enforcing) for safety if status check fails.
        } catch (Exception e) {
            LoggingHelper.logError(TAG, "Unexpected error checking SELinux status. Assuming enforcing.", e);
            return true;
        }
    }

    /**
     * Validates the SELinux context of a given file against expected patterns.
     *
     * @param file The {@link File} whose SELinux context is to be validated.
     * @return {@code true} if the file exists and its context matches an expected pattern,
     *         {@code false} otherwise, or if an error occurs during validation.
     */
    public static boolean validateContext(File file) {
        if (file == null || !file.exists()) {
            LoggingHelper.logWarning(TAG, "validateContext: File is null or does not exist: " + file);
            return false;
        }

        try {
            // Execute 'ls -Z <filepath>' to get the SELinux context.
            Process process = Runtime.getRuntime().exec(new String[]{"ls", "-Z", file.getAbsolutePath()});
            String result; // Full output line from ls -Z
            String errorOutput = null;

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                result = reader.readLine();
                // Read error stream in case 'ls -Z' fails for some reason
                StringBuilder errorBuilder = new StringBuilder();
                String errorLine;
                while ((errorLine = errorReader.readLine()) != null) {
                    errorBuilder.append(errorLine).append("\n");
                }
                if (errorBuilder.length() > 0) {
                    errorOutput = errorBuilder.toString().trim();
                }
            }
            
            boolean exited = process.waitFor(5, TimeUnit.SECONDS); // Wait for the process with a timeout
            if (!exited || process.exitValue() != 0) {
                LoggingHelper.logError(TAG, "'ls -Z' command failed or timed out for " + file.getAbsolutePath() + ". Exit code: " + (exited ? process.exitValue() : "timeout") + (errorOutput != null ? ". Error: " + errorOutput : ""));
                return false;
            }

            if (result == null || result.trim().isEmpty()) {
                LoggingHelper.logWarning(TAG, "validateContext: 'ls -Z' returned no output for " + file.getAbsolutePath());
                return false;
            }

            // Output is typically like: u:object_r:app_data_file:s0 /path/to/file
            // Or for directories: drwxrwx--x 10 u:object_r:app_data_file:s0 ... /path/to/dir
            // We need to reliably extract the context part.
            String[] parts = result.trim().split("\\s+");
            String actualContext = null;
            for (String part : parts) {
                if (part.matches("u:object_r:[_a-zA-Z0-9]+:s0(:c[0-9]+,c[0-9]+)?")) { // Basic SELinux context pattern
                    actualContext = part;
                    break;
                }
            }

            if (actualContext == null) {
                LoggingHelper.logWarning(TAG, "validateContext: Could not parse SELinux context from output: '" + result + "' for " + file.getAbsolutePath());
                return false;
            }

            String expectedContextPattern = findExpectedContextPattern(file);
            if (expectedContextPattern != null) {
                boolean matches = actualContext.equals(expectedContextPattern); // Exact match is often required
                LoggingHelper.logInfo(TAG, "Context for " + file.getName() + ": '" + actualContext + "'. Expected: '" + expectedContextPattern + "'. Matches: " + matches);
                return matches;
            }
            LoggingHelper.logWarning(TAG, "validateContext: No expected SELinux context pattern found for file: " + file.getAbsolutePath());
            return false; // No pattern to validate against
        } catch (IOException e) {
            LoggingHelper.logError(TAG, "IOException validating context for " + file.getAbsolutePath(), e);
            return false;
        } catch (InterruptedException e) {
            LoggingHelper.logError(TAG, "InterruptedException while validating context for " + file.getAbsolutePath(), e);
            Thread.currentThread().interrupt(); // Restore interrupt status
            return false;
        } catch (Exception e) {
            LoggingHelper.logError(TAG, "Unexpected error validating context for " + file.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Determines the expected SELinux context pattern for a file based on its name, extension, or path.
     *
     * @param file The file for which to find the expected context.
     * @return The expected SELinux context string, or {@code null} if no specific pattern is defined.
     */
    private static String findExpectedContextPattern(File file) {
        String fileName = file.getName().toLowerCase();
        String filePath = file.getAbsolutePath().toLowerCase();

        // Check by specific file extensions first
        int lastDotIndex = fileName.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < fileName.length() - 1) {
            String extension = fileName.substring(lastDotIndex + 1);
            if (EXPECTED_CONTEXTS.containsKey(extension)) {
                return EXPECTED_CONTEXTS.get(extension);
            }
        }

        // Check for directory type hints in the path
        if (file.isDirectory()) {
            if (filePath.contains("/overlays/") && EXPECTED_CONTEXTS.containsKey("overlays_dir")) {
                return EXPECTED_CONTEXTS.get("overlays_dir");
            }
            // Add other directory-specific checks if needed
        }
        
        // Fallback for APKs if not caught by extension (e.g. no extension in map but is an APK)
        if (fileName.endsWith(".apk") && EXPECTED_CONTEXTS.containsKey("apk")) {
            return EXPECTED_CONTEXTS.get("apk");
        }

        LoggingHelper.logVerbose(TAG, "No specific SELinux context pattern found for: " + filePath);
        return null;
    }

    /**
     * Attempts to set the SELinux context of a file to an expected pattern.
     * This operation requires root privileges (uses {@code su -c chcon}).
     *
     * @param file The {@link File} whose context is to be fixed.
     * @return {@code true} if the {@code chcon} command executes successfully (exit code 0),
     *         {@code false} if the file doesn't exist, no expected pattern is found, or the command fails.
     */
    public static boolean fixFileContext(File file) {
        if (file == null || !file.exists()) {
            LoggingHelper.logWarning(TAG, "fixFileContext: File is null or does not exist: " + file);
            return false;
        }

        String expectedContext = findExpectedContextPattern(file);
        if (expectedContext == null) {
            LoggingHelper.logWarning(TAG, "fixFileContext: No expected SELinux context pattern found for " + file.getAbsolutePath() + ". Cannot fix.");
            return false;
        }

        LoggingHelper.logInfo(TAG, "Attempting to set SELinux context to '" + expectedContext + "' for: " + file.getAbsolutePath());
        try {
            // Using array form of exec for better handling of spaces and special characters.
            String command = "chcon " + expectedContext + " " + file.getAbsolutePath();
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
            
            // It's good practice to consume output streams to prevent process blocking.
            new StreamGobbler(process.getInputStream(), "INFO", TAG + "-chcon-out").start();
            new StreamGobbler(process.getErrorStream(), "WARN", TAG + "-chcon-err").start();

            boolean exited = process.waitFor(10, TimeUnit.SECONDS); // Increased timeout for su commands
            if (!exited) {
                LoggingHelper.logError(TAG, "fixFileContext: 'su -c chcon' command timed out for " + file.getAbsolutePath());
                process.destroy(); // Ensure process is killed on timeout
                return false;
            }

            int exitCode = process.exitValue();
            LoggingHelper.logInfo(TAG, "'su -c chcon' for " + file.getName() + " exited with code: " + exitCode);
            return exitCode == 0;
        } catch (IOException e) {
            LoggingHelper.logError(TAG, "IOException while trying to fix SELinux context for " + file.getAbsolutePath(), e);
            return false;
        } catch (InterruptedException e) {
            LoggingHelper.logError(TAG, "InterruptedException while trying to fix SELinux context for " + file.getAbsolutePath(), e);
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception e) {
            LoggingHelper.logError(TAG, "Unexpected error fixing SELinux context for " + file.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Checks if the current device is likely running OxygenOS by querying a system property.
     *
     * @return {@code true} if the {@code ro.oxygen.version} property is found and non-empty, {@code false} otherwise.
     */
    public static boolean isOxygenOSDevice() {
        try {
            Process process = Runtime.getRuntime().exec("getprop ro.oxygen.version");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String result = reader.readLine();
                boolean isOOS = result != null && !result.trim().isEmpty();
                LoggingHelper.logInfo(TAG, "ro.oxygen.version: '" + result + "'. Is OxygenOS: " + isOOS);
                return isOOS;
            }
        } catch (IOException e) {
            LoggingHelper.logError(TAG, "IOException checking for OxygenOS via getprop.", e);
            return false;
        } catch (Exception e) {
            LoggingHelper.logError(TAG, "Unexpected error checking for OxygenOS.", e);
            return false;
        }
    }

    /**
     * Applies a specific SELinux context fix for APK files within a given directory,
     * typically targeting overlay APKs on OxygenOS devices.
     * This operation requires root privileges (uses {@code su -c chcon}).
     *
     * <p>The context applied is {@code u:object_r:vendor_overlay_file:s0}.</p>
     *
     * @param directory The directory containing APK files to be processed. If null or not a directory, no action is taken.
     * @return The number of APK files for which the context was successfully changed.
     */
    public static int applyOxygenOSOverlayFixes(File directory) {
        if (directory == null || !directory.exists() || !directory.isDirectory()) {
            LoggingHelper.logWarning(TAG, "applyOxygenOSOverlayFixes: Directory is null, does not exist, or is not a directory: " + directory);
            return 0;
        }
        if (!isOxygenOSDevice()) {
            LoggingHelper.logInfo(TAG, "Not an OxygenOS device, skipping overlay fixes.");
            return 0;
        }

        LoggingHelper.logInfo(TAG, "Applying OxygenOS overlay SELinux fixes to directory: " + directory.getAbsolutePath());
        int fixedCount = 0;
        File[] apkFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".apk"));

        if (apkFiles == null || apkFiles.length == 0) {
            LoggingHelper.logInfo(TAG, "No APK files found in " + directory.getAbsolutePath() + " for OxygenOS fix.");
            return 0;
        }

        String oxygenOSContext = "u:object_r:vendor_overlay_file:s0";
        for (File apkFile : apkFiles) {
            LoggingHelper.logInfo(TAG, "Attempting OxygenOS fix for: " + apkFile.getName() + " with context: " + oxygenOSContext);
            try {
                String command = "chcon " + oxygenOSContext + " " + apkFile.getAbsolutePath();
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", command});

                new StreamGobbler(process.getInputStream(), "INFO", TAG + "-oos-chcon-out").start();
                new StreamGobbler(process.getErrorStream(), "WARN", TAG + "-oos-chcon-err").start();

                boolean exited = process.waitFor(10, TimeUnit.SECONDS);
                if (!exited) {
                    LoggingHelper.logError(TAG, "OxygenOS fix: 'su -c chcon' command timed out for " + apkFile.getAbsolutePath());
                    process.destroy();
                    continue; // Try next file
                }
                if (process.exitValue() == 0) {
                    fixedCount++;
                    LoggingHelper.logInfo(TAG, "Successfully applied OxygenOS SELinux context to " + apkFile.getName());
                } else {
                    LoggingHelper.logWarning(TAG, "Failed to apply OxygenOS SELinux context to " + apkFile.getName() + ". Exit code: " + process.exitValue());
                }
            } catch (IOException e) {
                LoggingHelper.logError(TAG, "IOException during OxygenOS SELinux fix for " + apkFile.getName(), e);
            } catch (InterruptedException e) {
                LoggingHelper.logError(TAG, "InterruptedException during OxygenOS SELinux fix for " + apkFile.getName(), e);
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                LoggingHelper.logError(TAG, "Unexpected error during OxygenOS SELinux fix for " + apkFile.getName(), e);
            }
        }
        LoggingHelper.logInfo(TAG, "Finished OxygenOS overlay SELinux fixes. Fixed " + fixedCount + " files in " + directory.getName());
        return fixedCount;
    }

    /**
     * Simple stream gobbler thread to consume process output streams and prevent blocking.
     */
    private static class StreamGobbler extends Thread {
        private final InputStreamReader inputStreamReader;
        private final String type;
        private final String logTag;

        StreamGobbler(java.io.InputStream inputStream, String type, String logTag) {
            this.inputStreamReader = new InputStreamReader(inputStream);
            this.type = type.toUpperCase();
            this.logTag = logTag;
        }

        @Override
        public void run() {
            try (BufferedReader reader = new BufferedReader(inputStreamReader)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LoggingHelper.logInfo(logTag, "[" + type + "] " + line);
                }
            } catch (IOException e) {
                // Log error, but don't crash the gobbler thread itself if stream closes unexpectedly
                LoggingHelper.logWarning(logTag, "Error reading stream " + type + ": " + e.getMessage());
            }
        }
    }
} 