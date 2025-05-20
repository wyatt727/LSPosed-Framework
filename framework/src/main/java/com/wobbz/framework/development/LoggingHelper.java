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
 * Provides a centralized and configurable logging utility for the Wobbz LSPosed Modular Framework
 * and its modules.
 *
 * <p>This helper class supports multiple log levels (VERBOSE, DEBUG, INFO, WARN, ERROR, NONE)
 * and allows dynamic configuration of these levels on a per-tag basis. The configuration
 * is loaded from a JSON file located at {@code /data/data/com.wobbz.lsposedframework/files/log-config.json}.
 * If the configuration file is not found or is invalid, default log levels are used.</p>
 *
 * <p>Logging output is directed to {@link XposedInterface#log(String)} if an Xposed interface
 * instance has been provided via {@link #setXposedInterface(XposedInterface)}. Otherwise, it falls back
 * to the standard Android {@link android.util.Log}.</p>
 *
 * <p>Example {@code log-config.json}:</p>
 * <pre>{@code
 * {
 *   "defaultLevel": 2, // INFO
 *   "tagLevels": {
 *     "MyModuleTag": 0, // VERBOSE for MyModuleTag
 *     "AnotherTag": 4    // ERROR for AnotherTag
 *   }
 * }
 * }</pre>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * LoggingHelper.setXposedInterface(xposedInterface); // Early in module init
 * LoggingHelper.init(); // Load config
 *
 * LoggingHelper.logInfo("MyTag", "This is an info message.");
 * LoggingHelper.logError("MyTag", "An error occurred", exception);
 * }</pre>
 */
public class LoggingHelper {
    // Log levels constants are public for use in configuration or direct checks.
    /** Log level for verbose messages. Lowest priority, most detailed. */
    public static final int LEVEL_VERBOSE = 0;
    /** Log level for debug messages. */
    public static final int LEVEL_DEBUG = 1;
    /** Log level for informational messages. This is often the default. */
    public static final int LEVEL_INFO = 2;
    /** Log level for warning messages. Indicates potential issues. */
    public static final int LEVEL_WARN = 3;
    /** Log level for error messages. Indicates functional errors. */
    public static final int LEVEL_ERROR = 4;
    /** Special log level to disable all logging for a tag or globally. */
    public static final int LEVEL_NONE = 5;

    /**
     * Path to the log configuration file. The package name `com.wobbz.lsposedframework` corresponds
     * to the ID of the core framework module as defined in its `module.prop`.
     */
    private static final String CONFIG_FILE_PATH = "/data/data/com.wobbz.lsposedframework/files/log-config.json";
    private static final String DEFAULT_TAG = "LSPosedFramework"; // Default tag for LoggingHelper's own messages
    private static final int DEFAULT_GLOBAL_LOG_LEVEL = LEVEL_INFO; // Default global log level if config is absent

    private static LogConfig sLogConfig = new LogConfig(); // Holds the current configuration
    private static boolean sConfigLoaded = false;
    private static XposedInterface sXposedInterface; // Xposed interface for logging

    // Static initializer block to load config once when the class is loaded.
    static {
        init();
    }

    /**
     * Private constructor to prevent instantiation of this utility class.
     */
    private LoggingHelper() {}

    /**
     * Initializes the logging helper by loading the log configuration.
     * This is called automatically when the class is loaded, but can be called again to attempt a reload.
     * It is synchronized to prevent concurrent modification issues if called manually.
     */
    public static synchronized void init() {
        loadConfigFromFile();
    }

    /**
     * Sets the {@link XposedInterface} to be used for directing log output.
     * If not set, logs will fall back to {@link android.util.Log}.
     *
     * @param xposedInterface The {@link XposedInterface} instance. Can be null to clear.
     */
    public static void setXposedInterface(XposedInterface xposedInterface) {
        sXposedInterface = xposedInterface;
    }

    /**
     * Loads the log configuration from the JSON file specified by {@link #CONFIG_FILE_PATH}.
     * If the file doesn't exist or an error occurs during parsing, a default configuration is used.
     * This method is synchronized to ensure thread-safe loading of the configuration.
     */
    private static synchronized void loadConfigFromFile() {
        File configFile = new File(CONFIG_FILE_PATH);
        if (!configFile.exists() || !configFile.canRead()) {
            if (sConfigLoaded) { // Avoid logging repeatedly if already tried and failed
                 Log.i(DEFAULT_TAG, "Log config file not found or not readable: " + CONFIG_FILE_PATH + ". Using default log levels.");
            } else {
                 System.out.println(DEFAULT_TAG + ": Log config file not found or not readable: " + CONFIG_FILE_PATH + ". Using default log levels.");
            }
            sLogConfig = new LogConfig(); // Use default config
            sConfigLoaded = true; // Mark as loaded (even if default)
            return;
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            Gson gson = new Gson();
            sLogConfig = gson.fromJson(builder.toString(), LogConfig.class);
            if (sLogConfig == null) { // Possible if JSON is empty or malformed leading to null
                sLogConfig = new LogConfig();
                Log.w(DEFAULT_TAG, "Log config file was empty or malformed. Using default log levels.");
            } else {
                 Log.i(DEFAULT_TAG, "Successfully loaded log configuration from: " + CONFIG_FILE_PATH);
            }
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            Log.e(DEFAULT_TAG, "Error loading or parsing log config from " + CONFIG_FILE_PATH + ". Using default log levels.", e);
            sLogConfig = new LogConfig(); // Use default config on error
        }
        sConfigLoaded = true;
    }

    /**
     * Determines the effective log level for a given tag based on the loaded configuration.
     * If the configuration hasn't been loaded, it triggers a load attempt.
     *
     * @param tag The log tag (e.g., module name or class name).
     * @return The integer value of the log level (e.g., {@link #LEVEL_INFO}).
     */
    private static int getLogLevelForTag(String tag) {
        if (!sConfigLoaded) {
            // This might happen if init() wasn't called or failed silently before first log attempt.
            // Attempting to load config here ensures robustness, but it's better if init() succeeds first.
            Log.w(DEFAULT_TAG, "Log config not loaded when getLogLevelForTag was called. Attempting load.");
            loadConfigFromFile();
        }
        return sLogConfig.tagLevels.getOrDefault(tag, sLogConfig.defaultLevel);
    }

    /**
     * Logs a message at the VERBOSE level if the configured log level for the given tag allows it.
     *
     * @param tag The log tag.
     * @param message The message to log.
     */
    public static void logVerbose(String tag, String message) {
        if (getLogLevelForTag(tag) <= LEVEL_VERBOSE) {
            performLog(LEVEL_VERBOSE, tag, message, null);
        }
    }

    /**
     * Logs a message at the DEBUG level if the configured log level for the given tag allows it.
     *
     * @param tag The log tag.
     * @param message The message to log.
     */
    public static void logDebug(String tag, String message) {
        if (getLogLevelForTag(tag) <= LEVEL_DEBUG) {
            performLog(LEVEL_DEBUG, tag, message, null);
        }
    }

    /**
     * Logs a message at the INFO level if the configured log level for the given tag allows it.
     *
     * @param tag The log tag.
     * @param message The message to log.
     */
    public static void logInfo(String tag, String message) {
        if (getLogLevelForTag(tag) <= LEVEL_INFO) {
            performLog(LEVEL_INFO, tag, message, null);
        }
    }

    /**
     * Logs a message at the WARN level if the configured log level for the given tag allows it.
     *
     * @param tag The log tag.
     * @param message The message to log.
     */
    public static void logWarning(String tag, String message) {
        if (getLogLevelForTag(tag) <= LEVEL_WARN) {
            performLog(LEVEL_WARN, tag, message, null);
        }
    }

    /**
     * Logs a message at the ERROR level if the configured log level for the given tag allows it.
     *
     * @param tag The log tag.
     * @param message The message to log.
     */
    public static void logError(String tag, String message) {
        if (getLogLevelForTag(tag) <= LEVEL_ERROR) {
            performLog(LEVEL_ERROR, tag, message, null);
        }
    }

    /**
     * Logs a message and a {@link Throwable} at the ERROR level if the configured log level for the given tag allows it.
     *
     * @param tag The log tag.
     * @param message The message to log.
     * @param throwable The {@link Throwable} to log (including its stack trace).
     */
    public static void logError(String tag, String message, Throwable throwable) {
        if (getLogLevelForTag(tag) <= LEVEL_ERROR) {
            performLog(LEVEL_ERROR, tag, message, throwable);
        }
    }
    
    /**
     * Helper method to perform the actual logging via XposedInterface or android.util.Log.
     *
     * @param level The log level (e.g., LEVEL_INFO).
     * @param tag The log tag.
     * @param message The message.
     * @param throwable Optional throwable to log.
     */
    private static void performLog(int level, String tag, String message, Throwable throwable) {
        String fullMessage = "[" + tag + "] " + message;
        if (throwable != null && level >= LEVEL_WARN) { // Typically only log full throwable for WARN and ERROR
            fullMessage += "\n" + getFormattedStackTrace(throwable, tag);
        }

        if (sXposedInterface != null) {
            // XposedInterface.log only takes a string, so we format everything into the message.
            // It doesn't have distinct levels like android.util.Log beyond simple logging.
            sXposedInterface.log(fullMessage);
        } else {
            // Fallback to android.util.Log if XposedInterface is not available
            switch (level) {
                case LEVEL_VERBOSE:
                    Log.v(tag, message, throwable);
                    break;
                case LEVEL_DEBUG:
                    Log.d(tag, message, throwable);
                    break;
                case LEVEL_INFO:
                    Log.i(tag, message, throwable);
                    break;
                case LEVEL_WARN:
                    Log.w(tag, message, throwable);
                    break;
                case LEVEL_ERROR:
                    Log.e(tag, message, throwable);
                    break;
                // LEVEL_NONE is handled by the check in public log methods
            }
        }
    }

    /**
     * Converts a {@link Throwable}'s stack trace to a formatted string.
     * This method is designed to be more concise than the default {@code Log.getStackTraceString}.
     *
     * @param throwable The {@link Throwable} to format.
     * @param tag       The tag prepended to each line of the stack trace for better filterability in Xposed logs.
     * @return A string representation of the stack trace, or an empty string if throwable is null.
     */
    private static String getFormattedStackTrace(Throwable throwable, String tag) {
        if (throwable == null) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[").append(tag).append("] ").append(throwable.toString()).append("\n");

        for (StackTraceElement element : throwable.getStackTrace()) {
            sb.append("[").append(tag).append("] 	").append("at ").append(element.toString()).append("\n");
        }

        Throwable cause = throwable.getCause();
        if (cause != null) {
            // Recursively append cause, indenting it slightly for clarity
            sb.append("[").append(tag).append("] ").append("Caused by: ");
            sb.append(getFormattedStackTrace(cause, tag)); // Recursive call will prepend [tag]
        }
        return sb.toString().trim(); // Trim trailing newline
    }

    /**
     * Internal class representing the structure of the {@code log-config.json} file.
     */
    private static class LogConfig {
        @SerializedName("defaultLevel")
        int defaultLevel = DEFAULT_GLOBAL_LOG_LEVEL;

        @SerializedName("tagLevels")
        Map<String, Integer> tagLevels = new HashMap<>();
    }
} 