package com.wobbz.framework.ui.models;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.wobbz.framework.development.LoggingHelper;
import com.wobbz.framework.features.FeatureManager;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper class for accessing module settings.
 * Provides type-safe access to settings defined in settings.json.
 */
public class SettingsHelper {
    private static final String TAG = "SettingsHelper";
    private static final String PREFS_PREFIX = "module_settings_";
    
    private final Context mContext;
    private final String mModuleId;
    private final FeatureManager mFeatureManager;
    private final SharedPreferences mPrefs;
    private SettingsSchema mSchema;
    
    /**
     * Create a new settings helper for a module.
     * 
     * @param context The context.
     * @param moduleId The module ID.
     */
    public SettingsHelper(Context context, String moduleId) {
        mContext = context;
        mModuleId = moduleId;
        mFeatureManager = FeatureManager.getInstance(context);
        mPrefs = context.getSharedPreferences(PREFS_PREFIX + moduleId, Context.MODE_PRIVATE);
        
        // Load schema from settings.json
        loadSchema();
    }
    
    /**
     * Load the settings schema from settings.json.
     */
    private void loadSchema() {
        try {
            // First check if there's a settings.json file
            File settingsFile = new File(mContext.getFilesDir(), "modules/" + mModuleId + "/settings.json");
            if (!settingsFile.exists()) {
                LoggingHelper.debug(TAG, "No settings.json found for module " + mModuleId);
                return;
            }
            
            // Read the file
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(settingsFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }
            
            // Parse the schema
            Gson gson = new Gson();
            mSchema = gson.fromJson(builder.toString(), SettingsSchema.class);
            
            LoggingHelper.debug(TAG, "Loaded settings schema for module " + mModuleId);
            
        } catch (IOException e) {
            LoggingHelper.error(TAG, "Error loading settings schema", e);
        }
    }
    
    /**
     * Get a boolean setting.
     * 
     * @param key The setting key.
     * @param defaultValue The default value if not set.
     * @return The setting value.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        // Check for device-specific override
        JsonObject customSettings = mFeatureManager.getCustomSettings(mModuleId);
        if (customSettings != null && customSettings.has(key)) {
            return customSettings.get(key).getAsBoolean();
        }
        
        return mPrefs.getBoolean(key, getDefaultBoolean(key, defaultValue));
    }
    
    /**
     * Get a string setting.
     * 
     * @param key The setting key.
     * @param defaultValue The default value if not set.
     * @return The setting value.
     */
    public String getString(String key, String defaultValue) {
        // Check for device-specific override
        JsonObject customSettings = mFeatureManager.getCustomSettings(mModuleId);
        if (customSettings != null && customSettings.has(key)) {
            return customSettings.get(key).getAsString();
        }
        
        return mPrefs.getString(key, getDefaultString(key, defaultValue));
    }
    
    /**
     * Get an integer setting.
     * 
     * @param key The setting key.
     * @param defaultValue The default value if not set.
     * @return The setting value.
     */
    public int getInt(String key, int defaultValue) {
        // Check for device-specific override
        JsonObject customSettings = mFeatureManager.getCustomSettings(mModuleId);
        if (customSettings != null && customSettings.has(key)) {
            return customSettings.get(key).getAsInt();
        }
        
        return mPrefs.getInt(key, getDefaultInt(key, defaultValue));
    }
    
    /**
     * Get a string array setting.
     * 
     * @param key The setting key.
     * @return The setting value, or null if not set.
     */
    public String[] getStringArray(String key) {
        // Check for device-specific override
        JsonObject customSettings = mFeatureManager.getCustomSettings(mModuleId);
        if (customSettings != null && customSettings.has(key)) {
            Gson gson = new Gson();
            Type type = new TypeToken<String[]>(){}.getType();
            return gson.fromJson(customSettings.get(key), type);
        }
        
        String json = mPrefs.getString(key + "_array", null);
        if (json == null) {
            return null;
        }
        
        Gson gson = new Gson();
        Type type = new TypeToken<String[]>(){}.getType();
        return gson.fromJson(json, type);
    }
    
    /**
     * Set a boolean setting.
     * 
     * @param key The setting key.
     * @param value The setting value.
     */
    public void setBoolean(String key, boolean value) {
        mPrefs.edit().putBoolean(key, value).apply();
    }
    
    /**
     * Set a string setting.
     * 
     * @param key The setting key.
     * @param value The setting value.
     */
    public void setString(String key, String value) {
        mPrefs.edit().putString(key, value).apply();
    }
    
    /**
     * Set an integer setting.
     * 
     * @param key The setting key.
     * @param value The setting value.
     */
    public void setInt(String key, int value) {
        mPrefs.edit().putInt(key, value).apply();
    }
    
    /**
     * Set a string array setting.
     * 
     * @param key The setting key.
     * @param value The setting value.
     */
    public void setStringArray(String key, String[] value) {
        Gson gson = new Gson();
        String json = gson.toJson(value);
        mPrefs.edit().putString(key + "_array", json).apply();
    }
    
    /**
     * Get the default boolean value from the schema.
     */
    private boolean getDefaultBoolean(String key, boolean fallback) {
        if (mSchema == null || mSchema.getFields() == null) {
            return fallback;
        }
        
        for (SettingsField field : mSchema.getFields()) {
            if (field.getKey().equals(key) && field.getDefaultValue() != null) {
                return (boolean) field.getDefaultValue();
            }
        }
        
        return fallback;
    }
    
    /**
     * Get the default string value from the schema.
     */
    private String getDefaultString(String key, String fallback) {
        if (mSchema == null || mSchema.getFields() == null) {
            return fallback;
        }
        
        for (SettingsField field : mSchema.getFields()) {
            if (field.getKey().equals(key) && field.getDefaultValue() != null) {
                return field.getDefaultValue().toString();
            }
        }
        
        return fallback;
    }
    
    /**
     * Get the default int value from the schema.
     */
    private int getDefaultInt(String key, int fallback) {
        if (mSchema == null || mSchema.getFields() == null) {
            return fallback;
        }
        
        for (SettingsField field : mSchema.getFields()) {
            if (field.getKey().equals(key) && field.getDefaultValue() != null) {
                if (field.getDefaultValue() instanceof Number) {
                    return ((Number) field.getDefaultValue()).intValue();
                }
            }
        }
        
        return fallback;
    }
} 