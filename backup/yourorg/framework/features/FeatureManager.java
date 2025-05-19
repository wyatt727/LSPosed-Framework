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
    private static final String META_CONFIG_FILE = "META-INF/xposed/features.json";
    private static final long CONFIG_CHECK_INTERVAL = 60000; // 1 minute
    
    private static FeatureManager sInstance;
    
    private final Context mContext;
    private final Gson mGson;
    private final ScheduledExecutorService mExecutor;
    private final Handler mMainHandler;
    private final Set<FeatureChangeListener> mListeners = new HashSet<>();
    
    private FeatureConfig mConfig;
    private long mLastConfigCheckTime;
    
    /**
     * Get the singleton instance of the feature manager.
     */
    public static synchronized FeatureManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new FeatureManager(context);
        }
        return sInstance;
    }
    
    /**
     * Private constructor for singleton.
     */
    private FeatureManager(Context context) {
        mContext = context.getApplicationContext();
        mGson = new Gson();
        mExecutor = Executors.newSingleThreadScheduledExecutor();
        mMainHandler = new Handler(Looper.getMainLooper());
        
        // Load initial configuration
        loadConfig();
        
        // Schedule periodic config checks
        mExecutor.scheduleAtFixedRate(
            this::checkForConfigChanges,
            CONFIG_CHECK_INTERVAL,
            CONFIG_CHECK_INTERVAL,
            TimeUnit.MILLISECONDS
        );
    }
    
    /**
     * Add a listener for feature changes.
     */
    public void addListener(FeatureChangeListener listener) {
        synchronized (mListeners) {
            mListeners.add(listener);
        }
    }
    
    /**
     * Remove a listener for feature changes.
     */
    public void removeListener(FeatureChangeListener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
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
        
        if (!mConfig.globalEnabled) {
            return false;
        }
        
        FeatureInfo info = mConfig.features.get(featureId);
        if (info == null) {
            return false;
        }
        
        // Check device-specific overrides
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
    private void loadConfig() {
        // First try to load from the data directory
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
                StringBuilder json = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    json.append(line);
                }
                
                mConfig = mGson.fromJson(json.toString(), FeatureConfig.class);
                mLastConfigCheckTime = configFile.lastModified();
                LoggingHelper.info(TAG, "Loaded feature configuration from " + CONFIG_FILE);
                return;
            } catch (IOException e) {
                LoggingHelper.error(TAG, "Error loading feature configuration from file", e);
            }
        }
        
        // Fall back to the bundled configuration
        try {
            BufferedReader reader = new BufferedReader(
                new FileReader(mContext.getFileStreamPath(META_CONFIG_FILE)));
            StringBuilder json = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                json.append(line);
            }
            
            mConfig = mGson.fromJson(json.toString(), FeatureConfig.class);
            LoggingHelper.info(TAG, "Loaded bundled feature configuration");
        } catch (IOException e) {
            LoggingHelper.error(TAG, "Error loading bundled feature configuration", e);
            // Use default configuration
            mConfig = new FeatureConfig();
        }
    }
    
    /**
     * Check for changes to the configuration file.
     */
    private void checkForConfigChanges() {
        File configFile = new File(CONFIG_FILE);
        if (configFile.exists() && configFile.lastModified() > mLastConfigCheckTime) {
            LoggingHelper.info(TAG, "Feature configuration file changed, reloading");
            
            // Save old config for comparison
            FeatureConfig oldConfig = mConfig;
            
            // Load new config
            loadConfig();
            
            // Notify listeners on main thread
            mMainHandler.post(() -> notifyFeatureChanges(oldConfig, mConfig));
        }
    }
    
    /**
     * Notify listeners of feature changes.
     */
    private void notifyFeatureChanges(FeatureConfig oldConfig, FeatureConfig newConfig) {
        if (oldConfig == null || newConfig == null) {
            return;
        }
        
        Set<String> changedFeatures = new HashSet<>();
        
        // Check for global changes
        if (oldConfig.globalEnabled != newConfig.globalEnabled) {
            changedFeatures.addAll(newConfig.features.keySet());
        }
        
        // Check for feature-specific changes
        for (Map.Entry<String, FeatureInfo> entry : newConfig.features.entrySet()) {
            String featureId = entry.getKey();
            FeatureInfo newInfo = entry.getValue();
            FeatureInfo oldInfo = oldConfig.features.get(featureId);
            
            if (oldInfo == null || oldInfo.enabled != newInfo.enabled) {
                changedFeatures.add(featureId);
            }
        }
        
        // Notify listeners
        if (!changedFeatures.isEmpty()) {
            LoggingHelper.info(TAG, "Notifying listeners of feature changes: " + changedFeatures);
            
            synchronized (mListeners) {
                for (FeatureChangeListener listener : mListeners) {
                    for (String featureId : changedFeatures) {
                        boolean enabled = isFeatureEnabled(featureId);
                        listener.onFeatureChanged(featureId, enabled);
                    }
                }
            }
        }
    }
    
    /**
     * Feature configuration class.
     */
    private static class FeatureConfig {
        @SerializedName("globalEnabled")
        public boolean globalEnabled = true;
        
        @SerializedName("features")
        public Map<String, FeatureInfo> features = new HashMap<>();
        
        @SerializedName("deviceSpecific")
        public Map<String, JsonObject> deviceSpecific = new HashMap<>();
    }
    
    /**
     * Feature information class.
     */
    private static class FeatureInfo {
        @SerializedName("enabled")
        public boolean enabled = true;
        
        @SerializedName("enabledForPackages")
        public List<String> enabledForPackages = new ArrayList<>();
        
        @SerializedName("disabledForPackages")
        public List<String> disabledForPackages = new ArrayList<>();
    }
    
    /**
     * Listener for feature changes.
     */
    public interface FeatureChangeListener {
        /**
         * Called when a feature is enabled or disabled.
         * 
         * @param featureId The feature ID.
         * @param enabled Whether the feature is enabled.
         */
        void onFeatureChanged(String featureId, boolean enabled);
    }
} 