package com.wobbz.deepintegrator.models;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Model class that represents a log entry of a component being accessed
 */
public class ComponentAccessLog {
    private String componentType;
    private String componentName;
    private String callingPackage;
    private String action;
    private long timestamp;
    private boolean wasExported;
    private boolean wasPermissionBypassed;
    
    /**
     * Create a new component access log entry.
     */
    public ComponentAccessLog(String componentType, String componentName, String callingPackage, 
                             String action) {
        this.componentType = componentType;
        this.componentName = componentName;
        this.callingPackage = callingPackage;
        this.action = action;
        this.timestamp = System.currentTimeMillis();
        this.wasExported = false;
        this.wasPermissionBypassed = false;
    }
    
    /**
     * Create a new component access log entry with timestamp.
     */
    public ComponentAccessLog(String packageName, String componentName, String componentType, 
                             String callingPackage, long timestamp) {
        this.componentType = componentType;
        this.componentName = componentName;
        this.callingPackage = callingPackage;
        this.action = "EXPOSE";  // Default action
        this.timestamp = timestamp;
        this.wasExported = true;
        this.wasPermissionBypassed = false;
    }
    
    /**
     * Create a ComponentAccessLog from a JSON object.
     */
    public static ComponentAccessLog fromJson(JSONObject json) throws JSONException {
        String componentType = json.getString("componentType");
        String componentName = json.getString("componentName");
        String callingPackage = json.getString("callingPackage");
        String action = json.getString("action");
        
        ComponentAccessLog log = new ComponentAccessLog(componentType, componentName, callingPackage, action);
        log.setTimestamp(json.getLong("timestamp"));
        log.setWasExported(json.getBoolean("wasExported"));
        log.setWasPermissionBypassed(json.getBoolean("wasPermissionBypassed"));
        
        return log;
    }
    
    /**
     * Convert this log entry to a JSON object.
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("componentType", componentType);
        json.put("componentName", componentName);
        json.put("callingPackage", callingPackage);
        json.put("action", action);
        json.put("timestamp", timestamp);
        json.put("wasExported", wasExported);
        json.put("wasPermissionBypassed", wasPermissionBypassed);
        
        return json;
    }
    
    // Getters and setters
    
    public String getComponentType() {
        return componentType;
    }
    
    public void setComponentType(String componentType) {
        this.componentType = componentType;
    }
    
    public String getComponentName() {
        return componentName;
    }
    
    public void setComponentName(String componentName) {
        this.componentName = componentName;
    }
    
    public String getCallingPackage() {
        return callingPackage;
    }
    
    public void setCallingPackage(String callingPackage) {
        this.callingPackage = callingPackage;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public boolean isWasExported() {
        return wasExported;
    }
    
    public void setWasExported(boolean wasExported) {
        this.wasExported = wasExported;
    }
    
    public boolean isWasPermissionBypassed() {
        return wasPermissionBypassed;
    }
    
    public void setWasPermissionBypassed(boolean wasPermissionBypassed) {
        this.wasPermissionBypassed = wasPermissionBypassed;
    }
} 