package com.wobbz.permissionoverride;

import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Process;

import com.wobbz.framework.IHotReloadable;
import com.wobbz.framework.IModulePlugin;
import com.wobbz.framework.development.LoggingHelper;
import com.wobbz.framework.features.FeatureManager;
import com.wobbz.framework.ui.models.SettingsHelper;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * PermissionOverride module provides functionality to override permission checks,
 * bypass signature verification, and enhance reflection capabilities.
 */
public class PermissionOverrideModule implements IModulePlugin, IHotReloadable, IPermissionOverrideService {
    private static final String TAG = "PermissionOverrideModule";
    private static final String MODULE_ID = "com.wobbz.PermissionOverride";
    
    // Service key for other modules to find this module
    public static final String SERVICE_KEY = "com.wobbz.PermissionOverride.service";
    
    // Permission result values
    public static final int PERMISSION_GRANTED = PackageManager.PERMISSION_GRANTED;
    public static final int PERMISSION_DENIED = PackageManager.PERMISSION_DENIED;
    public static final int PERMISSION_DEFAULT = -2;
    
    // Store active hooks for hot reload support
    private final List<XC_MethodHook.Unhook> mActiveHooks = new ArrayList<>();
    
    // Cache permission decisions to avoid repeated lookups
    private final Map<String, Integer> mPermissionCache = new ConcurrentHashMap<>();
    
    // Permission overrides from settings
    private final Map<String, Map<String, Integer>> mAppPermissionOverrides = new HashMap<>();
    
    // Track permission request history for logging
    private final List<PermissionRequest> mPermissionRequests = new ArrayList<>();
    private static final int MAX_REQUEST_HISTORY = 100;
    
    private Context mContext;
    private SettingsHelper mSettings;
    private boolean mBypassSignatures;
    private boolean mLogRequests;
    private String mDefaultBehavior;
    private FeatureManager mFeatureManager;
    
    /**
     * Handle a package being loaded.
     *
     * @param context The context for this module.
     * @param lpparam The parameters for the loaded package.
     */
    @Override
    public void handleLoadPackage(Context context, XC_LoadPackage.LoadPackageParam lpparam) {
        mContext = context;
        mSettings = new SettingsHelper(context, MODULE_ID);
        mFeatureManager = FeatureManager.getInstance(context);
        
        // Register this instance as a service for other modules to use
        mFeatureManager.registerService(SERVICE_KEY, this);
        
        // Load settings
        loadSettings();
        
        // Hook permission checks in all packages
        hookPermissionChecks(lpparam);
        
        // Hook signature verification if enabled
        if (mBypassSignatures) {
            hookSignatureChecks(lpparam);
        }
    }
    
    /**
     * Load settings from SettingsHelper.
     */
    private void loadSettings() {
        mBypassSignatures = mSettings.getBoolean("bypassSignatureChecks", true);
        mLogRequests = mSettings.getBoolean("logPermissionRequests", false);
        mDefaultBehavior = mSettings.getString("defaultPermissionBehavior", "DEFAULT");
        
        // Load permission overrides
        loadPermissionOverrides();
    }
    
    /**
     * Load permission overrides from settings.
     */
    private void loadPermissionOverrides() {
        mAppPermissionOverrides.clear();
        
        try {
            String overridesJson = mSettings.getString("permissionOverrides", "[]");
            JSONArray overrides = new JSONArray(overridesJson);
            
            for (int i = 0; i < overrides.length(); i++) {
                JSONObject override = overrides.getJSONObject(i);
                String packageName = override.getString("packageName");
                
                Map<String, Integer> permissionMap = new HashMap<>();
                JSONObject permissions = override.getJSONObject("permissions");
                
                for (String permission : JSONObject.getNames(permissions)) {
                    String value = permissions.getString(permission);
                    int result = "GRANT".equals(value) ? PERMISSION_GRANTED : 
                                 "DENY".equals(value) ? PERMISSION_DENIED : 
                                 PERMISSION_DEFAULT;
                    
                    permissionMap.put(permission, result);
                }
                
                mAppPermissionOverrides.put(packageName, permissionMap);
            }
            
            log("Loaded permission overrides for " + mAppPermissionOverrides.size() + " packages");
            
        } catch (JSONException e) {
            LoggingHelper.error(TAG, "Error loading permission overrides", e);
        }
    }
    
