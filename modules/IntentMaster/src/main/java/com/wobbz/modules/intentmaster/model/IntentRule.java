package com.wobbz.modules.intentmaster.model;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Represents a rule for intercepting and modifying intents.
 * Each rule has match criteria and an action to take when a match is found.
 */
public class IntentRule {
    public enum Action {
        MODIFY, // Modify the intent fields
        REDIRECT, // Redirect the intent to a different component
        BLOCK, // Block the intent from being delivered
        LOG // Just log the intent without modifying it
    }

    private String id;
    private String name;
    private boolean enabled;
    private String packageName;
    private String action;
    private String data;
    private String type;
    private List<String> categories;
    private String component;
    private List<IntentExtraMatch> extraMatches;
    private Action intentAction;
    private IntentModification modification;

    public IntentRule() {
        this.id = UUID.randomUUID().toString();
        this.enabled = true;
        this.categories = new ArrayList<>();
        this.extraMatches = new ArrayList<>();
    }

    /**
     * Checks if this rule matches the given intent.
     */
    public boolean matches(Intent intent, String sourcePackage) {
        if (!enabled) {
            return false;
        }

        // Match source package if specified
        if (!TextUtils.isEmpty(packageName) && !packageName.equals(sourcePackage)) {
            return false;
        }

        // Match action if specified
        if (!TextUtils.isEmpty(action) && !action.equals(intent.getAction())) {
            return false;
        }

        // Match data URI if specified
        if (!TextUtils.isEmpty(data) && intent.getData() != null) {
            try {
                Pattern pattern = Pattern.compile(data);
                if (!pattern.matcher(intent.getData().toString()).matches()) {
                    return false;
                }
            } catch (Exception e) {
                // If regex fails, fall back to string comparison
                if (!data.equals(intent.getData().toString())) {
                    return false;
                }
            }
        }

        // Match type if specified
        if (!TextUtils.isEmpty(type) && !type.equals(intent.getType())) {
            return false;
        }

        // Match component if specified
        if (!TextUtils.isEmpty(component) && intent.getComponent() != null) {
            if (!component.equals(intent.getComponent().flattenToString())) {
                return false;
            }
        }

        // Match categories if specified
        if (categories != null && !categories.isEmpty()) {
            Set<String> intentCategories = intent.getCategories();
            if (intentCategories == null) {
                return false;
            }
            
            for (String category : categories) {
                if (!intentCategories.contains(category)) {
                    return false;
                }
            }
        }

        // Match extras if specified
        if (extraMatches != null && !extraMatches.isEmpty()) {
            for (IntentExtraMatch extraMatch : extraMatches) {
                if (!extraMatch.matches(intent)) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * Apply the rule's modifications to the given intent.
     */
    public Intent applyModification(Intent original) {
        if (intentAction == Action.BLOCK) {
            return null; // Returning null indicates the intent should be blocked
        }
        
        if (intentAction != Action.MODIFY && intentAction != Action.REDIRECT) {
            return original; // No modifications for LOG action
        }
        
        Intent modified = new Intent(original);
        
        if (modification != null) {
            // Apply modifications
            if (!TextUtils.isEmpty(modification.getNewAction())) {
                modified.setAction(modification.getNewAction());
            }
            
            if (!TextUtils.isEmpty(modification.getNewData())) {
                modified.setData(Uri.parse(modification.getNewData()));
            }
            
            if (!TextUtils.isEmpty(modification.getNewType())) {
                modified.setType(modification.getNewType());
            }
            
            if (modification.getNewCategories() != null && !modification.getNewCategories().isEmpty()) {
                // Remove existing categories first
                if (modified.getCategories() != null) {
                    for (String category : new HashSet<>(modified.getCategories())) {
                        modified.removeCategory(category);
                    }
                }
                
                // Add new categories
                for (String category : modification.getNewCategories()) {
                    modified.addCategory(category);
                }
            }
            
            if (!TextUtils.isEmpty(modification.getNewComponent())) {
                modified.setComponent(ComponentName.unflattenFromString(modification.getNewComponent()));
            }
            
            if (modification.getExtrasToAdd() != null) {
                for (IntentExtra extra : modification.getExtrasToAdd()) {
                    extra.addToIntent(modified);
                }
            }
            
            if (modification.getExtrasToRemove() != null) {
                for (String key : modification.getExtrasToRemove()) {
                    modified.removeExtra(key);
                }
            }

            // Set flags if specified
            if (modification.getFlags() != 0) {
                modified.setFlags(modification.getFlags());
            }
        }
        
        return modified;
    }

    // Serialization to JSON
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("name", name);
        json.put("enabled", enabled);
        json.put("packageName", packageName);
        json.put("action", action);
        json.put("data", data);
        json.put("type", type);
        json.put("component", component);
        
        JSONArray categoriesArray = new JSONArray();
        for (String category : categories) {
            categoriesArray.put(category);
        }
        json.put("categories", categoriesArray);
        
        JSONArray extrasArray = new JSONArray();
        for (IntentExtraMatch extraMatch : extraMatches) {
            extrasArray.put(extraMatch.toJson());
        }
        json.put("extraMatches", extrasArray);
        
        json.put("intentAction", intentAction.name());
        
        if (modification != null) {
            json.put("modification", modification.toJson());
        }
        
        return json;
    }

    // Deserialization from JSON
    public static IntentRule fromJson(JSONObject json) throws JSONException {
        IntentRule rule = new IntentRule();
        
        if (json.has("id")) rule.id = json.getString("id");
        if (json.has("name")) rule.name = json.getString("name");
        if (json.has("enabled")) rule.enabled = json.getBoolean("enabled");
        if (json.has("packageName")) rule.packageName = json.getString("packageName");
        if (json.has("action")) rule.action = json.getString("action");
        if (json.has("data")) rule.data = json.getString("data");
        if (json.has("type")) rule.type = json.getString("type");
        if (json.has("component")) rule.component = json.getString("component");
        
        if (json.has("categories")) {
            JSONArray categoriesArray = json.getJSONArray("categories");
            for (int i = 0; i < categoriesArray.length(); i++) {
                rule.categories.add(categoriesArray.getString(i));
            }
        }
        
        if (json.has("extraMatches")) {
            JSONArray extrasArray = json.getJSONArray("extraMatches");
            for (int i = 0; i < extrasArray.length(); i++) {
                rule.extraMatches.add(IntentExtraMatch.fromJson(extrasArray.getJSONObject(i)));
            }
        }
        
        if (json.has("intentAction")) {
            rule.intentAction = Action.valueOf(json.getString("intentAction"));
        }
        
        if (json.has("modification")) {
            rule.modification = IntentModification.fromJson(json.getJSONObject("modification"));
        }
        
        return rule;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
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

    public List<IntentExtraMatch> getExtraMatches() {
        return extraMatches;
    }

    public void setExtraMatches(List<IntentExtraMatch> extraMatches) {
        this.extraMatches = extraMatches;
    }

    public Action getIntentAction() {
        return intentAction;
    }

    public void setIntentAction(Action intentAction) {
        this.intentAction = intentAction;
    }

    public IntentModification getModification() {
        return modification;
    }

    public void setModification(IntentModification modification) {
        this.modification = modification;
    }
} 