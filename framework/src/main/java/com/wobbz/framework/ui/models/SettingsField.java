package com.wobbz.framework.ui.models;

import java.util.List;

/**
 * Model class representing a setting field in the UI
 */
public class SettingsField {
    
    public enum FieldType {
        BOOLEAN,
        STRING,
        INTEGER,
        CHOICE,
        PACKAGE_LIST
    }
    
    private String key;
    private String label;
    private String description;
    private FieldType type;
    private Object defaultValue;
    private List<String> options;
    
    public SettingsField() {
        // Default constructor for Gson
    }
    
    public SettingsField(String key, String label, FieldType type) {
        this.key = key;
        this.label = label;
        this.type = type;
    }
    
    public String getKey() {
        return key;
    }
    
    public void setKey(String key) {
        this.key = key;
    }
    
    public String getLabel() {
        return label;
    }
    
    public void setLabel(String label) {
        this.label = label;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public FieldType getType() {
        return type;
    }
    
    public void setType(FieldType type) {
        this.type = type;
    }
    
    public Object getDefaultValue() {
        return defaultValue;
    }
    
    public void setDefaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
    }
    
    public List<String> getOptions() {
        return options;
    }
    
    public void setOptions(List<String> options) {
        this.options = options;
    }
} 