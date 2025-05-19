package com.wobbz.permissionoverride;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

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

import com.github.libxposed.api.IXposedHookLoadPackage;
import com.github.libxposed.api.XC_MethodHook;
import com.github.libxposed.api.XposedInterface;
import com.github.libxposed.api.XposedHelpers;
import com.github.libxposed.api.callbacks.XC_LoadPackage;

/**
 * PermissionOverride module provides functionality to override permission checks,
 * bypass signature verification, and enhance reflection capabilities.
 */
public class PermissionOverrideModule implements IModulePlugin, IHotReloadable, IXposedHookLoadPackage, IPermissionOverrideService {
    private static final String TAG = "PermissionOverrideModule";
    private static final String MODULE_ID = "com.wobbz.PermissionOverride";
    
    // Service key for other modules to find this module
    public static final String SERVICE_KEY = "com.wobbz.PermissionOverride.service";
    
    // Permission result values
    public static final int PERMISSION_GRANTED = android.content.pm.PackageManager.PERMISSION_GRANTED;
    public static final int PERMISSION_DENIED = android.content.pm.PackageManager.PERMISSION_DENIED;
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
    
    private IPermissionOverrideService mService = null;
    private final Set<String> forcedPermissions = new HashSet<>();
    private final Set<String> suppressedPermissions = new HashSet<>();

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mService = IPermissionOverrideService.Stub.asInterface(service);
            XposedInterface.Utils.log(TAG + ": Connected to PermissionOverrideService");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            XposedInterface.Utils.log(TAG + ": Disconnected from PermissionOverrideService");
        }
    };

    /**
     * Handle a package being loaded.
     *
     * @param context The context for this module.
     * @param lpparam The parameters for the loaded package.
     */
    @Override
    public void handleLoadPackage(Context context, XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
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

        if (lpparam.packageName.equals("android")) {
            connectToService(lpparam);

            // Hook checkPermission
            XposedHelpers.findAndHookMethod(
                    "com.android.server.pm.permission.PermissionManagerService",
                    lpparam.classLoader,
                    "checkPermission",
                    String.class, String.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.Param param) throws Throwable {
                            String permissionName = (String) param.args[0];
                            String pkgName = (String) param.args[1];
                            // int callingUid = (int) param.args[2]; // Not directly used but available

                            if (mService != null) {
                                try {
                                    if (mService.P_isAppPermissionForced(pkgName, permissionName)) {
                                        param.setResult(android.content.pm.PackageManager.PERMISSION_GRANTED);
                                        XposedInterface.Utils.log(TAG + ": Forced permission GRANTED for " + pkgName + " / " + permissionName);
                                    } else if (mService.P_isAppPermissionSuppressed(pkgName, permissionName)) {
                                        param.setResult(android.content.pm.PackageManager.PERMISSION_DENIED);
                                        XposedInterface.Utils.log(TAG + ": Suppressed permission DENIED for " + pkgName + " / " + permissionName);
                                    }
                                } catch (RemoteException e) {
                                    XposedInterface.Utils.log(TAG + ": RemoteException while checking permission: " + e.getMessage());
                                }
                            } else {
                                // Fallback or log if service not connected
                                if (forcedPermissions.contains(pkgName + "/" + permissionName)) {
                                    param.setResult(android.content.pm.PackageManager.PERMISSION_GRANTED);
                                } else if (suppressedPermissions.contains(pkgName + "/" + permissionName)) {
                                     param.setResult(android.content.pm.PackageManager.PERMISSION_DENIED);
                                }
                            }
                        }
                    });

            // Hook grantRuntimePermission
            XposedHelpers.findAndHookMethod(
                    "com.android.server.pm.permission.PermissionManagerService",
                    lpparam.classLoader,
                    "grantRuntimePermission",
                    String.class, String.class, int.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.Param param) throws Throwable {
                            String packageName = (String) param.args[0];
                            String permName = (String) param.args[1];
                            if (mService != null) {
                                try {
                                    if (mService.P_isAppPermissionSuppressed(packageName, permName)) {
                                        XposedInterface.Utils.log(TAG + ": Attempt to grant suppressed permission " + permName + " for " + packageName + ". Preventing grant.");
                                        param.setResult(null); // Prevent permission grant
                                    }
                                } catch (RemoteException e) {
                                    XposedInterface.Utils.log(TAG + ": RemoteException in grantRuntimePermission hook: " + e.getMessage());
                                }
                            }
                        }
                    });

            // Hook revokeRuntimePermission
            XposedHelpers.findAndHookMethod(
                    "com.android.server.pm.permission.PermissionManagerService",
                    lpparam.classLoader,
                    "revokeRuntimePermission",
                    String.class, String.class, int.class, String.class, // Added String.class for reason
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.Param param) throws Throwable {
                            String packageName = (String) param.args[0];
                            String permName = (String) param.args[1];
                            if (mService != null) {
                                try {
                                    if (mService.P_isAppPermissionForced(packageName, permName)) {
                                        XposedInterface.Utils.log(TAG + ": Attempt to revoke forced permission " + permName + " for " + packageName + ". Preventing revocation.");
                                        param.setResult(null); // Prevent permission revocation
                                    }
                                } catch (RemoteException e) {
                                    XposedInterface.Utils.log(TAG + ": RemoteException in revokeRuntimePermission hook: " + e.getMessage());
                                }
                            }
                        }
                    });
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
        // Example: Hooking checkPermission in ActivityManagerService
        // Note: This is a simplified example. Real-world usage might involve more classes or specific methods.
        try {
            XposedHelpers.findAndHookMethod(Context.class, "checkPermission", String.class, int.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(XC_MethodHook.Param param) throws Throwable {
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
            });

            XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", lpparam.classLoader, "checkPermission", String.class, String.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(XC_MethodHook.Param param) throws Throwable {
                    String permission = (String) param.args[0];
                    String packageName = (String) param.args[1];
                    Integer result = checkPermissionOverride(packageName, permission);
                    if (result != null) {
                        if (result == PERMISSION_GRANTED) {
                            param.setResult(true); // Assuming this method returns boolean for granted
                        } else if (result == PERMISSION_DENIED) {
                            param.setResult(false); // Assuming this method returns boolean for denied
                        }
                    }
                    if (mLogRequests) {
                        logPermissionRequest(packageName, permission, result != null && result == PERMISSION_GRANTED);
                    }
                }
            });

            // For services that manage permissions using their own internal checks.
            // Example: Some system services might have custom permission checking logic.
            // This requires identifying specific methods in those services.
            // For instance, if a hypothetical "CustomPermissionService" has "verifyAccess":
            /*
            XposedHelpers.findAndHookMethod("com.android.server.CustomPermissionService", lpparam.classLoader, "verifyAccess",
                String.class, // packageName
                String.class, // permissionName
                int.class,    // callingUid
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.Param param) throws Throwable {
                        String packageName = (String) param.args[0];
                        String permission = (String) param.args[1];
                        Integer result = checkPermissionOverride(packageName, permission);
                        if (result != null) {
                            if (result == PERMISSION_GRANTED) {
                                param.setResult(true); // Assuming this method returns boolean for granted
                            } else if (result == PERMISSION_DENIED) {
                                param.setResult(false); // Assuming this method returns boolean for denied
                            }
                        }
                        if (mLogRequests) {
                            logPermissionRequest(packageName, permission, result != null && result == PERMISSION_GRANTED);
                        }
                    }
                });
            */

            // Hooking ContentProvider.checkPermission methods if necessary
            // This is more complex due to how ContentProviders are accessed.
            // Usually, permission checks happen before calling the provider or within its methods.
            // Example: Hooking a specific ContentProvider's query/insert/update/delete if they do custom checks.
            /*
            XposedHelpers.findAndHookMethod("android.content.ContentProvider", lpparam.classLoader,
                "checkPermission", // This is a hypothetical common check method in ContentProvider subclasses
                String.class, // permission
                int.class,    // pid
                int.class,    // uid
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(XC_MethodHook.Param param) throws Throwable {
                        // Similar override logic
                    }
            });
            */

            // Fallback for older Android versions or specific OEM implementations if needed
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                // Example: Hooking checkUidPermission for older systems
                try {
                    XposedHelpers.findAndHookMethod(
                        "com.android.server.pm.PackageManagerService", // May vary
                        lpparam.classLoader,
                        "checkUidPermission",
                        String.class, int.class,
                        new XC_MethodHook() {
                            @Override
                            protected void beforeHookedMethod(XC_MethodHook.Param param) throws Throwable {
                                String permissionName = (String) param.args[0];
                                int uid = (Integer) param.args[1];
                                String[] packages = lpparam.appInfo.packageName == null ? null : new String[]{lpparam.appInfo.packageName}; // Simplified
                                if (packages == null && mContext != null) {
                                   packages = mContext.getPackageManager().getPackagesForUid(uid);
                                }

                                if (packages != null) {
                                    for (String pkgName : packages) {
                                        Integer result = checkPermissionOverride(pkgName, permissionName);
                                        if (result != null && result == PERMISSION_GRANTED) {
                                            param.setResult(android.content.pm.PackageManager.PERMISSION_GRANTED);
                                            return;
                                        }
                                        if (result != null && result == PERMISSION_DENIED) {
                                            param.setResult(android.content.pm.PackageManager.PERMISSION_DENIED);
                                            // Potentially log or take action
                                            return; // If one package mapped to UID is denied, result is denial.
                                        }
                                    }
                                }
                            }
                        });
                } catch (Throwable t) {
                    LoggingHelper.log(TAG, "Failed to hook checkUidPermission (old systems): " + t.getMessage(), t);
                }
            }

        } catch (Throwable t) {
            LoggingHelper.log(TAG, "Error hooking permission checks: " + t.getMessage(), t);
        }
    }
    
    /**
     * Hook signature verification methods to bypass them if enabled.
     *
     * @param lpparam The parameters for the loaded package.
     */
    private void hookSignatureChecks(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod("com.android.server.pm.PackageManagerService", lpparam.classLoader, "compareSignatures",
                    "android.content.pm.Signature[]", "android.content.pm.Signature[]", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(XC_MethodHook.Param param) throws Throwable {
                    if (mBypassSignatures) {
                        param.setResult(android.content.pm.PackageManager.SIGNATURE_MATCH);
                    }
                }
            });

            // Hook for checking signing certificates (platform vs. app)
            XposedHelpers.findAndHookMethod("com.android.server.pm.PackageManagerServiceUtils", lpparam.classLoader, "compareSignatures",
                "android.content.pm.Signature[]", "android.content.pm.Signature[]", "boolean", // isPlatformAllowed
                new XC_MethodHook() {
                     @Override
                    protected void beforeHookedMethod(XC_MethodHook.Param param) throws Throwable {
                        if (mBypassSignatures) {
                             param.setResult(android.content.pm.PackageManager.SIGNATURE_MATCH);
                        }
                    }
                });

            // Some apps use PackageInfo.signatures
             XposedHelpers.findAndHookMethod("android.app.ApplicationPackageManager", lpparam.classLoader, "getPackageInfo", String.class, int.class, new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(XC_MethodHook.Param param) throws Throwable {
                    if (mBypassSignatures && param.getResult() != null) {
                        android.content.pm.PackageInfo pkgInfo = (android.content.pm.PackageInfo) param.getResult();
                        String callingPackage = getCallingPackageName(); // May need improvement to get actual caller
                        if (pkgInfo != null && callingPackage != null && isTargetedPackage(callingPackage)) {
                           // Potentially modify pkgInfo.signatures here if needed, e.g., to match platform sigs
                           // This is risky and app-specific.
                           // For now, primarily relying on compareSignatures hooks.
                        }
                    }
                }
            });
            
            // On Android P and above, checkSignatures is used
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                XposedHelpers.findAndHookMethod("com.android.server.pm.PackageManagerService", lpparam.classLoader, "checkSignatures",
                    String.class, String.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(XC_MethodHook.Param param) throws Throwable {
                            if (mBypassSignatures) {
                                param.setResult(android.content.pm.PackageManager.SIGNATURE_MATCH);
                            }
                        }
                    });
            }

        } catch (Throwable t) {
            LoggingHelper.log(TAG, "Error hooking signature checks: " + t.getMessage(), t);
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
    public boolean P_isAppPermissionForced(String packageName, String permission) {
        Map<String, Integer> packageOverrides = mAppPermissionOverrides.get(packageName);
        if (packageOverrides != null && packageOverrides.containsKey(permission)) {
            return packageOverrides.get(permission) == PERMISSION_GRANTED;
        }
        return false;
    }
    
    @Override
    public boolean P_isAppPermissionSuppressed(String packageName, String permission) {
        Map<String, Integer> packageOverrides = mAppPermissionOverrides.get(packageName);
        if (packageOverrides != null && packageOverrides.containsKey(permission)) {
            return packageOverrides.get(permission) == PERMISSION_DENIED;
        }
        return false;
    }

    @Override
    public Class<?> findClass(String className, ClassLoader classLoader) {
        // Implementation for the interface, using XposedHelpers
        try {
            return XposedHelpers.findClass(className, classLoader);
        } catch (XposedHelpers.ClassNotFoundError e) {
            log("IPermissionOverrideService: Class not found: " + className);
            return null;
        }
    }

    @Override
    public Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) {
        // Implementation for the interface
        if (clazz == null) return null;
        try {
            return XposedHelpers.findMethodExact(clazz, methodName, parameterTypes);
        } catch (NoSuchMethodError e) {
            log("IPermissionOverrideService: Method not found: " + methodName);
            return null;
        }
    }

    @Override
    public Method findMethodWithObjects(Class<?> clazz, String methodName, Object[] rawParameterTypes) {
        // Implementation for the interface
        if (clazz == null) return null;
        try {
            return XposedHelpers.findMethodExact(clazz, methodName, rawParameterTypes);
        } catch (NoSuchMethodError e) {
            log("IPermissionOverrideService: Method (with objects) not found: " + methodName);
            return null;
        }    
    }

    @Override
    public Constructor<?> findConstructor(Class<?> clazz, Class<?>... parameterTypes) {
        // Implementation for the interface
        if (clazz == null) return null;
        try {
            return XposedHelpers.findConstructorExact(clazz, parameterTypes);
        } catch (NoSuchMethodError e) {
            log("IPermissionOverrideService: Constructor not found for class " + clazz.getName());
            return null;
        }
    }

    @Override
    public Constructor<?> findConstructorWithObjects(Class<?> clazz, Object[] rawParameterTypes) {
        // Implementation for the interface
        if (clazz == null) return null;
        try {
            return XposedHelpers.findConstructorExact(clazz, rawParameterTypes);
        } catch (NoSuchMethodError e) {
            log("IPermissionOverrideService: Constructor (with objects) not found for class " + clazz.getName());
            return null;
        }
    }

    @Override
    public Object getFieldValue(Object obj, String className, String fieldName, ClassLoader classLoader) {
        // Implementation for the interface
        try {
            Class<?> targetClass = null;
            if (obj != null) {
                targetClass = obj.getClass();
            } else if (className != null) {
                targetClass = XposedHelpers.findClass(className, classLoader);
            } else {
                 log("IPermissionOverrideService:getFieldValue - Both object and class name are null.");
                return null;
            }
            return XposedHelpers.getObjectField(obj, fieldName);
        } catch (XposedHelpers.ClassNotFoundError e) {
            log("IPermissionOverrideService: Class not found for getFieldValue: " + className);
            return null;
        } catch (NoSuchFieldError e) {
            log("IPermissionOverrideService: Field not found for getFieldValue: " + fieldName);
            return null;
        }
    }

    @Override
    public boolean setFieldValue(Object obj, String className, String fieldName, Object value, ClassLoader classLoader) {
        // Implementation for the interface
         try {
            Class<?> targetClass = null;
            if (obj != null) {
                targetClass = obj.getClass();
            } else if (className != null) {
                targetClass = XposedHelpers.findClass(className, classLoader);
            } else {
                log("IPermissionOverrideService:setFieldValue - Both object and class name are null.");
                return false;
            }
            XposedHelpers.setObjectField(obj, fieldName, value);
            return true;
        } catch (XposedHelpers.ClassNotFoundError e) {
            log("IPermissionOverrideService: Class not found for setFieldValue: " + className);
            return false;
        } catch (NoSuchFieldError e) {
            log("IPermissionOverrideService: Field not found for setFieldValue: " + fieldName);
            return false;
        }
    }

    @Override
    public Object createInstance(String className, ClassLoader classLoader, Object... constructorParams) {
        // Implementation for the interface
        try {
            Class<?> clazz = XposedHelpers.findClass(className, classLoader);
            return XposedHelpers.newInstance(clazz, constructorParams);
        } catch (XposedHelpers.ClassNotFoundError e) {
            log("IPermissionOverrideService: Class not found for createInstance: " + className);
            return null;
        } catch (Throwable t) { // newInstance can throw various things
            log("IPermissionOverrideService: Error creating instance of " + className + ": " + t.getMessage());
            return null;
        }
    }

    @Override
    public Object invokeMethod(Object obj, String className, String methodName, ClassLoader classLoader, Object... params) {
        // Implementation for the interface
        try {
            Class<?> targetClass = null;
            if (obj != null) {
                targetClass = obj.getClass();
            } else if (className != null) {
                targetClass = XposedHelpers.findClass(className, classLoader);
            } else {
                 log("IPermissionOverrideService:invokeMethod - Both object and class name are null.");
                return null;
            }
            return XposedHelpers.callMethod(obj, methodName, params);
        } catch (XposedHelpers.ClassNotFoundError e) {
            log("IPermissionOverrideService: Class not found for invokeMethod: " + className);
            return null;
        } catch (Throwable t) { // callMethod can throw various things
             log("IPermissionOverrideService: Error invoking method " + methodName + ": " + t.getMessage());
            return null;
        }
    }

    private void connectToService(XC_LoadPackage.LoadPackageParam lpparam) {
        // This method should run early, perhaps from handleLoadPackage or a similar hook point
        // For system_server ("android" package), we need to find the right context.
        Context systemContext = (Context) XposedHelpers.callStaticMethod(
                XposedHelpers.findClass("android.app.ActivityThread", lpparam.classLoader),
                "getSystemContext");

        if (systemContext != null) {
            Intent intent = new Intent();
            intent.setComponent(new ComponentName("com.wobbz.permissionoverride", "com.wobbz.permissionoverride.PermissionOverrideService"));
            systemContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            XposedInterface.Utils.log(TAG + ": Attempting to bind to PermissionOverrideService from system_server.");
        } else {
            XposedInterface.Utils.log(TAG + ": Could not get system context to bind service.");
        }
    }
} 