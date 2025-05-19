package com.wobbz.modules.intentmaster.model;

import android.content.Intent;
import android.os.Bundle;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Represents an extra to be added to an intent.
 * Supports different types of extras.
 */
public class IntentExtra {
    public enum ExtraType {
        STRING,
        INT,
        BOOLEAN,
        FLOAT,
        LONG,
        DOUBLE,
        BYTE_ARRAY,
        CHAR_SEQUENCE
    }
    
    private String key;
    private String value;
    private ExtraType type;
    
    public IntentExtra() {
        this.type = ExtraType.STRING;
    }
    
    /**
     * Adds this extra to the given intent based on its type.
     */
    public void addToIntent(Intent intent) {
        if (key == null || key.isEmpty()) {
            return;
        }
        
        try {
            switch (type) {
                case STRING:
                    intent.putExtra(key, value);
                    break;
                case INT:
                    intent.putExtra(key, Integer.parseInt(value));
                    break;
                case BOOLEAN:
                    intent.putExtra(key, Boolean.parseBoolean(value));
                    break;
                case FLOAT:
                    intent.putExtra(key, Float.parseFloat(value));
                    break;
                case LONG:
                    intent.putExtra(key, Long.parseLong(value));
                    break;
                case DOUBLE:
                    intent.putExtra(key, Double.parseDouble(value));
                    break;
                case BYTE_ARRAY:
                    intent.putExtra(key, value.getBytes());
                    break;
                case CHAR_SEQUENCE:
                    intent.putExtra(key, value);
                    break;
            }
        } catch (Exception e) {
            // In case of parsing errors, fall back to string
            intent.putExtra(key, value);
        }
    }
    
    // Serialization to JSON
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("key", key);
        json.put("value", value);
        json.put("type", type.name());
        return json;
    }
    
    // Deserialization from JSON
    public static IntentExtra fromJson(JSONObject json) throws JSONException {
        IntentExtra extra = new IntentExtra();
        
        if (json.has("key")) extra.key = json.getString("key");
        if (json.has("value")) extra.value = json.getString("value");
        if (json.has("type")) extra.type = ExtraType.valueOf(json.getString("type"));
        
        return extra;
    }

    // Getters and setters
    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public ExtraType getType() {
        return type;
    }

    public void setType(ExtraType type) {
        this.type = type;
    }
} 