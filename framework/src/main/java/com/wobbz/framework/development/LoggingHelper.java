package com.wobbz.framework.development;

import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import io.github.libxposed.api.XposedInterface;

/**
 * Helper class for logging in modules.
 * Supports dynamic log levels from config file.
 */
public class LoggingHelper {
    // Log levels
    public static final int LEVEL_VERBOSE = 0;
    public static final int LEVEL_DEBUG = 1;
    public static final int LEVEL_INFO = 2;
    public static final int LEVEL_WARN = 3;
    public static final int LEVEL_ERROR = 4;
    public static final int LEVEL_NONE = 5;
    
    private static final String CONFIG_FILE = "/data/data/com.wobbz.lsposedframework/files/log-config.json";
    private static final String DEFAULT_TAG = "LSPosedFramework";
    private static final int DEFAULT_LOG_LEVEL = LEVEL_INFO;
    
    private static LogConfig sConfig = new LogConfig();
    private static boolean sConfigLoaded = false;
    private static XposedInterface sXposedInterface;
    
    /**
     * Initialize the logging helper.
     * Load configuration from file if available.
     */
    public static void init() {
        loadConfig();
    }
    
    /**
     * Set the XposedInterface for logging
     * 
     * @param xposedInterface The XposedInterface to use for logging
     */
    public static void setXposedInterface(XposedInterface xposedInterface) {
        sXposedInterface = xposedInterface;
    }
    
    /**
     * Load configuration from file.
     */
    private static void loadConfig() {
        try {
            File configFile = new File(CONFIG_FILE);
            if (!configFile.exists()) {
                // Use default config
                sConfig = new LogConfig();
                sConfigLoaded = true;
                return;
            }
            
            // Read the file
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }
            
            // Parse the config
            Gson gson = new Gson();
            sConfig = gson.fromJson(builder.toString(), LogConfig.class);
            sConfigLoaded = true;
            
        } catch (IOException e) {
            Log.e(DEFAULT_TAG, "Error loading log config", e);
            
            // Use default config
            sConfig = new LogConfig();
            sConfigLoaded = true;
        }
    }
    
    /**
     * Get the log level for a tag.
     * 
     * @param tag The tag.
     * @return The log level.
     */
    private static int getLogLevel(String tag) {
        if (!sConfigLoaded) {
            loadConfig();
        }
        
        // Check override for this tag
        Integer level = sConfig.tagLevels.get(tag);
        if (level != null) {
            return level;
        }
        
        // Use default level
        return sConfig.defaultLevel;
    }
    
    /**
     * Log a verbose message.
     * 
     * @param tag The tag.
     * @param message The message.
     */
    public static void verbose(String tag, String message) {
        if (getLogLevel(tag) <= LEVEL_VERBOSE) {
            log(tag, message);
        }
    }
    
    /**
     * Log a debug message.
     * 
     * @param tag The tag.
     * @param message The message.
     */
    public static void debug(String tag, String message) {
        if (getLogLevel(tag) <= LEVEL_DEBUG) {
            log(tag, message);
        }
    }
    
    /**
     * Log an info message.
     * 
     * @param tag The tag.
     * @param message The message.
     */
    public static void info(String tag, String message) {
        if (getLogLevel(tag) <= LEVEL_INFO) {
            log(tag, message);
        }
    }
    
    /**
     * Log a warning message.
     * 
     * @param tag The tag.
     * @param message The message.
     */
    public static void warning(String tag, String message) {
        if (getLogLevel(tag) <= LEVEL_WARN) {
            log(tag, message);
        }
    }
    
    /**
     * Log an error message.
     * 
     * @param tag The tag.
     * @param message The message.
     */
    public static void error(String tag, String message) {
        if (getLogLevel(tag) <= LEVEL_ERROR) {
            log(tag, message);
        }
    }
    
    /**
     * Log an error message with an exception.
     * 
     * @param tag The tag.
     * @param message The message.
     * @param throwable The exception.
     */
    public static void error(String tag, String message, Throwable throwable) {
        if (getLogLevel(tag) <= LEVEL_ERROR) {
            log(tag, message + ": " + throwable.getMessage());
            // Convert throwable to string instead of passing it directly
            String stackTrace = getStackTraceString(throwable);
            log(tag, stackTrace);
        }
    }
    
    /**
     * Modern naming for logging methods - logs at INFO level
     * 
     * @param tag The tag.
     * @param message The message.
     */
    public static void logInfo(String tag, String message) {
        info(tag, message);
    }
    
    /**
     * Modern naming for logging methods - logs at DEBUG level
     * 
     * @param tag The tag.
     * @param message The message.
     */
    public static void logDebug(String tag, String message) {
        debug(tag, message);
    }
    
    /**
     * Modern naming for logging methods - logs an error with a Throwable
     * 
     * @param tag The tag.
     * @param throwable The exception.
     */
    public static void logError(String tag, Throwable throwable) {
        error(tag, "Error", throwable);
    }
    
    /**
     * Convert a Throwable stack trace to a String
     * 
     * @param throwable The throwable
     * @return String representation of the stack trace
     */
    private static String getStackTraceString(Throwable throwable) {
        if (throwable == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(throwable.toString()).append("\n");
        
        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("\tat ").append(element.toString()).append("\n");
        }
        
        Throwable cause = throwable.getCause();
        if (cause != null) {
            sb.append("Caused by: ").append(getStackTraceString(cause));
        }
        
        return sb.toString();
    }
    
    /**
     * Log a message using XposedBridge.
     * 
     * @param tag The tag.
     * @param message The message.
     */
    private static void log(String tag, String message) {
        try {
            String formattedMessage = "[" + tag + "] " + message;
            
            // Use XposedInterface log if available
            if (sXposedInterface != null) {
                sXposedInterface.log(formattedMessage);
            } else {
                // Fallback to Android Log
                Log.d(DEFAULT_TAG, formattedMessage);
            }
        } catch (Throwable t) {
            // Last resort fallback
            try {
                Log.e(DEFAULT_TAG, "Error logging: " + t.getMessage());
                Log.d(DEFAULT_TAG, "[" + tag + "] " + message);
            } catch (Throwable ignored) {
                // Nothing more we can do
            }
        }
    }
    
    /**
     * Log configuration.
     */
    private static class LogConfig {
        @SerializedName("defaultLevel")
        int defaultLevel = DEFAULT_LOG_LEVEL;
        
        @SerializedName("tagLevels")
        Map<String, Integer> tagLevels = new HashMap<>();
    }
} 