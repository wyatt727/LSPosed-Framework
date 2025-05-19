package com.wobbz.deepintegrator.models;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Model class that represents the configuration for a component
 * (Activity, Service, ContentProvider, BroadcastReceiver)
 */
public class ComponentConfig {
    // Component types
    public static final String TYPE_ACTIVITY = "activity";
    public static final String TYPE_SERVICE = "service";
    public static final String TYPE_PROVIDER = "provider";
    public static final String TYPE_RECEIVER = "receiver";
    
    // Component identifiers
    private String packageName;
    private String componentName;
    private String componentType;
    
    // Configuration options
    private boolean exported;
    private boolean bypassPermissions;
    private List<IntentFilterConfig> intentFilters;
    
    /**
     * Create a new ComponentConfig.
     */
    public ComponentConfig(String packageName, String componentName, String componentType) {
        this.packageName = packageName;
        this.componentName = componentName;
        this.componentType = componentType;
        this.exported = true;
        this.bypassPermissions = true;
        this.intentFilters = new ArrayList<>();
    }
    
    /**
     * Create a ComponentConfig from a JSON object.
     */
    public static ComponentConfig fromJson(JSONObject json) throws JSONException {
        String packageName = json.getString("packageName");
        String componentName = json.getString("componentName");
        String componentType = json.getString("componentType");
        
        ComponentConfig config = new ComponentConfig(packageName, componentName, componentType);
        config.setExported(json.optBoolean("exported", true));
        config.setBypassPermissions(json.optBoolean("bypassPermissions", true));
        
        // Load intent filters
        if (json.has("intentFilters")) {
            JSONArray filtersArray = json.getJSONArray("intentFilters");
            for (int i = 0; i < filtersArray.length(); i++) {
                JSONObject filterJson = filtersArray.getJSONObject(i);
                IntentFilterConfig filter = IntentFilterConfig.fromJson(filterJson);
                config.addIntentFilter(filter);
            }
        }
        
        return config;
    }
    
    /**
     * Convert this configuration to a JSON object.
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("packageName", packageName);
        json.put("componentName", componentName);
        json.put("componentType", componentType);
        json.put("exported", exported);
        json.put("bypassPermissions", bypassPermissions);
        
        // Add intent filters
        JSONArray filtersArray = new JSONArray();
        for (IntentFilterConfig filter : intentFilters) {
            filtersArray.put(filter.toJson());
        }
        json.put("intentFilters", filtersArray);
        
        return json;
    }
    
    /**
     * Get the full component name as a string (package/component).
     */
    public String getFullComponentName() {
        return packageName + "/" + componentName;
    }
    
    // Getters and setters
    
    public String getPackageName() {
        return packageName;
    }
    
    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }
    
    public String getComponentName() {
        return componentName;
    }
    
    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }
    
    public String getComponentType() {
        return componentType;
    }
    
    public void setComponentType(String componentType) {
        this.componentType = componentType;
    }
    
    public boolean isExported() {
        return exported;
    }
    
    public void setExported(boolean exported) {
        this.exported = exported;
    }
    
    public boolean isBypassPermissions() {
        return bypassPermissions;
    }
    
    public void setBypassPermissions(boolean bypassPermissions) {
        this.bypassPermissions = bypassPermissions;
    }
    
    public List<IntentFilterConfig> getIntentFilters() {
        return intentFilters;
    }
    
    public void setIntentFilters(List<IntentFilterConfig> intentFilters) {
        this.intentFilters = intentFilters;
    }
    
    public void addIntentFilter(IntentFilterConfig filter) {
        this.intentFilters.add(filter);
    }
} 