package com.wobbz.framework.permissions;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PermissionInfo;
import android.os.Build;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.wobbz.framework.development.LoggingHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manager for handling permission declarations, validation, and manifest merging.
 */
public class PermissionManager {
    private static final String TAG = "PermissionManager";
    private static final String PERMISSIONS_FILE = "permissions.json";
    private static final String MANIFESTS_DIR = "manifests";
    
    private static PermissionManager sInstance;
    
    private final Context mContext;
    private final Map<String, List<PermissionInfo>> mPermissionsByModule = new HashMap<>();
    private final Set<String> mAllPermissions = new HashSet<>();
    
    /**
     * Private constructor for singleton pattern.
     */
    private PermissionManager(Context context) {
        mContext = context.getApplicationContext();
        
        // Create manifests directory
        File manifestsDir = new File(mContext.getFilesDir(), MANIFESTS_DIR);
        if (!manifestsDir.exists()) {
            manifestsDir.mkdirs();
        }
        
        // Load permissions from local storage
        loadPermissions();
    }
    
    /**
     * Get the singleton instance.
     */
    public static synchronized PermissionManager getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new PermissionManager(context);
        }
        return sInstance;
    }
    
    /**
     * Register permissions for a module.
     * 
     * @param moduleId The module ID.
     * @param permissions List of permission names.
     */
    public void registerPermissions(String moduleId, List<String> permissions) {
        List<PermissionInfo> permissionInfos = new ArrayList<>();
        
        for (String permission : permissions) {
            try {
                PermissionInfo info = mContext.getPackageManager().getPermissionInfo(permission, 0);
                permissionInfos.add(info);
                mAllPermissions.add(permission);
            } catch (PackageManager.NameNotFoundException e) {
                LoggingHelper.warning(TAG, "Permission not found: " + permission);
            }
        }
        
        mPermissionsByModule.put(moduleId, permissionInfos);
        savePermissions();
    }
    
    /**
     * Check if a module has a specific permission.
     * 
     * @param moduleId The module ID.
     * @param permission The permission name.
     * @return true if the module has the permission, false otherwise.
     */
    public boolean hasPermission(String moduleId, String permission) {
        List<PermissionInfo> permissions = mPermissionsByModule.get(moduleId);
        if (permissions == null) {
            return false;
        }
        
        for (PermissionInfo info : permissions) {
            if (info.name.equals(permission)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Get all permissions for a module.
     * 
     * @param moduleId The module ID.
     * @return List of permission names, or empty list if none.
     */
    public List<String> getPermissionsForModule(String moduleId) {
        List<String> result = new ArrayList<>();
        List<PermissionInfo> permissions = mPermissionsByModule.get(moduleId);
        
        if (permissions != null) {
            for (PermissionInfo info : permissions) {
                result.add(info.name);
            }
        }
        
        return result;
    }
    
    /**
     * Get all registered permissions across all modules.
     * 
     * @return Set of all permission names.
     */
    public Set<String> getAllPermissions() {
        return new HashSet<>(mAllPermissions);
    }
    
    /**
     * Generate a merged AndroidManifest.xml with all required permissions.
     * 
     * @return Path to the generated manifest, or null if error.
     */
    public String generateMergedManifest() {
        try {
            StringBuilder builder = new StringBuilder();
            builder.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n");
            builder.append("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"\n");
            builder.append("    package=\"com.wobbz.lsposedframework\">\n\n");
            
            // Add all permissions
            for (String permission : mAllPermissions) {
                builder.append("    <uses-permission android:name=\"").append(permission).append("\" />\n");
            }
            
            builder.append("\n</manifest>");
            
            // Write to file
            File manifestFile = new File(new File(mContext.getFilesDir(), MANIFESTS_DIR), 
                    "AndroidManifest_merged.xml");
            try (FileOutputStream fos = new FileOutputStream(manifestFile)) {
                fos.write(builder.toString().getBytes());
            }
            
            LoggingHelper.info(TAG, "Merged manifest written to " + manifestFile.getAbsolutePath());
            return manifestFile.getAbsolutePath();
            
        } catch (IOException e) {
            LoggingHelper.error(TAG, "Error generating merged manifest", e);
            return null;
        }
    }
    
    /**
     * Validate permissions against current device.
     * 
     * @return List of validation errors, or empty list if all valid.
     */
    public List<String> validatePermissions() {
        List<String> errors = new ArrayList<>();
        PackageManager pm = mContext.getPackageManager();
        
        for (String permission : mAllPermissions) {
            try {
                PermissionInfo info = pm.getPermissionInfo(permission, 0);
                
                // Check for API level compatibility
                if (info.protectionLevel == PermissionInfo.PROTECTION_DANGEROUS) {
                    if (Build.VERSION.SDK_INT < 23) {
                        errors.add("Permission " + permission + " requires runtime permissions (API 23+)");
                    }
                } else if ((info.protectionLevel & PermissionInfo.PROTECTION_FLAG_DEVELOPMENT) != 0) {
                    errors.add("Permission " + permission + " is a development permission");
                }
                
            } catch (PackageManager.NameNotFoundException e) {
                errors.add("Permission " + permission + " not found on device");
            }
        }
        
        return errors;
    }
    
    /**
     * Load permissions from local storage.
     */
    private void loadPermissions() {
        try {
            File file = new File(mContext.getFilesDir(), PERMISSIONS_FILE);
            if (!file.exists()) {
                return;
            }
            
            StringBuilder builder = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line);
                }
            }
            
            Gson gson = new Gson();
            JsonObject root = JsonParser.parseString(builder.toString()).getAsJsonObject();
            
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                String moduleId = entry.getKey();
                JsonArray permissionsArray = entry.getValue().getAsJsonArray();
                
                Type type = new TypeToken<List<String>>(){}.getType();
                List<String> permissions = gson.fromJson(permissionsArray, type);
                
                List<PermissionInfo> permissionInfos = new ArrayList<>();
                for (String permission : permissions) {
                    try {
                        PermissionInfo info = mContext.getPackageManager().getPermissionInfo(permission, 0);
                        permissionInfos.add(info);
                        mAllPermissions.add(permission);
                    } catch (PackageManager.NameNotFoundException e) {
                        LoggingHelper.warning(TAG, "Permission not found: " + permission);
                    }
                }
                
                mPermissionsByModule.put(moduleId, permissionInfos);
            }
            
            LoggingHelper.info(TAG, "Loaded permissions for " + mPermissionsByModule.size() + " modules");
            
        } catch (IOException e) {
            LoggingHelper.error(TAG, "Error loading permissions", e);
        }
    }
    
    /**
     * Save permissions to local storage.
     */
    private void savePermissions() {
        try {
            JsonObject root = new JsonObject();
            
            for (Map.Entry<String, List<PermissionInfo>> entry : mPermissionsByModule.entrySet()) {
                String moduleId = entry.getKey();
                List<PermissionInfo> permissions = entry.getValue();
                
                JsonArray permissionsArray = new JsonArray();
                for (PermissionInfo info : permissions) {
                    permissionsArray.add(info.name);
                }
                
                root.add(moduleId, permissionsArray);
            }
            
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(root);
            
            File file = new File(mContext.getFilesDir(), PERMISSIONS_FILE);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(json.getBytes());
            }
            
            LoggingHelper.info(TAG, "Saved permissions for " + mPermissionsByModule.size() + " modules");
            
        } catch (IOException e) {
            LoggingHelper.error(TAG, "Error saving permissions", e);
        }
    }
} 