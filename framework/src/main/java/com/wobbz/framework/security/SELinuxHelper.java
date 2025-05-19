package com.wobbz.framework.security;

import android.util.Log;

import com.wobbz.framework.development.LoggingHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper class for SELinux context management and validation.
 */
public class SELinuxHelper {
    private static final String TAG = "SELinuxHelper";
    
    private static final Map<String, String> CONTEXT_PATTERNS = new HashMap<>();
    
    static {
        // Initialize the context patterns for different file types
        CONTEXT_PATTERNS.put("updates", "u:object_r:app_data_file:s0");
        CONTEXT_PATTERNS.put("overlays", "u:object_r:overlay_service_app_data_file:s0");
        CONTEXT_PATTERNS.put("logs", "u:object_r:app_data_file:s0");
        CONTEXT_PATTERNS.put("json", "u:object_r:app_data_file:s0");
        CONTEXT_PATTERNS.put("apk", "u:object_r:system_file:s0");
    }
    
    /**
     * Check if SELinux is enabled and in enforcing mode.
     * 
     * @return true if SELinux is enforcing, false otherwise.
     */
    public static boolean isEnforcing() {
        try {
            Process process = Runtime.getRuntime().exec("getenforce");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String result = reader.readLine();
            return "Enforcing".equalsIgnoreCase(result);
        } catch (IOException e) {
            LoggingHelper.error(TAG, "Error checking SELinux status", e);
            // Assume enforcing for safety
            return true;
        }
    }
    
    /**
     * Validate the context of a file.
     * 
     * @param file The file to validate.
     * @return true if the context is valid, false otherwise.
     */
    public static boolean validateContext(File file) {
        if (!file.exists()) {
            return false;
        }
        
        try {
            Process process = Runtime.getRuntime().exec("ls -Z " + file.getAbsolutePath());
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String result = reader.readLine();
            
            if (result == null) {
                return false;
            }
            
            // Extract the context from the output
            String[] parts = result.split("\\s+");
            if (parts.length < 2) {
                return false;
            }
            
            String context = parts[0];
            
            // Check if the context matches the expected pattern
            String expectedPattern = getExpectedContextPattern(file);
            if (expectedPattern != null) {
                return context.contains(expectedPattern);
            }
            
            return false;
        } catch (IOException e) {
            LoggingHelper.error(TAG, "Error validating context for " + file.getAbsolutePath(), e);
            return false;
        }
    }
    
    /**
     * Get the expected context pattern for a file based on its type.
     * 
     * @param file The file to check.
     * @return The expected context pattern, or null if unknown.
     */
    private static String getExpectedContextPattern(File file) {
        String path = file.getAbsolutePath();
        String extension = getFileExtension(file);
        
        // Check for specific file extensions
        if (extension != null && CONTEXT_PATTERNS.containsKey(extension)) {
            return CONTEXT_PATTERNS.get(extension);
        }
        
        // Check for directory types
        for (Map.Entry<String, String> entry : CONTEXT_PATTERNS.entrySet()) {
            if (path.contains("/" + entry.getKey() + "/")) {
                return entry.getValue();
            }
        }
        
        return null;
    }
    
    /**
     * Get the file extension.
     * 
     * @param file The file to check.
     * @return The file extension, or null if none.
     */
    private static String getFileExtension(File file) {
        String name = file.getName();
        int lastDotIndex = name.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < name.length() - 1) {
            return name.substring(lastDotIndex + 1);
        }
        return null;
    }
    
    /**
     * Fix the context of a file.
     * 
     * @param file The file to fix.
     * @return true if the context was fixed successfully, false otherwise.
     */
    public static boolean fixContext(File file) {
        if (!file.exists()) {
            return false;
        }
        
        String expectedPattern = getExpectedContextPattern(file);
        if (expectedPattern == null) {
            return false;
        }
        
        try {
            Process process = Runtime.getRuntime().exec("su -c chcon " + expectedPattern + " " + file.getAbsolutePath());
            int exitCode = process.waitFor();
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            LoggingHelper.error(TAG, "Error fixing context for " + file.getAbsolutePath(), e);
            return false;
        }
    }
    
    /**
     * Check if we're running on OxygenOS.
     * 
     * @return true if running on OxygenOS, false otherwise.
     */
    public static boolean isOxygenOS() {
        try {
            Process process = Runtime.getRuntime().exec("getprop ro.oxygen.version");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String result = reader.readLine();
            return result != null && !result.isEmpty();
        } catch (IOException e) {
            LoggingHelper.error(TAG, "Error checking for OxygenOS", e);
            return false;
        }
    }
    
    /**
     * Apply OxygenOS-specific context fixes.
     * 
     * @param directory The overlays directory.
     * @return The number of fixed contexts.
     */
    public static int applyOxygenOSFixes(File directory) {
        if (!directory.exists() || !directory.isDirectory() || !isOxygenOS()) {
            return 0;
        }
        
        int fixedCount = 0;
        File[] files = directory.listFiles(file -> file.getName().endsWith(".apk"));
        if (files != null) {
            for (File file : files) {
                try {
                    Process process = Runtime.getRuntime().exec("su -c chcon u:object_r:vendor_overlay_file:s0 " + file.getAbsolutePath());
                    int exitCode = process.waitFor();
                    if (exitCode == 0) {
                        fixedCount++;
                        LoggingHelper.info(TAG, "Fixed OxygenOS context for " + file.getName());
                    }
                } catch (IOException | InterruptedException e) {
                    LoggingHelper.error(TAG, "Error fixing OxygenOS context for " + file.getName(), e);
                }
            }
        }
        
        return fixedCount;
    }
} 