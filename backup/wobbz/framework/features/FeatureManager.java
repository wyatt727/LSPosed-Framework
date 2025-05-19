package com.wobbz.framework.features;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.wobbz.framework.development.LoggingHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages feature toggles for modules.
 */
public class FeatureManager {
    private static final String TAG = "FeatureManager";
    private static final String CONFIG_FILE = "/data/data/com.wobbz.lsposedframework/files/features.json";
    private static final long CONFIG_CHECK_INTERVAL = 30; // seconds
    
    private static FeatureManager sInstance;
    
    private final Context mContext;
    private FeatureConfig mConfig;
    private final List<FeatureChangeListener> mListeners = new ArrayList<>();
    private final ScheduledExecutorService mExecutor = Executors.newSingleThreadScheduledExecutor();
    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    
    /**
     * Private constructor for singleton pattern.
     */
    private FeatureManager(Context context) {
        mContext = context.getApplicationContext();
        loadConfig();
        
        // Start periodic config reload
        mExecutor.scheduleAtFixedRate(this::loadConfig, CONFIG_CHECK_INTERVAL, 
                                     CONFIG_CHECK_INTERVAL, TimeUnit.SECONDS);
    }
    
    /**
     * Get the singleton instance.
     */
    public static synchronized FeatureManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new FeatureManager(context);
        }
        return sInstance;
    }
    
    /**
     * Add a listener for feature change events.
     */
    public void addListener(FeatureChangeListener listener) {
        if (!mListeners.contains(listener)) {
            mListeners.add(listener);
        }
    }
    
    /**
     * Remove a listener for feature change events.
     */
    public void removeListener(FeatureChangeListener listener) {
        mListeners.remove(listener);
    }
    
    /**
     * Check if a feature is enabled.
     * 
     * @param featureId The feature ID.
     * @return true if the feature is enabled, false otherwise.
     */
    public boolean isFeatureEnabled(String featureId) {
        if (mConfig == null) {
            loadConfig();
        }
        
        // Check global enabled state
        if (!mConfig.globalEnabled) {
            return false;
        }
        
        // Check feature enabled state
        FeatureInfo info = mConfig.features.get(featureId);
        if (info == null) {
            return false;
        }
        
        // Check device-specific override
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        JsonObject deviceConfig = mConfig.deviceSpecific.get(manufacturer);
        if (deviceConfig != null && deviceConfig.has(featureId)) {
            JsonObject featureConfig = deviceConfig.getAsJsonObject(featureId);
            if (featureConfig.has("enabled")) {
                return featureConfig.get("enabled").getAsBoolean();
            }
        }
        
        return info.enabled;
    }
    
    /**
     * Check if a feature is enabled for a specific package.
     * 
     * @param featureId The feature ID.
     * @param packageName The package name.
     * @return true if the feature is enabled for the package, false otherwise.
     */
    public boolean isFeatureEnabledForPackage(String featureId, String packageName) {
        if (!isFeatureEnabled(featureId)) {
            return false;
        }
        
        FeatureInfo info = mConfig.features.get(featureId);
        if (info == null) {
            return false;
        }
        
        // Check if explicitly disabled for this package
        if (info.disabledForPackages.contains(packageName)) {
            return false;
        }
        
        // Check if explicitly enabled for this package
        if (info.enabledForPackages.contains(packageName)) {
            return true;
        }
        
        // If enabledForPackages is empty, the feature is enabled for all packages
        return info.enabledForPackages.isEmpty();
    }
    
    /**
     * Get custom settings for a feature on the current device.
     * 
     * @param featureId The feature ID.
     * @return The custom settings, or null if none.
     */
    public JsonObject getCustomSettings(String featureId) {
        if (mConfig == null) {
            loadConfig();
        }
        
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        JsonObject deviceConfig = mConfig.deviceSpecific.get(manufacturer);
        if (deviceConfig != null && deviceConfig.has(featureId)) {
            JsonObject featureConfig = deviceConfig.getAsJsonObject(featureId);
            if (featureConfig.has("customSettings")) {
                return featureConfig.getAsJsonObject("customSettings");
            }
        }
        
        return null;
    }
    
    /**
     * Load the configuration from file.
     */
    private synchronized void loadConfig() {
        try {
            File configFile = new File(CONFIG_FILE);
            if (!configFile.exists()) {
                // Use default config
                mConfig = new FeatureConfig();
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
            FeatureConfig newConfig = gson.fromJson(builder.toString(), FeatureConfig.class);
            
            // Detect changes
            if (mConfig != null) {
                detectChanges(mConfig, newConfig);
            }
            
            mConfig = newConfig;
            LoggingHelper.debug(TAG, "Loaded feature config: " + 
                               (mConfig.globalEnabled ? "enabled" : "disabled") + ", " + 
                               mConfig.features.size() + " features");
            
        } catch (IOException e) {
            LoggingHelper.error(TAG, "Error loading feature config", e);
            
            // Use default config
            if (mConfig == null) {
                mConfig = new FeatureConfig();
            }
        }
    }
    
    /**
     * Detect changes between old and new configs and notify listeners.
     */
    private void detectChanges(FeatureConfig oldConfig, FeatureConfig newConfig) {
        // Check global enabled state
        if (oldConfig.globalEnabled != newConfig.globalEnabled) {
            for (FeatureInfo info : oldConfig.features.values()) {
                if (info.enabled) {
                    notifyFeatureChanged(info.id, !newConfig.globalEnabled);
                }
            }
        }
        
        // Check individual features
        for (Map.Entry<String, FeatureInfo> entry : oldConfig.features.entrySet()) {
            String featureId = entry.getKey();
            FeatureInfo oldInfo = entry.getValue();
            FeatureInfo newInfo = newConfig.features.get(featureId);
            
            if (newInfo == null) {
                // Feature removed
                if (oldInfo.enabled) {
                    notifyFeatureChanged(featureId, false);
                }
            } else if (oldInfo.enabled != newInfo.enabled) {
                // Enabled state changed
                notifyFeatureChanged(featureId, newInfo.enabled);
            }
        }
        
        // Check new features
        for (Map.Entry<String, FeatureInfo> entry : newConfig.features.entrySet()) {
            String featureId = entry.getKey();
            FeatureInfo newInfo = entry.getValue();
            
            if (!oldConfig.features.containsKey(featureId) && newInfo.enabled) {
                // New enabled feature
                notifyFeatureChanged(featureId, true);
            }
        }
    }
    
    /**
     * Notify listeners of a feature change.
     */
    private void notifyFeatureChanged(String featureId, boolean enabled) {
        // Notify on main thread
        mMainHandler.post(() -> {
            for (FeatureChangeListener listener : mListeners) {
                listener.onFeatureChanged(featureId, enabled);
            }
        });
    }
    
    /**
     * Feature configuration.
     */
    private static class FeatureConfig {
        @SerializedName("globalEnabled")
        boolean globalEnabled = true;
        
        @SerializedName("features")
        Map<String, FeatureInfo> features = new HashMap<>();
        
        @SerializedName("deviceSpecific")
        Map<String, JsonObject> deviceSpecific = new HashMap<>();
    }
    
    /**
     * Feature information.
     */
    private static class FeatureInfo {
        @SerializedName("id")
        String id;
        
        @SerializedName("enabled")
        boolean enabled = true;
        
        @SerializedName("enabledForPackages")
        Set<String> enabledForPackages = new HashSet<>();
        
        @SerializedName("disabledForPackages")
        Set<String> disabledForPackages = new HashSet<>();
    }
    
    /**
     * Listener for feature change events.
     */
    public interface FeatureChangeListener {
        /**
         * Called when a feature's enabled state changes.
         * 
         * @param featureId The feature ID.
         * @param enabled The new enabled state.
         */
        void onFeatureChanged(String featureId, boolean enabled);
    }
} 