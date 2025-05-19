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

import de.robv.android.xposed.XposedBridge;

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
    
    /**
     * Initialize the logging helper.
     * Load configuration from file if available.
     */
    public static void init() {
        loadConfig();
    }
    
    /**
     * Load configuration from file.
     */
    private static void loadConfig() {
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                
                Gson gson = new Gson();
                sConfig = gson.fromJson(json.toString(), LogConfig.class);
                sConfigLoaded = true;
                
                info(DEFAULT_TAG, "Loaded logging configuration from " + CONFIG_FILE);
            } catch (IOException e) {
                error(DEFAULT_TAG, "Error loading logging configuration", e);
            }
        } else {
            // Use default configuration
            sConfig = new LogConfig();
            sConfigLoaded = true;
        }
    }
    
    /**
     * Check if the given log level should be logged for the given tag.
     * 
     * @param tag The log tag.
     * @param level The log level.
     * @return true if the log should be output, false otherwise.
     */
    private static boolean shouldLog(String tag, int level) {
        // Force reload config if not loaded
        if (!sConfigLoaded) {
            loadConfig();
        }
        
        // Check if there's a specific level for this tag
        Integer tagLevel = sConfig.tagLevels.get(tag);
        if (tagLevel != null) {
            return level >= tagLevel;
        }
        
        // Use global level
        return level >= sConfig.globalLevel;
    }
    
    /**
     * Log a verbose message.
     * 
     * @param tag The log tag.
     * @param message The message to log.
     */
    public static void verbose(String tag, String message) {
        if (shouldLog(tag, LEVEL_VERBOSE)) {
            if (sConfig.useXposedLog) {
                XposedBridge.log("[V/" + tag + "] " + message);
            } else {
                Log.v(tag, message);
            }
        }
    }
    
    /**
     * Log a debug message.
     * 
     * @param tag The log tag.
     * @param message The message to log.
     */
    public static void debug(String tag, String message) {
        if (shouldLog(tag, LEVEL_DEBUG)) {
            if (sConfig.useXposedLog) {
                XposedBridge.log("[D/" + tag + "] " + message);
            } else {
                Log.d(tag, message);
            }
        }
    }
    
    /**
     * Log an info message.
     * 
     * @param tag The log tag.
     * @param message The message to log.
     */
    public static void info(String tag, String message) {
        if (shouldLog(tag, LEVEL_INFO)) {
            if (sConfig.useXposedLog) {
                XposedBridge.log("[I/" + tag + "] " + message);
            } else {
                Log.i(tag, message);
            }
        }
    }
    
    /**
     * Log a warning message.
     * 
     * @param tag The log tag.
     * @param message The message to log.
     */
    public static void warn(String tag, String message) {
        if (shouldLog(tag, LEVEL_WARN)) {
            if (sConfig.useXposedLog) {
                XposedBridge.log("[W/" + tag + "] " + message);
            } else {
                Log.w(tag, message);
            }
        }
    }
    
    /**
     * Log a warning message with a throwable.
     * 
     * @param tag The log tag.
     * @param message The message to log.
     * @param throwable The throwable to log.
     */
    public static void warn(String tag, String message, Throwable throwable) {
        if (shouldLog(tag, LEVEL_WARN)) {
            if (sConfig.useXposedLog) {
                XposedBridge.log("[W/" + tag + "] " + message);
                XposedBridge.log(throwable);
            } else {
                Log.w(tag, message, throwable);
            }
        }
    }
    
    /**
     * Log an error message.
     * 
     * @param tag The log tag.
     * @param message The message to log.
     */
    public static void error(String tag, String message) {
        if (shouldLog(tag, LEVEL_ERROR)) {
            if (sConfig.useXposedLog) {
                XposedBridge.log("[E/" + tag + "] " + message);
            } else {
                Log.e(tag, message);
            }
        }
    }
    
    /**
     * Log an error message with a throwable.
     * 
     * @param tag The log tag.
     * @param message The message to log.
     * @param throwable The throwable to log.
     */
    public static void error(String tag, String message, Throwable throwable) {
        if (shouldLog(tag, LEVEL_ERROR)) {
            if (sConfig.useXposedLog) {
                XposedBridge.log("[E/" + tag + "] " + message);
                XposedBridge.log(throwable);
            } else {
                Log.e(tag, message, throwable);
            }
        }
    }
    
    /**
     * Log configuration class.
     */
    public static class LogConfig {
        @SerializedName("globalLevel")
        public int globalLevel = DEFAULT_LOG_LEVEL;
        
        @SerializedName("useXposedLog")
        public boolean useXposedLog = true;
        
        @SerializedName("tagLevels")
        public Map<String, Integer> tagLevels = new HashMap<>();
    }
} 