    /**
     * Check if a package is targeted by this module.
     *
     * @param packageName The package name to check.
     * @return true if the package is targeted, false otherwise.
     */
    private boolean isTargetedPackage(String packageName) {
        String[] targetApps = mSettings.getStringArray("targetApps");
        if (targetApps == null || targetApps.length == 0) {
            // If no apps are specified, all apps are targeted
            return true;
        }
        
        for (String app : targetApps) {
            if (app.equals(packageName)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Hook permission check methods.
     *
     * @param lpparam The parameters for the loaded package.
     */
    private void hookPermissionChecks(XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook ContextImpl.checkPermission
        try {
            XC_MethodHook permissionCheck = new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    String permission = (String) param.args[0];
                    int pid = (int) param.args[1];
                    int uid = (int) param.args[2];
                    
                    // Skip self-checks
                    if (pid == Process.myPid() || uid == Process.myUid()) {
                        return;
                    }
                    
                    // Get the package name for the UID
                    String packageName = getPackageNameForUid(uid);
                    if (packageName == null || !isTargetedPackage(packageName)) {
                        return;
                    }
                    
                    // Check the permission override
                    Integer result = checkPermissionOverride(packageName, permission);
                    if (result != null) {
                        // Log the permission request if enabled
                        if (mLogRequests) {
                            logPermissionRequest(packageName, permission, result == PERMISSION_GRANTED);
                        }
                        
                        // Set the result and skip the original method
                        param.setResult(result);
                    }
                }
            };
            
            // Hook Android's main permission checking methods
            Class<?> contextImplClass = XposedHelpers.findClass("android.app.ContextImpl", lpparam.classLoader);
            mActiveHooks.add(XposedHelpers.findAndHookMethod(contextImplClass, 
                    "checkPermission", String.class, int.class, int.class, permissionCheck));
            
            // Also hook checkCallingPermission and other variants
            mActiveHooks.add(XposedHelpers.findAndHookMethod(contextImplClass, 
                    "checkCallingPermission", String.class, permissionCheck));
            
            mActiveHooks.add(XposedHelpers.findAndHookMethod(contextImplClass, 
                    "checkCallingOrSelfPermission", String.class, permissionCheck));
            
            mActiveHooks.add(XposedHelpers.findAndHookMethod(contextImplClass, 
                    "checkSelfPermission", String.class, permissionCheck));
            
            // Hook enforcePermission methods too
            mActiveHooks.add(XposedHelpers.findAndHookMethod(contextImplClass, 
                    "enforcePermission", String.class, int.class, int.class, String.class, 
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            String permission = (String) param.args[0];
                            int pid = (int) param.args[1];
                            int uid = (int) param.args[2];
                            
                            // Skip self-checks
                            if (pid == Process.myPid() || uid == Process.myUid()) {
                                return;
                            }
                            
                            // Get the package name for the UID
                            String packageName = getPackageNameForUid(uid);
                            if (packageName == null || !isTargetedPackage(packageName)) {
                                return;
                            }
                            
                            // Check the permission override
                            Integer result = checkPermissionOverride(packageName, permission);
                            if (result != null && result == PERMISSION_GRANTED) {
                                // Log the permission request if enabled
                                if (mLogRequests) {
                                    logPermissionRequest(packageName, permission, true);
                                }
                                
                                // Skip the original method
                                param.setResult(null);
                            }
                        }
                    }));
            
            // Hook ActivityManagerService for runtime permission checks
            if (lpparam.packageName.equals("android") || lpparam.packageName.equals("system")) {
                try {
                    Class<?> amsClass = XposedHelpers.findClass("com.android.server.am.ActivityManagerService", 
                            lpparam.classLoader);
                    
                    mActiveHooks.add(XposedHelpers.findAndHookMethod(amsClass, 
                            "checkPermission", String.class, int.class, int.class, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    String permission = (String) param.args[0];
                                    int pid = (int) param.args[1];
                                    int uid = (int) param.args[2];
                                    
                                    // Skip self-checks
                                    if (pid == Process.myPid() || uid == Process.myUid()) {
                                        return;
                                    }
                                    
                                    // Get the package name for the UID
                                    String packageName = getPackageNameForUid(uid);
                                    if (packageName == null || !isTargetedPackage(packageName)) {
                                        return;
                                    }
                                    
                                    // Check the permission override
                                    Integer result = checkPermissionOverride(packageName, permission);
                                    if (result != null) {
                                        // Log the permission request if enabled
                                        if (mLogRequests) {
                                            logPermissionRequest(packageName, permission, result == PERMISSION_GRANTED);
                                        }
                                        
                                        // Set the result and skip the original method
                                        param.setResult(result);
                                    }
                                }
                            }));
                } catch (Throwable t) {
                    // Not a critical error, the AMS might be different in this Android version
                    LoggingHelper.debug(TAG, "Could not hook ActivityManagerService: " + t.getMessage());
                }
            }
            
            // Hook requestPermissions in Activity
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Class<?> activityClass = XposedHelpers.findClass("android.app.Activity", lpparam.classLoader);
                mActiveHooks.add(XposedHelpers.findAndHookMethod(activityClass, 
                        "requestPermissions", String[].class, int.class, new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                String[] permissions = (String[]) param.args[0];
                                int requestCode = (int) param.args[1];
                                
                                // Get the activity's package name
                                String packageName = null;
                                if (param.thisObject != null) {
                                    packageName = (String) XposedHelpers.callMethod(param.thisObject, "getPackageName");
                                }
                                
                                if (packageName == null || !isTargetedPackage(packageName)) {
                                    return;
                                }
                                
                                // Check if we should handle this request
                                boolean shouldHandle = false;
                                int[] grantResults = new int[permissions.length];
                                
                                for (int i = 0; i < permissions.length; i++) {
                                    Integer result = checkPermissionOverride(packageName, permissions[i]);
                                    if (result != null) {
                                        shouldHandle = true;
                                        grantResults[i] = result;
                                        
                                        // Log the permission request if enabled
                                        if (mLogRequests) {
                                            logPermissionRequest(packageName, permissions[i], result == PERMISSION_GRANTED);
                                        }
                                    } else {
                                        grantResults[i] = PERMISSION_DENIED; // Default to denied
                                    }
                                }
                                
                                if (shouldHandle) {
                                    // Call onRequestPermissionsResult directly
                                    XposedHelpers.callMethod(param.thisObject, 
                                            "onRequestPermissionsResult", requestCode, permissions, grantResults);
                                    
                                    // Skip the original method
                                    param.setResult(null);
                                }
                            }
                        }));
            }
            
            log("Hooked permission checks in " + lpparam.packageName);
            
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Failed to hook permission checks in " + lpparam.packageName, t);
        }
    }
    
    /**
     * Hook signature verification methods.
     *
     * @param lpparam The parameters for the loaded package.
     */
    private void hookSignatureChecks(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            // Hook PackageManager.checkSignatures
            XC_MethodReplacement signatureCheckReplacement = new XC_MethodReplacement() {
                @Override
                protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
                    // Check if this is a targeted package
                    String callingPackage = getCallingPackageName();
                    if (callingPackage != null && isTargetedPackage(callingPackage)) {
                        // Return PackageManager.SIGNATURE_MATCH to bypass the check
                        return PackageManager.SIGNATURE_MATCH;
                    }
                    
                    // Call the original method
                    return XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args);
                }
            };
            
            Class<?> packageManagerClass = XposedHelpers.findClass("android.content.pm.PackageManager", 
                    lpparam.classLoader);
            
            // Hook both variants of checkSignatures
            mActiveHooks.add(XposedHelpers.findAndHookMethod(packageManagerClass, 
                    "checkSignatures", String.class, String.class, signatureCheckReplacement));
            
            mActiveHooks.add(XposedHelpers.findAndHookMethod(packageManagerClass, 
                    "checkSignatures", int.class, int.class, signatureCheckReplacement));
            
            // If this is the system package, hook the internal implementation as well
            if (lpparam.packageName.equals("android") || lpparam.packageName.equals("system")) {
                try {
                    Class<?> packageManagerServiceClass = XposedHelpers.findClass(
                            "com.android.server.pm.PackageManagerService", lpparam.classLoader);
                    
                    mActiveHooks.add(XposedHelpers.findAndHookMethod(packageManagerServiceClass, 
                            "checkSignaturesLP", "android.content.pm.PackageParser$Package", 
                            "android.content.pm.PackageParser$Package", signatureCheckReplacement));
                    
                    mActiveHooks.add(XposedHelpers.findAndHookMethod(packageManagerServiceClass, 
                            "compareSignatures", "android.content.pm.Signature[]", 
                            "android.content.pm.Signature[]", signatureCheckReplacement));
                } catch (Throwable t) {
                    // Not a critical error, method names may be different in this Android version
                    LoggingHelper.debug(TAG, "Could not hook internal signature methods: " + t.getMessage());
                }
            }
            
            log("Hooked signature checks in " + lpparam.packageName);
            
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Failed to hook signature checks in " + lpparam.packageName, t);
        }
    }
    
    /**
     * Check if a permission should be overridden.
     *
     * @param packageName The package requesting the permission.
     * @param permission The permission being requested.
     * @return The permission result, or null to use default behavior.
     */
    private Integer checkPermissionOverride(String packageName, String permission) {
        // Check cache first
        String cacheKey = packageName + ":" + permission;
        if (mPermissionCache.containsKey(cacheKey)) {
            return mPermissionCache.get(cacheKey);
        }
        
        // Check app-specific overrides
        Map<String, Integer> appOverrides = mAppPermissionOverrides.get(packageName);
        if (appOverrides != null && appOverrides.containsKey(permission)) {
            Integer result = appOverrides.get(permission);
            mPermissionCache.put(cacheKey, result);
            return result;
        }
        
        // Apply default behavior
        Integer defaultResult = null;
        if ("GRANT".equals(mDefaultBehavior)) {
            defaultResult = PERMISSION_GRANTED;
        } else if ("DENY".equals(mDefaultBehavior)) {
            defaultResult = PERMISSION_DENIED;
        }
        // If DEFAULT, return null to use system behavior
        
        mPermissionCache.put(cacheKey, defaultResult);
        return defaultResult;
    }
    
    /**
     * Log a permission request.
     *
     * @param packageName The package requesting the permission.
     * @param permission The permission being requested.
     * @param granted Whether the permission was granted.
     */
    private void logPermissionRequest(String packageName, String permission, boolean granted) {
        PermissionRequest request = new PermissionRequest();
        request.packageName = packageName;
        request.permission = permission;
        request.granted = granted;
        request.timestamp = System.currentTimeMillis();
        
        synchronized (mPermissionRequests) {
            mPermissionRequests.add(request);
            
            // Keep only the most recent requests
            while (mPermissionRequests.size() > MAX_REQUEST_HISTORY) {
                mPermissionRequests.remove(0);
            }
        }
    }
    
    /**
     * Get the package name for a UID.
     *
     * @param uid The UID.
     * @return The package name, or null if not found.
     */
    private String getPackageNameForUid(int uid) {
        if (mContext == null) {
            return null;
        }
        
        try {
            String[] packages = mContext.getPackageManager().getPackagesForUid(uid);
            if (packages != null && packages.length > 0) {
                return packages[0];
            }
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Error getting package name for UID: " + uid, t);
        }
        
        return null;
    }
    
    /**
     * Get the calling package name.
     *
     * @return The calling package name, or null if not found.
     */
    private String getCallingPackageName() {
        int uid = Binder.getCallingUid();
        return getPackageNameForUid(uid);
    }
    
    /**
     * Get the permission request history.
     *
     * @return A copy of the permission request history.
     */
    public List<PermissionRequest> getPermissionRequestHistory() {
        synchronized (mPermissionRequests) {
            return new ArrayList<>(mPermissionRequests);
        }
    }
    
    /**
     * Find a class with enhanced reflection capabilities.
     *
     * @param className The class name.
     * @param classLoader The class loader.
     * @return The class, or null if not found.
     */
    public Class<?> findClass(String className, ClassLoader classLoader) {
        try {
            // First try standard approach
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            // Try with more aggressive approach
            try {
                return XposedHelpers.findClass(className, classLoader);
            } catch (Throwable t) {
                LoggingHelper.error(TAG, "Failed to find class: " + className, t);
                return null;
            }
        }
    }
    
    /**
     * Find a method with enhanced reflection capabilities.
     *
     * @param clazz The class.
     * @param methodName The method name.
     * @param parameterTypes The parameter types.
     * @return The method, or null if not found.
     */
    public Method findMethod(Class<?> clazz, String methodName, Object[] parameterTypes) {
        try {
            // First try standard approach
            if (parameterTypes == null) {
                // Find any method with this name
                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    if (method.getName().equals(methodName)) {
                        method.setAccessible(true);
                        return method;
                    }
                }
                return null;
            } else {
                // Convert Object[] to Class[]
                Class<?>[] paramClasses = new Class<?>[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (parameterTypes[i] instanceof Class) {
                        paramClasses[i] = (Class<?>) parameterTypes[i];
                    } else {
                        // Try to find the class by name
                        paramClasses[i] = findClass(parameterTypes[i].toString(), clazz.getClassLoader());
                    }
                }
                
                Method method = clazz.getDeclaredMethod(methodName, paramClasses);
                method.setAccessible(true);
                return method;
            }
        } catch (Throwable t) {
            // Try with more aggressive approach
            try {
                return XposedHelpers.findMethodExact(clazz, methodName, parameterTypes);
            } catch (Throwable e) {
                LoggingHelper.error(TAG, "Failed to find method: " + methodName, e);
                return null;
            }
        }
    }
    
    /**
     * Find a constructor with enhanced reflection capabilities.
     *
     * @param clazz The class.
     * @param parameterTypes The parameter types.
     * @return The constructor, or null if not found.
     */
    public Constructor<?> findConstructor(Class<?> clazz, Object[] parameterTypes) {
        try {
            // First try standard approach
            if (parameterTypes == null) {
                // Find the default constructor
                Constructor<?> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor;
            } else {
                // Convert Object[] to Class[]
                Class<?>[] paramClasses = new Class<?>[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (parameterTypes[i] instanceof Class) {
                        paramClasses[i] = (Class<?>) parameterTypes[i];
                    } else {
                        // Try to find the class by name
                        paramClasses[i] = findClass(parameterTypes[i].toString(), clazz.getClassLoader());
                    }
                }
                
                Constructor<?> constructor = clazz.getDeclaredConstructor(paramClasses);
                constructor.setAccessible(true);
                return constructor;
            }
        } catch (Throwable t) {
            // Try with more aggressive approach
            try {
                return XposedHelpers.findConstructorExact(clazz, parameterTypes);
            } catch (Throwable e) {
                LoggingHelper.error(TAG, "Failed to find constructor", e);
                return null;
            }
        }
    }
    
    /**
     * Get the value of a field from an object.
     *
     * @param obj The object (null for static fields).
     * @param className The class name.
     * @param fieldName The field name.
     * @param classLoader The class loader.
     * @return The field value, or null if not found.
     */
    public Object getFieldValue(Object obj, String className, String fieldName, ClassLoader classLoader) {
        try {
            Class<?> clazz = findClass(className, classLoader);
            if (clazz == null) {
                return null;
            }
            
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Throwable t) {
            // Try with more aggressive approach
            try {
                return XposedHelpers.getObjectField(obj, fieldName);
            } catch (Throwable e) {
                LoggingHelper.error(TAG, "Failed to get field value: " + fieldName, e);
                return null;
            }
        }
    }
    
    /**
     * Set the value of a field in an object.
     *
     * @param obj The object (null for static fields).
     * @param className The class name.
     * @param fieldName The field name.
     * @param value The value to set.
     * @param classLoader The class loader.
     * @return true if successful, false otherwise.
     */
    public boolean setFieldValue(Object obj, String className, String fieldName, Object value, ClassLoader classLoader) {
        try {
            Class<?> clazz = findClass(className, classLoader);
            if (clazz == null) {
                return false;
            }
            
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
            return true;
        } catch (Throwable t) {
            // Try with more aggressive approach
            try {
                XposedHelpers.setObjectField(obj, fieldName, value);
                return true;
            } catch (Throwable e) {
                LoggingHelper.error(TAG, "Failed to set field value: " + fieldName, e);
                return false;
            }
        }
    }
    
    /**
     * Create a new instance of a class.
     *
     * @param className The class name.
     * @param classLoader The class loader.
     * @param constructorParams The constructor parameters.
     * @return The new instance, or null if failed.
     */
    public Object createInstance(String className, ClassLoader classLoader, Object... constructorParams) {
        try {
            Class<?> clazz = findClass(className, classLoader);
            if (clazz == null) {
                return null;
            }
            
            if (constructorParams == null || constructorParams.length == 0) {
                // Use default constructor
                Constructor<?> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } else {
                // Use XposedHelpers for constructor access
                return XposedHelpers.newInstance(clazz, constructorParams);
            }
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Failed to create instance: " + className, t);
            return null;
        }
    }
    
    /**
     * Invoke a method on an object.
     *
     * @param obj The object (null for static methods).
     * @param className The class name.
     * @param methodName The method name.
     * @param classLoader The class loader.
     * @param params The method parameters.
     * @return The method result, or null if failed.
     */
    public Object invokeMethod(Object obj, String className, String methodName, ClassLoader classLoader, Object... params) {
        try {
            Class<?> clazz;
            if (obj != null) {
                clazz = obj.getClass();
            } else {
                clazz = findClass(className, classLoader);
                if (clazz == null) {
                    return null;
                }
            }
            
            // Use XposedHelpers for method invocation
            return XposedHelpers.callMethod(obj, methodName, params);
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Failed to invoke method: " + methodName, t);
            return null;
        }
    }
    
    /**
     * Handle hot reload requests.
     * This removes active hooks so they can be reinstalled with new settings.
     */
    @Override
    public void onHotReload() {
        log("Hot reloading PermissionOverride");
        
        // Unhook all active hooks
        for (XC_MethodHook.Unhook unhook : mActiveHooks) {
            unhook.unhook();
        }
        
        // Clear the active hooks
        mActiveHooks.clear();
        
        // Clear the permission cache
        mPermissionCache.clear();
        
        // Reload settings
        loadSettings();
        
        log("PermissionOverride hot reload complete");
    }
    
    /**
     * Model for permission requests, used for logging.
     */
    public static class PermissionRequest {
        public String packageName;
        public String permission;
        public boolean granted;
        public long timestamp;
    }
    
    private void log(String message) {
        LoggingHelper.info(TAG, message);
    }

    // Implementation of IPermissionOverrideService
    @Override
    public Integer checkPermissionOverrideStatus(String packageName, String permission) {
        String cacheKey = packageName + ":" + permission;
        if (mPermissionCache.containsKey(cacheKey)) {
            return mPermissionCache.get(cacheKey);
        }

        Map<String, Integer> packageOverrides = mAppPermissionOverrides.get(packageName);
        if (packageOverrides != null && packageOverrides.containsKey(permission)) {
            Integer result = packageOverrides.get(permission);
            mPermissionCache.put(cacheKey, result);
            return result;
        }
        // If no specific override, return PERMISSION_DEFAULT to indicate module's default, not system default
        // This distinguishes from `null` which means "no opinion from this module for this specific permission entry"
        return PERMISSION_DEFAULT; 
    }

    @Override
    public Class<?> findClass(String className, ClassLoader classLoader) {
        try {
            // First try standard approach
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException e) {
            // Try with more aggressive approach
            try {
                return XposedHelpers.findClass(className, classLoader);
            } catch (Throwable t) {
                LoggingHelper.error(TAG, "Failed to find class: " + className, t);
                return null;
            }
        }
    }

    @Override
    public Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        try {
            // First try standard approach
            if (parameterTypes == null) {
                // Find any method with this name
                Method[] methods = clazz.getDeclaredMethods();
                for (Method method : methods) {
                    if (method.getName().equals(methodName)) {
                        method.setAccessible(true);
                        return method;
                    }
                }
                return null;
            } else {
                // Convert Class[] to Class<?>[]
                Class<?>[] paramClasses = new Class<?>[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (parameterTypes[i] instanceof Class) {
                        paramClasses[i] = (Class<?>) parameterTypes[i];
                    } else {
                        // Try to find the class by name
                        paramClasses[i] = findClass(parameterTypes[i].toString(), clazz.getClassLoader());
                    }
                }
                
                Method method = clazz.getDeclaredMethod(methodName, paramClasses);
                method.setAccessible(true);
                return method;
            }
        } catch (Throwable t) {
            // Try with more aggressive approach
            try {
                return XposedHelpers.findMethodExact(clazz, methodName, parameterTypes);
            } catch (Throwable e) {
                LoggingHelper.error(TAG, "Failed to find method: " + methodName, e);
                return null;
            }
        }
    }

    @Override
    public Method findMethodWithObjects(Class<?> clazz, String methodName, Object[] rawParameterTypes) {
        if (clazz == null || methodName == null) return null;
        try {
            Class<?>[] parameterClasses = null;
            if (rawParameterTypes != null) {
                parameterClasses = new Class<?>[rawParameterTypes.length];
                for (int i = 0; i < rawParameterTypes.length; i++) {
                    if (rawParameterTypes[i] instanceof Class) {
                        parameterClasses[i] = (Class<?>) rawParameterTypes[i];
                    } else if (rawParameterTypes[i] instanceof String) {
                        // Attempt to load class by name if a string is provided
                        try {
                            parameterClasses[i] = XposedHelpers.findClass((String) rawParameterTypes[i], clazz.getClassLoader());
                        } catch (XposedHelpers.ClassNotFoundError e) {
                            LoggingHelper.warning(TAG, "Could not find class for parameter type string: " + rawParameterTypes[i]);
                            return null; // Or throw, or handle error appropriately
                        }
                    } else {
                        LoggingHelper.warning(TAG, "Unsupported parameter type object: " + rawParameterTypes[i]);
                        return null; // Or throw
                    }
                }
            }
            return XposedHelpers.findMethodExact(clazz, methodName, parameterClasses);
        } catch (Throwable t) {
            log("Error finding method (object params) " + methodName + " in class " + clazz.getName() + ": " + t.getMessage());
            return null;
        }
    }

    @Override
    public Constructor<?> findConstructor(Class<?> clazz, Class<?>... parameterTypes) {
        try {
            // First try standard approach
            if (parameterTypes == null) {
                // Find the default constructor
                Constructor<?> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor;
            } else {
                // Convert Class[] to Class<?>[]
                Class<?>[] paramClasses = new Class<?>[parameterTypes.length];
                for (int i = 0; i < parameterTypes.length; i++) {
                    if (parameterTypes[i] instanceof Class) {
                        paramClasses[i] = (Class<?>) parameterTypes[i];
                    } else {
                        // Try to find the class by name
                        paramClasses[i] = findClass(parameterTypes[i].toString(), clazz.getClassLoader());
                    }
                }
                
                Constructor<?> constructor = clazz.getDeclaredConstructor(paramClasses);
                constructor.setAccessible(true);
                return constructor;
            }
        } catch (Throwable t) {
            // Try with more aggressive approach
            try {
                return XposedHelpers.findConstructorExact(clazz, parameterTypes);
            } catch (Throwable e) {
                LoggingHelper.error(TAG, "Failed to find constructor", e);
                return null;
            }
        }
    }

    @Override
    public Constructor<?> findConstructorWithObjects(Class<?> clazz, Object[] rawParameterTypes) {
         if (clazz == null) return null;
        try {
            Class<?>[] parameterClasses = null;
            if (rawParameterTypes != null) {
                parameterClasses = new Class<?>[rawParameterTypes.length];
                for (int i = 0; i < rawParameterTypes.length; i++) {
                     if (rawParameterTypes[i] instanceof Class) {
                        parameterClasses[i] = (Class<?>) rawParameterTypes[i];
                    } else if (rawParameterTypes[i] instanceof String) {
                        try {
                            parameterClasses[i] = XposedHelpers.findClass((String) rawParameterTypes[i], clazz.getClassLoader());
                        } catch (XposedHelpers.ClassNotFoundError e) {
                            LoggingHelper.warning(TAG, "Could not find class for constructor parameter type string: " + rawParameterTypes[i]);
                            return null;
                        }
                    } else {
                        LoggingHelper.warning(TAG, "Unsupported constructor parameter type object: " + rawParameterTypes[i]);
                        return null;
                    }
                }
            }
            return XposedHelpers.findConstructorExact(clazz, parameterClasses);
        } catch (Throwable t) {
            log("Error finding constructor (object params) in class " + clazz.getName() + ": " + t.getMessage());
            return null;
        }
    }

    @Override
    public Object getFieldValue(Object obj, String className, String fieldName, ClassLoader classLoader) {
        try {
            Class<?> clazz = findClass(className, classLoader);
            if (clazz == null) {
                return null;
            }
            
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(obj);
        } catch (Throwable t) {
            // Try with more aggressive approach
            try {
                return XposedHelpers.getObjectField(obj, fieldName);
            } catch (Throwable e) {
                LoggingHelper.error(TAG, "Failed to get field value: " + fieldName, e);
                return null;
            }
        }
    }

    @Override
    public boolean setFieldValue(Object obj, String className, String fieldName, Object value, ClassLoader classLoader) {
        try {
            Class<?> clazz = findClass(className, classLoader);
            if (clazz == null) {
                return false;
            }
            
            Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(obj, value);
            return true;
        } catch (Throwable t) {
            // Try with more aggressive approach
            try {
                XposedHelpers.setObjectField(obj, fieldName, value);
                return true;
            } catch (Throwable e) {
                LoggingHelper.error(TAG, "Failed to set field value: " + fieldName, e);
                return false;
            }
        }
    }

    @Override
    public Object createInstance(String className, ClassLoader classLoader, Object... constructorParams) {
        try {
            Class<?> clazz = findClass(className, classLoader);
            if (clazz == null) {
                return null;
            }
            
            if (constructorParams == null || constructorParams.length == 0) {
                // Use default constructor
                Constructor<?> constructor = clazz.getDeclaredConstructor();
                constructor.setAccessible(true);
                return constructor.newInstance();
            } else {
                // Use XposedHelpers for constructor access
                return XposedHelpers.newInstance(clazz, constructorParams);
            }
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Failed to create instance: " + className, t);
            return null;
        }
    }

    @Override
    public Object invokeMethod(Object obj, String className, String methodName, ClassLoader classLoader, Object... params) {
        try {
            Class<?> clazz;
            if (obj != null) {
                clazz = obj.getClass();
            } else {
                clazz = findClass(className, classLoader);
                if (clazz == null) {
                    return null;
                }
            }
            
            // Use XposedHelpers for method invocation
            return XposedHelpers.callMethod(obj, methodName, params);
        } catch (Throwable t) {
            LoggingHelper.error(TAG, "Failed to invoke method: " + methodName, t);
            return null;
        }
    }
} 