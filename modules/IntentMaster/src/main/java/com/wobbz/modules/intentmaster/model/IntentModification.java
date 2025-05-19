package com.wobbz.modules.intentmaster.model;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents modifications to be applied to an intent.
 * This is used when a rule's action is MODIFY or REDIRECT.
 */
public class IntentModification {
    private String newAction;
    private String newData;
    private String newType;
    private List<String> newCategories;
    private String newComponent;
    private List<IntentExtra> extrasToAdd;
    private List<String> extrasToRemove;
    private int flags;
    
    public IntentModification() {
        this.newCategories = new ArrayList<>();
        this.extrasToAdd = new ArrayList<>();
        this.extrasToRemove = new ArrayList<>();
    }
    
    // Serialization to JSON
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("newAction", newAction);
        json.put("newData", newData);
        json.put("newType", newType);
        json.put("newComponent", newComponent);
        json.put("flags", flags);
        
        JSONArray categoriesArray = new JSONArray();
        for (String category : newCategories) {
            categoriesArray.put(category);
        }
        json.put("newCategories", categoriesArray);
        
        JSONArray extrasToAddArray = new JSONArray();
        for (IntentExtra extra : extrasToAdd) {
            extrasToAddArray.put(extra.toJson());
        }
        json.put("extrasToAdd", extrasToAddArray);
        
        JSONArray extrasToRemoveArray = new JSONArray();
        for (String key : extrasToRemove) {
            extrasToRemoveArray.put(key);
        }
        json.put("extrasToRemove", extrasToRemoveArray);
        
        return json;
    }
    
    // Deserialization from JSON
    public static IntentModification fromJson(JSONObject json) throws JSONException {
        IntentModification modification = new IntentModification();
        
        if (json.has("newAction")) modification.newAction = json.getString("newAction");
        if (json.has("newData")) modification.newData = json.getString("newData");
        if (json.has("newType")) modification.newType = json.getString("newType");
        if (json.has("newComponent")) modification.newComponent = json.getString("newComponent");
        if (json.has("flags")) modification.flags = json.getInt("flags");
        
        if (json.has("newCategories")) {
            JSONArray categoriesArray = json.getJSONArray("newCategories");
            for (int i = 0; i < categoriesArray.length(); i++) {
                modification.newCategories.add(categoriesArray.getString(i));
            }
        }
        
        if (json.has("extrasToAdd")) {
            JSONArray extrasArray = json.getJSONArray("extrasToAdd");
            for (int i = 0; i < extrasArray.length(); i++) {
                modification.extrasToAdd.add(IntentExtra.fromJson(extrasArray.getJSONObject(i)));
            }
        }
        
        if (json.has("extrasToRemove")) {
            JSONArray extrasArray = json.getJSONArray("extrasToRemove");
            for (int i = 0; i < extrasArray.length(); i++) {
                modification.extrasToRemove.add(extrasArray.getString(i));
            }
        }
        
        return modification;
    }

    // Getters and setters
    public String getNewAction() {
        return newAction;
    }

    public void setNewAction(String newAction) {
        this.newAction = newAction;
    }

    public String getNewData() {
        return newData;
    }

    public void setNewData(String newData) {
        this.newData = newData;
    }

    public String getNewType() {
        return newType;
    }

    public void setNewType(String newType) {
        this.newType = newType;
    }

    public List<String> getNewCategories() {
        return newCategories;
    }

    public void setNewCategories(List<String> newCategories) {
        this.newCategories = newCategories;
    }

    public String getNewComponent() {
        return newComponent;
    }

    public void setNewComponent(String newComponent) {
        this.newComponent = newComponent;
    }

    public List<IntentExtra> getExtrasToAdd() {
        return extrasToAdd;
    }

    public void setExtrasToAdd(List<IntentExtra> extrasToAdd) {
        this.extrasToAdd = extrasToAdd;
    }

    public List<String> getExtrasToRemove() {
        return extrasToRemove;
    }

    public void setExtrasToRemove(List<String> extrasToRemove) {
        this.extrasToRemove = extrasToRemove;
    }

    public int getFlags() {
        return flags;
    }

    public void setFlags(int flags) {
        this.flags = flags;
    }
} 