package com.wobbz.deepintegrator.models;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Model class that represents an intent filter configuration
 */
public class IntentFilterConfig {
    private List<String> actions;
    private List<String> categories;
    private List<String> schemes;
    private List<String> hosts;
    private List<String> paths;
    private List<String> mimeTypes;
    private int priority;
    
    /**
     * Create a new IntentFilterConfig.
     */
    public IntentFilterConfig() {
        this.actions = new ArrayList<>();
        this.categories = new ArrayList<>();
        this.schemes = new ArrayList<>();
        this.hosts = new ArrayList<>();
        this.paths = new ArrayList<>();
        this.mimeTypes = new ArrayList<>();
        this.priority = 0;
    }
    
    /**
     * Create an IntentFilterConfig from a JSON object.
     */
    public static IntentFilterConfig fromJson(JSONObject json) throws JSONException {
        IntentFilterConfig config = new IntentFilterConfig();
        
        if (json.has("actions")) {
            JSONArray array = json.getJSONArray("actions");
            for (int i = 0; i < array.length(); i++) {
                config.addAction(array.getString(i));
            }
        }
        
        if (json.has("categories")) {
            JSONArray array = json.getJSONArray("categories");
            for (int i = 0; i < array.length(); i++) {
                config.addCategory(array.getString(i));
            }
        }
        
        if (json.has("schemes")) {
            JSONArray array = json.getJSONArray("schemes");
            for (int i = 0; i < array.length(); i++) {
                config.addScheme(array.getString(i));
            }
        }
        
        if (json.has("hosts")) {
            JSONArray array = json.getJSONArray("hosts");
            for (int i = 0; i < array.length(); i++) {
                config.addHost(array.getString(i));
            }
        }
        
        if (json.has("paths")) {
            JSONArray array = json.getJSONArray("paths");
            for (int i = 0; i < array.length(); i++) {
                config.addPath(array.getString(i));
            }
        }
        
        if (json.has("mimeTypes")) {
            JSONArray array = json.getJSONArray("mimeTypes");
            for (int i = 0; i < array.length(); i++) {
                config.addMimeType(array.getString(i));
            }
        }
        
        if (json.has("priority")) {
            config.setPriority(json.getInt("priority"));
        }
        
        return config;
    }
    
    /**
     * Convert this configuration to a JSON object.
     */
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        
        if (!actions.isEmpty()) {
            JSONArray array = new JSONArray();
            for (String action : actions) {
                array.put(action);
            }
            json.put("actions", array);
        }
        
        if (!categories.isEmpty()) {
            JSONArray array = new JSONArray();
            for (String category : categories) {
                array.put(category);
            }
            json.put("categories", array);
        }
        
        if (!schemes.isEmpty()) {
            JSONArray array = new JSONArray();
            for (String scheme : schemes) {
                array.put(scheme);
            }
            json.put("schemes", array);
        }
        
        if (!hosts.isEmpty()) {
            JSONArray array = new JSONArray();
            for (String host : hosts) {
                array.put(host);
            }
            json.put("hosts", array);
        }
        
        if (!paths.isEmpty()) {
            JSONArray array = new JSONArray();
            for (String path : paths) {
                array.put(path);
            }
            json.put("paths", array);
        }
        
        if (!mimeTypes.isEmpty()) {
            JSONArray array = new JSONArray();
            for (String mimeType : mimeTypes) {
                array.put(mimeType);
            }
            json.put("mimeTypes", array);
        }
        
        json.put("priority", priority);
        
        return json;
    }
    
    // Getters and setters
    
    public List<String> getActions() {
        return actions;
    }
    
    public void setActions(List<String> actions) {
        this.actions = actions;
    }
    
    public void addAction(String action) {
        if (!this.actions.contains(action)) {
            this.actions.add(action);
        }
    }
    
    public List<String> getCategories() {
        return categories;
    }
    
    public void setCategories(List<String> categories) {
        this.categories = categories;
    }
    
    public void addCategory(String category) {
        if (!this.categories.contains(category)) {
            this.categories.add(category);
        }
    }
    
    public List<String> getSchemes() {
        return schemes;
    }
    
    public void setSchemes(List<String> schemes) {
        this.schemes = schemes;
    }
    
    public void addScheme(String scheme) {
        if (!this.schemes.contains(scheme)) {
            this.schemes.add(scheme);
        }
    }
    
    public List<String> getHosts() {
        return hosts;
    }
    
    public void setHosts(List<String> hosts) {
        this.hosts = hosts;
    }
    
    public void addHost(String host) {
        if (!this.hosts.contains(host)) {
            this.hosts.add(host);
        }
    }
    
    public List<String> getPaths() {
        return paths;
    }
    
    public void setPaths(List<String> paths) {
        this.paths = paths;
    }
    
    public void addPath(String path) {
        if (!this.paths.contains(path)) {
            this.paths.add(path);
        }
    }
    
    public List<String> getMimeTypes() {
        return mimeTypes;
    }
    
    public void setMimeTypes(List<String> mimeTypes) {
        this.mimeTypes = mimeTypes;
    }
    
    public void addMimeType(String mimeType) {
        if (!this.mimeTypes.contains(mimeType)) {
            this.mimeTypes.add(mimeType);
        }
    }
    
    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
} 