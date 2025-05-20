package com.wobbz.modules.intentmaster.model;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a log entry for an intercepted intent.
 * This includes details about the original intent and any modifications made.
 */
public class IntentLog {
    private String id;
    private long timestamp;
    private String sourcePackage;
    private String formattedTime;
    private String action;
    private String data;
    private String type;
    private List<String> categories;
    private String component;
    private List<IntentExtraLog> extras;
    private Integer flags;
    private String appliedRuleId;
    private String appliedRuleName;
    private String resultAction;
    private boolean wasModified;
    private boolean wasBlocked;
    
    public IntentLog() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.categories = new ArrayList<>();
        this.extras = new ArrayList<>();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        this.formattedTime = sdf.format(new Date(timestamp));
    }
    
    /**
     * Constructor that directly logs an intent with status and rule information.
     * 
     * @param intent The intent to log
     * @param sourcePackage The package name of the app sending the intent
     * @param status The status of the intent (e.g., "ALLOWED", "BLOCKED", "MODIFIED")
     * @param ruleMatched The name of the rule that matched the intent, or null if none
     */
    public IntentLog(Intent intent, String sourcePackage, String status, String ruleMatched) {
        this();
        
        this.sourcePackage = sourcePackage;
        this.action = intent.getAction();
        
        if (intent.getData() != null) {
            this.data = intent.getData().toString();
        }
        
        this.type = intent.getType();
        
        if (intent.getCategories() != null) {
            this.categories.addAll(intent.getCategories());
        }
        
        if (intent.getComponent() != null) {
            this.component = intent.getComponent().flattenToString();
        }
        
        this.flags = intent.getFlags();
        
        // Extract extras
        Bundle extras = intent.getExtras();
        if (extras != null) {
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                IntentExtraLog extraLog = new IntentExtraLog();
                extraLog.setKey(key);
                
                if (value != null) {
                    extraLog.setValue(value.toString());
                    extraLog.setType(value.getClass().getSimpleName());
                } else {
                    extraLog.setValue("null");
                    extraLog.setType("null");
                }
                
                this.extras.add(extraLog);
            }
        }
        
        // Set rule information
        if (ruleMatched != null) {
            this.appliedRuleName = ruleMatched;
        }
        
        this.resultAction = status;
        this.wasModified = "MODIFIED".equals(status);
        this.wasBlocked = "BLOCKED".equals(status);
    }
    
    /**
     * Creates an IntentLog from an Intent.
     */
    public static IntentLog fromIntent(Intent intent, String sourcePackage) {
        IntentLog log = new IntentLog();
        
        log.sourcePackage = sourcePackage;
        log.action = intent.getAction();
        
        if (intent.getData() != null) {
            log.data = intent.getData().toString();
        }
        
        log.type = intent.getType();
        
        if (intent.getCategories() != null) {
            log.categories.addAll(intent.getCategories());
        }
        
        if (intent.getComponent() != null) {
            log.component = intent.getComponent().flattenToString();
        }
        
        log.flags = intent.getFlags();
        
        // Extract extras
        Bundle extras = intent.getExtras();
        if (extras != null) {
            for (String key : extras.keySet()) {
                Object value = extras.get(key);
                IntentExtraLog extraLog = new IntentExtraLog();
                extraLog.setKey(key);
                
                if (value != null) {
                    extraLog.setValue(value.toString());
                    extraLog.setType(value.getClass().getSimpleName());
                } else {
                    extraLog.setValue("null");
                    extraLog.setType("null");
                }
                
                log.extras.add(extraLog);
            }
        }
        
        return log;
    }
    
    /**
     * Updates this log with information about the rule that was applied.
     */
    public void setAppliedRule(IntentRule rule) {
        if (rule != null) {
            this.appliedRuleId = rule.getId();
            this.appliedRuleName = rule.getName();
            this.resultAction = rule.getIntentAction().name();
            
            // Record if the intent was modified or blocked
            this.wasModified = rule.getIntentAction() == IntentRule.Action.MODIFY 
                    || rule.getIntentAction() == IntentRule.Action.REDIRECT;
            this.wasBlocked = rule.getIntentAction() == IntentRule.Action.BLOCK;
        }
    }
    
    // Serialization to JSON
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("timestamp", timestamp);
        json.put("formattedTime", formattedTime);
        json.put("sourcePackage", sourcePackage);
        json.put("action", action);
        json.put("data", data);
        json.put("type", type);
        json.put("component", component);
        json.put("flags", flags);
        json.put("appliedRuleId", appliedRuleId);
        json.put("appliedRuleName", appliedRuleName);
        json.put("resultAction", resultAction);
        json.put("wasModified", wasModified);
        json.put("wasBlocked", wasBlocked);
        
        JSONArray categoriesArray = new JSONArray();
        for (String category : categories) {
            categoriesArray.put(category);
        }
        json.put("categories", categoriesArray);
        
        JSONArray extrasArray = new JSONArray();
        for (IntentExtraLog extra : extras) {
            extrasArray.put(extra.toJson());
        }
        json.put("extras", extrasArray);
        
        return json;
    }
    
    // Deserialization from JSON
    public static IntentLog fromJson(JSONObject json) throws JSONException {
        IntentLog log = new IntentLog();
        
        if (json.has("id")) log.id = json.getString("id");
        if (json.has("timestamp")) log.timestamp = json.getLong("timestamp");
        if (json.has("formattedTime")) log.formattedTime = json.getString("formattedTime");
        if (json.has("sourcePackage")) log.sourcePackage = json.getString("sourcePackage");
        if (json.has("action")) log.action = json.getString("action");
        if (json.has("data")) log.data = json.getString("data");
        if (json.has("type")) log.type = json.getString("type");
        if (json.has("component")) log.component = json.getString("component");
        if (json.has("flags")) log.flags = json.getInt("flags");
        if (json.has("appliedRuleId")) log.appliedRuleId = json.getString("appliedRuleId");
        if (json.has("appliedRuleName")) log.appliedRuleName = json.getString("appliedRuleName");
        if (json.has("resultAction")) log.resultAction = json.getString("resultAction");
        if (json.has("wasModified")) log.wasModified = json.getBoolean("wasModified");
        if (json.has("wasBlocked")) log.wasBlocked = json.getBoolean("wasBlocked");
        
        if (json.has("categories")) {
            JSONArray categoriesArray = json.getJSONArray("categories");
            for (int i = 0; i < categoriesArray.length(); i++) {
                log.categories.add(categoriesArray.getString(i));
            }
        }
        
        if (json.has("extras")) {
            JSONArray extrasArray = json.getJSONArray("extras");
            for (int i = 0; i < extrasArray.length(); i++) {
                log.extras.add(IntentExtraLog.fromJson(extrasArray.getJSONObject(i)));
            }
        }
        
        return log;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getSourcePackage() {
        return sourcePackage;
    }

    public void setSourcePackage(String sourcePackage) {
        this.sourcePackage = sourcePackage;
    }

    public String getFormattedTime() {
        return formattedTime;
    }

    public void setFormattedTime(String formattedTime) {
        this.formattedTime = formattedTime;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public List<IntentExtraLog> getExtras() {
        return extras;
    }

    public void setExtras(List<IntentExtraLog> extras) {
        this.extras = extras;
    }

    public Integer getFlags() {
        return flags;
    }

    public void setFlags(Integer flags) {
        this.flags = flags;
    }

    public String getAppliedRuleId() {
        return appliedRuleId;
    }

    public void setAppliedRuleId(String appliedRuleId) {
        this.appliedRuleId = appliedRuleId;
    }

    public String getAppliedRuleName() {
        return appliedRuleName;
    }

    public void setAppliedRuleName(String appliedRuleName) {
        this.appliedRuleName = appliedRuleName;
    }

    public String getResultAction() {
        return resultAction;
    }

    public void setResultAction(String resultAction) {
        this.resultAction = resultAction;
    }

    public boolean isWasModified() {
        return wasModified;
    }

    public void setWasModified(boolean wasModified) {
        this.wasModified = wasModified;
    }

    public boolean isWasBlocked() {
        return wasBlocked;
    }

    public void setWasBlocked(boolean wasBlocked) {
        this.wasBlocked = wasBlocked;
    }
    
    /**
     * Helper class to log intent extras.
     */
    public static class IntentExtraLog {
        private String key;
        private String value;
        private String type;
        
        public IntentExtraLog() {}
        
        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("key", key);
            json.put("value", value);
            json.put("type", type);
            return json;
        }
        
        public static IntentExtraLog fromJson(JSONObject json) throws JSONException {
            IntentExtraLog log = new IntentExtraLog();
            
            if (json.has("key")) log.key = json.getString("key");
            if (json.has("value")) log.value = json.getString("value");
            if (json.has("type")) log.type = json.getString("type");
            
            return log;
        }

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

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }
    }
} 