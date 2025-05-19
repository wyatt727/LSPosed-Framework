package com.wobbz.framework.ui.models;

import java.util.List;

/**
 * Model class for the overall settings schema
 */
public class SettingsSchema {
    private boolean enabledByDefault = true;
    private List<SettingsField> fields;
    
    public SettingsSchema() {
        // Default constructor for Gson
    }
    
    public boolean isEnabledByDefault() {
        return enabledByDefault;
    }
    
    public void setEnabledByDefault(boolean enabledByDefault) {
        this.enabledByDefault = enabledByDefault;
    }
    
    public List<SettingsField> getFields() {
        return fields;
    }
    
    public void setFields(List<SettingsField> fields) {
        this.fields = fields;
    }
} 