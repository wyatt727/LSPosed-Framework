package com.wobbz.modules.intentmaster.model;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.regex.Pattern;

/**
 * Represents a matcher for an intent extra. This is used to
 * match against extras in an intent during rule matching.
 */
public class IntentExtraMatch {
    public enum MatchType {
        EQUALS,
        CONTAINS,
        REGEX,
        EXISTS,
        NOT_EXISTS
    }
    
    private String key;
    private String value;
    private MatchType matchType;
    
    public IntentExtraMatch() {
        this.matchType = MatchType.EXISTS;
    }
    
    /**
     * Checks if this matcher matches the given intent extra.
     */
    public boolean matches(Intent intent) {
        Bundle extras = intent.getExtras();
        
        if (extras == null) {
            return matchType == MatchType.NOT_EXISTS;
        }
        
        if (matchType == MatchType.NOT_EXISTS) {
            return !extras.containsKey(key);
        }
        
        if (matchType == MatchType.EXISTS) {
            return extras.containsKey(key);
        }
        
        // For other match types, we need to actually check the value
        if (!extras.containsKey(key)) {
            return false;
        }
        
        Object extraValue = extras.get(key);
        if (extraValue == null) {
            return TextUtils.isEmpty(value);
        }
        
        String extraValueStr = extraValue.toString();
        
        switch (matchType) {
            case EQUALS:
                return extraValueStr.equals(value);
            case CONTAINS:
                return extraValueStr.contains(value);
            case REGEX:
                try {
                    Pattern pattern = Pattern.compile(value);
                    return pattern.matcher(extraValueStr).matches();
                } catch (Exception e) {
                    return false;
                }
            default:
                return false;
        }
    }
    
    // Serialization to JSON
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("key", key);
        json.put("value", value);
        json.put("matchType", matchType.name());
        return json;
    }
    
    // Deserialization from JSON
    public static IntentExtraMatch fromJson(JSONObject json) throws JSONException {
        IntentExtraMatch match = new IntentExtraMatch();
        
        if (json.has("key")) match.key = json.getString("key");
        if (json.has("value")) match.value = json.getString("value");
        if (json.has("matchType")) match.matchType = MatchType.valueOf(json.getString("matchType"));
        
        return match;
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

    public MatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(MatchType matchType) {
        this.matchType = matchType;
    }
} 