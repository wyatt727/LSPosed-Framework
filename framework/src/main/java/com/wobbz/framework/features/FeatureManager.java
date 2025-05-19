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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages feature toggles for modules and handles dependencies between modules.
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
    
    // Module dependency management
    private final Map<String, Set<String>> mDependencyMap = new HashMap<>();
    private final Map<String, Integer> mModulePriorities = new HashMap<>();
    private final Map<String, Object> mSharedServices = new ConcurrentHashMap<>();
    
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
     * Register a dependency between modules.
     * 
     * @param moduleId The module ID that depends on another.
     * @param dependsOnId The module ID that is depended upon.
     */
    public void registerDependency(String moduleId, String dependsOnId) {
        Set<String> dependencies = mDependencyMap.computeIfAbsent(moduleId, k -> new HashSet<>());
        dependencies.add(dependsOnId);
        
        LoggingHelper.debug(TAG, "Registered dependency: " + moduleId + " -> " + dependsOnId);
    }
    
    /**
     * Set the priority of a module. Higher priority modules are initialized first.
     * 
     * @param moduleId The module ID.
     * @param priority The priority (higher value = higher priority).
     */
    public void setModulePriority(String moduleId, int priority) {
        mModulePriorities.put(moduleId, priority);
        LoggingHelper.debug(TAG, "Set module priority: " + moduleId + " = " + priority);
    }
    
    /**
     * Get the priority of a module.
     * 
     * @param moduleId The module ID.
     * @return The priority, or 0 if not set.
     */
    public int getModulePriority(String moduleId) {
        return mModulePriorities.getOrDefault(moduleId, 0);
    }
    
    /**
     * Check if all dependencies for a module are satisfied.
     * 
     * @param moduleId The module ID.
     * @return true if all dependencies are satisfied, false otherwise.
     */
    public boolean areDependenciesSatisfied(String moduleId) {
        Set<String> dependencies = mDependencyMap.get(moduleId);
        if (dependencies == null || dependencies.isEmpty()) {
            return true;
        }
        
        for (String dependId : dependencies) {
            if (!isFeatureEnabled(dependId)) {
                LoggingHelper.warning(TAG, "Dependency not satisfied: " + moduleId + " -> " + dependId);
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get a sorted list of modules based on dependencies and priorities.
     * Modules with higher priorities come first.
     * Dependent modules come after modules they depend on.
     * 
     * @return Sorted list of module IDs.
     */
    public List<String> getSortedModules() {
        List<String> result = new ArrayList<>(mConfig.features.keySet());
        
        // Filter enabled modules
        result.removeIf(moduleId -> !isFeatureEnabled(moduleId));
        
        // Sort by priority and dependencies
        Collections.sort(result, new Comparator<String>() {
            @Override
            public int compare(String m1, String m2) {
                // First compare by priority (higher first)
                int p1 = getModulePriority(m1);
                int p2 = getModulePriority(m2);
                if (p1 != p2) {
                    return p2 - p1; // Higher priority first
                }
                
                // Then consider dependencies
                Set<String> deps1 = mDependencyMap.get(m1);
                Set<String> deps2 = mDependencyMap.get(m2);
                
                // If m1 depends on m2, m2 should come first
                if (deps1 != null && deps1.contains(m2)) {
                    return 1;
                }
                
                // If m2 depends on m1, m1 should come first
                if (deps2 != null && deps2.contains(m1)) {
                    return -1;
                }
                
                // Otherwise, maintain stable order
                return m1.compareTo(m2);
            }
        });
        
        return result;
    }
    
    /**
     * Register a shared service for dependency injection.
     * 
     * @param serviceKey The service key.
     * @param service The service instance.
     */
    public void registerService(String serviceKey, Object service) {
        mSharedServices.put(serviceKey, service);
        LoggingHelper.debug(TAG, "Registered service: " + serviceKey);
    }
    
    /**
     * Get a shared service.
     * 
     * @param serviceKey The service key.
     * @return The service instance, or null if not found.
     */
    @SuppressWarnings("unchecked")
    public <T> T getService(String serviceKey) {
        return (T) mSharedServices.get(serviceKey);
    }
    
    /**
     * Check if a service is available.
     * 
     * @param serviceKey The service key.
     * @return true if the service is available, false otherwise.
     */
    public boolean hasService(String serviceKey) {
        return mSharedServices.containsKey(serviceKey);
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
            
            // Load module priorities and dependencies if present
            if (newConfig.modulePriorities != null) {
                mModulePriorities.clear();
                mModulePriorities.putAll(newConfig.modulePriorities);
            }
            
            if (newConfig.dependencies != null) {
                mDependencyMap.clear();
                for (Map.Entry<String, List<String>> entry : newConfig.dependencies.entrySet()) {
                    mDependencyMap.put(entry.getKey(), new HashSet<>(entry.getValue()));
                }
            }
            
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
        
        @SerializedName("modulePriorities")
        Map<String, Integer> modulePriorities = new HashMap<>();
        
        @SerializedName("dependencies")
        Map<String, List<String>> dependencies = new HashMap<>();
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
        
        @SerializedName("priority")
        int priority = 0;
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